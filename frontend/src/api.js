import axios from 'axios';

const API_BASE_URL = '/api';

export const announcementAPI = {
  list: (management = false) => apiClient.get('/announcements', { params: { management }, background: true }),
  open: (id, management = false) => apiClient.post(`/announcements/${id}/open`, null, { params: { management } }),
  save: (id, data) => id ? apiClient.put(`/announcements/${id}`, data) : apiClient.post('/announcements', data),
  status: (id, status, version) => apiClient.post(`/announcements/${id}/status`, null, { params: { status, version } }),
  remove: (id, version) => apiClient.delete(`/announcements/${id}`, { params: { version } }),
  acknowledge: (id, version) => apiClient.post(`/announcements/${id}/acknowledge`, null, { params: { version } }),
  tracking: id => apiClient.get(`/announcements/${id}/tracking`),
  attachment: (id, management = false) => apiClient.get(`/announcements/${id}/attachment`, { params: { management }, responseType: 'blob' }),
};

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json',
  },
});

function dispatchApiActivityEvent(name) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(name));
  }
}

// Add token to requests if available
apiClient.interceptors.request.use((config) => {
  if (!config.background) dispatchApiActivityEvent('chronos:api-start');
  const token = localStorage.getItem('authToken');
  if (token && !config.publicAuth && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  if (!error.config?.background) dispatchApiActivityEvent('chronos:api-end');
  return Promise.reject(error);
});

apiClient.interceptors.response.use((response) => {
  if (!response.config?.background) dispatchApiActivityEvent('chronos:api-end');
  if (typeof window !== 'undefined' && window.__chronosConnectionLost && response.config?.recoveryProbe) {
    window.__chronosConnectionLost = false;
    dispatchApiActivityEvent('chronos:connection-restored');
  }
  return response;
}, (error) => {
  if (!error.config?.background) dispatchApiActivityEvent('chronos:api-end');
  const status = error.response?.status;
  const leaveRequest = /^\/(?:vacation(?:\/|$)|approvals\/vacation\/|settings\/leave-defaults\/)/.test(error.config?.url || '');
  const validationMessage = leaveRequest && (status === 400 || status === 409)
    && typeof error.response?.data?.message === 'string'
    ? error.response.data.message.trim() : '';
  const passwordRequest = /^\/auth\/(?:reset-password(?:\/validate)?|change-password)$/.test(error.config?.url || '');
  const passwordErrors = new Set([
    'This reset link is invalid or expired. Request a new reset link.',
    'Choose a password different from your current password.',
    'Current password is incorrect.',
    'Passwords do not match.',
    'Use at least 12 characters with uppercase, lowercase and a number (maximum 72 UTF-8 bytes).',
  ]);
  const passwordMessage = passwordRequest && status === 400
    && passwordErrors.has(error.response?.data?.message)
    ? error.response.data.message : '';
  const message = !error.response && error.code === 'ECONNABORTED'
    ? 'The request is taking longer than expected. Please try again.'
    : !error.response ? 'Unable to connect. Check your internet connection and try again.'
    : status === 401 ? 'Your session has expired. Please sign in again.'
    : status === 403 ? "You don't have permission to perform this action."
    : status === 404 ? 'The requested item was not found.'
    : status === 423 && error.config?.url === '/auth/login'
      ? 'Your account is locked. Contact an administrator.'
    : status >= 500 ? 'Something went wrong on our side. Please try again.'
    : status === 429 && error.config?.publicAuth ? 'Unable to sign in. Check your credentials or try again later.'
    : validationMessage || passwordMessage || (status === 400 ? 'Please check your entries and try again.'
      : 'Unable to complete the request. Please try again.');
  error.userMessage = message;
  error.message = message;
  if (error.response && typeof error.response.data === 'object' && !(error.response.data instanceof Blob)) {
    error.response.data = { ...error.response.data, message };
  }
  if (!error.response && error.code !== 'ECONNABORTED' && typeof window !== 'undefined') {
    window.__chronosConnectionLost = true;
    dispatchApiActivityEvent('chronos:connection-lost');
  }
  if (status === 401 && !error.config?.publicAuth && !error.config?.skipSessionEvent) {
    dispatchApiActivityEvent('chronos:session-expired');
  }
  return Promise.reject(error);
});

export const authAPI = {
  register: data => apiClient.post('/auth/register', data, { publicAuth: true }),
  forgotPassword: email => apiClient.post('/auth/forgot-password', { email }, { publicAuth: true }),
  validatePasswordReset: token => apiClient.post('/auth/reset-password/validate', { token }, { publicAuth: true }),
  resetPassword: data => apiClient.post('/auth/reset-password', data, { publicAuth: true }),
  changePassword: data => apiClient.post('/auth/change-password', data),
  login: (email, password) => apiClient.post('/auth/login', { email, password }, { publicAuth: true }),
  validateInvitation: (token) => apiClient.post('/auth/invitations/validate', { token }, { publicAuth: true }),
  activate: (token, password) => apiClient.post('/auth/activate', { token, password }, { publicAuth: true }),
  getCurrentUser: () => apiClient.get('/auth/me'),
  activity: () => apiClient.post('/auth/activity', null, { background: true }),
  logout: token => apiClient.post('/auth/logout', null, { background: true, skipSessionEvent: true,
    timeout: 5000, headers: { Authorization: `Bearer ${token}` } }),
};

export const expenseAPI = {
  mine: () => apiClient.get('/expenses/mine'),
  pending: () => apiClient.get('/expenses/pending'),
  project: id => apiClient.get(`/expenses/projects/${id}`),
  totals: id => apiClient.get(`/expenses/projects/${id}/totals`),
  detail: id => apiClient.get(`/expenses/${id}`),
  save: (id, expense, receipt) => {
    const data = new FormData();
    data.append('expense', new Blob([JSON.stringify(expense)], { type: 'application/json' }));
    if (receipt) data.append('receipt', receipt);
    return id ? apiClient.put(`/expenses/${id}`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
      : apiClient.post('/expenses', data, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  decide: (id, status, comment) => apiClient.post(`/expenses/${id}/decision`, { status, comment }),
  receipt: id => apiClient.get(`/expenses/${id}/receipt`, { responseType: 'blob' }),
};

export const userAPI = {
  importEmployees: (file) => {
    const data = new FormData();
    data.append('file', file);
    return apiClient.post('/users/import', data, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  createEmployee: (data) => apiClient.post('/users', data),
  sendInvitation: (id) => apiClient.post('/users/' + id + '/invitation'),
  revokeInvitation: (id) => apiClient.delete('/users/' + id + '/invitation'),
  updateJoiningDate: (id, joiningDate) => apiClient.patch(`/users/${id}/joining-date`, { joiningDate: joiningDate || null }),
  getUser: (id) => apiClient.get(`/users/${id}`),
  getAllUsers: () => apiClient.get('/users'),
  getAllUsersAsAdmin: () => apiClient.get('/users/all'),
  getLeaveBalance: (id, year) => apiClient.get(`/users/${id}/leave-balance`, { params: { year } }),
  getMyLeaveBalance: year => apiClient.get('/users/me/leave-balance', { params: { year } }),
  updateLeaveAllowance: (id, data) => apiClient.put(`/users/${id}/leave-allowance`, data),
  updateMyProfile: (data) => apiClient.put('/users/me/profile', data),
  deactivateUser: (id) => apiClient.patch(`/users/${id}/deactivate`),
  reactivateUser: (id) => apiClient.patch(`/users/${id}/reactivate`),
  changeRole: (id, role) => apiClient.patch(`/users/${id}/role`, null, { params: { role } }),
  lockAccount: (id, reason) => apiClient.patch(`/users/${id}/lock`, { reason }),
  unlockAccount: id => apiClient.patch(`/users/${id}/unlock`),
  signOutAll: id => apiClient.post(`/users/${id}/sign-out-all`),
};

export const timesheetAPI = {
  getApprovalHistory: (id, projectId) => apiClient.get('/timesheets/' + id + '/projects/' + projectId + '/history'),
  getMissingTimesheets: (year, month) => apiClient.get('/timesheets/missing', { params: { year, month } }),
  getTimesheet: (year, month) => apiClient.get(`/timesheets/${year}/${month}`),
  getTimesheetById: (timesheetId, projectId) => apiClient.get(`/timesheets/id/${timesheetId}`, projectId ? { params: { projectId } } : undefined),
  createTimesheet: (year, month) => apiClient.post('/timesheets', null, { params: { year, month } }),
  addTimeEntry: (timesheetId, data) => apiClient.post(`/timesheets/${timesheetId}/time-entries`, data),
  updateTimeEntry: (timesheetId, entryId, data) => apiClient.put(`/timesheets/${timesheetId}/time-entries/${entryId}`, data),
  deleteTimeEntry: (timesheetId, entryId) => apiClient.delete(`/timesheets/${timesheetId}/time-entries/${entryId}`),
  submitTimesheet: (timesheetId) => apiClient.post(`/timesheets/${timesheetId}/submit`),
  getProjectSubmission: (timesheetId, projectId) => apiClient.get(`/timesheets/${timesheetId}/projects/${projectId}/submission`),
  submitProjectTimesheet: (timesheetId, projectId) => apiClient.post(`/timesheets/${timesheetId}/projects/${projectId}/submit`),
  reopenTimesheet: (timesheetId, reason) => apiClient.post(`/timesheets/${timesheetId}/reopen`, { reason }),
  approveTimesheet: (timesheetId) => apiClient.post(`/approvals/timesheet/${timesheetId}/approve`),
  rejectTimesheet: (timesheetId, reason) => apiClient.post(`/approvals/timesheet/${timesheetId}/reject`, { reason }),
  approveProjectSubmission: (submissionId) => apiClient.post(`/approvals/timesheet-project/${submissionId}/approve`),
  rejectProjectSubmission: (submissionId, reason) => apiClient.post(`/approvals/timesheet-project/${submissionId}/reject`, { reason }),
  getPendingTimesheets: () => apiClient.get('/timesheets/pending'),
  getPendingProjectSubmissions: () => apiClient.get('/timesheets/project-submissions/pending'),
  getMyTimesheets: () => apiClient.get('/timesheets/my'),
};

export const vacationAPI = {
  getTeamCalendar: (year, month) => apiClient.get('/vacation/team-calendar', { params: { year, month } }),
  createRequest: (startDate, endDate, vacationType, notes, specialReason) =>
    apiClient.post('/vacation', null, { params: { startDate, endDate, type: vacationType, notes, specialReason } }),
  updateRequest: (vacationId, startDate, endDate, vacationType, notes, specialReason) =>
    apiClient.put(`/vacation/${vacationId}`, null, { params: { startDate, endDate, type: vacationType, notes, specialReason } }),
  submitRequest: (vacationId) => apiClient.post(`/vacation/${vacationId}/submit`),
  deleteRequest: (vacationId) => apiClient.delete(`/vacation/${vacationId}`),
  approveVacation: (vacationId, accountingType) => apiClient.post(`/approvals/vacation/${vacationId}/approve`, accountingType ? { accountingType } : null),
  rejectVacation: (vacationId, reason) => apiClient.post(`/approvals/vacation/${vacationId}/reject`, { reason }),
  getPendingRequests: () => apiClient.get('/vacation/pending'),
  getMyRequests: () => apiClient.get('/vacation/my'),
};

export const projectAPI = {
  getHealth: () => apiClient.get('/projects/health', { background: true }),
  getProjects: () => apiClient.get('/projects'),
  getAssignedProjects: (year, month) => apiClient.get('/projects/assigned', {
    params: year && month ? { year, month } : {},
  }),
  getHoursDashboard: (year, month) => apiClient.get('/projects/hours-dashboard', { params: { year, month } }),
  createProject: (data) => apiClient.post('/projects', data),
  updateProject: (id, data) => apiClient.put(`/projects/${id}`, data),
  assignEmployee: (projectId, userId, startDate, endDate, billRate, plannedHours) => apiClient.post(`/projects/${projectId}/assignments/${userId}`, null, {
    params: {
      startDate,
      endDate,
      billRate,
      plannedHours,
    },
  }),
  updateAssignmentDates: (projectId, userId, startDate, endDate, billRate) =>
    apiClient.patch(`/projects/${projectId}/assignments/${userId}/dates`, null, {
      params: {
        startDate,
        endDate,
        billRate,
      },
    }),
  removeEmployee: (projectId, userId, replacementManagerId) => apiClient.delete(`/projects/${projectId}/assignments/${userId}`, { params: { replacementManagerId } }),
  updatePlannedHours: (projectId, userId, plannedHours, year, month) =>
    apiClient.patch(`/projects/${projectId}/assignments/${userId}/planned-hours`, null, {
      params: {
        ...(plannedHours === '' || plannedHours === null || plannedHours === undefined ? {} : { plannedHours }),
        ...(year && month ? { year, month } : {}),
      },
    }),
};

export const letterRequestAPI = {
  createRequest: (data) => apiClient.post('/letter-requests', data),
  getRequest: (requestId) => apiClient.get(`/letter-requests/${requestId}`),
  getMyRequests: () => apiClient.get('/letter-requests/my'),
  getPendingRequests: () => apiClient.get('/letter-requests/pending'),
  approveRequest: (requestId) => apiClient.post(`/approvals/letter-request/${requestId}/approve`),
  rejectRequest: (requestId, reason) => apiClient.post(`/approvals/letter-request/${requestId}/reject`, { reason }),
  downloadPdf: (requestId) => apiClient.get(`/letter-requests/${requestId}/pdf`, { responseType: 'blob' }),
};

export const notificationAPI = {
  getEmailPreferences: () => apiClient.get('/notifications/email-preferences', { background: true }),
  saveEmailPreferences: data => apiClient.put('/notifications/email-preferences', data),
  getNotifications: () => apiClient.get('/notifications'),
  getUnreadNotifications: () => apiClient.get('/notifications/unread'),
  getUnreadCount: () => apiClient.get('/notifications/unread-count'),
  markAsRead: (id) => apiClient.patch(`/notifications/${id}/read`),
  markAllAsRead: () => apiClient.post('/notifications/read-all'),
};

export const auditAPI = {
  getAuditLogs: () => apiClient.get('/audit'),
};

export const settingsAPI = {
  previewLeaveDefaults: year => apiClient.get('/settings/leave-defaults/preview', { params: { year } }),
  applyLeaveDefaults: (year, preview) => apiClient.post('/settings/leave-defaults/apply', { year, confirmed: true,
    expectedVacationDays: preview?.vacationDays, expectedSickDays: preview?.sickDays,
    expectedBereavementDays: preview?.bereavementDays }),
  getAllSettings: () => apiClient.get('/settings'),
  getSetting: (key) => apiClient.get(`/settings/${key}`),
  updateSetting: (key, value) => apiClient.put(`/settings/${key}`, { value }),
};

export const reportsAPI = {
  exportTimesheets: (year, month, userIds = []) => apiClient.get('/reports/timesheets/export', {
    params: { year, month, ...(userIds.length ? { userIds: userIds.join(',') } : {}) },
    responseType: 'blob',
  }),
  exportProjectTimesheets: (submissionIds = []) => apiClient.get('/reports/project-timesheets/export', {
    params: { submissionIds: submissionIds.join(',') },
    responseType: 'blob',
  }),
  exportSingleTimesheet: (timesheetId) => apiClient.get(`/reports/timesheets/${timesheetId}/export`, {
    responseType: 'blob',
  }),
  exportSingleTimesheetPdf: (timesheetId) => apiClient.get(`/reports/timesheets/${timesheetId}/pdf`, { responseType: 'blob' }),
  exportProjectTimesheetPdf: (submissionId) => apiClient.get(`/reports/timesheet-projects/${submissionId}/pdf`, { responseType: 'blob' }),
  exportVacationRequests: (year) => apiClient.get('/reports/vacation/export', { params: { year }, responseType: 'blob' }),
  getMonthlySummary: (year, month) => apiClient.get('/reports/summary', { params: { year, month } }),
};

export default apiClient;

export const feedbackReviewsAPI = {
  employees: query => apiClient.get('/feedback-reviews/employees', { params: { query } }),
  feedback: given => apiClient.get('/feedback-reviews/feedback', { params: { given } }),
  submit: data => apiClient.post('/feedback-reviews/feedback', data),
  reviews: employeeId => apiClient.get('/feedback-reviews/reviews', { params: { employeeId } }),
  save: (id, data) => id ? apiClient.put(`/feedback-reviews/reviews/${id}`, data) : apiClient.post('/feedback-reviews/reviews', data),
  publish: (id, version) => apiClient.post(`/feedback-reviews/reviews/${id}/publish`, null, { params: { version } }),
  audit: id => apiClient.get(`/feedback-reviews/reviews/${id}/audit`),
};

export const employeeReportsAPI = {
  mine: (params) => apiClient.get('/employee-reports/mine', { params, background: true }),
  myDetail: (id) => apiClient.get(`/employee-reports/mine/${id}`, { background: true }),
  submit: (data) => apiClient.post('/employee-reports', data, { headers: { 'Content-Type': undefined } }),
  list: (params) => apiClient.get('/employee-reports', { params, background: true }),
  detail: (id) => apiClient.get(`/employee-reports/${id}`),
  review: (id, data) => apiClient.patch(`/employee-reports/${id}`, data),
  download: (id, attachmentId) => apiClient.get(`/employee-reports/${id}/attachments/${attachmentId}`, { responseType: 'blob' }),
};
