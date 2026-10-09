const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
test('ordinary employee receives hours-only project approval access without assigned hours', async ({ page, request }) => {
  const f = await resetFixtures(request), owner = await apiAs(request, 'projectAdmin');
  const start = f.today.slice(0,7)+'-01', end = new Date(Date.UTC(Number(f.today.slice(0,4)),Number(f.today.slice(5,7)),0)).toISOString().slice(0,10);
  await json(await owner.post(`/api/projects/${f.projectId}/assignments/${f.employeeId}`, { params:{startDate:start,endDate:end,billRate:0,plannedHours:0,approveHours:true,approveExpenses:false} }));
  const employee = await apiAs(request,'employee');
  const context = await json(await employee.get(`/api/companies/${f.companyId}/context`));
  const permissions = context.permissions.projects.find(p=>p.projectId===f.projectId);
  expect(permissions.capabilities.canReviewTime).toBe(true);
  expect(permissions.capabilities.canReviewExpenses).toBe(false);
  expect(permissions.capabilities.canManageProject).toBe(false);
  const manager = await apiAs(request,'manager');
  const sheet = await json(await manager.post('/api/timesheets',{params:{year:Number(f.today.slice(0,4)),month:Number(f.today.slice(5,7)),companyId:f.companyId}}));
  await json(await manager.post(`/api/timesheets/${sheet.id}/time-entries`,{data:{projectId:f.projectId,entryDate:f.today,hours:'1',notes:'Moderator approval test'}}));
  const submission = await json(await manager.post('/api/timesheet-periods/submit',{params:{projectId:f.projectId,date:f.today}}));
  await authenticatePage(page,request,'employee',f.companyId);
  await page.goto('/admin');
  await expect(page.getByRole('link',{name:'Approvals',exact:true})).toBeVisible();
  await page.goto(`/timesheet/${sheet.id}?projectId=${f.projectId}&date=${f.today}`);
  await page.getByRole('button',{name:'Approve',exact:true}).click();
  await expect.poll(async () => (await json(await manager.get('/api/timesheet-periods',{params:{projectId:f.projectId,date:f.today}}))).status).toBe('APPROVED');
  for (const [approveHours,approveExpenses] of [[false,true],[true,true]]) {
    await json(await owner.post(`/api/projects/${f.projectId}/assignments/${f.employeeId}`,{params:{startDate:start,endDate:end,billRate:0,plannedHours:0,approveHours,approveExpenses}}));
    const updated=await json(await employee.get(`/api/companies/${f.companyId}/context`));
    const capabilities=updated.permissions.projects.find(p=>p.projectId===f.projectId).capabilities;
    expect(capabilities.canReviewTime).toBe(approveHours);
    expect(capabilities.canReviewExpenses).toBe(approveExpenses);
    expect(capabilities.canManageProject).toBe(false);
  }
  await json(await owner.post(`/api/projects/${f.projectId}/assignments/${f.employeeId}`,{params:{startDate:start,endDate:end,billRate:0,plannedHours:1,approveHours:false,approveExpenses:false}}));
  expect((await employee.post(`/api/timesheet-periods/${submission.id}/decision`,{data:{approve:true}})).ok()).toBe(false);
});
