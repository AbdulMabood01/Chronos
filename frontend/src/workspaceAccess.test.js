import {expect,it} from 'vitest';
import {canOpenWorkspaceRoute,canCreateProjectNow,workspaceNavigation} from './workspaceAccess';
const company={currentCompany:{id:12},companyCapabilities:{canManageCompanyPeople:true,canViewProjects:true,canCreateProjects:true,
  canManageCompanySettings:true,canManageLeavePolicy:true,canViewCompanyReports:true,canViewCompanyAudit:true},companyRoles:['COMPANY_ADMIN']};
it('enables migrated personal workflows but denies sensitive workflows without grants',()=>{
  for(const path of ['/audit','/requests','/feedback','/performance-reviews','/workplace-reports'])
    expect(canOpenWorkspaceRoute(path,company)).toBe(true);
  for(const path of ['/sensitive-access','/confidential-reports'])expect(canOpenWorkspaceRoute(path,company)).toBe(false);
  expect(canOpenWorkspaceRoute('/companies',company)).toBe(true);
  expect(canOpenWorkspaceRoute('/settings',company)).toBe(true);
  expect(canOpenWorkspaceRoute('/platform-settings',company)).toBe(false);
  expect(canOpenWorkspaceRoute('/projects',company)).toBe(true);
  expect(canCreateProjectNow(company)).toBe(true);
});
it('separates platform configuration from company settings even with dual roles',()=>{
  const platform={...company,platformAdmin:true,platformCapabilities:{canConfigurePlatform:true}};
  expect(canOpenWorkspaceRoute('/settings',platform)).toBe(false);
  expect(canOpenWorkspaceRoute('/platform-settings',platform)).toBe(true);
  expect(canOpenWorkspaceRoute('/settings',{...company,companyCapabilities:{}})).toBe(false);
});
it('enables project creation only for the role accepted by the current backend',()=>{
  expect(canCreateProjectNow({...company,companyRoles:['PROJECT_ADMIN']})).toBe(true);
  expect(canCreateProjectNow({...company,platformAdmin:true})).toBe(false);
});
it('every management/work navigation entry has a matching route guard',()=>{
  for(const context of [company,{...company,companyCapabilities:{canReviewWork:true,canViewProjects:true,canSubmitWork:true}},
    {platformAdmin:true,platformCapabilities:{canCreateCompanies:true}}]){
    const navigation=workspaceNavigation(context);
    for(const [path] of [...navigation.primary,...navigation.management])expect(canOpenWorkspaceRoute(path,context)).toBe(true);
  }
});
