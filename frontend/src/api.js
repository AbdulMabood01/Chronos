import axios from 'axios';

const API_BASE_URL = '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
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
  dispatchApiActivityEvent('chronos:api-start');
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  dispatchApiActivityEvent('chronos:api-end');
  return Promise.reject(error);
});

apiClient.interceptors.response.use((response) => {
  dispatchApiActivityEvent('chronos:api-end');
  return response;
}, (error) => {
  dispatchApiActivityEvent('chronos:api-end');
  return Promise.reject(error);
});

export const authAPI = {
  login: () => apiClient.post('/auth/login'),
  getCurrentUser: () => apiClient.get('/auth/me'),
  devLogin: (email, firstName, lastName) =>
    apiClient.post('/auth/dev-login', null, { params: { email, firstName, lastName } }),
};

export const userAPI = {
  updateJoiningDate: (id, joiningDate) => apiClient.patch(`/users/${id}/joining-date`, { joiningDate: joiningDate || null }),
  getUser: (id) => apiClient.get(`/users/${id}`),
  getAllUsers: () => apiClient.get('/users'),
  getAllUsersAsAdmin: () => apiClient.get('/users/all'),
  getLeaveBalance: (id, year) => apiClient.get(`/users/${id}/leave-balance`, { params: { year } }),
  updateLeaveAllowance: (id, data) => apiClient.put(`/users/${id}/leave-allowance`, data),
  updateMyProfile: (data) => apiClient.put('/users/me/profile', data),
  deactivateUser: (id) => apiClient.patch(`/users/${id}/deactivate`),
  reactivateUser: (id) => apiClient.patch(`/users/${id}/reactivate`),
  changeRole: (id, role) => apiClient.patch(`/users/${id}/role`, null, { params: { role } }),
};

export const timesheetAPI = {
  getApprovalHistory: (id, projectId) => apiClient.get('/timesheets/' + id + '/projects/' + projectId + '/history'),
  getMissingTimesheets: (year, month) => apiClient.get('/timesheets/missing', { params: { year, month } }),
  previewPreviousWeek: (id, projectId, weekStart) => apiClient.get(`/timesheets/${id}/projects/${projectId}/copy-week`, { params: { weekStart } }),
  copyPreviousWeek: (id, projectId, weekStart) => apiClient.post(`/timesheets/${id}/projects/${projectId}/copy-week`, null, { params: { weekStart } }),
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
  createRequest: (startDate, endDate, vacationType, notes) => 
    apiClient.post('/vacation', null, { params: { startDate, endDate, type: vacationType, notes } }),
  updateRequest: (vacationId, startDate, endDate, vacationType, notes) =>
    apiClient.put(`/vacation/${vacationId}`, null, { params: { startDate, endDate, type: vacationType, notes } }),
  submitRequest: (vacationId) => apiClient.post(`/vacation/${vacationId}/submit`),
  deleteRequest: (vacationId) => apiClient.delete(`/vacation/${vacationId}`),
  approveVacation: (vacationId) => apiClient.post(`/approvals/vacation/${vacationId}/approve`),
  rejectVacation: (vacationId, reason) => apiClient.post(`/approvals/vacation/${vacationId}/reject`, { reason }),
  getPendingRequests: () => apiClient.get('/vacation/pending'),
  getMyRequests: () => apiClient.get('/vacation/my'),
};

export const projectAPI = {
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
  applyLeaveDefaults: (year) => apiClient.post('/settings/leave-defaults/apply', { year, confirmed: true }),
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
