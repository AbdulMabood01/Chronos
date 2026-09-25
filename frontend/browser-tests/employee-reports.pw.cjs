const { test, expect } = require('@playwright/test');

test('Admin Reports opens incident cases, filters, and saves the private tracker on desktop and mobile', async ({ page }) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => localStorage.setItem('authToken', 'test-session'));
  const report = { id: 'abcdef12-1234-4567-8123-123456789012', category: 'SAFETY_CONCERN', subject: 'Workplace safety concern', description: 'A detailed incident description.', anonymous: true, submitted_by: 'Anonymous', status: 'SUBMITTED', submitted_at: '2026-09-21T12:00:00Z', updated_at: '2026-09-21T12:00:00Z', location: 'Main office', attachments: [], history: [{ id: 1, action: 'SUBMITTED', status: 'SUBMITTED', created_at: '2026-09-21T12:00:00Z' }] };
  const queries = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id: 1, firstName: 'HR', lastName: 'Admin', role: 'ADMIN', profileCompleted: true };
    else if (url.pathname.endsWith('/notifications/unread-count')) data = 0;
    else if (url.pathname === '/api/employee-reports') { queries.push(url.search); data = [report]; }
    else if (url.pathname === '/api/employee-reports/' + report.id) {
      if (route.request().method() === 'PATCH') {
        const update = route.request().postDataJSON();
        report.status = update.status;
        report.history.push({ id: 2, action: 'SUBMITTED → UNDER_REVIEW', status: update.status, note: update.note, first_name: 'HR', last_name: 'Admin', created_at: '2026-09-21T13:00:00Z' });
      }
      data = report;
    }
    await route.fulfill({ json: data });
  });
  await page.goto('/reports');
  await expect(page.getByRole('link', { name: 'Reports', exact: true })).toHaveAttribute('href', '/reports');
  await expect(page.getByRole('columnheader', { name: 'Submitted by', exact: true })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Last updated', exact: true })).toBeVisible();
  await page.getByLabel('Search Report ID').fill('abcdef');
  await page.getByRole('combobox', { name: 'Submitted by', exact: true }).selectOption('true');
  await expect.poll(() => queries.some(query => query.includes('reportId=abcdef') && query.includes('anonymous=true'))).toBe(true);
  await page.getByRole('button', { name: 'Review', exact: true }).click();
  await expect(page.getByText('Submitted By: Anonymous', { exact: true })).toBeVisible();
  await page.getByRole('region', { name: 'Report details' }).getByRole('combobox', { name: 'Status', exact: true }).selectOption('UNDER_REVIEW');
  await page.getByLabel('Internal note').fill('Confidential follow-up scheduled.');
  await page.getByRole('button', { name: 'Save review', exact: true }).click();
  await expect(page.getByText(/Updated by: HR Admin/)).toBeVisible();
  await expect(page.locator('.concern-timeline')).toContainText('Confidential follow-up scheduled.');
  await page.screenshot({ path: 'test-results/reports-admin-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/reports-admin-mobile.png', fullPage: true });
  expect(await page.evaluate(() => ({ width: document.documentElement.scrollWidth, viewport: innerWidth,
    overflow: [...document.querySelectorAll('body *')].filter(el => el.getBoundingClientRect().right > innerWidth && !el.closest('.concern-table')).map(el => ({ element: el.tagName + '.' + el.className, width: el.getBoundingClientRect().width, right: el.getBoundingClientRect().right })) }))).toMatchObject({ width: 390, viewport: 390, overflow: [] });
  expect(errors).toEqual([]);
});


test('employee submitted reports open a responsive dedicated lifecycle view', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('authToken', 'test-session'));
  const report = { id: 'own-report', subject: 'Workplace safety concern', category: 'SAFETY_CONCERN', status: 'UNDER_REVIEW', description: 'A detailed incident description.', submitted_at: '2026-09-21T12:00:00Z', history: [{ id: 1, status: 'SUBMITTED', created_at: '2026-09-21T12:00:00Z' }, { id: 2, status: 'UNDER_REVIEW', created_at: '2026-09-21T13:00:00Z', actions_taken: 'Inspection scheduled', resolution: 'Awaiting inspection' }] };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data = [];
    if (path === '/api/auth/me') data = { id: 2, firstName: 'Test', lastName: 'Employee', role: 'EMPLOYEE', profileCompleted: true };
    else if (path.endsWith('/notifications/unread-count')) data = 0;
    else if (path === '/api/employee-reports/mine') data = [report];
    else if (path === '/api/employee-reports/mine/own-report') data = report;
    await route.fulfill({ json: data });
  });
  await page.goto('/workplace-reports');
  const view = page.getByRole('button', { name: 'View Workplace safety concern' });
  await expect(view).toBeVisible();
  const box = await view.boundingBox();
  expect(box.height).toBeGreaterThanOrEqual(44);
  await view.click();
  await expect(page.getByRole('button', { name: 'Submit confidential report' })).toBeHidden();
  await expect(page.getByRole('region', { name: 'Report details' })).toBeVisible();
  await expect(page.getByText('Inspection scheduled', { exact: false })).toBeVisible();
  await expect(page.getByRole('progressbar', { name: 'Report review progress' })).toHaveAttribute('value', '2');
  await page.screenshot({ path: 'test-results/employee-report-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/employee-report-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  await page.getByRole('button', { name: 'Back to Submitted Reports' }).click();
  await expect(view).toBeVisible();
});
