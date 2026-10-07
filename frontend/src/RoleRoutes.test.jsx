// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { companyAPI, notificationAPI } from './api';
vi.mock('./api');
const user = { id:1, role:'ADMIN', roles:['COMPANY_ADMIN','PROJECT_ADMIN'], profileCompleted:true,
  canReviewProjects:true, canManageProjects:true, canViewProjects:true, canSubmitWork:true };
const workspace = { companies:[{id:12,name:'Company A'}], currentCompany:{id:12,name:'Company A'},
  companyCapabilities:{}, companyRoles:[], projectPermissions:[], platformAdmin:false,platformCapabilities:{},
  loading:false,switching:false,error:'',selectCompany:vi.fn() };
vi.mock('./AuthContext',()=>({AuthProvider:({children})=>children,useAuth:()=>({user,loading:false,logout:vi.fn()})}));
vi.mock('./CompanyContext',()=>({CompanyProvider:({children})=>children,useCompany:()=>workspace}));
vi.mock('./pages/WorkspaceOverview',()=>({default:()=> <p>Workspace overview</p>}));
vi.mock('./pages/Companies',()=>({default:()=> <p>Company workspace</p>,CompanyInvitation:()=> <p>Invitations</p>}));
vi.mock('./pages/ProjectManagement',()=>({default:()=> <p>Project workspace</p>}));
vi.mock('./pages/AdminDashboard',()=>({default:()=> <p>Approval workspace</p>}));
beforeEach(()=>{
  workspace.currentCompany={id:12,name:'Company A'};
  workspace.platformAdmin=false;workspace.platformCapabilities={};workspace.companyCapabilities={};workspace.companyRoles=[];workspace.projectPermissions=[];
  notificationAPI.getUnreadCount.mockResolvedValue({data:0});
  companyAPI.context.mockResolvedValue({data:{companies:[]}});
});
afterEach(cleanup);

it.each(['/projects','/project-hours','/admin','/settings','/users','/reports','/employee-reports',
  '/time-reports','/missing-timesheets','/team-leave-calendar','/sensitive-access','/confidential-reports','/letter-management',
  '/timesheets','/expenses','/admin/letter-request/5','/timesheet/5'])('blocks %s without selected-company capabilities despite legacy ADMIN flags',async path=>{
  window.history.replaceState({},'',path);render(<App/>);
  await screen.findByText('Workspace overview');
  await waitFor(()=>expect(window.location.pathname).toBe('/dashboard'));
  expect(screen.queryByRole('link',{name:'Projects'})).toBeNull();
  expect(screen.queryByRole('link',{name:'Settings'})).toBeNull();
});

it('opens company people through a safe alias and shows company admin tools',async()=>{
  workspace.companyCapabilities={canManageCompanyPeople:true,canViewProjects:true};workspace.companyRoles=['COMPANY_ADMIN'];
  window.history.replaceState({},'','/users');render(<App/>);
  await screen.findByText('Company workspace');
  expect(window.location.pathname).toBe('/companies');
  expect(screen.getByRole('link',{name:'People & Access'})).toHaveAttribute('href','/companies');
  expect(screen.getByRole('link',{name:'Projects'})).toBeInTheDocument();
  expect(screen.queryByRole('link',{name:'Approvals'})).toBeNull();
  expect(screen.queryByRole('link',{name:'Settings'})).toBeNull();
});

it('opens approvals for a scoped reviewer',async()=>{
  workspace.companyCapabilities={canReviewWork:true};
  window.history.replaceState({},'','/admin');render(<App/>);
  await screen.findByText('Approval workspace');
  expect(screen.getByRole('link',{name:'Approvals'})).toBeInTheDocument();
});

it('removes administrative navigation and redirects when switching to a User company',async()=>{
  workspace.companyCapabilities={canManageCompanyPeople:true,canViewProjects:true};workspace.companyRoles=['COMPANY_ADMIN'];
  window.history.replaceState({},'','/projects');const view=render(<App/>);
  await screen.findByText('Project workspace');
  workspace.currentCompany={id:13,name:'Company B'};workspace.companyCapabilities={canViewProjects:true,canSubmitWork:true};workspace.companyRoles=[];
  workspace.projectPermissions=[{projectId:201,roles:['USER'],capabilities:{canSubmitWork:true}}];view.rerender(<App/>);
  await screen.findByText('Workspace overview');
  expect(screen.queryByRole('link',{name:'People & Access'})).toBeNull();
  expect(screen.queryByRole('link',{name:'Projects'})).toBeNull();
  expect(screen.getByRole('link',{name:'Timesheets'})).toBeInTheDocument();
  expect(screen.getByText('Company Member')).toBeInTheDocument();
});

it('keeps the platform menu free of company operational and HR screens',async()=>{
  workspace.platformAdmin=true;workspace.platformCapabilities={canCreateCompanies:true,canManageCompanyPlans:true};
  window.history.replaceState({},'','/dashboard');render(<App/>);
  await screen.findByText('Workspace overview');
  expect(screen.getByRole('link',{name:'Companies'})).toBeInTheDocument();
  for(const name of ['People & Access','Projects','Approvals','Reports','Settings','Audit log','Timesheets','Time off'])
    expect(screen.queryByRole('link',{name})).toBeNull();
});
