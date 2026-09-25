const { test, expect } = require('@playwright/test');
test('people and reports fit mobile and expose import', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('authToken', 'layout-test'));
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname === '/api/auth/me') data = { id: 1, firstName: 'Sam', lastName: 'Admin', role: 'ADMIN', profileCompleted: true };
    if (pathname === '/api/users/all') data = [{ id: 2, firstName: 'Alice', lastName: 'Smith', email: 'alice@example.com', employeeId: 'EMP-102', role: 'EMPLOYEE', isActive: true, accountStatus: 'INVITED' }];
    if (pathname.includes('unread-count')) data = 0;
    await route.fulfill({ json: data });
  });
  await page.goto('/users');
  await expect(page.getByRole('heading', { name: 'People', exact: true })).toBeVisible();
  await expect(page.getByLabel('First name')).toHaveCount(0);
  await page.getByRole('button', { name: 'Import employees', exact: true }).click();
  await expect(page.getByLabel('Excel spreadsheet')).toBeVisible();
  await page.screenshot({ path: '../backend/target/ui-preview/people-import-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.goto('/reports');
  await expect(page.getByText('No reports match these filters.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '../backend/target/ui-preview/reports-mobile.png', fullPage: true });
});
