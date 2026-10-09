const { expect } = require('@playwright/test');
async function json(response) {
  const body = await response.text();
  expect(response.ok(), `${response.url()}: ${response.status()} ${body}`).toBeTruthy();
  return JSON.parse(body);
}
function monthOffset(today, offset) {
  const [year, month] = today.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1 + offset, 1));
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, date: date.toISOString().slice(0, 10) };
}
async function configureLetters(admin, companyId) {
  return json(await admin.put(`/api/companies/${companyId}/letter-settings`, { data: {
    version: -1,
    employers: [{ id: 'employer', name: 'E2E Company', address: '100 Test Street', email: 'hr@e2e.chronos.test' }],
    signatories: [{ id: 'hr', name: 'Casey HR', title: 'HR Manager' }], templates: [],
  } }));
}
const letterInput = { requestType: 'EMPLOYMENT_VERIFICATION', requestedFullName: 'Test Employee', requestedJobTitle: 'Software Engineer', employmentStartDate: '2024-01-15', purpose: 'E2E employment verification' };
function letterDecision(letter) {
  return { configurationVersion: 0, version: letter.version, fullName: letter.requestedFullName, jobTitle: letter.requestedJobTitle, employmentStartDate: letter.employmentStartDate };
}
module.exports = { json, monthOffset, configureLetters, letterInput, letterDecision };

