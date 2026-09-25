const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const output = path.resolve(__dirname, '../../backend/target/ui-preview');
async function setup(page, role = 'EMPLOYEE') {
  const now = new Date('2026-09-23T12:00:00Z'), year = now.getFullYear(), month = now.getMonth() + 1;
  await page.clock.setFixedTime(now);
  const prefix = year + '-' + String(month).padStart(2, '0');
  const sheet = { id: 2, userId: 1, year, month, status: 'REJECTED', timeEntries: [{ id: 10, projectId: 4, projectCode: 'ATLAS', entryDate: prefix + '-07', hours: 8 }], vacationDays: [] };
  let status = 'REJECTED';
  let preferences = { enabled: true, announcements: true, timesheets: true, vacation: true, letters: true, reports: true, feedback: true, performance: true };
  await page.addInitScript(() => localStorage.setItem('authToken', 'workflow-test'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()), method = route.request().method();
    let data = [];
    if (url.pathname.endsWith('/auth/me')) data = { id: 1, firstName: 'Alice', lastName: 'Smith', role, canReviewProjects: role === 'ADMIN', profileCompleted: true };
    else if (url.pathname.endsWith('/projects/assigned') || url.pathname === '/api/projects') data = [{ id: 4, code: 'ATLAS', name: 'Atlas platform', status: 'ACTIVE', assignments: [{ userId: 1, isActive: true, startDate: prefix + '-01', endDate: prefix + '-' + new Date(year, month, 0).getDate() }] }];
    else if (url.pathname.endsWith('/timesheets/missing')) data = [{ userId: 8, userName: 'Sam Jones', projectId: 4, projectCode: 'ATLAS', status: 'NOT_STARTED', hours: 0 }];
    else if (url.pathname.endsWith('/projects/4/submit')) { status = 'SUBMITTED'; data = {}; }
    else if (url.pathname.endsWith('/submission')) data = { id: 3, timesheetId: 2, projectId: 4, status, totalHours: 8, plannedHours: 160, rejectionReason: status === 'REJECTED' ? 'Please verify Monday hours.' : null, rejectedByName: 'Taylor Manager' };
    else if (url.pathname.includes('/timesheets/id/') || /\/timesheets\/\d{4}\/\d+$/.test(url.pathname)) data = sheet;
    else if (url.pathname.endsWith('/leave-balance')) { const bucket = { allowanceDays: 10, extraDays: 0, usedDays: 2, remainingDays: 8, unpaidDays: 0 }; data = { configured: true, vacation: bucket, sick: bucket, bereavement: bucket }; }
    else if (url.pathname.endsWith('/email-preferences')) {
      if (method === 'PUT') preferences = route.request().postDataJSON();
      data = preferences;
    }
    else if (url.pathname.includes('unread-count')) data = 0;
    await route.fulfill({ json: data });
  });
  fs.mkdirSync(output, { recursive: true });
  return prefix;
}
test('employee resubmits corrections without copy-week controls', async ({ page }) => {
  await setup(page);
  await page.goto('/timesheet/2');
  await expect(page.getByRole('region', { name: 'Requested corrections' })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.getByText('Copy last week', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Resubmit for Approval' }).click();
  await expect(page.getByRole('region', { name: 'Requested corrections' })).toHaveCount(0);
});

test('email preferences persist, support keyboard dismissal and fit mobile and dark theme', async ({ page }) => {
  await setup(page);
  await page.goto('/timesheet/2');
  const button = page.getByRole('button', { name: 'Email alerts', exact: true });
  await button.click();
  const dialog = page.getByRole('dialog', { name: 'Email alerts' });
  await expect(dialog.getByRole('switch')).toHaveCount(8);
  await dialog.getByRole('switch', { name: 'Feedback', exact: true }).click();
  await dialog.getByRole('switch', { name: 'Enable email alerts' }).click();
  await expect(dialog.getByRole('switch', { name: 'Announcements' })).toBeDisabled();
  await dialog.getByRole('button', { name: 'Save preferences' }).click();
  await expect(dialog.getByRole('status')).toHaveText('Email preferences saved.');
  await page.screenshot({ path: path.join(output, 'email-alerts-desktop.png'), fullPage: true });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(button).toBeFocused();
  await page.reload();
  await button.click();
  await expect(dialog.getByRole('switch', { name: 'Enable email alerts' })).toHaveAttribute('aria-checked', 'false');
  await dialog.getByRole('switch', { name: 'Enable email alerts' }).click();
  await expect(dialog.getByRole('switch', { name: 'Feedback', exact: true })).toHaveAttribute('aria-checked', 'false');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await button.click();
  await expect(dialog).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await page.screenshot({ path: path.join(output, 'email-alerts-mobile-dark.png'), fullPage: true });
});
test('manager favorites a project and keeps the selection after reloading', async ({ page }) => {
  await setup(page, 'ADMIN');
  await page.goto('/projects');
  await page.getByRole('button', { name: 'Add to favorites' }).click();
  await expect(page.getByRole('button', { name: 'Remove from favorites' })).toHaveAttribute('aria-pressed', 'true');
  await page.reload();
  await expect(page.getByRole('button', { name: 'Remove from favorites' })).toHaveAttribute('aria-pressed', 'true');
});
test('leave request shows balance preview before saving on mobile', async ({ page }) => {
  await setup(page);
  await page.goto('/vacation');
  await page.getByLabel('Start Date').fill('2026-09-14');
  await page.getByLabel('End Date').fill('2026-09-15');
  await expect(page.getByRole('region', { name: 'Leave balance preview' })).toContainText('Paid days remaining');
  await expect(page.getByText('Paid days remaining', { exact: true }).locator('..')).toContainText('6');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: path.join(output, 'leave-preview-mobile.png'), fullPage: true });
});
test('manager sees employees who never started a timesheet', async ({ page }) => {
  await setup(page, 'ADMIN');
  await page.goto('/missing-timesheets');
  await expect(page.getByRole('link', { name: 'Missing timesheets' })).toHaveClass(/active/);
  await expect(page.getByText('Sam Jones')).toBeVisible();
  await expect(page.getByText('No timesheet yet')).toBeVisible();
  await page.screenshot({ path: path.join(output, 'missing-timesheets-desktop.png'), fullPage: true });
});

test('manager sorts and filters active project timesheet records', async ({ page }) => {
  await setup(page, 'ADMIN');
  await page.route('**/api/timesheets/missing?*', route => route.fulfill({ json: [
    { userId: 8, userName: 'Sam Jones', projectId: 4, projectCode: 'ATLAS', status: 'NOT_STARTED', hours: 0 },
    { userId: 9, userName: 'Alex Smith', projectId: 5, projectCode: 'BETA', status: 'APPROVED', hours: 100, timesheetId: 9 },
  ] }));
  await page.goto('/missing-timesheets');
  await expect(page.getByText('Approved', { exact: true })).toHaveClass(/status-approved/);
  await page.getByRole('button', { name: 'Hours', exact: true }).click();
  await expect(page.locator('tbody tr').first()).toContainText('Sam Jones');
  await page.getByRole('button', { name: 'Hours', exact: true }).click();
  await expect(page.locator('tbody tr').first()).toContainText('Alex Smith');
  await page.getByLabel('Project (optional)').selectOption('5');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.getByRole('link', { name: 'View timesheet' })).toHaveAttribute('href', '/timesheet/9?projectId=5');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: path.join(output, 'missing-timesheets-filter-mobile.png'), fullPage: true });
});
