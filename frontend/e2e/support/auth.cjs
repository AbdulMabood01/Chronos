const { expect } = require('@playwright/test');

const accounts = {
  employee: { email: 'employee@e2e.chronos.test', password: 'Chronos-E2E-123!', role: 'EMPLOYEE' },
  projectAdmin: { email: 'project-admin@e2e.chronos.test', password: 'Chronos-E2E-123!', role: 'PROJECT_ADMIN' },
  superAdmin: { email: 'admin@e2e.chronos.test', password: 'Chronos-E2E-123!', role: 'ADMIN' },
};
async function loginByApi(request, account) {
  const response = await request.post('/api/auth/login', { data: account });
  expect(response.ok(), `Login failed for ${account.email}: ${response.status()}`).toBeTruthy();
  const token = (await response.json()).token;
  return token;
}

async function authenticatePage(page, request, role) {
  const account = accounts[role];
  const token = await loginByApi(request, account);
  await page.addInitScript(value => localStorage.setItem('authToken', value), token);
  return token;
}

async function loginThroughUi(page, role) {
  const account = accounts[role];
  await page.goto('/login');
  await page.getByLabel('Work email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  return account;
}

module.exports = { accounts, loginByApi, authenticatePage, loginThroughUi };
