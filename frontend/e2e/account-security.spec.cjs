const { test, expect } = require('@playwright/test');
const { accounts } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test.describe.configure({ mode: 'serial' });
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

test('five failed logins temporarily lock an account without identifying it', async ({ request }) => {
  for (let i = 0; i < 5; i++) {
    const response = await request.post('/api/auth/login', { data: { email: accounts.employee.email, password: 'wrong-password' } });
    expect(response.status()).toBe(401);
    expect((await response.json()).message).toBe('Unable to sign in. Check your credentials or try again later.');
  }
  const blocked = await request.post('/api/auth/login', { data: accounts.employee });
  expect(blocked.status()).toBe(401);
  const unknown = await request.post('/api/auth/login', { data: { email: 'unknown@e2e.chronos.test', password: 'wrong-password' } });
  expect(unknown.status()).toBe(401);
  expect((await unknown.json()).message).toBe((await blocked.json()).message);
});

test('admin lock and sign-out-all revoke active tokens; other roles cannot control accounts', async ({ request }) => {
  const employee = await apiAs(request, 'employee');
  const projectAdmin = await apiAs(request, 'projectAdmin');
  const admin = await apiAs(request, 'superAdmin');
  for (const client of [employee, projectAdmin]) {
    expect((await client.patch(`/api/users/${fixture.employeeId}/lock`, { data: { reason: 'test' } })).status()).toBe(403);
    expect((await client.patch(`/api/users/${fixture.employeeId}/unlock`)).status()).toBe(403);
    expect((await client.post(`/api/users/${fixture.employeeId}/sign-out-all`)).status()).toBe(403);
  }
  const locked = await admin.patch(`/api/users/${fixture.employeeId}/lock`, { data: { reason: 'Security review' } });
  expect(locked.ok(), `Lock failed: ${locked.status()} ${await locked.text()}`).toBeTruthy();
  expect((await employee.get('/api/auth/me')).status()).toBe(401);
  expect((await request.post('/api/auth/login', { data: accounts.employee })).status()).toBe(401);
  expect((await admin.patch(`/api/users/${fixture.employeeId}/unlock`)).ok()).toBeTruthy();
  const replacement = await request.post('/api/auth/login', { data: accounts.employee });
  expect(replacement.ok()).toBeTruthy();
  const token = (await replacement.json()).token;
  expect((await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })).ok()).toBeTruthy();
  expect((await admin.post(`/api/users/${fixture.employeeId}/sign-out-all`)).ok()).toBeTruthy();
  expect((await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(401);
});
