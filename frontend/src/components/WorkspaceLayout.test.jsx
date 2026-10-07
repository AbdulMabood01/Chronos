// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import WorkspaceLayout from './WorkspaceLayout';
const workspace={companies:[{id:12,name:'Company A'}],currentCompany:{id:12},companyCapabilities:{},companyRoles:[],projectPermissions:[],platformAdmin:false,platformCapabilities:{},selectCompany:vi.fn()};
vi.mock('../AuthContext',()=>({useAuth:()=>({user:{id:1,role:'ADMIN',roles:['COMPANY_ADMIN']},logout:vi.fn()})}));
vi.mock('../CompanyContext',()=>({useCompany:()=>workspace}));
vi.mock('../api',()=>({notificationAPI:{getUnreadCount:vi.fn().mockResolvedValue({data:0})}}));
beforeEach(()=>{workspace.companyCapabilities={};workspace.companyRoles=[];workspace.projectPermissions=[];workspace.platformAdmin=false;workspace.platformCapabilities={};});
afterEach(cleanup);
const mount=()=>render(<MemoryRouter><WorkspaceLayout><p>Content</p></WorkspaceLayout></MemoryRouter>);
it('ignores legacy ADMIN roles and denies unscoped management links',()=>{
  mount();expect(screen.queryByText('Management')).toBeNull();expect(screen.getByText('Company Member')).toBeInTheDocument();
  for(const name of ['Settings','Reports','Approvals','Projects','Time off'])expect(screen.queryByRole('link',{name})).toBeNull();
});
it('shows company management and labels the current company role',()=>{
  workspace.companyCapabilities={canManageCompanyPeople:true,canViewProjects:true};workspace.companyRoles=['COMPANY_ADMIN'];mount();
  expect(screen.getByRole('link',{name:'People & Access'})).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Projects'})).toBeInTheDocument();expect(screen.getByText('Company Admin')).toBeInTheDocument();
  expect(screen.queryByRole('link',{name:'Approvals'})).toBeNull();
});
it('shows work and approval links only for the selected project permissions',()=>{
  workspace.companyCapabilities={canSubmitWork:true,canReviewWork:true,canViewProjects:true};workspace.projectPermissions=[{projectId:1,roles:['PROJECT_MANAGER']}];mount();
  expect(screen.getByRole('link',{name:'Timesheets'})).toBeInTheDocument();expect(screen.getByRole('link',{name:'Approvals'})).toBeInTheDocument();
  expect(screen.getByText('Project Manager')).toBeInTheDocument();
});
