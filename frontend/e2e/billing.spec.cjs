const { test, expect } = require('@playwright/test');
const { resetFixtures, apiAs } = require('./support/api.cjs');
const { authenticatePage } = require('./support/auth.cjs');
const { json } = require('./support/workflows.cjs');

test.beforeEach(async ({ request }) => { await resetFixtures(request); });

test('company billing shows actual prepaid prices and never activates a plan from an unpaid quote', async ({ page, request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  const companies = await json(await admin.get('/api/companies'));
  const company = companies.find(c => c.slug === 'e2e-company');
  await authenticatePage(page, request, 'companyAdmin', company.id);
  await page.goto('/billing');
  await expect(page.getByRole('heading', { name: 'Company billing' })).toBeVisible();
  await expect(page.getByText('Dated support / contract grant. No payment is recorded.')).toBeVisible();
  await page.getByRole('button', { name: 'Review prepaid quote' }).click();
  const quote = page.getByRole('region', { name: 'Purchase quote' });
  await expect(quote.getByText('Pay once: $1,430.40')).toBeVisible();
  await quote.getByRole('link', { name: 'Continue to payment review' }).click();
  await expect(page).toHaveURL(/\/billing\/payment\?quote=/);
  await expect(page.getByRole('heading',{name:'Online payment is not available yet'})).toBeVisible();
  await page.getByRole('link',{name:'Return to billing'}).click();
  const after = await json(await admin.get(`/api/companies/${company.id}/billing`));
  expect(after.entitlement.source).toBe('CONTRACT');
  expect(after.purchases[0].status).toBe('QUOTED');
  await page.screenshot({ path: '../reports/billing-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../reports/billing-mobile.png', fullPage: true });
});

test('a new company enforces seven people under concurrent invitations and starts only one trial', async ({ page, request }) => {
  const platform = await apiAs(request, 'platformAdmin');
  const other = await apiAs(request, 'otherAdmin');
  const key = `billing-${Date.now()}`;
  const company = await json(await platform.post('/api/companies', { data: { name: 'Billing Trial Company', slug: key, adminEmail: 'other-admin@e2e.chronos.test' } }));
  const invites = await json(await platform.get(`/api/platform/companies/${company.id}/admin-invitations`));
  expect((await other.post(`/api/companies/invitations/${invites[0].id}/accept`)).ok()).toBeTruthy();
  let state = await json(await other.get(`/api/companies/${company.id}/billing`));
  expect(state.entitlement.activeUsers).toBe(1); expect(state.entitlement.includedUsers).toBe(7);
  for (let i = 0; i < 5; i++) expect((await other.post(`/api/companies/${company.id}/invitations`, { data: { email: `${key}-${i}@example.com`, role: 'PROJECT_ADMIN' } })).ok()).toBeTruthy();
  const raced = await Promise.all([0, 1].map(i => other.post(`/api/companies/${company.id}/invitations`, { data: { email: `${key}-last-${i}@example.com`, role: 'PROJECT_ADMIN' } })));
  expect(raced.map(r => r.status()).sort()).toEqual([200, 409]);
  expect((await other.post(`/api/companies/${company.id}/invitations`, { data: { email: `${key}-0@example.com`, role: 'PROJECT_ADMIN' } })).ok()).toBeTruthy();
  state = await json(await other.get(`/api/companies/${company.id}/billing`)); expect(state.entitlement.reservations).toBe(6);
  await authenticatePage(page, request, 'otherAdmin', company.id); await page.goto('/billing');
  await page.getByRole('button', { name: 'Start Pro Plus trial' }).click();
  await expect(page.getByText('Current plan: Pro Plus')).toBeVisible();
  state = await json(await other.get(`/api/companies/${company.id}/billing`));
  expect(state.entitlement.source).toBe('TRIAL'); expect(state.entitlement.includedUsers).toBe(175); expect(state.profile.trial_used).toBe(true);
  expect((await other.post(`/api/companies/${company.id}/billing/trial`)).status()).toBe(409);
});

test('project roles and another company cannot read billing or forge checkout fulfillment', async ({ page, request }) => {
  const admin = await apiAs(request, 'companyAdmin'), employee = await apiAs(request, 'employee'), manager = await apiAs(request, 'manager'), other = await apiAs(request, 'otherAdmin');
  const companies = await json(await admin.get('/api/companies')), company = companies.find(c => c.slug === 'e2e-company');
  for (const account of [employee, manager, other]) expect((await account.get(`/api/companies/${company.id}/billing`)).status()).toBe(403);
  const unpaid = await json(await admin.post(`/api/companies/${company.id}/billing/quotes`, { data: { plan: 'PRO', months: 3, extraSeats: 0, kind: 'PLAN', amount_cents: 1 } }));
  expect(unpaid.amount_cents).toBe(44700);
  expect((await request.post('/api/billing/stripe/webhook', { data: { type: 'checkout.session.completed', company: company.id, paid: true } })).ok()).toBe(false);
  expect((await json(await admin.get(`/api/companies/${company.id}/billing`))).entitlement.source).toBe('CONTRACT');
  await page.goto('/pricing'); await expect(page.getByRole('heading', { name: 'Company plans' })).toBeVisible(); await expect(page.getByText('$5,750.40')).toBeVisible();
});
