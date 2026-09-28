const { test, expect } = require('@playwright/test');
const { accounts, loginByApi, loginThroughUi, authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test.describe.configure({ mode: 'serial' });
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

test('role authentication and route/API authorization', async ({ page, request }) => {
  await loginThroughUi(page, 'employee');
  await page.goto('/users');
  await expect(page).toHaveURL(/\/dashboard$/);

  const employee = await apiAs(request, 'employee');
  expect((await employee.get('/api/users/all')).status()).toBe(403);

  await page.evaluate(() => localStorage.removeItem('authToken'));
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);

  await loginThroughUi(page, 'projectAdmin');
  await page.goto('/projects');
  await expect(page).toHaveURL(/\/projects$/);
  await page.goto('/settings');
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.evaluate(() => localStorage.removeItem('authToken'));
  await loginThroughUi(page, 'superAdmin');
  await page.goto('/users');
  await expect(page).toHaveURL(/\/users$/);
});

test('employee submits hours and project admin approves them', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  const now = new Date();
  const year = now.getFullYear(); const month = now.getMonth() + 1;
  let response = await employee.post('/api/timesheets', { params: { year, month } });
  expect(response.ok()).toBeTruthy();
  const sheet = await response.json();
  response = await employee.post(`/api/timesheets/${sheet.id}/time-entries`, { data: {
    timesheetId: sheet.id, entryDate: `${year}-${String(month).padStart(2, '0')}-01`, hours: '8', notes: 'E2E work', projectId: fixture.projectId,
  }});
  expect(response.ok()).toBeTruthy();
  response = await employee.post(`/api/timesheets/${sheet.id}/projects/${fixture.projectId}/submit`);
  expect(response.ok()).toBeTruthy();
  const submission = await response.json();

  const manager = await apiAs(request, 'projectAdmin');
  response = await manager.post(`/api/approvals/timesheet-project/${submission.id}/approve`);
  expect(response.ok()).toBeTruthy();
  expect((await response.json()).status).toBe('APPROVED');

  await authenticatePage(page, request, 'employee');
  await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
});

test('employee vacation request is approved by super admin', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  const start = new Date(); start.setDate(start.getDate() + 20);
  const end = new Date(start); end.setDate(end.getDate() + 1);
  const iso = date => date.toISOString().slice(0, 10);
  let response = await employee.post('/api/vacation', { params: { startDate: iso(start), endDate: iso(end), type: 'VACATION', notes: 'E2E vacation' } });
  expect(response.ok()).toBeTruthy();
  const vacation = await response.json();
  expect((await employee.post(`/api/vacation/${vacation.id}/submit`)).ok()).toBeTruthy();
  const admin = await apiAs(request, 'superAdmin');
  response = await admin.post(`/api/approvals/vacation/${vacation.id}/approve`);
  expect(response.ok()).toBeTruthy();
  expect((await response.json()).status).toBe('APPROVED');

  await authenticatePage(page, request, 'employee');
  await page.goto('/vacation');
  await expect(page.getByText('E2E vacation')).toBeVisible();
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
});

test('super admin creates an employee who activates and signs in', async ({ page, request }) => {
  const admin = await apiAs(request, 'superAdmin');
  const email = `new.employee.${Date.now()}@e2e.chronos.test`;
  let response = await admin.post('/api/users', { data: { firstName: 'New', lastName: 'Employee', email } });
  expect(response.status()).toBe(201);
  const invited = await response.json();
  expect(invited.accountStatus).toBe('INVITED');

  response = await admin.post(`/api/e2e/invitation-token/${invited.id}`);
  const invitationBody = await response.text();
  expect(response.ok(), `${response.status()} ${invitationBody}`).toBeTruthy();
  const { token } = JSON.parse(invitationBody);
  const activatedPassword = 'Activated-E2E-123!';
  await page.goto(`/activate?token=${token}`);
  await expect(page.getByText(email)).toBeVisible();
  await page.getByLabel('Create password').fill(activatedPassword);
  await page.getByLabel('Confirm password').fill(activatedPassword);
  await page.getByRole('button', { name: 'Activate account' }).click();
  await expect(page.getByText(/Your account is active/)).toBeVisible();
  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password').fill(activatedPassword);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

});

test('project admin creates a draft, completes setup, and activates it for timesheets', async ({ page, request }) => {
  const code = `E2E-${Date.now()}`;
  await authenticatePage(page, request, 'projectAdmin');
  await page.goto('/projects');
  await page.getByRole('button', { name: 'New Project' }).click();
  await page.getByLabel('Project Name').nth(1).fill('Assigned through E2E');
  await page.getByLabel('Project Code').fill(code);
  await page.getByRole('button', { name: 'Create Draft' }).click();
  await expect(page.getByText('Prepare this draft for activation')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Activate Project' })).toBeDisabled();
  await page.getByLabel('Primary Project Manager').selectOption(String(fixture.projectAdminId));
  await expect(page.getByLabel("Approver for the PM's own hours")).toHaveValue(String(fixture.projectAdminId));
  await page.getByRole('button', { name: /Save/ }).last().click();
  await page.getByRole('button', { name: 'Add team' }).click();
  const now = new Date(); const start = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
  const end = new Date(now.getFullYear(), now.getMonth() + 2, 0).toISOString().slice(0, 10);
  await page.getByLabel('Employee').selectOption(String(fixture.employeeId));
  await page.getByLabel('Start date').fill(start);
  await page.getByLabel('End date').fill(end);
  await page.getByLabel('Bill rate').fill('95');
  await page.getByLabel('Assigned hours').fill('80');
  await page.getByRole('button', { name: 'Assign', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Activate Project' })).toBeEnabled();
  await page.getByRole('button', { name: 'Activate Project' }).click();
  await expect(page.getByText('Project activated. The team can now use it.')).toBeVisible();
  await page.getByRole('button', { name: 'Expenses', exact: true }).click();
  await page.getByRole('spinbutton', { name: 'Expense Budget' }).fill('750');
  await page.getByRole('button', { name: 'Save Budget' }).click();
  await expect(page.locator('.pc-expense-budget-metrics')).toContainText('$750.00');
  await page.getByRole('button', { name: 'Project Details', exact: true }).click();
  await expect(page.getByRole('spinbutton', { name: 'Expense Budget' })).toHaveCount(0);

  await authenticatePage(page, request, 'employee');
  await page.goto('/timesheets');
  await expect(page.getByLabel('Project')).toContainText('Assigned through E2E');
});

test('super admin deactivation invalidates an employee session', async ({ page, request }) => {
  const token = await loginByApi(request, accounts.employee);
  const admin = await apiAs(request, 'superAdmin');
  const response = await admin.patch(`/api/users/${fixture.employeeId}/deactivate`);
  expect(response.ok()).toBeTruthy();
  const me = await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } });
  expect([401, 403]).toContain(me.status());

  await page.addInitScript(value => localStorage.setItem('authToken', value), token);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});
