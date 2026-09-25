const { test, expect } = require('@playwright/test');
const path = require('path');
const project = { id: 10, code: 'ATL', name: 'Atlas Platform', status: 'ACTIVE', projectManagerId: 3, projectManagerHoursApproverId: 4, assignments: [] };
const health = { projectId: 10, projectCode: 'ATL', projectName: 'Atlas Platform', status: 'AT_RISK', monitored: true,
  loggedHours: 820, allocatedHours: 1000, plannedHours: 1000, remainingHours: 180, hoursUtilization: 82, activeResources: 4,
  signals: [{ code: 'BURN_AHEAD', severity: 'AT_RISK', message: '82% of hours used with 45% of assignment timeline remaining' },
    { code: 'APPROVALS', severity: 'ATTENTION_NEEDED', message: '3 project timesheets awaiting approval' }],
  evaluatedOn: '2026-09-22', coverageNotes: ['Expense budgets and milestones are not tracked in this workspace.'] };

// Project Health is temporarily disabled in ProjectManagement.jsx.
test.skip('health overview opens details, responds to mobile and dark theme, and recovers from errors', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let fail = false;
  await page.addInitScript(() => localStorage.setItem('authToken', 'health-ui-test'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id: 1, firstName: 'Jordan', lastName: 'Rivera', role: 'PROJECT_ADMIN', profileCompleted: true };
    else if (url.pathname.includes('unread-count')) data = 0;
    else if (url.pathname === '/api/projects/health') {
      if (fail) return route.fulfill({ status: 503, json: { message: 'Unavailable' } });
      data = [health];
    } else if (url.pathname === '/api/projects') data = [project];
    await route.fulfill({ json: data });
  });
  await page.goto('/projects');
  const overview = page.getByRole('region', { name: 'Project Health overview' });
  await expect(overview.getByText('At Risk', { exact: true })).toBeVisible();
  await overview.getByRole('button', { name: /Atlas Platform/ }).click();
  const card = page.getByRole('region', { name: 'Project Health', exact: true });
  await expect(card.getByText(health.signals[0].message)).toBeVisible();
  await page.getByRole('button', { name: 'Project Details', exact: true }).click();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(card.getByRole('progressbar')).toHaveAttribute('value', '82');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: path.resolve(__dirname, `../../backend/target/ui-preview/project-health-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await page.screenshot({ path: path.resolve(__dirname, '../../backend/target/ui-preview/project-health-dark.png'), fullPage: true });
  await page.goto('/projects?projectId=10');
  await expect(card.getByText(health.signals[0].message)).toBeVisible();
  fail = true;
  await page.goto('/projects');
  await expect(overview.getByRole('alert')).toBeVisible();
  await expect(overview.getByText('Healthy', { exact: true })).toHaveCount(0);
  fail = false;
  await overview.getByRole('button', { name: 'Retry health' }).click();
  await expect(overview.getByRole('button', { name: /Atlas Platform/ })).toBeVisible();
  expect(errors).toEqual([]);
});
