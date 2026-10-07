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
  const correctionRequestMessage = /^\/timesheet-corrections(?:\/|$)/.test(error.config?.url || '')
    && (status === 400 || status === 409) && typeof error.response?.data?.message === 'string'
    ? error.response.data.message.trim() : '';
  const timesheetClosedMessage = /^\/timesheets(?:\/|$)/.test(error.config?.url || '')
    && status === 400
    && error.response?.data?.message === 'This timesheet month is closed; request an opening from your Project Admin'
    ? 'This timesheet month is closed. Request an opening from your Project Admin.' : '';
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
  const invitationMessage = /^\/companies\/invitations(?:\/|$)/.test(error.config?.url || '')
    && [400, 403].includes(status) && [
      'Invalid invitation', 'Invitation has expired or is no longer valid',
      'Invitation not found for this account', 'Sign in using the invited email address',
      'This email already has an account. Sign in to accept the invitation.',
      'This account is inactive. Contact your administrator.',
      'Use at least 12 characters with uppercase, lowercase and a number (maximum 72 UTF-8 bytes).',
      'First and last name are required',
    ].includes(error.response?.data?.message) ? error.response.data.message : '';
  const companyErrors = new Set([
    'Review pending leave requests before removing this member', 'Review pending letter requests before removing this member', 'Leave record changed. Reload and try again.',
    'Leave policy changed. Preview it again.', 'Company leave request not found',
    'Choose a leave date range of up to 367 days', 'Choose a special leave reason', 'Describe the special leave reason in Notes',
    'Choose how this special leave is counted before approval', 'Rejection reason is required',
    'These dates overlap another submitted or approved leave request',
    'Leave conflicts with submitted or finalized hours; reopen the timesheet first',
    'Confirm the company leave policy before applying it',
    'Settings changed. Reload and try again.', 'Reload settings before editing',
    'Choose a supported company setting', 'Enter a setting value',
    'Enter a company display name of 1 to 150 characters', 'Choose true or false for reminders',
    'Leave days must be between 0 and 366 with at most two decimal places',
    'Appoint another active Company Admin before removing this administrator\'s access',
    'Transfer project ownership and reassign manager/approver duties before removing this member',
    'Review pending time and expense submissions before removing this member',
    'Member access changed. Reload and try again.',
    'The account is unavailable for company access. Resolve its platform account status first.',
    'An active company membership is required', 'Only removed memberships can be reactivated',
    'Member already has this company role', 'Active company role not found',
    'Reload the member before changing access', 'Choose a company role', 'Choose ACTIVE or REMOVED membership status',
    'Active company member not found', 'Company member not found',
    'Employment details changed. Reload and try again.', 'That employee ID is already in use in this company',
    'Only active company memberships can be edited', 'Reload employment details before editing',
    'Enter a company name of 1 to 150 characters.',
    'Workspace ID must be 2 to 79 characters using letters, numbers and hyphens, starting with a letter or number.',
    'Enter a valid initial company admin email address.', 'That workspace ID is already in use. Choose another.',
    'Company was not created because the admin invitation could not be sent. Check the email configuration and retry.',
    'Reactivate this company membership before sending a new role invitation',
    'Choose an email address for a company member account',
    'A Company Admin must reactivate your membership before you can accept a role invitation',
    'Platform administrator accounts cannot accept company operational roles',
    'You no longer have access to this company. Choose another workspace.',
  ]);
  ["Access denied","Acknowledgment is not required","Add a note, action, resolution, or status change","An active confidential-handler grant is required","An available company account is required","An explicit permission for this review subject is required","Announcement changed; reopen it before continuing","Announcement not found","Another Company Admin must authorize your sensitive access grant","Attach up to 5 files, 20 MB total","Attachment not found","Case changed; reopen before updating","Choose another employee","Choose company members for recusal","Closed reports cannot be changed","Confirm the name, title, and employment start date before approval","Each attachment must be nonempty, up to 10 MB, with a filename under 200 characters","Employee and review period cannot be changed","Employment start date cannot be in the future","Enter a Report ID or part of an ID","Expiration date must be on or after publish date","Explain corrections to the employee\u0027s requested details","Full name, title, and job start date are required","Grant changed; reload before revoking","Grant dates must cover no more than one year","Handler grants apply to eligible company cases","Invalid attachment","Invalid date range or page","Invalid page","Letter details are too long","Letter changed; reload before review","Letter not found","The record changed or conflicts with existing data. Reload and try again.","Letter is not approved yet","Letter request type is required","Move reports forward one status at a time","Not authorized to download this letter","Not authorized to view this letter request","Only submitted letters can be reviewed","Processed attachments exceed 20 MB. Use smaller files.","Provide a valid attachment up to 5 MB","Read and acknowledge the privacy notice","Record actions taken and resolution details before resolving","Rejection reason is required","Rejection reason is too long","Report not found","Review changed; reload before publishing","Review changed; reload before saving","Review is already published","Review not found","Reviewed letter details are too long","Search is too long","Select a company member","Select a different review subject","Select an available company member","Supported attachments: PNG, JPG, GIF, WebP, PDF, DOC, DOCX, TXT, ODT","Travel dates are required","Travel start date cannot be after end date","Unable to read attachment","User not found","Vacation letter dates are required","Vacation start date cannot be after end date","You are recused from this case","You cannot approve your own letter request","You cannot reject your own letter request"].forEach(message => companyErrors.add(message));
  const platformAdministrationErrors = new Set(["A Company Admin must reactivate your membership before you can accept a role invitation","A different authorized reviewer is required; Project Admin fallback requires a reason","Account changed. Reload and try again.","Account not found","Active company membership is required","Active company membership is required to access company work records","Active Moderator grant not found","Active role assignment not found","An active account is required","An active company membership is required before assigning project access","An active User role is required to submit ","Another Platform Admin must change your administrative access.","Appoint another available Platform Admin before removing this administrator\u0027s access.","Choose a different PM hours approver before removing this Project Admin","Choose a different primary Project Manager before removing this role","Choose a supported account action","Choose a valid membership status","Choose an appointed Project Admin from this project","Choose an approval type and valid date range","Choose an available activated account","Choose an email address for a company member account","Choose an invited Platform Admin","Company Admin invitation permission required","Company administration changed. Reload and try again.","Company invitation management permission required","Company not found","Company permission required: ","Company project role view permission required","Company role management permission required","Company roster permission required","Create a new invitation for an accepted or revoked invitation","Enter a company name of 1 to 150 characters.","Enter a valid initial company admin email address.","Enter a workspace ID, name and valid email address","First and last name are required","Invalid invitation","Invalid page","Invitation has expired or is no longer valid","Invitation management permission required","Invitation not found","Invitation not found for this account","Invitation project does not belong to its company","Pending access request not found","Pending access request not found for this email","Pending invitation not found","Platform Admin permission required","Platform administrator accounts cannot accept company operational roles","Please wait one minute before resending an invitation","Project Admin invitation permission required","Project company mismatch","Project does not belong to this company","Project not found","Project role management permission required","Project role view permission required","Reactivate this company membership before sending a new role invitation","Remove active company memberships before appointing a Platform Admin.","Role scope does not match invitation","Search is too long","Search must be at most 100 characters","Sign in required","Sign in using the invited email address","Team size limit reached for this company tier","The Moderator must first accept their company invitation","The new limits must cover existing active projects, teams, and pending invitations.","This account is inactive. Contact your administrator.","This account is locked. Contact your administrator.","This company is suspended. Contact your administrator.","This email already has an account. Sign in to accept the invitation.","Transfer project ownership before removing its owner","Use company invitations to onboard company members","Valid invitee email is required","Workspace ID must be 2 to 79 characters using letters, numbers and hyphens, starting with a letter or number."]);
  const platformAdministrationMessage=/^\/platform\//.test(error.config?.url||'')&&[400,403,404,409].includes(status)&&platformAdministrationErrors.has(error.response?.data?.message)?error.response.data.message:'';
  ['This company is suspended. Contact your administrator.','Please wait one minute before resending an invitation','Create a new invitation for an accepted or revoked invitation'].forEach(message=>companyErrors.add(message));
  const companyMessage = /^\/companies(?:\/|$)/.test(error.config?.url || '') && [400,403,404,409,503].includes(status)
    && (companyErrors.has(error.response?.data?.message)||/^Not enough (vacation|sick|bereavement) leave for \d{4}; choose unpaid leave or update the allowance$/.test(error.response?.data?.message||'')||/^Leave allowance is not set for \d{4}$/.test(error.response?.data?.message||'')) ? error.response.data.message : '';
  const platformSettingsMessage = /^\/platform\/settings(?:\/|$)/.test(error.config?.url || '') && [400,409].includes(status)
    && ['Settings changed. Reload and try again.', 'Reload settings before editing', 'Choose a supported platform setting', 'Choose true or false for reminders'].includes(error.response?.data?.message)
    ? error.response.data.message : '';
  const message = !error.response && error.code === 'ECONNABORTED'
    ? 'The request is taking longer than expected. Please try again.'
    : !error.response ? 'Unable to connect. Check your internet connection and try again.'
    : status === 401 ? 'Your session has expired. Please sign in again.'
    : status === 403 ? platformAdministrationMessage || companyMessage || invitationMessage || "You don't have permission to perform this action."
    : status === 404 ? companyMessage || 'The requested item was not found.'
    : status === 423 && error.config?.url === '/auth/login'
      ? 'Your account is locked. Contact an administrator.'
    : status >= 500 ? companyMessage || 'Something went wrong on our side. Please try again.'
    : status === 429 && error.config?.publicAuth ? 'Too many attempts. Please try again in a minute.'
    : platformAdministrationMessage || companyMessage || platformSettingsMessage || validationMessage || correctionRequestMessage || timesheetClosedMessage || passwordMessage || invitationMessage || (status === 400 ? 'Please check your entries and try again.'
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

export const companyAPI = {
  employment: (companyId,userId) => apiClient.get(`/companies/${companyId}/members/${userId}/employment`),
  updateEmployment: (companyId,userId,data) => apiClient.put(`/companies/${companyId}/members/${userId}/employment`,data),
  context: () => apiClient.get('/companies/context', { background: true }),
  validateContext: id => apiClient.get(`/companies/${id}/context`, { background: true }),
  mine: () => apiClient.get('/companies'),
  create: (name, slug, adminEmail) => apiClient.post('/companies', { name, slug, adminEmail }),
  members: (id,params) => apiClient.get(`/companies/${id}/members`,{params}),
  setMemberStatus: (companyId,userId,status,version) => apiClient.put(`/companies/${companyId}/members/${userId}/status`,{status,version}),
  assignCompanyRole: (companyId,userId,role,version) => apiClient.post(`/companies/${companyId}/members/${userId}/roles`,{role,version}),
  removeCompanyRole: (companyId,userId,role,version) => apiClient.delete(`/companies/${companyId}/members/${userId}/roles/${role}`,{params:{version}}),
  recoverMemberPassword: (companyId,userId) => apiClient.post(`/companies/${companyId}/members/${userId}/password-reset`),
  invite: (id, data) => apiClient.post(`/companies/${id}/invitations`, data),
  resendInvitation:(id,invitation)=>apiClient.post(`/companies/${id}/invitations/${invitation}/resend`),
  invitations: id => apiClient.get(`/companies/${id}/invitations`),
  revokeInvitation: (id, invitationId) => apiClient.delete(`/companies/${id}/invitations/${invitationId}`),
  accept: token => apiClient.post('/companies/invitations/accept', { token }),
  previewInvitation: token => apiClient.post('/companies/invitations/preview', { token }, { publicAuth: true }),
  claimInvitation: data => apiClient.post('/companies/invitations/claim', data, { publicAuth: true }),
  requestAccess: data => apiClient.post('/companies/access-requests', data, { publicAuth: true }),
  accessRequests: id => apiClient.get(`/companies/${id}/access-requests`),
  dismissAccessRequest: (id, requestId) => apiClient.delete(`/companies/${id}/access-requests/${requestId}`),
  myPendingInvitations: () => apiClient.get('/companies/invitations/mine', { background: true }),
  acceptInvitation: id => apiClient.post(`/companies/invitations/${id}/accept`),
  grantModerator: (id, data) => apiClient.post(`/companies/${id}/moderator-grants`, data),
  moderatorGrants: id => apiClient.get(`/companies/${id}/moderator-grants`),
  revokeModerator: (id, grantId) => apiClient.delete(`/companies/${id}/moderator-grants/${grantId}`),
  removeRole: (id, role, userId, projectId) => apiClient.delete(`/companies/${id}/roles/${role}/users/${userId}`, { params: { projectId } }),
  transferOwner: (id, projectId, userId) => apiClient.post(`/companies/${id}/projects/${projectId}/owner`, { userId }),
  projectRoles: (id, projectId) => apiClient.get(`/companies/${id}/projects/${projectId}/roles`),
};

export const expenseAPI = {
  mine: () => apiClient.get('/expenses/mine'),
  pending: () => apiClient.get('/expenses/pending'),
  project: id => apiClient.get(`/expenses/projects/${id}`),
  totals: id => apiClient.get(`/expenses/projects/${id}/totals`),
  budgetCheck: (id, amount, excludeExpenseId) => apiClient.get(`/expenses/projects/${id}/budget-check`, { params: { amount, excludeExpenseId } }),
  detail: id => apiClient.get(`/expenses/${id}`),
  save: (id, expense, receipt) => {
    const data = new FormData();
    data.append('expense', new Blob([JSON.stringify(expense)], { type: 'application/json' }));
    if (receipt) data.append('receipt', receipt);
    return id ? apiClient.put(`/expenses/${id}`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
      : apiClient.post('/expenses', data, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  decide: (id, status, comment, fallbackReason) => apiClient.post(`/expenses/${id}/decision`, { status, comment, fallbackReason }),
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
  getApprovalPeriod: (projectId, date, userId) => apiClient.get('/timesheet-periods', { params: { projectId, date, userId } }),
  getApprovalMonth: (projectId, date, userId) => apiClient.get('/timesheet-periods/month', { params: { projectId, date, userId } }),
  submitApprovalPeriods: (projectId, dates) => apiClient.post('/timesheet-periods/submit-batch', { dates }, { params: { projectId } }),
  submitApprovalPeriod: (projectId, date) => apiClient.post('/timesheet-periods/submit', null, { params: { projectId, date } }),
  decideApprovalPeriod: (id, approve, comment, fallbackReason) => apiClient.post(`/timesheet-periods/${id}/decision`, { approve, comment, fallbackReason }),
  requestPeriodOpening: (projectId, date, reason) => apiClient.post('/timesheet-periods/opening', { reason }, { params: { projectId, date } }),
  decidePeriodOpening: (id, approve, comment) => apiClient.post(`/timesheet-periods/${id}/opening-decision`, { approve, comment }),
  getPendingApprovalPeriods: () => apiClient.get('/timesheet-periods/pending'),
  getApprovalPeriodHistory: id => apiClient.get(`/timesheet-periods/${id}/history`),
  getPendingPeriodOpenings: () => apiClient.get('/timesheet-periods/openings/pending'),
  getApprovalHistory: (id, projectId) => apiClient.get('/timesheets/' + id + '/projects/' + projectId + '/history'),
  getMissingTimesheets: (year, month,companyId) => apiClient.get('/timesheets/missing', { params: { year, month,companyId } }),
  getTimesheet: (year, month, companyId) => apiClient.post('/timesheets', null, { params: { year, month, companyId } }),
  getTimesheetById: (timesheetId, projectId) => apiClient.get(`/timesheets/id/${timesheetId}`, projectId ? { params: { projectId } } : undefined),
  createTimesheet: (year, month) => apiClient.post('/timesheets', null, { params: { year, month } }),
  addTimeEntry: (timesheetId, data) => apiClient.post(`/timesheets/${timesheetId}/time-entries`, data),
  updateTimeEntry: (timesheetId, entryId, data) => apiClient.put(`/timesheets/${timesheetId}/time-entries/${entryId}`, data),
  deleteTimeEntry: (timesheetId, entryId) => apiClient.delete(`/timesheets/${timesheetId}/time-entries/${entryId}`),
  submitTimesheet: (timesheetId) => apiClient.post(`/timesheets/${timesheetId}/submit`),
  getProjectSubmission: (timesheetId, projectId) => apiClient.get(`/timesheets/${timesheetId}/projects/${projectId}/submission`),
  submitProjectTimesheet: (timesheetId, projectId) => apiClient.post(`/timesheets/${timesheetId}/projects/${projectId}/submit`),
  requestOpening: (timesheetId, projectId, comment) => apiClient.post(`/timesheet-corrections/${timesheetId}/projects/${projectId}`, { comment }),
  getOpeningRequests: (timesheetId, projectId) => apiClient.get(`/timesheet-corrections/${timesheetId}/projects/${projectId}`),
  getPendingOpeningRequests: () => apiClient.get('/timesheet-corrections/pending'),
  decideOpeningRequest: (requestId, approve, comment) => apiClient.post(`/timesheet-corrections/${requestId}/${approve ? 'approve' : 'decline'}`, { comment }),
  approveTimesheet: (timesheetId) => apiClient.post(`/approvals/timesheet/${timesheetId}/approve`),
  rejectTimesheet: (timesheetId, reason) => apiClient.post(`/approvals/timesheet/${timesheetId}/reject`, { reason }),
  approveProjectSubmission: (submissionId, fallbackReason) => apiClient.post(`/approvals/timesheet-project/${submissionId}/approve`, { fallbackReason }),
  rejectProjectSubmission: (submissionId, reason, fallbackReason) => apiClient.post(`/approvals/timesheet-project/${submissionId}/reject`, { reason, fallbackReason }),
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
export const companyLeaveAPI = {
  mine: company => apiClient.get(`/companies/${company}/leave/my`),
  requests: company => apiClient.get(`/companies/${company}/leave/requests`),
  save: (company,id,input) => id?apiClient.put(`/companies/${company}/leave/requests/${id}`,input):apiClient.post(`/companies/${company}/leave/requests`,input),
  submit: (company,id,version) => apiClient.post(`/companies/${company}/leave/requests/${id}/submit`,null,{params:{version}}),
  remove: (company,id,version) => apiClient.delete(`/companies/${company}/leave/requests/${id}`,{params:{version}}),
  decide: (company,id,approve,input) => apiClient.post(`/companies/${company}/leave/requests/${id}/${approve?'approve':'reject'}`,input),
  balance: (company,user,year) => apiClient.get(`/companies/${company}/leave/balance/${user??'me'}`,{params:{year}}),
  allowance: (company,user,input) => apiClient.put(`/companies/${company}/leave/balance/${user}`,input),
  preview: (company,year) => apiClient.get(`/companies/${company}/leave/policy/preview`,{params:{year}}),
  apply: (company,preview) => apiClient.post(`/companies/${company}/leave/policy/apply`,{year:preview.year,settingsVersion:preview.settingsVersion,policyVersion:preview.policyVersion,confirmed:true}),
  calendar: (company,year,month) => apiClient.get(`/companies/${company}/leave/calendar`,{params:{year,month}}),
};

export const projectAPI = {
  getHealth: companyId => apiClient.get('/projects/health', { background: true, params:{companyId} }),
  getProjects: companyId => apiClient.get('/projects', {params:{companyId}}),
  getAssignedProjects: (year, month) => apiClient.get('/projects/assigned', {
    params: year && month ? { year, month } : {},
  }),
  getHoursDashboard: (year, month,companyId) => apiClient.get('/projects/hours-dashboard', { params: { year, month,companyId } }),
  archive: id => apiClient.post(`/projects/${id}/archive`),
  createProject: (data) => apiClient.post('/projects', data),
  updateProject: (id, data) => apiClient.put(`/projects/${id}`, data),
  assignEmployee: (projectId, userId, startDate, endDate, billRate, plannedHours, approveHours=false, approveExpenses=false) => apiClient.post(`/projects/${projectId}/assignments/${userId}`, null, {
    params: {
      startDate,
      endDate,
      billRate,
      plannedHours,
      approveHours,
      approveExpenses,
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
  approveRequest: (requestId, review) => apiClient.post(`/approvals/letter-request/${requestId}/approve`, review),
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
  company: companyId => apiClient.get(`/companies/${companyId}/settings`),
  updateCompany: (companyId,key,value,version) => apiClient.put(`/companies/${companyId}/settings/${encodeURIComponent(key)}`, {value,version}),
  platform: () => apiClient.get('/platform/settings'),
  updatePlatform: (key,value,version) => apiClient.put(`/platform/settings/${encodeURIComponent(key)}`, {value,version}),
  previewLeaveDefaults: year => apiClient.get('/settings/leave-defaults/preview', { params: { year } }),
  applyLeaveDefaults: (year, preview) => apiClient.post('/settings/leave-defaults/apply', { year, confirmed: true,
    expectedVacationDays: preview?.vacationDays, expectedSickDays: preview?.sickDays,
    expectedBereavementDays: preview?.bereavementDays }),
  getAllSettings: () => apiClient.get('/settings'),
  getSetting: (key) => apiClient.get(`/settings/${key}`),
  updateSetting: (key, value) => apiClient.put(`/settings/${key}`, { value }),
};

export const reportsAPI = {
  companyPeriods: (company,year,month) => apiClient.get(`/companies/${company}/reports/timesheet-periods`,{params:{year,month}}),
  companyExportPeriods: (company,ids) => apiClient.get(`/companies/${company}/reports/timesheet-periods/export`,{params:{ids:ids.join(',')},responseType:'blob'}),
  companyExportLeave: (company,year) => apiClient.get(`/companies/${company}/reports/vacation/export`,{params:{year},responseType:'blob'}),
  approvalPeriods: (year, month) => apiClient.get('/reports/timesheet-periods', { params: { year, month } }),
  exportApprovalPeriods: ids => apiClient.get('/reports/timesheet-periods/export', { params: { ids: ids.join(',') }, responseType: 'blob' }),
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
  exportApprovalPeriodPdf: id => apiClient.get(`/reports/timesheet-periods/${id}/pdf`, { responseType: 'blob' }),
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

export const companyAnnouncementAPI = companyId => ({
  list: (management = false) => apiClient.get(`/companies/${companyId}/announcements`, { params: { management }, background: true }),
  open: (id, management = false) => apiClient.post(`/companies/${companyId}/announcements/${id}/open`, null, { params: { management } }),
  save: (id, data) => id ? apiClient.put(`/companies/${companyId}/announcements/${id}`, data) : apiClient.post(`/companies/${companyId}/announcements`, data),
  status: (id, status, version) => apiClient.post(`/companies/${companyId}/announcements/${id}/status`, null, { params: { status, version } }),
  remove: (id, version) => apiClient.delete(`/companies/${companyId}/announcements/${id}`, { params: { version } }),
  acknowledge: (id, version) => apiClient.post(`/companies/${companyId}/announcements/${id}/acknowledge`, null, { params: { version } }),
  tracking: id => apiClient.get(`/companies/${companyId}/announcements/${id}/tracking`),
  attachment: (id, management = false) => apiClient.get(`/companies/${companyId}/announcements/${id}/attachment`, { params: { management }, responseType: 'blob' }),
});

export const companyFeedbackReviewsAPI = companyId => ({
  employees: query => apiClient.get(`/companies/${companyId}/feedback-reviews/employees`, { params: { query } }),
  feedback: given => apiClient.get(`/companies/${companyId}/feedback-reviews/feedback`, { params: { given } }),
  submit: data => apiClient.post(`/companies/${companyId}/feedback-reviews/feedback`, data),
  reviews: employeeId => apiClient.get(`/companies/${companyId}/feedback-reviews/reviews`, { params: { employeeId } }),
  save: (id, data) => id ? apiClient.put(`/companies/${companyId}/feedback-reviews/reviews/${id}`, data) : apiClient.post(`/companies/${companyId}/feedback-reviews/reviews`, data),
  publish: (id, version) => apiClient.post(`/companies/${companyId}/feedback-reviews/reviews/${id}/publish`, null, { params: { version } }),
  audit: id => apiClient.get(`/companies/${companyId}/feedback-reviews/reviews/${id}/audit`),
});

export const companyEmployeeReportsAPI = companyId => ({
  mine: (params) => apiClient.get(`/companies/${companyId}/employee-reports/mine`, { params, background: true }),
  myDetail: (id) => apiClient.get(`/companies/${companyId}/employee-reports/mine/${id}`, { background: true }),
  submit: (data) => apiClient.post(`/companies/${companyId}/employee-reports`, data, { headers: { 'Content-Type': undefined } }),
  list: (params) => apiClient.get(`/companies/${companyId}/employee-reports`, { params, background: true }),
  detail: (id) => apiClient.get(`/companies/${companyId}/employee-reports/${id}`),
  review: (id, data) => apiClient.patch(`/companies/${companyId}/employee-reports/${id}`, data),
  download: (id, attachmentId) => apiClient.get(`/companies/${companyId}/employee-reports/${id}/attachments/${attachmentId}`, { responseType: 'blob' }),
});

export const companyLetterRequestAPI = companyId => ({
  configuration: () => apiClient.get(`/companies/${companyId}/letter-settings`),
  saveConfiguration: data => apiClient.put(`/companies/${companyId}/letter-settings`,data),
  previewTemplate: (configuration,type) => apiClient.post(`/companies/${companyId}/letter-settings/preview`,{configuration,type},{responseType:'blob'}),
  availability: () => apiClient.get(`/companies/${companyId}/letter-settings/availability`),
  previewReview: (id,review,version) => apiClient.post(`/companies/${companyId}/letter-requests/${id}/preview`,{...review,version},{responseType:'blob'}),
  createRequest: (data) => apiClient.post(`/companies/${companyId}/letter-requests`, data),
  getRequest: (requestId) => apiClient.get(`/companies/${companyId}/letter-requests/${requestId}`),
  getMyRequests: () => apiClient.get(`/companies/${companyId}/letter-requests/my`),
  getPendingRequests: () => apiClient.get(`/companies/${companyId}/letter-requests/pending`),
  approveRequest: (requestId, review, version) => apiClient.post(`/companies/${companyId}/letter-requests/${requestId}/approve`, { ...review,version }),
  rejectRequest: (requestId, reason, version) => apiClient.post(`/companies/${companyId}/letter-requests/${requestId}/reject`, { reason,version }),
  downloadPdf: (requestId) => apiClient.get(`/companies/${companyId}/letter-requests/${requestId}/pdf`, { responseType: 'blob' }),
});

export const companyGovernanceAPI = company => ({
 grants:()=>apiClient.get(`/companies/${company}/sensitive-grants`),
 grant:data=>apiClient.post(`/companies/${company}/sensitive-grants`,data),
 revoke:(id,version)=>apiClient.delete(`/companies/${company}/sensitive-grants/${id}`,{params:{version}}),
 configuration:()=>apiClient.get(`/companies/${company}/confidential-configuration`),
 audit:page=>apiClient.get(`/companies/${company}/audit`,{params:{page}}),
 recuse:id=>apiClient.post(`/companies/${company}/employee-reports/${id}/recuse`),
});

export const platformAdministrationAPI={
 companies:()=>apiClient.get('/platform/companies'),
 usage:id=>apiClient.get(`/platform/companies/${id}/usage`),
 plan:(id,data)=>apiClient.put(`/platform/companies/${id}/plan`,data),
 status:(id,data)=>apiClient.put(`/platform/companies/${id}/status`,data),
 accounts:query=>apiClient.get('/platform/accounts',{params:{query}}),
 account:(id,data)=>apiClient.post(`/platform/accounts/${id}/actions`,data),
 createAdmin:data=>apiClient.post('/platform/administrators',data),
 audit:page=>apiClient.get('/platform/audit',{params:{page}}),
 adminInvitations:id=>apiClient.get(`/platform/companies/${id}/admin-invitations`),
 resendAdmin:(id,invitation)=>apiClient.post(`/platform/companies/${id}/admin-invitations/${invitation}/resend`),
 revokeAdmin:(id,invitation)=>apiClient.delete(`/platform/companies/${id}/admin-invitations/${invitation}`),
};
