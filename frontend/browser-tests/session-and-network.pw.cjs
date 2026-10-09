const { test, expect } = require('./support/company-fixture.cjs');

async function mockEmployee(page, { noProjects = false, failOnce = false } = {}) {
  await page.addInitScript(() => localStorage.setItem('authToken', 'browser-test-token'));
  let failed = false;
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/expenses/mine' && failOnce && !failed) {
      failed = true;
      return route.abort('failed');
    }
    const data = path === '/api/auth/me'
      ? { id: 4, firstName: 'Alex', lastName: 'Employee', role: 'EMPLOYEE', profileCompleted: true }
      : path === '/api/projects/assigned' && !noProjects
        ? [{ id: 3, code: 'ATLAS', name: 'Atlas', isActive: true }]
        : [];
    await route.fulfill({ json: data });
  });
}

test('employee without active project assignments sees guidance', async ({ page }) => {
  await mockEmployee(page, { noProjects: true });
  await page.goto('/expenses');
  await expect(page.getByRole('heading', { name: 'No projects assigned' })).toBeVisible();
  await expect(page.getByText(/Contact your Project Admin/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Submit for approval' })).toBeDisabled();
});

test('connection banner persists and announces recovery', async ({ page }) => {
  await mockEmployee(page, { failOnce: true });
  await page.goto('/expenses');
  await expect(page.getByText(/Connection lost\. Check your internet connection/)).toBeVisible();
  await expect(page.getByText('Connection restored')).toBeVisible({ timeout: 15000 });
});

test('warns before idle expiry and lets a user renew then expires without activity', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-09-27T12:00:00Z') });
  await mockEmployee(page);
  await page.goto('/expenses');
  await expect(page.getByRole('heading', { name: 'Expenses', exact: true })).toBeVisible();
  await page.clock.fastForward(28 * 60 * 1000);
  await expect(page.getByRole('dialog', { name: 'Your session is about to expire' })).toBeVisible();
  await page.getByRole('button', { name: 'Stay Signed In' }).click();
  await expect(page.getByRole('dialog', { name: 'Your session is about to expire' })).toHaveCount(0);
  await page.clock.fastForward(30 * 60 * 1000);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('Your session has expired. Please sign in again.')).toBeVisible();
});
