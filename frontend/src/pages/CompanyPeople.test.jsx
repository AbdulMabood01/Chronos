// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import Companies from './Companies';
import {projectAPI} from '../api';
const {api,workspace}=vi.hoisted(()=>({api:{employment:vi.fn(),updateEmployment:vi.fn(),corrections:vi.fn(),invite:vi.fn(),memberProfile:vi.fn(),updateMemberProfile:vi.fn(),members:vi.fn(),invitations:vi.fn(),moderatorGrants:vi.fn(),accessRequests:vi.fn(),setMemberStatus:vi.fn(),assignCompanyRole:vi.fn(),removeCompanyRole:vi.fn(),recoverMemberPassword:vi.fn()},
  workspace:{companies:[{id:12,name:'Company A',slug:'company-a'}],currentCompany:{id:12},platformAdmin:false,platformCapabilities:{},companyCapabilities:{canManageCompanyPeople:true,canViewProjects:true},companyRoles:['COMPANY_ADMIN'],refreshCompanies:vi.fn(),selectCompany:vi.fn()}}));
vi.mock('../AuthContext',()=>({useAuth:()=>({user:{id:5,email:'admin@example.com',role:'EMPLOYEE'}})}));
vi.mock('../CompanyContext',()=>({useCompany:()=>workspace}));
vi.mock('../api',()=>({companyAPI:api,projectAPI:{getProjects:vi.fn().mockResolvedValue({data:[]})}}));
let members;
beforeEach(()=>{
  vi.resetAllMocks();api.corrections.mockResolvedValue({data:[]});api.employment.mockResolvedValue({data:{companyId:12,userId:7,employeeId:'2',jobTitle:'Engineer',joiningDate:'2024-01-01',membershipStatus:'ACTIVE',version:0,locked:false}});workspace.companyCapabilities={canManageCompanyPeople:true,canViewProjects:true};workspace.companyRoles=['COMPANY_ADMIN'];workspace.refreshCompanies.mockResolvedValue();
  members=[{user_id:5,email:'admin@example.com',first_name:'Admin',status:'ACTIVE',roles:['COMPANY_ADMIN'],account_available:true,platform_account:false,membership_version:0},
    {user_id:7,email:'member@example.com',first_name:'Sam',job_title:'Engineer',status:'ACTIVE',roles:[],account_available:true,platform_account:false,membership_version:0},
    {user_id:8,email:'former@example.com',first_name:'Former',status:'REMOVED',roles:[],account_available:true,platform_account:false,membership_version:3}];
  api.members.mockImplementation(()=>Promise.resolve({data:members.map(member=>({...member}))}));
  projectAPI.getProjects.mockResolvedValue({data:[]});
  for(const key of ['invitations','moderatorGrants','accessRequests'])api[key].mockResolvedValue({data:[]});
});
afterEach(cleanup);
const mount=()=>render(<MemoryRouter><Companies/></MemoryRouter>);
const card=email=>screen.getByRole('group',{name:`Member ${email}`});
it('searches people and filters active versus removed memberships',async()=>{
  mount();await screen.findByRole('group',{name:'Member member@example.com'});
  expect(screen.queryByRole('group',{name:'Member former@example.com'})).toBeNull();
  fireEvent.change(screen.getByLabelText('Search people'),{target:{value:'Engineer'}});
  expect(screen.queryByRole('group',{name:'Member admin@example.com'})).toBeNull();
  fireEvent.change(screen.getByLabelText('Search people'),{target:{value:''}});fireEvent.change(screen.getByLabelText('Membership status'),{target:{value:'REMOVED'}});
  expect(card('former@example.com')).toBeInTheDocument();expect(screen.queryByRole('group',{name:'Member member@example.com'})).toBeNull();
});
it('disables removal of the only effective active admin',async()=>{
  mount();await screen.findByRole('group',{name:'Member admin@example.com'});
  expect(within(card('admin@example.com')).getByRole('button',{name:'Remove membership'})).toBeDisabled();
  expect(within(card('admin@example.com')).getByRole('button',{name:'Remove Company admin role',exact:true})).toBeDisabled();
});
it('removes and reactivates membership with the latest version and no role restoration',async()=>{
  api.setMemberStatus.mockImplementation((company,user,status,version)=>{
    members=members.map(member=>member.user_id===user?{...member,status,roles:[],membership_version:version+1}:member);return Promise.resolve({data:{status,version:version+1}});
  });mount();await screen.findByRole('group',{name:'Member member@example.com'});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'Remove membership'}));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Remove access'}));
  await waitFor(()=>expect(api.setMemberStatus).toHaveBeenCalledWith('12',7,'REMOVED',0));
  await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
  fireEvent.change(screen.getByLabelText('Membership status'),{target:{value:'REMOVED'}});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'Reactivate membership'}));
  expect(within(screen.getByRole('dialog')).getByText(/Previous company roles and project access stay revoked/)).toBeInTheDocument();
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Reactivate access'}));
  await waitFor(()=>expect(api.setMemberStatus).toHaveBeenLastCalledWith('12',7,'ACTIVE',1));expect(api.assignCompanyRole).not.toHaveBeenCalled();
});
it('assigns a company role without changing global roles',async()=>{
  api.assignCompanyRole.mockResolvedValue({data:{version:1}});mount();await screen.findByRole('group',{name:'Member member@example.com'});
  fireEvent.change(screen.getByLabelText('Company role for member@example.com'),{target:{value:'PROJECT_ADMIN'}});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'Assign role'}));
  await waitFor(()=>expect(api.assignCompanyRole).toHaveBeenCalledWith('12',7,'PROJECT_ADMIN',0));
});
it('keeps a stale/conflicting removal dialog open with the server reason',async()=>{
  api.setMemberStatus.mockRejectedValue({response:{status:409,data:{message:'Transfer project ownership first'}}});mount();await screen.findByRole('group',{name:'Member member@example.com'});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'Remove membership'}));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Remove access'}));
  expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Transfer project ownership first');
});
it('requests recovery for the member ID rather than an editable email destination',async()=>{
  api.recoverMemberPassword.mockResolvedValue({data:{}});mount();await screen.findByRole('group',{name:'Member member@example.com'});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'Send password recovery'}));
  await waitFor(()=>expect(api.recoverMemberPassword).toHaveBeenCalledWith('12',7));
});

it('restores View Profile and loads only the selected company member',async()=>{
  api.memberProfile.mockResolvedValue({data:{id:7,firstName:'Sam',lastName:'Member',email:'member@example.com',profileCompleted:false}});
  const show=vi.fn(function(){this.setAttribute('open','');});const close=vi.fn();
  HTMLDialogElement.prototype.showModal=show;HTMLDialogElement.prototype.close=close;
  mount();await screen.findByRole('group',{name:'Member member@example.com'});
  fireEvent.click(within(card('member@example.com')).getByRole('button',{name:'View Profile'}));
  const dialog=await screen.findByRole('dialog',{name:'View Profile'});
  expect(await within(dialog).findByLabelText('First Name')).toHaveValue('Sam');expect(within(dialog).getByLabelText('First Name')).toBeDisabled();expect(within(dialog).queryByRole('button',{name:'Save Profile'})).toBeNull();expect(api.memberProfile).toHaveBeenCalledWith(12,7);
  expect(within(dialog).queryByLabelText('Blood group')).toBeNull();
  fireEvent.click(within(dialog).getByRole('button',{name:'Close profile'}));expect(screen.queryByRole('dialog')).toBeNull();
});
it('invites a company member without showing a project selector',async()=>{
 api.invite.mockResolvedValue({});mount();await screen.findByRole('group',{name:'Member member@example.com'});
 fireEvent.click(screen.getByRole('button',{name:'Invitations'}));
 expect(screen.queryByLabelText('Project')).toBeNull();
 fireEvent.change(screen.getByLabelText('Email address'),{target:{value:'new@example.com'}});
 fireEvent.click(screen.getByRole('button',{name:'Send invitation'}));
 await waitFor(()=>expect(api.invite).toHaveBeenCalledWith('12',expect.objectContaining({email:'new@example.com',role:'USER',projectId:null})));
});
