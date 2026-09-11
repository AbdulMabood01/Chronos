const { test, expect } = require('@playwright/test');
const path = require('path');
const project = { id: 10, code: 'ATL-001', name: 'Atlas Platform', description: 'A unified platform for the next chapter of customer delivery.', status: 'ACTIVE', totalAllocatedHours: 1200, projectManagerId: 3, projectManagerName: 'Taylor Morgan', projectManagerHoursApproverId: 4, projectManagerHoursApproverName: 'Alex Chen', assignments: [{ id: 5, userId: 3, userName: 'Taylor Morgan', email: 'taylor@example.com', jobTitle: 'Project Manager', isActive: true, startDate: '2026-09-01', endDate: '2026-12-31', billRate: 85, plannedHours: 240 }] };
test('project control tabs, reporting period, themes and mobile layouts', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => localStorage.setItem('authToken', 'project-ui-test'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id: 1, firstName: 'Jordan', lastName: 'Rivera', role: 'ADMIN', profileCompleted: true };
    else if (url.pathname.includes('unread-count')) data = 0;
    else if (url.pathname.includes('hours-dashboard')) data = [{ projectId: 10, plannedHours: 160, totalLoggedHours: 112, submittedHours: 24, approvedHours: 80, rejectedHours: 0, employees: [{ userId: 3, userName: 'Taylor Morgan', email: 'taylor@example.com', assignmentStartDate: '2026-09-01', assignmentEndDate: '2026-12-31', plannedHours: 160, totalLoggedHours: 112, submittedHours: 24, approvedHours: 80, status: 'SUBMITTED' }] }];
    else if (url.pathname === '/api/projects') data = [project];
    else if (url.pathname === '/api/users') data = [{ id: 3, firstName: 'Taylor', lastName: 'Morgan', isActive: true }, { id: 4, firstName: 'Alex', lastName: 'Chen', isActive: true }];
    await route.fulfill({ json: data });
  });
  await page.goto('/projects');
  await page.getByRole('button', { name: /Atlas Platform/ }).click();
  await expect(page.getByRole('meter')).toHaveAttribute('aria-valuenow', String(112 / 240 * 100));
  await expect(page.getByRole('img', { name: 'Total project hours: 240.00, summed across all project resources' })).toBeVisible();
  const period = page.getByLabel('Reporting month');
  const previousMonth = await period.locator('option').nth(1).getAttribute('value');
  const request = page.waitForRequest(req => req.url().includes('hours-dashboard') && req.url().includes(`month=${previousMonth.split('-')[1]}`));
  await period.selectOption(previousMonth);
  await request;
  await expect(page.getByRole('heading', { name: 'Atlas Platform', exact: true })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Total project hours: 240.00, summed across all project resources' })).toBeVisible();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    for (const tab of ['Overview', 'Team', 'Hours', 'Project Details']) {
      await page.getByRole('button', { name: tab, exact: true }).click();
      await expect(page.getByRole('button', { name: tab, exact: true })).toHaveAttribute('aria-pressed', 'true');
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      await page.screenshot({ path: path.resolve(__dirname, `../../backend/target/ui-preview/project-${tab.replaceAll(' ', '-').toLowerCase()}-${width}.png`), fullPage: true });
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  for (const tab of ['Overview', 'Team', 'Hours', 'Project Details']) {
    await page.getByRole('button', { name: tab, exact: true }).click();
    await page.screenshot({ path: path.resolve(__dirname, `../../backend/target/ui-preview/project-${tab.replaceAll(' ', '-').toLowerCase()}-dark.png`), fullPage: true });
  }
  expect(errors).toEqual([]);
});
