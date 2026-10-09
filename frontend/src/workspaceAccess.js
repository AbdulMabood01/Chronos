// Enable a route only after its API enforces the corresponding resource scope.
// Leave/HR administration, reports and global exports move in later chunks.
const has = (context, key) => context.companyCapabilities?.[key] === true;

export const canCreateProjectNow = context => !context.platformAdmin && has(context, 'canCreateProjects')
  && (context.companyRoles?.includes('PROJECT_ADMIN')||context.companyRoles?.includes('COMPANY_ADMIN'));

export function canOpenWorkspaceRoute(pathname, context) {
  const path = pathname.replace(/\/+$/, '') || '/';
  if (['/dashboard', '/profile', '/notifications', '/company-invite'].includes(path)) return true;
  if(path==='/platform-accounts')return context.platformAdmin===true&&context.platformCapabilities?.canManagePlatformAdmins===true;
  if(path==='/platform-audit')return context.platformAdmin===true&&context.platformCapabilities?.canViewPlatformAudit===true;
  if (path === '/platform-settings') return context.platformAdmin === true && context.platformCapabilities?.canConfigurePlatform === true;
  if (path === '/companies') return context.platformAdmin
    ? context.platformCapabilities?.canCreateCompanies === true || context.platformCapabilities?.canManageCompanyPlans === true
    : Boolean(context.currentCompany);
  if (context.platformAdmin || !context.currentCompany) return false;
  if (path === '/billing') return has(context, 'canViewCompanyBilling');
  if (path === '/settings') return has(context, 'canManageCompanySettings');
  if (path === '/workplace-reports' && context.companyRoles?.includes('COMPANY_ADMIN')) return false;
  if(['/vacation','/requests','/announcements','/feedback','/performance-reviews','/workplace-reports','/audit'].includes(path))return true;
  if(path==='/sensitive-access')return has(context,'canManageSensitiveGrants');
  if(path==='/confidential-reports'||path==='/employee-reports')return has(context,'canHandleConfidentialReports');
  if(path==='/letter-management'||/^\/admin\/letter-request\/\d+$/.test(path))return has(context,'canReviewLeaveAndLetters');
  if(path==='/leave-management')return has(context,'canManageLeavePolicy');
  if(path==='/team-leave-calendar')return has(context,'canManageLeavePolicy')||(context.projectPermissions||[]).some(p=>(p.roles||[]).some(r=>['PROJECT_ADMIN','PROJECT_MANAGER'].includes(r)));
  if(path==='/reports')return has(context,'canViewCompanyReports')||(context.projectPermissions||[]).some(p=>(p.roles||[]).some(r=>['PROJECT_ADMIN','PROJECT_MANAGER'].includes(r)));
  if(path==='/missing-timesheets')return has(context,'canViewCompanyReports')||has(context,'canReviewWork');
  if (path === '/users') return has(context, 'canManageCompanyPeople'); // Safe alias to the scoped company page.
  if (path === '/projects') return has(context, 'canViewProjects')
    && (has(context, 'canManageCompanyPeople') || has(context, 'canManageProjects') || has(context, 'canReviewWork'));
  if (path === '/admin') return has(context, 'canReviewWork');
  if (path === '/timesheets' || path === '/expenses') return has(context, 'canSubmitWork');
  if (/^\/timesheet\/\d+$/.test(path)) return has(context, 'canSubmitWork') || has(context, 'canReviewWork');
  return false;
}

export function workspaceNavigation(context) {
  const primary = [['/dashboard', 'Overview', 'grid']];
  const management = [];
  if (context.platformAdmin) {
    if (canOpenWorkspaceRoute('/companies', context)) primary.push(['/companies', 'Companies', 'users']);
    if(canOpenWorkspaceRoute('/platform-accounts',context))primary.push(['/platform-accounts','Platform accounts','users']);
    if(canOpenWorkspaceRoute('/platform-audit',context))primary.push(['/platform-audit','Platform audit','file']);
    if (canOpenWorkspaceRoute('/platform-settings', context)) primary.push(['/platform-settings', 'Platform settings', 'settings']);
  } else if (context.currentCompany) {
    if (canOpenWorkspaceRoute('/timesheets', context)) primary.push(['/timesheets', 'Timesheets', 'clock'], ['/expenses', 'Expenses', 'file']);
    if (has(context, 'canManageCompanyPeople')) management.push(['/companies', 'People & access', 'users']);
    else primary.push(['/companies', 'Workspace', 'users']);
    if (canOpenWorkspaceRoute('/projects', context)) management.push(['/projects', 'Projects', 'briefcase']);
    if (canOpenWorkspaceRoute('/admin', context)) management.push(['/admin', 'Approvals', 'check']);
    if (!context.companyRoles?.includes('COMPANY_ADMIN')) primary.push(['/vacation','My leave','calendar'],['/requests','My letters','file']);
    primary.push(['/announcements','Announcements','bell'],['/feedback','Feedback','mail'],['/performance-reviews','Performance reviews','chart']);
    if (!context.companyRoles?.includes('COMPANY_ADMIN')) primary.push(['/workplace-reports','Workplace reports','file']);
    for(const [path,label,icon] of [['/letter-management','Letter management','file'],['/sensitive-access','Sensitive access','users'],['/confidential-reports','Confidential cases','file']])if(canOpenWorkspaceRoute(path,context))management.push([path,label,icon]);
    if(has(context,'canViewCompanyAudit'))management.push(['/audit','Company audit','file']);else primary.push(['/audit','My activity','file']);
    for(const [path,label,icon] of [['/leave-management','Leave management','calendar'],['/team-leave-calendar','Team leave calendar','calendar'],['/reports','Reports','chart'],['/missing-timesheets','Missing timesheets','clock']])if(canOpenWorkspaceRoute(path,context))management.push([path,label,icon]);
    if (canOpenWorkspaceRoute('/settings', context)) management.push(['/settings', 'Company settings', 'settings']);
    if (canOpenWorkspaceRoute('/billing', context)) management.push(['/billing', 'Billing & plans', 'briefcase']);
  }
  const personal = [['/notifications', 'Inbox', 'bell'], ['/company-invite', 'My invitations', 'mail']];
  return { primary, management, personal };
}

export function workspaceRoleLabel(context) {
  if (context.platformAdmin) return 'Platform super admin';
  const companyRoles = context.companyRoles || [];
  if (companyRoles.includes('COMPANY_ADMIN')) return 'Company admin';
  if (companyRoles.includes('PROJECT_ADMIN')) return 'Company project admin';
  const projectRoles = (context.projectPermissions || []).flatMap(project => project.roles || []);
  if (projectRoles.includes('PROJECT_ADMIN')) return 'Project admin';
  if (projectRoles.includes('PROJECT_MANAGER')) return 'Project manager';

  return 'Company member';
}
