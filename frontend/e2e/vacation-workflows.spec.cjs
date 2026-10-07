const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });
async function leaveDate() {
  const { leaveWorkingDates } = await import('../src/utils/federalHolidays.js');
  const day = new Date(`${fixture.today}T12:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 14);
  while (!leaveWorkingDates(day.toISOString().slice(0,10), day.toISOString().slice(0,10)).length) day.setUTCDate(day.getUTCDate() + 1);
  return day.toISOString().slice(0, 10);
}
for (const decision of ['approve', 'reject']) {
  test(`vacation draft is created and submitted in browser, then ${decision === 'approve' ? 'approved' : 'rejected'} by another admin`, async ({ page, request }) => {
    const actor = await apiAs(request, 'employee');
    const base = `/api/companies/${fixture.companyId}/leave`;
    const date = await leaveDate(); const year = Number(date.slice(0, 4));
    const before = await json(await actor.get(`${base}/balance/me`, { params: { year } }));
    await authenticatePage(page, request, 'employee', fixture.companyId);
    await page.goto('/vacation');
    await page.getByRole('button', { name: 'New leave request' }).click();
    await page.getByLabel('Start date', { exact: true }).fill(date);
    await page.getByLabel('End date', { exact: true }).fill(date);
    await page.getByRole('button', { name: 'Save draft' }).click();
    await expect(page.getByText('Leave draft saved.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Edit leave', exact: true }).click();
    await page.getByLabel('Notes', { exact: true }).fill('E2E vacation request');
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    await expect(page.getByText('Leave draft saved.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Submit leave', exact: true }).click();
    await expect(page.getByText('Leave submitted.', { exact: true })).toBeVisible();
    await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
    await page.goto('/leave-management');
    await page.getByRole('button', { name: 'Review leave', exact: true }).click();
    if (decision === 'reject') await page.getByLabel(/Reason/i).fill('Requested dates need adjustment');
    await page.getByRole('button', { name: decision === 'approve' ? 'Approve leave' : 'Reject leave', exact: true }).click();
    await expect(page.getByText(decision === 'approve' ? 'Leave approved.' : 'Leave rejected.', { exact: true })).toBeVisible();
    const [leave] = await json(await actor.get(`${base}/my`));
    expect(leave.status).toBe(decision === 'approve' ? 'APPROVED' : 'REJECTED');
    const after = await json(await actor.get(`${base}/balance/me`, { params: { year } }));
    expect(Number(after.vacation.usedDays) - Number(before.vacation.usedDays)).toBe(decision === 'approve' ? 1 : 0);
    await authenticatePage(page, request, 'employee', fixture.companyId);
    await page.goto('/vacation');
    await expect(page.locator('.leave-history-item .status-badge')).toHaveText(decision === 'approve' ? 'Approved' : 'Rejected');
  });
}

test('vacation approval checks roles, company, self, overlap and stale versions', async ({ request }) => {
  const actor = await apiAs(request, 'companyAdmin');
  const base = `/api/companies/${fixture.companyId}/leave`;
  const date = await leaveDate();
  const input = { startDate: date, endDate: date, vacationType: 'VACATION', notes: 'Own leave' };
  let leave = await json(await actor.post(`${base}/requests`, { data: input }));
  leave = await json(await actor.post(`${base}/requests/${leave.id}/submit`, { params: { version: leave.version } }));
  for (const role of ['companyAdmin', 'employee', 'manager', 'projectAdmin', 'moderator', 'platformAdmin', 'otherAdmin']) {
    const reviewer = await apiAs(request, role);
    expect((await reviewer.post(`${base}/requests/${leave.id}/approve`, { data: { version: leave.version } })).status()).toBe(403);
    expect((await json(await actor.get(`${base}/my`)))[0].status).toBe('SUBMITTED');
  }
  const second = await apiAs(request, 'secondAdmin');
  expect((await second.post(`/api/companies/${fixture.otherCompanyId}/leave/requests/${leave.id}/approve`, { data: { version: leave.version } })).ok()).toBeFalsy();
  expect((await second.post(`${base}/requests/${leave.id}/approve`, { data: { version: leave.version - 1 } })).status()).toBe(409);
  expect((await json(await second.post(`${base}/requests/${leave.id}/approve`, { data: { version: leave.version } }))).status).toBe('APPROVED');
  const overlap = await actor.post(`${base}/requests`, { data: input });
  if (overlap.ok()) {
    const draft = await overlap.json();
    expect((await actor.post(`${base}/requests/${draft.id}/submit`, { params: { version: draft.version } })).ok()).toBeFalsy();
  } else expect(overlap.status()).toBe(400);
});

test('company switching clears personal leave and company membership alone permits leave', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  const date = await leaveDate();
  const base = `/api/companies/${fixture.companyId}/leave`;
  await json(await employee.post(`${base}/requests`, { data: { startDate: date, endDate: date, vacationType: 'VACATION', notes: 'Company A private leave' } }));
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/vacation');
  await page.getByText('View details', { exact: true }).click();
  await expect(page.getByText('Company A private leave', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Current company', exact: true }).selectOption(String(fixture.otherCompanyId));
  await expect(page.getByText('Company A private leave', { exact: true })).toHaveCount(0);
  await expect(page.getByText('No leave requests in this company.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'New leave request', exact: true }).click();
  await page.getByLabel('Start date', { exact: true }).fill(date);
  await page.getByLabel('End date', { exact: true }).fill(date);
  await page.getByLabel('Notes', { exact: true }).fill('Company B personal leave');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByText('Leave draft saved.', { exact: true })).toBeVisible();
  const [leave] = await json(await employee.get(`/api/companies/${fixture.otherCompanyId}/leave/my`));
  expect(leave.companyId).toBe(fixture.otherCompanyId);
  expect((await json(await employee.get(`${base}/my`)))[0].notes).toBe('Company A private leave');
});

test('vacation quota failure leaves submitted request and balance unchanged', async ({ request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  const employee = await apiAs(request, 'employee');
  const base = `/api/companies/${fixture.companyId}/leave`; const date = await leaveDate(); const year = Number(date.slice(0, 4));
  await json(await admin.put(`${base}/balance/${fixture.employeeId}`, { data: {
    year, vacationDays: 0, sickDays: 10, bereavementDays: 5, addVacationDays: 0, addSickDays: 0, reason: 'E2E exhausted quota', version: 0,
  } }));
  let leave = await json(await employee.post(`${base}/requests`, { data: { startDate: date, endDate: date, vacationType: 'VACATION' } }));
  leave = await json(await employee.post(`${base}/requests/${leave.id}/submit`, { params: { version: leave.version } }));
  expect((await admin.post(`${base}/requests/${leave.id}/approve`, { data: { version: leave.version } })).status()).toBe(400);
  expect((await json(await employee.get(`${base}/my`)))[0]).toMatchObject({ status: 'SUBMITTED', version: leave.version });
  expect(Number((await json(await employee.get(`${base}/balance/me`, { params: { year } }))).vacation.usedDays)).toBe(0);
});
