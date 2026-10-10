const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json, monthOffset } = require('./support/workflows.cjs');
let f;
test.beforeEach(async ({request}) => { f=await resetFixtures(request); });

function workdays(period) {
  const days=[];
  for(let n=1;n<=21;n++) {
    const date=`${period.year}-${String(period.month).padStart(2,'0')}-${String(n).padStart(2,'0')}`;
    const weekday=new Date(`${date}T12:00:00Z`).getUTCDay();
    if(weekday!==0 && weekday!==6) days.push(date);
  }
  return days;
}

for(const frequency of ['daily','weekly','monthly']) {
  test(`${frequency}: old draft entries, independent submissions, duplicate protection and red late marks`, async ({page,request}) => {
    const projectId=frequency==='daily'?f.dailyProjectId:frequency==='weekly'?f.weeklyProjectId:f.projectId;
    const actor=await apiAs(request,'employee');
    const period=monthOffset(f.today,-2);
    const dates=workdays(period);
    const first=dates[0], second=frequency==='weekly'?dates[8]:dates[1];
    const sheet=await json(await actor.post('/api/timesheets',{params:{year:period.year,month:period.month,companyId:f.companyId}}));
    const add=async date=>json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{entryDate:date,projectId,hours:'4',notes:'Historical work'}}));
    await add(first); await add(second);
    await authenticatePage(page,request,'employee',f.companyId);
    await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${first}`);
    const firstInput=page.getByRole('spinbutton',{name:`Hours for ${first}`,exact:true});
    await expect(firstInput).toBeEnabled();
    await firstInput.fill('5'); await firstInput.blur();
    await expect.poll(async()=> (await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:first}}))).totalHours).toBe(frequency==='monthly'?9:5);
    const singleLabel=/^Submit [A-Z][a-z]{2} /;
    await page.getByRole('button',{name:singleLabel,exact:true}).click();
  await page.getByRole('dialog',{name:'Review submission'}).getByRole('button',{name:'Submit for approval',exact:true}).click();
    await expect.poll(async()=> (await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:first}}))).status).toBe('SUBMITTED');
    const duplicate=await actor.post('/api/timesheet-periods/submit',{params:{projectId,date:first}});
    expect(duplicate.status()).toBe(400);
    if(frequency!=='monthly') {
      await page.getByRole('button',{name:`Select approval period for ${second}`,exact:true}).click();
      await expect(page.getByRole('spinbutton',{name:`Hours for ${second}`,exact:true})).toBeEnabled();
      await expect(page.getByRole('button',{name:singleLabel,exact:true})).toBeEnabled();
    }
    await authenticatePage(page,request,'manager',f.companyId);
    await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${first}`);
    const late=page.getByLabel(`Late entry for ${first}`,{exact:true});
    await expect(late).toBeVisible();
    await expect(late).toHaveCSS('color','rgb(180, 35, 53)');
    const reviewer=await apiAs(request,'manager');
    let snapshot=await json(await reviewer.get('/api/timesheet-periods',{params:{projectId,date:first,userId:f.employeeId}}));
    expect(snapshot.timeEntries.find(entry=>entry.entryDate===first).late).toBe(true);
    const rejected=await json(await reviewer.post(`/api/timesheet-periods/${snapshot.id}/decision`,{data:{approve:false,comment:'Clarify the daily notes'}}));
    expect(rejected.late).toBe(true);
    await actor.post('/api/timesheet-periods/submit',{params:{projectId,date:first}}).then(json);
    snapshot=await json(await reviewer.get('/api/timesheet-periods',{params:{projectId,date:first,userId:f.employeeId}}));
    await json(await reviewer.post(`/api/timesheet-periods/${snapshot.id}/decision`,{data:{approve:true}}));
    expect((await actor.post('/api/timesheet-periods/submit',{params:{projectId,date:first}})).status()).toBe(400);
    expect((await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{entryDate:first,projectId,hours:'6'}})).status()).toBe(400);
  });
}

test('daily batch submits only chosen days and rolls back if one period is already pending',async({page,request})=>{
  const actor=await apiAs(request,'employee');
  const period=monthOffset(f.today,-1), dates=workdays(period).slice(0,3), projectId=f.dailyProjectId;
  const sheet=await json(await actor.post('/api/timesheets',{params:{year:period.year,month:period.month,companyId:f.companyId}}));
  for(const date of dates) await json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{entryDate:date,projectId,hours:'4'}}));
  await authenticatePage(page,request,'employee',f.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${dates[0]}`);
  await page.getByRole('button',{name:'Select multiple days',exact:true}).click();
  for(const date of dates.slice(0,2)) await page.getByRole('checkbox',{name:`Include ${date} in batch submission`,exact:true}).check();
  await page.getByRole('button',{name:'Submit 2 days',exact:true}).click();
  await page.getByRole('dialog',{name:'Review submission'}).getByRole('button',{name:'Submit for approval',exact:true}).click();
  for(const date of dates.slice(0,2)) await expect.poll(async()=> (await json(await actor.get('/api/timesheet-periods',{params:{projectId,date}}))).status).toBe('SUBMITTED');
  expect((await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:dates[2]}}))).status).toBe('DRAFT');
  await expect(page.getByRole('button',{name:/Submit \d+ days/})).toHaveCount(0);
  const fourth=workdays(period)[3];
  await json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{entryDate:fourth,projectId,hours:'4'}}));
  expect((await actor.post('/api/timesheet-periods/submit-batch',{params:{projectId},data:{dates:[fourth,dates[0]]}})).status()).toBe(400);
  expect((await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:fourth}}))).status).toBe('DRAFT');
});

test('weekly approval includes entries across month boundaries and links to an existing calendar',async({page,request})=>{
  const actor=await apiAs(request,'employee'), projectId=f.weeklyProjectId;
  let month;
  for(let offset=-1;offset>=-6;offset--) {
    const candidate=monthOffset(f.today,offset);
    const weekday=new Date(`${candidate.date}T12:00:00Z`).getUTCDay();
    if(weekday>=2 && weekday<=5) { month=candidate; break; }
  }
  expect(month).toBeTruthy();
  const sheet=await json(await actor.post('/api/timesheets',{params:{year:month.year,month:month.month,companyId:f.companyId}}));
  await json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{projectId,entryDate:month.date,hours:'4',notes:'Current month work'}}));
  let snapshot=await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:month.date}}));
  expect(snapshot.timesheetId).toBe(sheet.id);
  const previous=monthOffset(month.date,-1);
  const earlier=await json(await actor.post('/api/timesheets',{params:{year:previous.year,month:previous.month,companyId:f.companyId}}));
  await json(await actor.post(`/api/timesheets/${earlier.id}/time-entries`,{data:{projectId,entryDate:snapshot.periodStart,hours:'3',notes:'Previous month work'}}));
  await authenticatePage(page,request,'employee',f.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${month.date}`);
  const previousMonthName=new Date(`${snapshot.periodStart}T12:00:00Z`).toLocaleString('en-US',{month:'long',timeZone:'UTC'});
  await page.getByRole('button',{name:`Open ${previousMonthName} Days`,exact:true}).click();
  const boundaryHours=page.getByRole('spinbutton',{name:`Hours for ${snapshot.periodStart}`,exact:true});
  await expect(boundaryHours).toBeEnabled(); await boundaryHours.fill('5'); await boundaryHours.blur();
  await expect.poll(async()=>(await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:month.date}}))).totalHours).toBe(9);
  snapshot=await json(await actor.post('/api/timesheet-periods/submit',{params:{projectId,date:month.date}}));
  expect(snapshot.totalHours).toBe(9);
  await authenticatePage(page,request,'manager',f.companyId);
  await page.goto(`/timesheet/${snapshot.timesheetId}?projectId=${projectId}&date=${snapshot.periodStart}`);
  const adjacent=page.getByRole('region',{name:'Approval days outside this month'});
  await expect(adjacent).toContainText(month.date);
  await expect(adjacent).toContainText('4.00 hours');
  await expect(adjacent).toContainText('Current month work');
  await expect(adjacent.getByText('Late',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Approve',exact:true})).toBeEnabled();
});

test('weekly batch and concurrent single submissions each submit a period only once',async({request})=>{
  const actor=await apiAs(request,'employee'), projectId=f.weeklyProjectId, period=monthOffset(f.today,-1);
  const dates=workdays(period), first=dates[0], second=dates[8];
  const sheet=await json(await actor.post('/api/timesheets',{params:{year:period.year,month:period.month,companyId:f.companyId}}));
  for(const date of [first,second]) await json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{projectId,entryDate:date,hours:'4'}}));
  const responses=await Promise.all([1,2].map(()=>actor.post('/api/timesheet-periods/submit',{params:{projectId,date:first}})));
  expect(responses.map(response=>response.status()).sort()).toEqual([200,400]);
  const submitted=await json(await actor.get('/api/timesheet-periods',{params:{projectId,date:first}}));
  const history=await json(await actor.get(`/api/timesheet-periods/${submitted.id}/history`));
  expect(history.filter(event=>event.event==='SUBMITTED_LATE')).toHaveLength(1);
  const batch=await json(await actor.post('/api/timesheet-periods/submit-batch',{params:{projectId},data:{dates:[second]}}));
  expect(batch[0].status).toBe('SUBMITTED');
});
