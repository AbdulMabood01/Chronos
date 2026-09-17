const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

test('profile workspace separates details and management on desktop and fits mobile', async ({ page }) => {
  const employee = { id: 2, firstName: 'Alice', lastName: 'Smith', jobTitle: 'Software Engineer', employeeId: 'EMP-102', email: 'alice@example.test', role: 'EMPLOYEE', isActive: true, joiningDate: '2024-01-15', phoneNumber: '555-0100', emergencyContactName: 'Jane Smith', profileCompleted: true };
  const days = { allowanceDays: 15, remainingDays: 12, extraDays: 0, usedDays: 3, unpaidDays: 0 };
  await page.addInitScript(() => localStorage.setItem('authToken', 'profile-test'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id: 1, firstName: 'Sam', lastName: 'Admin', role: 'SUPER_ADMIN', profileCompleted: true };
    else if (url.pathname === '/api/users/all') data = [employee];
    else if (url.pathname.includes('leave-balance')) data = { configured: true, vacation: days, sick: days, bereavement: days };
    else if (url.pathname.includes('unread-count')) data = 0;
    await route.fulfill({ json: data });
  });
  await page.goto('/users');
  await page.getByRole('button', { name: 'View profile' }).click();
  await expect(page.getByRole('heading', { name: 'Alice Smith', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const personal = await page.getByRole('region', { name: 'Employee profile', exact: true }).boundingBox();
  const employment = await page.getByRole('region', { name: 'Employment details' }).boundingBox();
  expect(employment.x).toBeGreaterThan(personal.x + personal.width);
  await page.getByRole('button', { name: 'Leave allowance', exact: true }).click();
  await expect(page.getByLabel('Annual vacation days')).toBeVisible();
  const preview = path.resolve(__dirname, '../../backend/target/ui-preview');
  fs.mkdirSync(preview, { recursive: true });
  await page.screenshot({ path: path.join(preview, 'profile-workspace-desktop.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: path.join(preview, 'profile-workspace-mobile.png'), fullPage: true });
});
