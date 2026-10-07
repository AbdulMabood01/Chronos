const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json, configureLetters, letterInput, letterDecision } = require('./support/workflows.cjs');
let f;
test.beforeEach(async ({ request }) => { f = await resetFixtures(request); });
const company = suffix => `/api/companies/${f.companyId}${suffix}`;
function offsetDate(days) { return new Date(Date.parse(f.today) + days * 86400000).toISOString().slice(0, 10); }
async function workingDate(days = 14) {
  const { leaveWorkingDates } = await import('../src/utils/federalHolidays.js');
  let day = days;
  while (!leaveWorkingDates(offsetDate(day), offsetDate(day)).length) day += days > 0 ? 1 : -1;
  return offsetDate(day);
}
async function sheet(actor, date = f.today) {
  return json(await actor.post('/api/timesheets', { params: { companyId: f.companyId, year: Number(date.slice(0,4)), month: Number(date.slice(5,7)) } }));
}
async function hours(actor, s, date = f.today, projectId = f.projectId) {
  return json(await actor.post(`/api/timesheets/${s.id}/time-entries`, { data: { timesheetId: s.id, projectId, entryDate: date, hours: '4', notes: 'Edge-case hours' } }));
}
async function submission(actor, projectId = f.projectId, date = f.today) {
  return json(await actor.post('/api/timesheet-periods/submit', { params: { projectId, date } }));
}
async function leave(actor, date, request, submit = true) {
  const admin = await apiAs(request, 'companyAdmin');
  const year = Number(date.slice(0,4));
  const balance = await json(await actor.get(company('/leave/balance/me'), { params: { year } }));
  if (!balance.configured) await json(await admin.put(company(`/leave/balance/${f.employeeId}`), { data: {
    year, vacationDays: 20, sickDays: 10, bereavementDays: 5, addVacationDays: 0, addSickDays: 0, reason: 'Historical test allowance', version: balance.version,
  } }));
  let item = await json(await actor.post(company('/leave/requests'), { data: { startDate: date, endDate: date, vacationType: 'VACATION' } }));
  if (submit) item = await json(await actor.post(company(`/leave/requests/${item.id}/submit`), { params: { version: item.version } }));
  return item;
}
async function letter(request) {
  const admin = await apiAs(request, 'companyAdmin'); const employee = await apiAs(request, 'employee');
  await configureLetters(admin, f.companyId);
  const item = await json(await employee.post(company('/letter-requests'), { data: letterInput }));
  return { admin, employee, item };
}
function oneWinner(responses, failureStatus) {
  expect(responses.map(r => r.status()).sort((a,b) => a-b)).toEqual([200, failureStatus]);
}

test('concurrent creation returns one timesheet for the same company and month', async ({ request }) => {
  const employee = await apiAs(request, 'employee');
  const sheets = await Promise.all([sheet(employee), sheet(employee)]);
  expect(sheets[0].id).toBe(sheets[1].id);
  const all = await json(await employee.get('/api/timesheets/my'));
  expect(all.filter(s => s.companyId === f.companyId && s.year === sheets[0].year && s.month === sheets[0].month)).toHaveLength(1);
});

test('duplicate concurrent submissions and reviews produce one decision and one history event', async ({ request }) => {
  const employee = await apiAs(request, 'employee'); const manager = await apiAs(request, 'manager'); const moderator = await apiAs(request, 'moderator');
  const s = await sheet(employee); await hours(employee, s);
  const submits = await Promise.all([0,1].map(() => employee.post('/api/timesheet-periods/submit', { params: { projectId: f.projectId, date: f.today } })));
  oneWinner(submits, 400);
  const item = await json(submits.find(r => r.ok()));
  const decisions = await Promise.all([manager, moderator].map(actor => actor.post(`/api/timesheet-periods/${item.id}/decision`, { data: { approve: true } })));
  oneWinner(decisions, 400);
  const history = await json(await employee.get(`/api/timesheet-periods/${item.id}/history`));
  expect(history.filter(h => ['SUBMITTED', 'SUBMITTED_LATE'].includes(h.event))).toHaveLength(1);
  expect(history.filter(h => h.event === 'APPROVED')).toHaveLength(1);
});

test('submitted hours cannot be edited and grant revocation applies to existing sessions', async ({ request }) => {
  const employee = await apiAs(request, 'employee'); const moderator = await apiAs(request, 'moderator'); const admin = await apiAs(request, 'companyAdmin');
  const s = await sheet(employee); const entry = await hours(employee, s); const item = await submission(employee);
  expect((await employee.put(`/api/timesheets/${s.id}/time-entries/${entry.id}`, { data: { hours: '8', projectId: f.projectId } })).status()).toBe(400);
  const grants = await json(await admin.get(company('/moderator-grants')));
  expect((await admin.delete(company(`/moderator-grants/${grants[0].id}`))).ok()).toBe(true);
  expect((await moderator.post(`/api/timesheet-periods/${item.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  const current = await json(await employee.get('/api/timesheet-periods', { params: { projectId: f.projectId, date: f.today } }));
  expect(current.status).toBe('SUBMITTED'); expect(Number(current.totalHours)).toBe(4);
});

for (const daysAgo of [7, 8, 30, 31]) {
  test(`daily historical period enforces the ${daysAgo}-day editing/opening boundary`, async ({ request }) => {
    const employee = await apiAs(request, 'employee'); const date = offsetDate(-daysAgo);
    await sheet(employee, date);
    const current = await json(await employee.get('/api/timesheet-periods', { params: { projectId: f.dailyProjectId, date } }));
    expect(current.editable).toBe(daysAgo === 7);
    const response = await employee.post('/api/timesheet-periods/opening', { params: { projectId: f.dailyProjectId, date }, data: { reason: 'Deadline boundary test' } });
    expect(response.status()).toBe(daysAgo === 8 || daysAgo === 30 ? 200 : 400);
  });
}

test('future periods cannot accept hours, submit or request opening', async ({ request }) => {
  const employee = await apiAs(request, 'employee');
  const next = new Date(`${f.today.slice(0,7)}-01T00:00:00Z`); next.setUTCMonth(next.getUTCMonth()+1); const date = next.toISOString().slice(0,10);
  const s = await sheet(employee, date);
  expect((await employee.post(`/api/timesheets/${s.id}/time-entries`, { data: { timesheetId: s.id, projectId: f.projectId, entryDate: date, hours: '4' } })).status()).toBe(400);
  expect((await employee.post('/api/timesheet-periods/submit', { params: { projectId: f.projectId, date } })).status()).toBe(400);
  expect((await employee.post('/api/timesheet-periods/opening', { params: { projectId: f.projectId, date }, data: { reason: 'Future opening' } })).status()).toBe(400);
  expect((await json(await employee.get(`/api/timesheets/id/${s.id}`))).timeEntries).toHaveLength(0);
});

test('concurrent leave approvals consume quota once; finalized leave cannot be altered or deleted', async ({ request }) => {
  const employee = await apiAs(request, 'employee'); const admin = await apiAs(request, 'companyAdmin'); const second = await apiAs(request, 'secondAdmin');
  const date = await workingDate(); const item = await leave(employee, date, request);
  const decisions = await Promise.all([admin,second].map(actor => actor.post(company(`/leave/requests/${item.id}/approve`), { data: { version: item.version } })));
  oneWinner(decisions, 409);
  const balance = await json(await employee.get(company('/leave/balance/me'), { params: { year: Number(date.slice(0,4)) } }));
  expect(Number(balance.vacation.usedDays)).toBe(1);
  const [saved] = await json(await employee.get(company('/leave/my')));
  expect(saved.status).toBe('APPROVED');
  expect((await employee.put(company(`/leave/requests/${item.id}`), { data: { startDate: date, endDate: date, vacationType: 'VACATION', version: saved.version } })).status()).toBe(400);
  expect((await employee.delete(company(`/leave/requests/${item.id}`), { params: { version: saved.version } })).status()).toBe(400);
});

for (const status of ['DRAFT', 'SUBMITTED', 'APPROVED']) {
  test(`leave approval handles ${status.toLowerCase()} hours without silently changing finalized work`, async ({ request }) => {
    const employee = await apiAs(request, 'employee'); const manager = await apiAs(request, 'manager'); const admin = await apiAs(request, 'companyAdmin');
    const date = await workingDate(0); const s = await sheet(employee, date); await hours(employee, s, date);
    if (status !== 'DRAFT') {
      const item = await submission(employee, f.projectId, date);
      if (status === 'APPROVED') await json(await manager.post(`/api/timesheet-periods/${item.id}/decision`, { data: { approve: true } }));
    }
    const item = await leave(employee, date, request);
    const result = await admin.post(company(`/leave/requests/${item.id}/approve`), { data: { version: item.version } });
    expect(result.status()).toBe(status === 'DRAFT' ? 200 : 400);
    const current = await json(await employee.get(`/api/timesheets/id/${s.id}`));
    expect(Number(current.timeEntries[0].hours)).toBe(status === 'DRAFT' ? 0 : 4);
    expect((await json(await employee.get(company('/leave/my'))))[0].status).toBe(status === 'DRAFT' ? 'APPROVED' : 'SUBMITTED');
  });
}

test('concurrent letter reviews permit one reviewer and preserve the issued PDF after settings change', async ({ request }) => {
  const { admin, employee, item } = await letter(request); const second = await apiAs(request, 'secondAdmin');
  const responses = await Promise.all([admin,second].map(actor => actor.post(company(`/letter-requests/${item.id}/approve`), { data: letterDecision(item) })));
  oneWinner(responses, 409);
  const original = await employee.get(company(`/letter-requests/${item.id}/pdf`)); expect(original.ok()).toBe(true); const pdf = await original.body();
  const settings = await json(await admin.get(company('/letter-settings')));
  settings.employers[0].name = 'Changed legal employer';
  await json(await admin.put(company('/letter-settings'), { data: settings }));
  const issued = await employee.get(company(`/letter-requests/${item.id}/pdf`)); expect(issued.ok()).toBe(true);
  expect((await issued.body()).equals(pdf)).toBe(true);
});

test('letter settings revision and reviewer-role removal invalidate stale approval attempts', async ({ request }) => {
  const { admin, employee, item } = await letter(request); const second = await apiAs(request, 'secondAdmin');
  const settings = await json(await admin.get(company('/letter-settings')));
  settings.employers[0].name = 'Updated company details';
  await json(await admin.put(company('/letter-settings'), { data: settings }));
  expect((await second.post(company(`/letter-requests/${item.id}/approve`), { data: letterDecision(item) })).status()).toBe(409);
  const member = (await json(await admin.get(company('/members')))).find(m => m.email === 'second-admin@e2e.chronos.test');
  expect((await admin.delete(company(`/members/${member.user_id}/roles/COMPANY_ADMIN`), { params: { version: member.membership_version } })).ok()).toBe(true);
  expect((await second.post(company(`/letter-requests/${item.id}/approve`), { data: { ...letterDecision(item), configurationVersion: 1 } })).status()).toBe(403);
  expect((await json(await employee.get(company(`/letter-requests/${item.id}`)))).status).toBe('SUBMITTED');
});

test('removed company membership revokes an open browser session but preserves another company', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee'); const admin = await apiAs(request, 'companyAdmin');
  await authenticatePage(page, request, 'employee', f.companyId); await page.goto('/vacation');
  const member = (await json(await admin.get(company('/members')))).find(m => m.user_id === f.employeeId);
  await json(await admin.put(company(`/members/${f.employeeId}/status`), { data: { status: 'REMOVED', version: member.membership_version } }));
  expect((await employee.get(company('/leave/my'))).status()).toBe(403);
  expect((await employee.get(`/api/companies/${f.otherCompanyId}/leave/my`)).ok()).toBe(true);
  expect((await employee.get('/api/auth/me')).ok()).toBe(true);
  await page.reload();
  await expect(page.getByRole('button', { name: 'New leave request' })).toHaveCount(0);
});

test('suspension denies operational reads/writes and reactivation restores the same records', async ({ request }) => {
  const { admin, employee, item } = await letter(request); const platform = await apiAs(request, 'platformAdmin');
  expect((await platform.put(`/api/platform/companies/${f.companyId}/status`, { data: { suspended: true, version: 0, reason: 'E2E suspension' } })).ok()).toBe(true);
  expect((await employee.get(company(`/letter-requests/${item.id}`))).status()).toBe(403);
  expect((await employee.post(company('/letter-requests'), { data: letterInput })).status()).toBe(403);
  expect((await admin.post(company(`/letter-requests/${item.id}/approve`), { data: letterDecision(item) })).status()).toBe(403);
  expect((await employee.get(`/api/companies/${f.otherCompanyId}/leave/my`)).ok()).toBe(true);
  expect((await platform.put(`/api/platform/companies/${f.companyId}/status`, { data: { suspended: false, version: 1, reason: 'E2E recovery' } })).ok()).toBe(true);
  expect((await json(await employee.get(company(`/letter-requests/${item.id}`)))).status).toBe('SUBMITTED');
});
