const { test, expect } = require('@playwright/test');
const { authenticatePage, loginThroughUi } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test.describe.configure({ mode: 'serial' });
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

async function createLetter(request) {
  const employee = await apiAs(request, 'employee');
  const response = await employee.post('/api/letter-requests', { data: {
    requestType: 'EMPLOYMENT_VERIFICATION',
    requestedFullName: 'Test Employee',
    requestedJobTitle: 'Software Engineer',
    employmentStartDate: '2024-01-15',
    recipientOrganization: 'E2E Verification Recipient',
    purpose: 'Employment verification test',
  }});
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test('letter request is approved, downloaded, and protected by role', async ({ page, request }) => {
  const letter = await createLetter(request);
  const projectAdmin = await apiAs(request, 'projectAdmin');
  expect((await projectAdmin.post(`/api/approvals/letter-request/${letter.id}/approve`)).status()).toBe(403);

  const admin = await apiAs(request, 'superAdmin');
  let response = await admin.get('/api/letter-requests/pending');
  expect((await response.json()).some(item => item.id === letter.id)).toBeTruthy();
  response = await admin.post(`/api/approvals/letter-request/${letter.id}/approve`);
  expect(response.ok()).toBeTruthy();
  expect((await response.json()).status).toBe('APPROVED');

  const employee = await apiAs(request, 'employee');
  response = await employee.get(`/api/letter-requests/${letter.id}/pdf`);
  expect(response.ok()).toBeTruthy();
  expect(response.headers()['content-type']).toContain('application/pdf');
  expect((await response.body()).length).toBeGreaterThan(500);

  await authenticatePage(page, request, 'employee');
  await page.goto('/requests');
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
});

test('super admin manages a person and audit history records each change', async ({ page, request }) => {
  const admin = await apiAs(request, 'superAdmin');
  let response = await admin.patch(`/api/users/${fixture.employeeId}/role`, { params: { role: 'PROJECT_ADMIN' } });
  expect(response.ok()).toBeTruthy();
  expect((await response.json()).role).toBe('PROJECT_ADMIN');
  response = await admin.patch(`/api/users/${fixture.employeeId}/deactivate`);
  expect((await response.json()).isActive).toBeFalsy();
  response = await admin.patch(`/api/users/${fixture.employeeId}/reactivate`);
  expect((await response.json()).isActive).toBeTruthy();

  response = await admin.get('/api/audit');
  expect(response.ok()).toBeTruthy();
  const actions = (await response.json()).map(row => row.action);
  expect(actions).toEqual(expect.arrayContaining(['ROLE_CHANGED', 'USER_DEACTIVATED', 'USER_REACTIVATED']));

  await authenticatePage(page, request, 'superAdmin');
  await page.goto('/audit');
  await expect(page.getByRole('cell', { name: 'ROLE_CHANGED', exact: true })).toBeVisible();
});

test('report exports return real files and reject employees', async ({ page, request }) => {
  const year = new Date().getFullYear();
  const employee = await apiAs(request, 'employee');
  expect((await employee.get('/api/reports/vacation/export', { params: { year } })).status()).toBe(403);

  const admin = await apiAs(request, 'superAdmin');
  let response = await admin.get('/api/reports/vacation/export', { params: { year } });
  expect(response.ok()).toBeTruthy();
  expect(response.headers()['content-type']).toContain('spreadsheetml.sheet');
  expect(response.headers()['content-disposition']).toContain(`vacation-requests-${year}.xlsx`);
  expect((await response.body()).length).toBeGreaterThan(1000);

  response = await admin.get('/api/reports/summary', { params: { year, month: new Date().getMonth() + 1 } });
  expect(response.ok()).toBeTruthy();
  expect(Array.isArray(await response.json())).toBeTruthy();

  await authenticatePage(page, request, 'superAdmin');
  await page.goto('/time-reports');
  await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();
});

test('each role receives its correct dashboard and navigation', async ({ browser, request }) => {
  const expectations = [
    ['employee', 'A little focus.', 'Timesheets'],
    ['projectAdmin', 'Great teams.', 'Approvals'],
    ['superAdmin', 'See the bigger picture.', 'People'],
  ];
  for (const [role, heading, navigation] of expectations) {
    const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5174' });
    const page = await context.newPage();
    await loginThroughUi(page, role);
    await expect(page.getByRole('heading', { name: new RegExp(heading, 'i') })).toBeVisible();
    await expect(page.getByRole('link', { name: navigation, exact: true })).toBeVisible();
    await context.close();
  }
});

test('workflow notifications reach only the recipient and can be marked read', async ({ page, request }) => {
  const letter = await createLetter(request);
  const admin = await apiAs(request, 'superAdmin');
  expect((await admin.post(`/api/approvals/letter-request/${letter.id}/approve`)).ok()).toBeTruthy();

  const employee = await apiAs(request, 'employee');
  let response = await employee.get('/api/notifications/unread');
  const unread = await response.json();
  expect(unread.some(item => item.title === 'Letter Request Approved')).toBeTruthy();

  await authenticatePage(page, request, 'employee');
  await page.goto('/notifications');
  await expect(page.getByRole('heading', { name: 'Letter Request Approved' })).toBeVisible();
  await page.getByRole('button', { name: 'Mark All as Read' }).click();
  await expect(page.getByText('Unread', { exact: true })).toHaveCount(0);

  response = await employee.get('/api/notifications/unread-count');
  expect(await response.json()).toBe(0);
});
