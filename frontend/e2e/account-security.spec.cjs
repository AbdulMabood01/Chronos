const { test, expect } = require('@playwright/test');
const { accounts } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });
test('five failed logins temporarily lock an account without identifying it', async ({ request }) => {
  for (let i = 0; i < 5; i++) {
    const response = await request.post('/api/auth/login', { data: { email: accounts.employee.email, password: 'wrong-password' } });
    expect(response.status()).toBe(401);
    expect((await response.json()).message).toBe('Unable to sign in. Check your credentials or try again later.');
  }
  const blocked = await request.post('/api/auth/login', { data: accounts.employee }); expect(blocked.status()).toBe(401);
  const unknown = await request.post('/api/auth/login', { data: { email: 'unknown@e2e.chronos.test', password: 'wrong-password' } });
  expect(unknown.status()).toBe(401); expect((await unknown.json()).message).toBe((await blocked.json()).message);
});
test('platform account lock and sign-out revoke sessions; company roles cannot control global accounts', async ({ request }) => {
  const employee = await apiAs(request, 'employee');
  const path = `/api/platform/accounts/${fixture.employeeId}/actions`;
  for (const role of ['employee', 'projectAdmin', 'companyAdmin']) {
    const actor = await apiAs(request, role);
    expect((await actor.post(path, { data: { action: 'LOCK', version: 0, reason: 'Security review' } })).status()).toBe(403);
  }
  const platform = await apiAs(request, 'platformAdmin');
  expect((await platform.post(path, { data: { action: 'LOCK', version: 0, reason: 'Security review' } })).ok()).toBe(true);
  expect((await employee.get('/api/auth/me')).status()).toBe(401);
  expect((await request.post('/api/auth/login', { data: accounts.employee })).status()).toBe(423);
  expect((await platform.post(path, { data: { action: 'UNLOCK', version: 1, reason: 'Review complete' } })).ok()).toBe(true);
  const { token } = await json(await request.post('/api/auth/login', { data: accounts.employee }));
  expect((await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })).ok()).toBe(true);
  expect((await platform.post(path, { data: { action: 'SIGN_OUT', version: 2, reason: 'Session revoked' } })).ok()).toBe(true);
  expect((await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(401);
});
