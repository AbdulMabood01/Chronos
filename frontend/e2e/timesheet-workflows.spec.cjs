const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json, monthOffset } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });
const periodParams = date => ({ projectId: fixture.projectId, date });
async function createSheet(request, role = 'employee', offset = 0) {
  const actor = await apiAs(request, role);
  const period = monthOffset(fixture.today, offset);
  const sheet = await json(await actor.post('/api/timesheets', { params: { year: period.year, month: period.month, companyId: fixture.companyId } }));
  return { actor, sheet, period };
}
async function addHours(actor, sheet, date, hours = '4') {
  return json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`, { data: {
    timesheetId: sheet.id, projectId: fixture.projectId, entryDate: date, hours, notes: 'E2E work',
  } }));
}
async function submit(actor, period) {
  return json(await actor.post('/api/timesheet-periods/submit', { params: periodParams(period.date) }));
}
async function decide(actor, submission, approve = true, comment = null, fallbackReason = null) {
  return json(await actor.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve, comment, fallbackReason } }));
}
async function opening(actor, period) {
  return json(await actor.post('/api/timesheet-periods/opening', { params: periodParams(period.date), data: { reason: 'Enter missed historical hours' } }));
}
async function openingDecision(actor, period, approve) {
  return json(await actor.post(`/api/timesheet-periods/${period.id}/opening-decision`, { data: { approve, comment: 'Historical correction reviewed' } }));
}
async function view(actor, period) {
  return json(await actor.get('/api/timesheet-periods', { params: periodParams(period.date) }));
}

test('month helper handles January to December rollover', () => {
  expect(monthOffset('2026-01-06', -1)).toEqual({ year: 2025, month: 12, date: '2025-12-01' });
});

test('assigned member opens their first timesheet through the Timesheets menu', async ({ page, request }) => {
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/dashboard');
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Timesheets', exact: true }).click();
  await expect(page.getByText('Timesheet not found', { exact: true })).not.toBeVisible();
  await expect(page.getByRole('button', { name: /^Submit [A-Z][a-z]{2} / })).toBeVisible();
  const actor = await apiAs(request, 'employee');
  const sheets = await json(await actor.get('/api/timesheets/my'));
  expect(sheets.some(sheet => sheet.companyId === fixture.companyId)).toBe(true);
});

test('user creates hours in browser and manager approves in browser', async ({ page, request }) => {
  const { actor, sheet, period } = await createSheet(request);
  expect(sheet.companyId, 'Timesheet responses must identify their owning company for the UI').toBe(fixture.companyId);
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  const hours = page.getByRole('spinbutton', { name: `Hours for ${period.date}`, exact: true });
  await hours.fill('8'); await hours.blur();
  await expect(page.getByRole('button', { name: /^Submit [A-Z][a-z]{2} / })).toBeEnabled();
  await page.getByRole('button', { name: /^Submit [A-Z][a-z]{2} / }).click();
  await page.getByRole('dialog',{name:'Review submission'}).getByRole('button',{name:'Submit for approval',exact:true}).click();
  await expect.poll(async () => (await view(actor, period)).status).toBe('SUBMITTED');
  await authenticatePage(page, request, 'manager', fixture.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect.poll(async () => (await view(actor, period)).status).toBe('APPROVED');
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  await expect(page.getByRole('region', { name: 'Selected approval period' }).getByText('Approved', { exact: true })).toBeVisible();
});

for (const offset of [-1, -2]) {
  test(`past draft timesheet (${offset} months) stays editable and submits late`, async ({ page, request }) => {
    const { actor, sheet, period } = await createSheet(request, 'employee', offset);
    expect(sheet.year).toBe(period.year); expect(sheet.month).toBe(period.month);
    expect((await view(actor, period)).editable).toBe(true);
    await addHours(actor, sheet, period.date);
    const submission = await submit(actor, period);
    expect(submission.late).toBe(true);
    const manager = await apiAs(request, 'manager');
    expect((await decide(manager, submission)).status).toBe('APPROVED');
    await authenticatePage(page, request, 'employee', fixture.companyId);
    await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
    await expect(page.getByRole('region', { name: 'Selected approval period' }).getByText('Approved', { exact: true })).toBeVisible();
  });
}

test('rejection is corrected and resubmitted; unauthorized reviewers cannot decide', async ({ request }) => {
  const { actor, sheet, period } = await createSheet(request);
  const entry = await addHours(actor, sheet, period.date);
  let submission = await submit(actor, period);
  for (const role of ['employee', 'companyAdmin', 'platformAdmin', 'otherAdmin', 'creator']) {
    const reviewer = await apiAs(request, role);
    expect((await reviewer.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
    expect((await view(actor, period)).status).toBe('SUBMITTED');
  }
  const manager = await apiAs(request, 'manager');
  expect((await decide(manager, submission, false, 'Correct the hours')).status).toBe('REJECTED');
  await json(await actor.put(`/api/timesheets/${sheet.id}/time-entries/${entry.id}`, { data: { hours: '8', notes: 'Corrected hours', projectId: fixture.projectId } }));
  submission = await submit(actor, period);
  expect(submission.status).toBe('SUBMITTED');
  expect((await decide(manager, submission)).status).toBe('APPROVED');
});

for (const reviewerRole of ['projectAdmin', 'moderator']) {
  test(`manager cannot self-approve; ${reviewerRole} reviews manager hours`, async ({ request }) => {
    const { actor, sheet, period } = await createSheet(request, 'manager');
    await addHours(actor, sheet, period.date);
    const submission = await submit(actor, period);
    expect((await actor.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
    expect((await decide(await apiAs(request, reviewerRole), submission)).status).toBe('APPROVED');
  });
}

test('past approved period opening is declined, approved, corrected and resubmitted', async ({ request }) => {
  fixture.projectId = fixture.dailyProjectId;
  const day = new Date(`${fixture.today}T12:00:00Z`); day.setUTCDate(day.getUTCDate() - 10);
  while ([0, 6].includes(day.getUTCDay())) day.setUTCDate(day.getUTCDate() - 1);
  const date = day.toISOString().slice(0, 10);
  const actor = await apiAs(request, 'employee');
  const sheet = await json(await actor.post('/api/timesheets', { params: { companyId: fixture.companyId, year: day.getUTCFullYear(), month: day.getUTCMonth()+1 } }));
  const period = { date };
  const admin = await apiAs(request, 'projectAdmin');
  expect((await view(actor, period)).editable).toBe(true);
  const entry = await addHours(actor, sheet, period.date);
  const manager = await apiAs(request, 'manager');
  await decide(manager, await submit(actor, period));
  let correction = await opening(actor, period);
  expect((await actor.post(`/api/timesheet-periods/${correction.id}/opening-decision`, { data: { approve: true } })).status()).toBe(403);
  expect((await openingDecision(admin, correction, false)).openingStatus).toBe('DECLINED');
  expect((await view(actor, period)).editable).toBe(false);
  correction = await opening(actor, period);
  await openingDecision(admin, correction, true);
  await json(await actor.put(`/api/timesheets/${sheet.id}/time-entries/${entry.id}`, { data: { hours: '8', projectId: fixture.projectId, notes: 'Approved correction' } }));
  expect((await decide(manager, await submit(actor, period))).status).toBe('APPROVED');
});

test('project admin fallback requires a recorded reason', async ({ request }) => {
  const { actor, sheet, period } = await createSheet(request);
  await addHours(actor, sheet, period.date);
  const submission = await submit(actor, period);
  const admin = await apiAs(request, 'projectAdmin');
  expect((await admin.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  expect((await decide(admin, submission, true, null, 'Project Manager is unavailable')).status).toBe('APPROVED');
  const history = await json(await actor.get(`/api/timesheet-periods/${submission.id}/history`));
  expect(history.some(row => row.event === 'APPROVED')).toBe(true);
});

test('Moderator time grant cannot review work outside its date bounds', async ({ request }) => {
  const { actor, sheet, period } = await createSheet(request);
  await addHours(actor, sheet, period.date);
  const submission = await submit(actor, period);
  const admin = await apiAs(request, 'companyAdmin');
  const grants = await json(await admin.get(`/api/companies/${fixture.companyId}/moderator-grants`));
  for (const grant of grants) expect((await admin.delete(`/api/companies/${fixture.companyId}/moderator-grants/${grant.id}`)).ok()).toBe(true);
  const moderator = await apiAs(request, 'moderator');
  const me = await json(await moderator.get('/api/auth/me'));
  const previous = monthOffset(fixture.today, -2);
  expect((await admin.post(`/api/companies/${fixture.companyId}/moderator-grants`, { data: {
    projectId: fixture.projectId, userId: me.id, timesheets: true, expenses: false, startsOn: previous.date, endsOn: previous.date,
  } })).ok()).toBe(true);
  expect((await moderator.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  expect((await view(actor, period)).status).toBe('SUBMITTED');
});
