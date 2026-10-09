const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json, configureLetters, letterInput, letterDecision } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => {
  fixture = await resetFixtures(request);
  await configureLetters(await apiAs(request, 'companyAdmin'), fixture.companyId);
});

for (const decision of ['approve', 'reject']) {
  test(`member creates a letter in the browser and company admin ${decision}s it`, async ({ page, request }) => {
    const employee = await apiAs(request, 'employee');
    await authenticatePage(page, request, 'employee', fixture.companyId);
    await page.goto('/requests');
    await page.getByLabel('Full Name', { exact: true }).fill(letterInput.requestedFullName);
    await page.getByLabel('Title', { exact: true }).fill(letterInput.requestedJobTitle);
    await page.getByLabel('Job Start Date', { exact: true }).fill(letterInput.employmentStartDate);
    await page.getByRole('button', { name: 'Submit for Approval', exact: true }).click();
    await expect.poll(async () => (await json(await employee.get(`/api/companies/${fixture.companyId}/letter-requests/my`))).length).toBe(1);
    const [letter] = await json(await employee.get(`/api/companies/${fixture.companyId}/letter-requests/my`));
    await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
    await page.goto('/letter-management');
    await page.getByRole('link', { name: 'Review letter', exact: true }).click();
    if (decision === 'approve') {
      await page.getByRole('button', { name: 'Preview final letter', exact: true }).click();
      await page.getByRole('checkbox', { name: 'I verified these details for the final PDF' }).check();
      await page.getByRole('button', { name: 'Approve', exact: true }).click();
    } else {
      await page.getByRole('button', { name: 'Reject', exact: true }).click();
      await page.locator('textarea').last().fill('Please correct the employment details');
      await page.getByRole('dialog').getByRole('button', { name: 'Reject', exact: true }).click();
    }
    await expect.poll(async () => (await json(await employee.get(`/api/companies/${fixture.companyId}/letter-requests/${letter.id}`))).status).toBe(decision === 'approve' ? 'APPROVED' : 'REJECTED');
    if (decision === 'approve') {
      const pdf = await employee.get(`/api/companies/${fixture.companyId}/letter-requests/${letter.id}/pdf`);
      expect(pdf.ok()).toBeTruthy(); expect(pdf.headers()['content-type']).toContain('application/pdf');
      expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');
    }
    await authenticatePage(page, request, 'employee', fixture.companyId);
    await page.goto('/requests');
    await expect(page.getByText(decision === 'approve' ? 'APPROVED' : 'REJECTED', { exact: true }).first()).toBeVisible();
  });
}

test('letter approvals deny unrelated roles, another company, platform and self', async ({ request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  const base = `/api/companies/${fixture.companyId}/letter-requests`;
  const letter = await json(await admin.post(base, { data: letterInput }));
  for (const role of ['projectAdmin', 'manager', 'moderator', 'employee', 'otherAdmin', 'platformAdmin', 'companyAdmin']) {
    const actor = await apiAs(request, role);
    const response = await actor.post(`${base}/${letter.id}/approve`, { data: letterDecision(letter) });
    expect(response.status()).toBe(role === 'companyAdmin' ? 400 : 403);
    expect((await json(await admin.get(`${base}/${letter.id}`))).status).toBe('SUBMITTED');
  }
  const second = await apiAs(request, 'secondAdmin');
  expect((await second.get(`/api/companies/${fixture.otherCompanyId}/letter-requests/${letter.id}`)).ok()).toBeFalsy();
  expect((await json(await second.post(`${base}/${letter.id}/approve`, { data: letterDecision(letter) }))).status).toBe('APPROVED');
  expect((await second.post(`${base}/${letter.id}/approve`, { data: letterDecision(letter) })).ok()).toBeFalsy();
});
