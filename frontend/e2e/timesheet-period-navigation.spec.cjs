const {test,expect}=require('@playwright/test');
const {apiAs,resetFixtures}=require('./support/api.cjs');
const {authenticatePage}=require('./support/auth.cjs');
const {json,monthOffset}=require('./support/workflows.cjs');

for(const frequency of ['weekly','daily']) {
 test(`${frequency}: submit a period, continue with the next, and enter older missed hours`,async({page,request},testInfo)=>{
  const f=await resetFixtures(request), actor=await apiAs(request,'employee');
  const projectId=frequency==='weekly'?f.weeklyProjectId:f.dailyProjectId;
  const month=monthOffset(f.today,-1), prefix=month.date.slice(0,7);
  const dates=Array.from({length:24},(_,i)=>`${prefix}-${String(i+1).padStart(2,'0')}`);
  const first=dates.find(date=>Number(date.slice(-2))>=10 && new Date(`${date}T12:00:00Z`).getUTCDay()===(frequency==='weekly'?1:2));
  const older=dates.find(date=>[1,2,3,4,5].includes(new Date(`${date}T12:00:00Z`).getUTCDay()));
  const sheet=await json(await actor.post('/api/timesheets',{params:{year:month.year,month:month.month,companyId:f.companyId}}));
  const view=async date=>json(await actor.get('/api/timesheet-periods',{params:{projectId,date}}));
  await authenticatePage(page,request,'employee',f.companyId);
  await page.setViewportSize({width:1440,height:1000});
  await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${first}`);
  const unit=frequency==='weekly'?'Week':'Day';
  const selected=page.getByRole('region',{name:'Selected approval period'});
  const rail=page.getByRole('group',{name:`${frequency==='weekly'?'Weekly':'Daily'} approval periods`});
  const edit=async(date,value)=>{
   const hours=page.getByRole('spinbutton',{name:`Hours for ${date}`,exact:true});
   await expect(hours).toBeEnabled(); await hours.fill(value); await hours.blur();
   await expect.poll(async()=>(await view(date)).totalHours).toBe(Number(value));
  };
  await edit(first,'4');
  await page.getByRole('button',{name:`Submit ${unit}`,exact:true}).click();
  await expect(selected.getByText('Awaiting Approval',{exact:true})).toBeVisible();
  await expect(page.getByText(`This ${frequency==='weekly'?'week':'day'} has been submitted for approval. Select another period to continue entering hours.`)).toBeVisible();
  await expect(rail).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/timesheet/${sheet.id}`));
  if(frequency==='weekly') await page.screenshot({path:testInfo.outputPath('submitted-week.png'),fullPage:true});
  const firstSnapshot=await view(first), nextDate=new Date(`${firstSnapshot.periodEnd}T12:00:00Z`);
  nextDate.setUTCDate(nextDate.getUTCDate()+1);
  const next=nextDate.toISOString().slice(0,10);
  await page.getByRole('button',{name:`Go to Next ${unit}`,exact:true}).click();
  await edit(next,'6');
  await page.getByRole('button',{name:`Submit ${unit}`,exact:true}).click();
  await expect.poll(async()=>(await view(next)).status).toBe('SUBMITTED');
  expect((await view(first)).status).toBe('SUBMITTED');
  const oldSnapshot=await view(older);
  await rail.getByRole('button',{name:`Select ${frequency==='weekly'?'week':'day'} starting ${oldSnapshot.periodStart}`,exact:true}).click();
  await edit(older,'3');
  await page.getByRole('button',{name:`Submit ${unit}`,exact:true}).click();
  await expect.poll(async()=>(await view(older)).status).toBe('SUBMITTED');
  expect((await view(older)).late).toBe(true);
  await rail.getByRole('button',{name:`Select ${frequency==='weekly'?'week':'day'} starting ${firstSnapshot.periodStart}`,exact:true}).click();
  await expect(selected.getByText('Awaiting Approval',{exact:true})).toBeVisible();
  await expect(page.getByRole('spinbutton',{name:`Hours for ${first}`,exact:true})).toHaveCount(0);
  await authenticatePage(page,request,'manager',f.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${older}`);
  await expect(page.getByLabel(`Late entry for ${older}`,{exact:true})).toHaveCSS('color','rgb(180, 35, 53)');
 });
}
