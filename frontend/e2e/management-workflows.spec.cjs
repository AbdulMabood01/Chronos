const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json, configureLetters, letterInput, letterDecision } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

test('company admin assigns membership role and audit retains the change', async ({ page, request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  const base = `/api/companies/${fixture.companyId}`;
  const member = (await json(await admin.get(`${base}/members`))).find(r => r.user_id === fixture.employeeId);
  await json(await admin.post(`${base}/members/${fixture.employeeId}/roles`, { data: { role: 'MODERATOR', version: member.membership_version } }));
  const updated = (await json(await admin.get(`${base}/members`))).find(r => r.user_id === fixture.employeeId);
  expect(updated.roles).toContain('MODERATOR');
  const audit = await json(await admin.get(`${base}/audit`));
  expect(audit.some(r => r.action === 'COMPANY_ROLE_ASSIGNED')).toBe(true);
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/audit');
  await expect(page.getByRole('cell', { name: 'COMPANY ROLE ASSIGNED', exact: true }).first()).toBeVisible();
});

test('company report exports are real files and reject personal or platform roles', async ({ page, request }) => {
  const year = Number(fixture.today.slice(0, 4));
  const base = `/api/companies/${fixture.companyId}/reports`;
  for (const role of ['employee', 'platformAdmin', 'otherAdmin']) {
    const actor = await apiAs(request, role);
    expect((await actor.get(`${base}/vacation/export`, { params: { year } })).status()).toBe(403);
  }
  const admin = await apiAs(request, 'companyAdmin');
  const file = await admin.get(`${base}/vacation/export`, { params: { year } });
  expect(file.ok()).toBeTruthy(); expect(file.headers()['content-type']).toContain('spreadsheetml.sheet');
  expect((await file.body()).subarray(0, 2).toString()).toBe('PK');
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/reports'); await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();
});

test('letter approval notification reaches requester and can be marked read', async ({ page, request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  await configureLetters(admin, fixture.companyId);
  const employee = await apiAs(request, 'employee');
  const base = `/api/companies/${fixture.companyId}/letter-requests`;
  const letter = await json(await employee.post(base, { data: letterInput }));
  await json(await admin.post(`${base}/${letter.id}/approve`, { data: letterDecision(letter) }));
  const unread = await json(await employee.get('/api/notifications/unread'));
  expect(unread.some(r => r.title === 'Letter Request Approved')).toBe(true);
  const other = await apiAs(request, 'otherAdmin');
  expect((await json(await other.get('/api/notifications/unread'))).some(r => r.title === 'Letter Request Approved')).toBe(false);
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/notifications'); await expect(page.getByRole('heading', { name: 'Letter Request Approved' })).toBeVisible();
  await page.getByRole('button', { name: 'Mark All as Read' }).click();
  await expect.poll(async () => json(await employee.get('/api/notifications/unread-count'))).toBe(0);
});
