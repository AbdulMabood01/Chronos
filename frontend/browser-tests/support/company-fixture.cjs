const base = require('@playwright/test');
// Shared company context for screen fixtures written before company-scoped APIs.
// Operational responses still come from each test's own mock handler.
const test = base.test.extend({page: async ({page}, use) => {
  let actor = {id: 1, role: 'EMPLOYEE'};
  const originalRoute = page.route.bind(page);
  const company = () => {
    const admin = actor.role === 'ADMIN', projectAdmin = actor.role === 'PROJECT_ADMIN';
    return {id: 1, name: 'Maxwell', slug: 'maxwell', workforce_enabled: true, permissions: {companyId: 1, companyRoles: admin ? ['COMPANY_ADMIN'] : [],
      capabilities: {canSubmitWork: !admin && !projectAdmin, canReviewWork: admin || projectAdmin,
        canViewProjects: true, canManageProjects: projectAdmin, canCreateProjects: admin || projectAdmin,
        canManageCompanyPeople: admin, canManageCompanyAnnouncements: admin, canManageCompanySettings: admin,
        canReviewLeaveAndLetters: admin, canManageLeavePolicy: admin, canViewCompanyReports: admin,
        canViewCompanyAudit: admin, canHandleConfidentialReports: admin, canManagePerformanceReviews: admin},
      projects: [3,4,10,11].map(projectId => ({projectId, roles: [projectAdmin || admin ? 'PROJECT_ADMIN' : 'USER'], capabilities: {canManageProject: projectAdmin || admin, canReviewTimesheets: projectAdmin || admin, canSubmitWork: !projectAdmin && !admin}}))}};
  };
  page.route = (pattern, handler, options) => originalRoute(pattern, async route => {
    const actual = new URL(route.request().url());
    if(actual.pathname === '/api/companies/context') return route.fulfill({json: {companies: [company()], memberships: [company()], platformAdmin: false}});
    if(actual.pathname === '/api/companies/1/context') return route.fulfill({json: company()});
    const originalRequest = route.request();
    const url = new URL(actual); url.pathname = url.pathname.replace(/^\/api\/companies\/1\//, '/api/');
    const request = new Proxy(originalRequest, {get(target, key) {if(key === 'url') return () => url.href; const value=target[key];return typeof value === 'function' ? value.bind(target) : value;}});
    const proxy = new Proxy(route, {get(target, key) {
      if(key === 'request') return () => request;
      if(key === 'fulfill') return async response => {
        if(actual.pathname === '/api/auth/me') {
          const data = response.json || (response.body ? JSON.parse(response.body) : null);
          if(data && typeof data === 'object' && !Array.isArray(data)) actor = data;
        }
        if(response.json) { const addScope = value => value && typeof value === 'object' && !Array.isArray(value) ? {...value, companyId: value.companyId ?? 1} : value; response = {...response, json: Array.isArray(response.json) ? response.json.map(addScope) : addScope(response.json)}; }
        return target.fulfill(response);
      };
      const value=target[key]; return typeof value === 'function' ? value.bind(target) : value;
    }});
    await handler(proxy);
  }, options);
  await use(page);
}});
module.exports = {test, expect: base.expect};
