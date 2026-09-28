const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test('expense changes, approval, and project totals stay in sync', async ({ page, request }) => {
  test.setTimeout(120000);
  const fixture = await resetFixtures(request);
  const manager = await apiAs(request, 'projectAdmin');
  const project = (await (await manager.get('/api/projects')).json()).find(p => p.id === fixture.projectId);
  expect((await manager.put(`/api/projects/${fixture.projectId}`, { data: {
    code: project.code, name: project.name, description: project.description, status: project.status,
    projectManagerId: project.projectManagerId, projectManagerHoursApproverId: project.projectManagerHoursApproverId,
    expenseBudget: 100,
  }})).ok()).toBeTruthy();

  await authenticatePage(page, request, 'employee');
  await page.goto('/expenses');
  await page.getByRole('combobox', { name: 'Project', exact: true }).selectOption(String(fixture.projectId));
  await page.getByRole('combobox', { name: 'Category', exact: true }).selectOption('TRAVEL');
  await page.getByLabel('Amount').fill('45.00');
  await page.getByLabel('Description').fill('Train fare for site visit');
  await page.getByLabel(/Receipt or document/).setInputFiles({ name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n1 0 obj\nendobj\n%%EOF') });
  await page.getByRole('button', { name: 'Submit for approval' }).click();
  await expect(page.getByRole('heading', { name: 'My expenses' })).toBeVisible();
  await expect(page.getByText('Pending approval').first()).toBeVisible();

  const employee = await apiAs(request, 'employee');
  const expenses = await (await employee.get('/api/expenses/mine')).json();
  const id = expenses[0].id;
  expect(expenses[0].status).toBe('PENDING_APPROVAL');
  expect((await (await manager.get('/api/expenses/pending')).json()).some(e => e.id === id)).toBeTruthy();
  expect((await (await manager.get(`/api/expenses/projects/${fixture.projectId}/totals`)).json()).pending).toBe(45);

  const changes = await manager.post(`/api/expenses/${id}/decision`, { data: { status: 'CHANGES_REQUESTED', comment: 'Attach the final fare receipt' } });
  expect(changes.ok()).toBeTruthy();
  await page.reload();
  await expect(page.getByText('Changes requested').first()).toBeVisible();
  await page.getByRole('button', { name: 'View' }).first().click();
  await expect(page.getByText('Attach the final fare receipt').first()).toBeVisible();
  await page.getByRole('button', { name: 'Edit and resubmit' }).click();
  await page.getByLabel('Amount').fill('50.00');
  await page.getByRole('button', { name: 'Resubmit for approval' }).click();
  await expect(page.getByText('Pending approval').first()).toBeVisible();

  await authenticatePage(page, request, 'projectAdmin');
  await page.goto('/expenses');
  await expect(page.getByRole('heading', { name: 'Expense approvals' })).toHaveCount(0);
  await page.goto('/admin');
  await expect(page.getByText('Expense Approval')).toBeVisible();
  await page.getByText('Expense Approval').locator('xpath=ancestor::article').getByRole('button', { name: 'Review' }).click();
  await Promise.all([
    page.waitForResponse(response => response.url().includes(`/api/expenses/${id}/decision`) && response.request().method() === 'POST' && response.ok()),
    page.getByRole('button', { name: 'Approve' }).click(),
  ]);
  const totals = await (await manager.get(`/api/expenses/projects/${fixture.projectId}/totals`)).json();
  expect(Number(totals.approved)).toBe(50);
  expect(Number(totals.pending)).toBe(0);
  expect(Number(totals.remaining)).toBe(50);
  await page.goto('/projects');
  await page.getByRole('button', { name: /E2E Core Project/ }).click();
  await expect(page.getByRole('img', { name: /Project expenses: 50.00 approved, 0.00 pending, 50.00 remaining/ })).toBeVisible();
  await authenticatePage(page, request, 'employee');
  await page.goto('/expenses');
  await expect(page.getByText('Approved').first()).toBeVisible();
});
