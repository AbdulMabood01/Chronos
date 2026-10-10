const { test, expect } = require('@playwright/test');
const { resetFixtures, apiAs } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
let f;
test.beforeEach(async ({ request }) => { f = await resetFixtures(request); });
const shift = (date, days) => new Date(Date.parse(date) + days * 86400000).toISOString().slice(0,10);
const project = frequency => f[frequency === 'DAILY' ? 'dailyProjectId' : frequency === 'WEEKLY' ? 'weeklyProjectId' : 'projectId'];
async function view(actor, id, date) { return json(await actor.get('/api/timesheet-periods', { params: { projectId: id, date } })); }
async function submit(actor, id, date) { return actor.post('/api/timesheet-periods/submit', { params: { projectId: id, date } }); }
async function decision(actor, id, approve = true, comment = null, fallbackReason = null) { return actor.post(`/api/timesheet-periods/${id}/decision`, { data: { approve, comment, fallbackReason } }); }
async function entry(actor, id, date) {
  const s = await json(await actor.post('/api/timesheets', { params: { companyId: f.companyId, year: +date.slice(0,4), month: +date.slice(5,7) } }));
  const e = await json(await actor.post(`/api/timesheets/${s.id}/time-entries`, { data: { timesheetId: s.id, projectId: id, entryDate: date, hours: '4', notes: 'Period regression' } }));
  return { s, e };
}
for (const frequency of ['DAILY','WEEKLY','MONTHLY']) {
  test(`${frequency}: historical rejection, correction, resubmission and immutable approval`, async ({ request }) => {
    const employee = await apiAs(request,'employee'), manager = await apiAs(request,'manager');
    const id = project(frequency), date = shift(f.today,-40);
    const { s, e } = await entry(employee,id,date);
    let item = await json(await submit(employee,id,date));
    expect(item.late).toBe(true); expect(item.editable).toBe(false);
    expect((await submit(employee,id,date)).status()).toBe(400);
    expect((await decision(manager,item.id,false,' ')).status()).toBe(400);
    expect((await decision(manager,item.id,true,'x'.repeat(501))).status()).toBe(400);
    item = await json(await decision(manager,item.id,false,'Correct the hours'));
    expect(item.rejectionReason).toBe('Correct the hours'); expect(item.pdfExportEligible).toBe(false);
    await json(await employee.put(`/api/timesheets/${s.id}/time-entries/${e.id}`, { data: { projectId: id, hours: '6', notes: 'Corrected' } }));
    item = await json(await submit(employee,id,date));
    expect(item.reviewComment ?? null).toBeNull(); expect(item.late).toBe(true);
    item = await json(await decision(manager,item.id));
    expect(item.status).toBe('APPROVED'); expect(item.pdfExportEligible).toBe(true); expect(Number(item.totalHours)).toBe(6);
    expect((await decision(manager,item.id)).status()).toBe(400);
    expect((await submit(employee,id,date)).status()).toBe(400);
    expect((await employee.put(`/api/timesheets/${s.id}/time-entries/${e.id}`, { data: { projectId: id, hours: '8' } })).status()).toBe(400);
    const history = await json(await employee.get(`/api/timesheet-periods/${item.id}/history`));
    expect(history.map(h=>h.event)).toEqual(['SUBMITTED_LATE','REJECTED','SUBMITTED_LATE','APPROVED']);
  });
  test(`${frequency}: role matrix prevents self approval, rejection and unauthorized decisions`, async ({ request }) => {
    const manager = await apiAs(request,'manager'), id = project(frequency), date = shift(f.today,-14);
    await entry(manager,id,date); const item = await json(await submit(manager,id,date));
    for (const role of ['manager','employee','companyAdmin','secondAdmin','creator','platformAdmin','otherAdmin']) {
      const actor = await apiAs(request,role);
      for (const approve of [true,false]) expect((await decision(actor,item.id,approve,'Review')).status(),`${role} ${approve}`).toBe(403);
    }
    expect((await view(manager,id,date)).status).toBe('SUBMITTED');
    const owner = await apiAs(request,'projectAdmin');
    expect((await json(await decision(owner,item.id))).status).toBe('APPROVED');
  });
  test(`${frequency}: project owner cannot submit their own work or acquire approval through fallback`, async ({ request }) => {
    const owner = await apiAs(request,'projectAdmin');
    expect((await submit(owner,project(frequency),f.today)).status()).toBe(403);
    const employee = await apiAs(request,'employee'); const item = await json(await submit(employee,project(frequency),f.today));
    expect((await decision(employee,item.id,true,null,'Manager unavailable')).status()).toBe(403);
    expect((await decision(owner,item.id)).status()).toBe(403);
    expect((await json(await decision(owner,item.id,true,null,'Manager unavailable'))).status).toBe('APPROVED');
  });
  test(`${frequency}: batch failure rolls back earlier submissions`, async ({ request }) => {
    const employee = await apiAs(request,'employee'), id = project(frequency);
    const date = `${f.today.slice(0,7)}-01`;
    const response = await employee.post('/api/timesheet-periods/submit-batch',{ params: { projectId: id }, data: { dates: [date,date] } });
    expect(response.status()).toBe(400); expect((await view(employee,id,date)).status).toBe('DRAFT');
    for (const dates of [[],[date,null],Array(32).fill(date),[date,shift(date,-1)]]) {
      expect((await employee.post('/api/timesheet-periods/submit-batch',{ params: { projectId: id }, data: { dates } })).status()).toBe(400);
    }
    expect((await view(employee,id,date)).status).toBe('DRAFT');
  });
}
for (const daysAgo of [1,30,31]) test(`approved daily reopening ${daysAgo} days after close`, async ({ request }) => {
  const employee = await apiAs(request,'employee'), manager = await apiAs(request,'manager'), owner = await apiAs(request,'projectAdmin');
  const date = shift(f.today,-daysAgo), id = f.dailyProjectId;
  const item = await json(await submit(employee,id,date)); await json(await decision(manager,item.id));
  const params = { projectId:id,date }, data = { reason:'Historical correction' };
  const opening = await employee.post('/api/timesheet-periods/opening',{params,data});
  expect(opening.status()).toBe(daysAgo <= 30 ? 200 : 400);
  if(daysAgo > 30) return;
  expect((await employee.post('/api/timesheet-periods/opening',{params,data})).status()).toBe(400);
  for(const role of ['employee','manager','companyAdmin','moderator','otherAdmin','platformAdmin']) {
    const actor = await apiAs(request,role);
    expect((await actor.post(`/api/timesheet-periods/${item.id}/opening-decision`,{data:{approve:true}})).status()).toBe(403);
  }
  await json(await owner.post(`/api/timesheet-periods/${item.id}/opening-decision`,{data:{approve:true}}));
  const current = await view(employee,id,date);
  expect(current.editable).toBe(true); expect(current.openingActive).toBe(true); expect(current.pdfExportEligible).toBe(false);
});
test('weekly periods cross month and year boundaries without duplicate selections', async ({ request }) => {
  const employee = await apiAs(request,'employee'), id = f.weeklyProjectId;
  let year=+f.today.slice(0,4);
  if(new Date(Date.UTC(year,0,1)).getUTCDay()===1) year--;
  const january=`${year}-01-01`, december=`${year-1}-12-31`;
  const weekday=new Date(Date.parse(january)).getUTCDay();
  const start=shift(january,-((weekday+6)%7)), end=shift(start,6);
  for(const date of [december,january]) {
    const current = await view(employee,id,date);
    expect(current.periodStart).toBe(start); expect(current.periodEnd).toBe(end);
  }
  const periods = await json(await employee.get('/api/timesheet-periods/month',{params:{projectId:id,date:january}}));
  expect(new Set(periods.map(p=>p.periodStart)).size).toBe(periods.length);
  expect(periods[0].selectionDate).toBe(january); expect(periods[0].periodStart).toBe(start);
  const item = await json(await submit(employee,id,december));
  expect((await view(employee,id,january)).id).toBe(item.id);
  expect((await submit(employee,id,january)).status()).toBe(400);
});
test('daily batch submits distinct periods atomically and exposes only authorized queues', async ({ request }) => {
  const employee = await apiAs(request,'employee'), manager = await apiAs(request,'manager');
  const previous = new Date(Date.UTC(+f.today.slice(0,4),+f.today.slice(5,7)-2,1));
  const date = previous.toISOString().slice(0,10), dates = [date,shift(date,1)];
  const items = await json(await employee.post('/api/timesheet-periods/submit-batch',{params:{projectId:f.dailyProjectId},data:{dates}}));
  expect(items).toHaveLength(2); expect(new Set(items.map(p=>p.id)).size).toBe(2);
  const pending = await json(await manager.get('/api/timesheet-periods/pending'));
  expect(items.every(i=>pending.some(p=>p.id===i.id))).toBe(true);
  for(const role of ['employee','companyAdmin','creator','platformAdmin','otherAdmin']) {
    const actor = await apiAs(request,role); const queue = await json(await actor.get('/api/timesheet-periods/pending'));
    expect(queue.some(p=>items.some(i=>p.id===i.id))).toBe(false);
  }
});

test('Moderator can submit assigned hours but cannot approve or reject their own submission',async({request})=>{
  const moderator=await apiAs(request,'moderator'), manager=await apiAs(request,'manager'), owner=await apiAs(request,'projectAdmin');
  const moderatorId=(await json(await moderator.get('/api/auth/me'))).id;
  await json(await owner.patch(`/api/projects/${f.projectId}/assignments/${moderatorId}/planned-hours`,{params:{plannedHours:8}}));
  await entry(moderator,f.projectId,f.today);
  const item=await json(await submit(moderator,f.projectId,f.today));
  for(const approve of [true,false])expect((await decision(moderator,item.id,approve,'Self review','Fallback')).status()).toBe(403);
  expect((await json(await decision(manager,item.id))).status).toBe('APPROVED');
});
for(const [date,expected] of [['2026-03-07','2026-03-08T06:00:00Z'],['2026-03-08','2026-03-09T05:00:00Z'],['2026-10-31','2026-11-01T05:00:00Z'],['2026-11-01','2026-11-02T06:00:00Z']]) {
  test(`daily deadline uses employee timezone across DST: ${date}`,async({request})=>{
    const employee=await apiAs(request,'employee');const item=await view(employee,f.dailyProjectId,date);
    expect(item.employeeTimezone).toBe('America/Chicago');expect(Date.parse(item.submissionDeadline)).toBe(Date.parse(expected));
    expect(item.periodStart).toBe(date);expect(item.periodEnd).toBe(date);
  });
}

for(const frequency of ['DAILY','WEEKLY']) test(`${frequency}: browser submits selected period and manager approves`,async({page,request})=>{
  const { authenticatePage } = require('./support/auth.cjs');
  const employee=await apiAs(request,'employee'),id=project(frequency),date=shift(f.today,-14);
  const { s }=await entry(employee,id,date);
  await authenticatePage(page,request,'employee',f.companyId);
  await page.goto(`/timesheet/${s.id}?projectId=${id}&date=${date}`);
  await page.getByRole('button',{name:/^Submit [A-Z][a-z]{2} /}).click();
  await page.getByRole('dialog',{name:'Review submission'}).getByRole('button',{name:'Submit for approval',exact:true}).click();
  await expect.poll(async()=>(await view(employee,id,date)).status).toBe('SUBMITTED');
  await authenticatePage(page,request,'manager',f.companyId);
  await page.goto(`/timesheet/${s.id}?projectId=${id}&date=${date}`);
  await page.getByRole('button',{name:'Approve',exact:true}).click();
  await expect.poll(async()=>(await view(employee,id,date)).status).toBe('APPROVED');
  await authenticatePage(page,request,'employee',f.companyId);
  await page.goto(`/timesheet/${s.id}?projectId=${id}&date=${date}`);
  await expect(page.getByRole('region',{name:'Selected approval period'}).getByText('Approved',{exact:true})).toBeVisible();
});

for(const frequency of ['DAILY','WEEKLY'])test(`${frequency}: concurrent approval and rejection have one winner and one audit event`,async({request})=>{
  const employee=await apiAs(request,'employee'),manager=await apiAs(request,'manager'),owner=await apiAs(request,'projectAdmin');
  const id=project(frequency),item=await json(await submit(employee,id,f.today));
  const responses=await Promise.all([decision(manager,item.id,true),decision(owner,item.id,false,'Needs correction','Manager unavailable')]);
  expect(responses.map(r=>r.status()).sort((a,b)=>a-b)).toEqual([200,400]);
  const winner=await json(responses.find(r=>r.ok()));expect((await view(employee,id,f.today)).status).toBe(winner.status);
  const history=await json(await employee.get(`/api/timesheet-periods/${item.id}/history`));
  expect(history.filter(h=>['APPROVED','REJECTED'].includes(h.event))).toHaveLength(1);
});

for(const original of ['DAILY','WEEKLY','MONTHLY'])test(`${original}: scheduled frequency changes preserve history, split boundaries and can be cancelled`,async({request})=>{
  const employee=await apiAs(request,'employee'),owner=await apiAs(request,'projectAdmin');
  const id=project(original);
  const projects=await json(await owner.get('/api/projects',{params:{companyId:f.companyId}}));
  const current=projects.find(p=>p.id===id);
  const payload={code:current.code,name:current.name,status:'ACTIVE',projectManagerId:f.managerId,projectManagerHoursApproverId:f.projectAdminId};
  const past=shift(f.today,-40),before=await view(employee,id,past);
  for(const next of ['DAILY','WEEKLY','MONTHLY'].filter(v=>v!==original)) {
    const changed=await json(await owner.put(`/api/projects/${id}`,{data:{...payload,approvalFrequency:next}}));
    expect(changed.approvalFrequency).toBe(original);expect(changed.pendingApprovalFrequency).toBe(next);
    const effective=changed.approvalFrequencyEffectiveOn;expect(effective>f.today).toBe(true);
    const preceding=await view(employee,id,shift(effective,-1)),following=await view(employee,id,effective);
    expect(preceding.frequency).toBe(original);expect(preceding.periodEnd).toBe(shift(effective,-1));
    expect(following.frequency).toBe(next);expect(following.periodStart).toBe(effective);
    expect((await view(employee,id,past)).periodStart).toBe(before.periodStart);
    expect((await view(employee,id,past)).periodEnd).toBe(before.periodEnd);
    expect((await submit(employee,id,effective)).status()).toBe(400);
    await json(await owner.put(`/api/projects/${id}`,{data:{...payload,approvalFrequency:original}}));
    expect((await view(employee,id,effective)).frequency).toBe(original);
  }
});
