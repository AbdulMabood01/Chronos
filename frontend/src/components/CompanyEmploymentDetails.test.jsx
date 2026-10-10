// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import CompanyEmploymentDetails from './CompanyEmploymentDetails';
const {workspace,api}=vi.hoisted(()=>({workspace:{currentCompany:{id:12,name:'Company A'},platformAdmin:false,companyCapabilities:{canManageCompanyPeople:true}},
  api:{corrections:vi.fn(),employment:vi.fn(),updateEmployment:vi.fn()}}));
vi.mock('../AuthContext',()=>({useAuth:()=>({user:{id:5,role:'ADMIN',jobTitle:'Legacy shared title'}})}));
vi.mock('../CompanyContext',()=>({useCompany:()=>workspace}));
vi.mock('../api',()=>({companyAPI:api}));
const record={companyId:12,userId:5,employeeId:'1',jobTitle:'Engineer A',joiningDate:'2020-01-01',membershipStatus:'ACTIVE',version:4};
beforeEach(()=>{vi.resetAllMocks();api.corrections.mockResolvedValue({data:[]});workspace.currentCompany={id:12,name:'Company A'};workspace.platformAdmin=false;workspace.companyCapabilities={canManageCompanyPeople:true};api.employment.mockResolvedValue({data:record});});
afterEach(cleanup);
it('shows company employment separately from the read-only personal profile',async()=>{
  render(<CompanyEmploymentDetails/>);await screen.findByText('Engineer A');
  expect(screen.queryByLabelText('Job title')).toBeNull();expect(screen.queryByText('Legacy shared title')).toBeNull();
  expect(api.employment).toHaveBeenCalledWith(12,5);
});
it('edits only the selected membership and includes its concurrency version',async()=>{
  api.updateEmployment.mockResolvedValue({data:{...record,jobTitle:'Lead A',version:5}});render(<CompanyEmploymentDetails editable/>);
  fireEvent.change(await screen.findByLabelText('Job title'),{target:{value:' Lead A '}});
  fireEvent.click(screen.getByRole('button',{name:'Save employment details'}));
  await waitFor(()=>expect(api.updateEmployment).toHaveBeenCalledWith(12,5,{employeeId:'1',jobTitle:'Lead A',joiningDate:'2020-01-01',version:4}));
  expect(await screen.findByText('Employment details saved.')).toBeInTheDocument();
});
it('never falls back to legacy ADMIN role for editing permission',async()=>{
  workspace.companyCapabilities={};render(<CompanyEmploymentDetails editable/>);await screen.findByText('Engineer A');
  expect(screen.queryByRole('button',{name:'Save employment details'})).toBeNull();
});
it('clears company A details while company B loads',async()=>{
  const view=render(<CompanyEmploymentDetails/>);await screen.findByText('Engineer A');
  let finish;api.employment.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  workspace.currentCompany={id:13,name:'Company B'};view.rerender(<CompanyEmploymentDetails/>);
  expect(screen.queryByText('Engineer A')).toBeNull();
  await act(async()=>finish({data:{...record,companyId:13,jobTitle:'Manager B',employeeId:'2'}}));
  expect(screen.getByText('Manager B')).toBeInTheDocument();expect(api.employment).toHaveBeenLastCalledWith(13,5);
});
it('shows a conflict and reloads the latest record before retry',async()=>{
  api.updateEmployment.mockRejectedValue({response:{status:409,data:{message:'Employment details changed. Reload and try again.'}}});
  render(<CompanyEmploymentDetails editable/>);await screen.findByLabelText('Job title');
  fireEvent.click(screen.getByRole('button',{name:'Save employment details'}));
  await screen.findByRole('alert');
  api.employment.mockResolvedValue({data:{...record,jobTitle:'Latest title',version:8}});
  fireEvent.click(screen.getByRole('button',{name:'Reload employment details'}));
  await waitFor(()=>expect(screen.getByLabelText('Job title')).toHaveValue('Latest title'));
});
it('does not load company employment for platform administrators',()=>{
  workspace.platformAdmin=true;render(<CompanyEmploymentDetails editable/>);
  expect(api.employment).not.toHaveBeenCalled();expect(screen.queryByText('Company employment details')).toBeNull();
});

it('shows locked employment as read-only and offers a correction request',async()=>{
 api.employment.mockResolvedValue({data:{...record,locked:true}});render(<CompanyEmploymentDetails editable/>);
 expect(await screen.findByText('Employment details are saved and locked.')).toBeInTheDocument();
 expect(screen.queryByLabelText('Job title')).toBeNull();expect(screen.queryByRole('button',{name:'Save employment details'})).toBeNull();
 expect(await screen.findByRole('button',{name:'Request correction'})).toBeInTheDocument();
});
