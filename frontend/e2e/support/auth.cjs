const { expect } = require('@playwright/test');

const accounts = {
  employee: { email: 'employee@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  projectAdmin: { email: 'project-admin@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  companyAdmin: { email: 'admin@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  manager: { email: 'manager@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  platformAdmin: { email: 'platform@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  otherAdmin: { email: 'other-admin@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  secondAdmin: { email: 'second-admin@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  creator: { email: 'creator@e2e.chronos.test', password: 'Chronos-E2E-123!' },
  moderator: { email: 'moderator@e2e.chronos.test', password: 'Chronos-E2E-123!' },
};
const sessions = new WeakMap();
function clearSessions(request) { sessions.delete(request); }
async function loginByApi(request, account) {
  const cache = sessions.get(request) || new Map();
  sessions.set(request, cache);
  const key = `${account.email}:${account.password}`;
  if (cache.has(key)) return cache.get(key);
  let response = await request.post('/api/auth/login', { data: { email: account.email, password: account.password } });
  if (response.status() === 429) {
    // Respect the real server limiter instead of disabling authentication protection in E2E.
    await new Promise(resolve => setTimeout(resolve, Number(response.headers()['retry-after'] || 60) * 1000));
    response = await request.post('/api/auth/login', { data: { email: account.email, password: account.password } });
  }
  expect(response.ok(), `Login failed for ${account.email}: ${response.status()}`).toBeTruthy();
  const token = (await response.json()).token;
  cache.set(key, token);
  return token;
}

async function authenticatePage(page, request, role, companyId) {
  const account = accounts[role];
  const token = await loginByApi(request, account);
  const me = await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } });
  expect(me.ok()).toBeTruthy();
  const { id } = await me.json();
  // One replaceable script avoids old identities winning on subsequent navigations.
  await page.addInitScript(() => {
    const value = window.name.startsWith('chronos-e2e:') ? JSON.parse(window.name.slice(12)) : null;
    if (!value) return;
    localStorage.setItem('authToken', value.token);
    if (value.companyId) localStorage.setItem(`chronos:company:${value.id}`, String(value.companyId));
  });
  await page.evaluate(value => { window.name = `chronos-e2e:${JSON.stringify(value)}`; }, { token, id, companyId });
  return token;
}

async function loginThroughUi(page, role) {
  const account = accounts[role];
  await page.goto('/login');
  await page.getByLabel('Work email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  return account;
}

module.exports = { accounts, loginByApi, authenticatePage, loginThroughUi, clearSessions };
