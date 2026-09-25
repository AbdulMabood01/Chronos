const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');
const preview = path.resolve(__dirname, '../../backend/target/ui-preview');
const now = new Date();
const employee = { id: 1, firstName: 'Jordan', lastName: 'Rivera', role: 'EMPLOYEE', profileCompleted: true };
const sheet = { id: 2, userId: 1, userName: 'Jordan Rivera', year: now.getFullYear(), month: now.getMonth() + 1, status: 'APPROVED', totalHours: 64, timeEntries: [{ id: 8, projectId: 4, projectCode: 'ATLAS', projectName: 'Atlas Platform', entryDate: `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-03`, hours: 8 }], vacationDays: [] };
async function mockWorkspace(page, role = 'EMPLOYEE') {
  await page.addInitScript(() => localStorage.setItem('authToken', 'visual-test-session'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { ...employee, role };
    else if (url.pathname.includes('/notifications/unread-count')) data = 3;
    else if (url.pathname.includes('/submission')) data = { id: 3, timesheetId: 2, projectId: 4, status: 'APPROVED', pdfExportEligible: true, approvedByName: 'Taylor Morgan', approvedAt: '2026-09-01T10:00:00', billRate: 85, plannedHours: 160, totalHours: 64 };
    else if (url.pathname === '/api/timesheets/my') data = [sheet];
    else if (/\/timesheets\/(\d|id)/.test(url.pathname)) data = sheet;
    else if (url.pathname === '/api/projects/assigned') data = [{ id: 4, code: 'ATLAS', name: 'Atlas Platform', projectManagerName: 'Taylor Morgan' }];
    else if (url.pathname === '/api/vacation/my') data = [{ id: 1, status: 'APPROVED', hours: 24, startDate: '2026-08-17', endDate: '2026-08-19', vacationType: 'VACATION' }];
    else if (url.pathname === '/api/letter-requests/my') data = [{ id: 1, status: 'APPROVED', requestType: 'EMPLOYMENT_VERIFICATION' }];
    await route.fulfill({ json: data });
  });
}
async function screenshot(page, name) {
  fs.mkdirSync(preview, { recursive: true });
  await page.screenshot({ path: path.join(preview, name + '.png'), fullPage: true });
}
test('employee overview, keyboard navigation, theme and approved timesheet', async ({ page }) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await mockWorkspace(page);
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: /A little focus/ })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Workspace summary' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Overview', exact: true })).toHaveAttribute('aria-current', 'page');
  await screenshot(page, 'employee-desktop');
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await expect(page.locator('body')).toHaveClass('theme-dark');
  await screenshot(page, 'employee-dark');
  await page.getByRole('button', { name: 'Switch to light theme' }).click();
  await page.getByRole('link', { name: 'Timesheets', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Export PDF' })).toBeEnabled();
  await screenshot(page, 'timesheet-desktop');
  expect(errors).toEqual([]);
});
test('mobile navigation, responsive dashboard and calendar', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockWorkspace(page);
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: /A little focus/ })).toBeVisible();
  await screenshot(page, 'employee-mobile');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Open navigation' })).toBeFocused();
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('link', { name: 'Timesheets', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Export PDF' })).toBeEnabled();
  await screenshot(page, 'timesheet-mobile');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('management navigation and approvals', async ({ page }) => {
  await mockWorkspace(page, 'ADMIN');
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: /See the bigger picture/ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'People', exact: true })).toBeVisible();
  await screenshot(page, 'admin-desktop');
});
test('sign-in desktop and mobile', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Make yourself at home.' })).toBeVisible();
  await screenshot(page, 'login-desktop');
  await page.setViewportSize({ width: 390, height: 844 });
  await screenshot(page, 'login-mobile');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
