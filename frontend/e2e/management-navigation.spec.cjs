const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { resetFixtures } = require('./support/api.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });
test('company admin management navigation stays in workspace and reports include projects without submissions', async ({ page, request }) => {
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/dashboard');
  const navigation = page.getByRole('navigation', { name: 'Main navigation' });
  await expect(navigation.getByRole('link', { name: 'Workplace Reports', exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: 'My Invitations', exact: true }).click();
  await expect(navigation).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your company invitations' })).toBeVisible();
  await page.goto('/letter-management');
  await expect(page.getByRole('link', { name: 'My letter requests' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Pending letter requests' })).toBeVisible();
  await page.goto('/leave-management');
  await expect(page.getByRole('button', { name: 'Requests', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('heading', { name: 'Annual company policy' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Policy', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Annual company policy' })).toBeVisible();
  await page.goto('/reports');
  await expect(page.getByLabel('Project')).toBeEnabled();
  await expect(page.getByLabel('Project').locator('option')).not.toHaveCount(0);
});
test('member keeps workplace reporting and invitations inside the workspace', async ({ page, request }) => {
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/dashboard');
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Workplace Reports', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'My Invitations', exact: true }).click();
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
});

test('employment details open in a dialog and submitted profile identity is locked', async ({ page, request }) => {
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/companies');
  const member = page.getByRole('group', { name: 'Member employee@e2e.chronos.test', exact: true });
  await member.getByRole('button', { name: 'Employment details', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Employment details', exact: true });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Employee ID')).toBeVisible();
  await dialog.getByRole('button', { name: 'Close employment details' }).click();
  await expect(dialog).toHaveCount(0);
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/profile');
  for (const label of ['First Name','Last Name','DOB']) {
    await expect(page.getByLabel(label, { exact: true })).toHaveAttribute('readonly', '');
  }
});
