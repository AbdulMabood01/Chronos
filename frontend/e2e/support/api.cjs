const { expect } = require('@playwright/test');
const { accounts, loginByApi, clearSessions } = require('./auth.cjs');

async function apiAs(request, role) {
  const token = await loginByApi(request, accounts[role]);
  const headers = { Authorization: `Bearer ${token}` };
  return {
    token,
    get: (url, options = {}) => request.get(url, { ...options, headers: { ...headers, ...options.headers } }),
    post: (url, options = {}) => request.post(url, { ...options, headers: { ...headers, ...options.headers } }),
    put: (url, options = {}) => request.put(url, { ...options, headers: { ...headers, ...options.headers } }),
    patch: (url, options = {}) => request.patch(url, { ...options, headers: { ...headers, ...options.headers } }),
    delete: (url, options = {}) => request.delete(url, { ...options, headers: { ...headers, ...options.headers } }),
  };
}

async function resetFixtures(request) {
  const admin = await apiAs(request, 'companyAdmin');
  const response = await admin.post('/api/e2e/reset');
  const body = await response.text();
  expect(response.ok(), `Fixture reset failed: ${response.status()} ${body}`).toBeTruthy();
  clearSessions(request);
  return JSON.parse(body);
}

module.exports = { apiAs, resetFixtures };
