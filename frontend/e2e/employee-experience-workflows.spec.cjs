const { test, expect } = require('@playwright/test');
const { accounts, authenticatePage, loginByApi } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test.describe.configure({ mode: 'serial' });
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

test('admin announcement is published, opened, acknowledged, tracked, and archived', async ({ page, request }) => {
  const admin = await apiAs(request, 'superAdmin');
  const today = new Date().toISOString().slice(0, 10);
  let response = await admin.post('/api/announcements', { data: {
    title: 'E2E Policy Update', content: 'Please read and acknowledge this test policy.',
    publishDate: today, expirationDate: null, priority: 'IMPORTANT', status: 'PUBLISHED',
    acknowledgmentRequired: true, version: 0, attachmentName: 'policy.txt',
    attachmentBase64: Buffer.from('E2E policy attachment').toString('base64'), removeAttachment: false,
  }});
  expect(response.ok()).toBeTruthy();
  const announcementId = await response.json();

  const employee = await apiAs(request, 'employee');
  response = await employee.post(`/api/announcements/${announcementId}/open`);
  expect(response.ok()).toBeTruthy();
  const opened = await response.json();
  expect(opened.title).toBe('E2E Policy Update');
  response = await employee.get(`/api/announcements/${announcementId}/attachment`);
  expect(response.ok()).toBeTruthy();
  expect((await response.body()).toString()).toBe('E2E policy attachment');
  expect((await employee.post(`/api/announcements/${announcementId}/acknowledge`, { params: { version: opened.version } })).ok()).toBeTruthy();

  response = await admin.get(`/api/announcements/${announcementId}/tracking`);
  const tracking = await response.json();
  const employeeTracking = tracking.employees.find(row => row.email === accounts.employee.email);
  expect(employeeTracking.viewed_at).toBeTruthy();
  expect(employeeTracking.acknowledged_at).toBeTruthy();

  await authenticatePage(page, request, 'employee');
  await page.goto(`/announcements?id=${announcementId}`);
  await expect(page.getByText('You acknowledged this announcement.')).toBeVisible();

  response = await admin.post(`/api/announcements/${announcementId}/status`, { params: { status: 'ARCHIVED', version: opened.version } });
  expect(response.ok()).toBeTruthy();
  response = await employee.get('/api/announcements');
  expect((await response.json()).some(row => row.id === announcementId)).toBeFalsy();
});

test('anonymous feedback stays private and published review reaches employee', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  let response = await employee.post('/api/feedback-reviews/feedback', { data: {
    employeeId: fixture.projectAdminId, content: 'Anonymous E2E collaboration feedback',
    category: 'COLLABORATION', anonymous: true,
  }});
  expect(response.ok()).toBeTruthy();

  const manager = await apiAs(request, 'projectAdmin');
  response = await manager.get('/api/feedback-reviews/feedback');
  const received = (await response.json()).find(row => row.content.includes('Anonymous E2E'));
  expect(received.anonymous).toBeTruthy();
  expect(received.sender_name).toBeUndefined();

  const admin = await apiAs(request, 'superAdmin');
  const now = new Date();
  response = await admin.post('/api/feedback-reviews/reviews', { data: {
    employeeId: fixture.employeeId, year: now.getFullYear(), quarter: Math.floor(now.getMonth() / 3) + 1,
    summary: 'Strong E2E performance', accomplishments: 'Delivered test coverage', strengths: 'Collaboration',
    improvements: 'Continue documenting', goals: 'Expand automation', comments: 'Ready to publish', version: 0,
  }});
  expect(response.ok()).toBeTruthy();
  const reviewId = await response.json();
  expect((await admin.post(`/api/feedback-reviews/reviews/${reviewId}/publish`, { params: { version: 0 } })).ok()).toBeTruthy();

  response = await employee.get('/api/feedback-reviews/reviews');
  expect((await response.json()).some(row => row.summary === 'Strong E2E performance' && row.published_at)).toBeTruthy();
  await authenticatePage(page, request, 'employee');
  await page.goto('/performance-reviews');
  await expect(page.getByText('Strong E2E performance')).toBeVisible();
});

test('password reset changes credentials, consumes token, and invalidates old session', async ({ page, request }) => {
  const oldToken = await loginByApi(request, accounts.employee);
  const admin = await apiAs(request, 'superAdmin');
  let response = await admin.post(`/api/e2e/password-reset-token/${fixture.employeeId}`);
  expect(response.ok()).toBeTruthy();
  const { token } = await response.json();
  const newPassword = 'Reset-E2E-Password-456!';

  await page.goto(`/reset-password#token=${token}`);
  await page.getByLabel('New password', { exact: true }).fill(newPassword);
  await page.getByLabel('Confirm new password', { exact: true }).fill(newPassword);
  await page.getByRole('button', { name: /reset password/i }).click();
  await expect(page.getByText(/Password reset. Sign in with your new password/)).toBeVisible();

  response = await request.post('/api/auth/reset-password/validate', { data: { token } });
  expect(response.status()).toBe(400);
  response = await request.get('/api/auth/me', { headers: { Authorization: `Bearer ${oldToken}` } });
  expect([401, 403]).toContain(response.status());
  response = await request.post('/api/auth/login', { data: { email: accounts.employee.email, password: newPassword } });
  expect(response.ok()).toBeTruthy();
});

test('anonymous workplace report protects identity and supports admin review', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  let response = await employee.post('/api/employee-reports', { multipart: {
    report: { name: 'report.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({
      category: 'SAFETY_CONCERN', subject: 'Anonymous E2E safety concern',
      description: 'A confidential condition requiring review.', incidentAt: null,
      location: 'Test facility', peopleInvolved: '', witnesses: '', anonymous: true, privacyAcknowledged: true,
    })) },
    attachments: { name: 'evidence.txt', mimeType: 'text/plain', buffer: Buffer.from('sanitized evidence') },
  }});
  expect(response.ok()).toBeTruthy();
  const receipt = await response.json();
  response = await employee.get('/api/employee-reports/mine');
  expect((await response.json()).some(row => row.id === receipt.reportId)).toBeFalsy();
  response = await employee.get(`/api/employee-reports/mine/${receipt.reportId}`);
  expect([403, 404]).toContain(response.status());

  const admin = await apiAs(request, 'superAdmin');
  response = await admin.get(`/api/employee-reports/${receipt.reportId}`);
  expect(response.ok()).toBeTruthy();
  const report = await response.json();
  expect(report.anonymous).toBeTruthy();
  expect(report.reporter).toBeUndefined();
  expect(report.attachments).toHaveLength(1);
  response = await admin.patch(`/api/employee-reports/${receipt.reportId}`, { data: {
    status: 'UNDER_REVIEW', note: 'Private HR note', actionsTaken: '', resolution: '', shareWithEmployee: false,
  }});
  expect(response.ok()).toBeTruthy();

  await authenticatePage(page, request, 'superAdmin');
  await page.goto('/reports');
  const reportRow = page.getByRole('row').filter({ hasText: 'Anonymous E2E safety concern' });
  await expect(reportRow).toBeVisible();
  await expect(reportRow).toContainText('Anonymous');
});

test('rejected timesheet is corrected, resubmitted, and approved', async ({ page, request }) => {
  const employee = await apiAs(request, 'employee');
  const now = new Date(); const year = now.getFullYear(); const month = now.getMonth() + 1;
  let response = await employee.post('/api/timesheets', { params: { year, month } });
  const sheet = await response.json();
  response = await employee.post(`/api/timesheets/${sheet.id}/time-entries`, { data: {
    timesheetId: sheet.id, entryDate: now.toISOString().slice(0, 10), hours: '4', notes: 'Needs correction', projectId: fixture.projectId,
  }});
  const entry = await response.json();
  response = await employee.post(`/api/timesheets/${sheet.id}/projects/${fixture.projectId}/submit`);
  let submission = await response.json();

  const manager = await apiAs(request, 'projectAdmin');
  response = await manager.post(`/api/approvals/timesheet-project/${submission.id}/reject`, { data: { reason: 'Please correct the hours' } });
  expect((await response.json()).status).toBe('REJECTED');
  response = await employee.put(`/api/timesheets/${sheet.id}/time-entries/${entry.id}`, { data: {
    hours: '8', notes: 'Corrected E2E hours', projectId: fixture.projectId,
  }});
  expect(response.ok()).toBeTruthy();
  response = await employee.post(`/api/timesheets/${sheet.id}/projects/${fixture.projectId}/submit`);
  submission = await response.json();
  expect(submission.status).toBe('SUBMITTED');
  response = await manager.post(`/api/approvals/timesheet-project/${submission.id}/approve`);
  expect((await response.json()).status).toBe('APPROVED');

  await authenticatePage(page, request, 'employee');
  await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
});
