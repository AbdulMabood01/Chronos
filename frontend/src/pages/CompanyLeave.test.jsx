// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {render,screen,fireEvent,cleanup,waitFor} from '@testing-library/react';
import CompanyLeave from './CompanyLeave';
import {companyLeaveAPI,companyAPI} from '../api';
vi.mock('../api');
const context={currentCompany:{id:12,name:'Company A'},platformAdmin:false,companyCapabilities:{canManageLeavePolicy:true}};
vi.mock('../CompanyContext',()=>({useCompany:()=>context}));
vi.mock('../AuthContext',()=>({useAuth:()=>({user:{id:5,role:'EMPLOYEE'}})}));
vi.mock('../components/LeaveBalancePanel',()=>({default:()=> <p>Selected company balances</p>}));
const draft={id:7,companyId:12,userId:5,userName:'You',startDate:'2026-10-06',endDate:'2026-10-06',vacationType:'VACATION',status:'DRAFT',version:2};
beforeEach(()=>{
  vi.resetAllMocks();Object.assign(context,{currentCompany:{id:12,name:'Company A'},platformAdmin:false,companyCapabilities:{canManageLeavePolicy:true}});
  companyLeaveAPI.mine.mockResolvedValue({data:[draft]});companyLeaveAPI.requests.mockResolvedValue({data:[{...draft,userId:6,userName:'Member',status:'SUBMITTED'},{...draft,id:8,status:'SUBMITTED'}]});companyAPI.members.mockResolvedValue({data:[]});
});
afterEach(cleanup);
it('creates and submits using selected company and the current request revision',async()=>{
  companyLeaveAPI.save.mockResolvedValue({data:draft});companyLeaveAPI.submit.mockResolvedValue({data:{...draft,status:'SUBMITTED'}});
  render(<CompanyLeave/>);await screen.findByRole('button',{name:'Submit leave'});fireEvent.click(screen.getByRole('button',{name:'New leave request'}));
  fireEvent.change(screen.getByLabelText('Start date'),{target:{value:'2026-10-06'}});fireEvent.change(screen.getByLabelText('End date'),{target:{value:'2026-10-06'}});fireEvent.click(screen.getByRole('button',{name:'Save draft'}));
  await screen.findByText('Leave draft saved.');expect(companyLeaveAPI.save).toHaveBeenCalledWith(12,null,expect.objectContaining({startDate:'2026-10-06',vacationType:'VACATION'}));
  fireEvent.click(await screen.findByRole('button',{name:'Submit leave'}));await screen.findByText('Leave submitted.');expect(companyLeaveAPI.submit).toHaveBeenCalledWith(12,7,2);
});
it('previews company defaults before publishing the versioned policy',async()=>{
  const preview={year:2026,settingsVersion:3,policyVersion:-1,vacationDays:15,sickDays:5,bereavementDays:3,newAllowances:2,policyAllowances:1,overrides:1};
  companyLeaveAPI.preview.mockResolvedValue({data:preview});companyLeaveAPI.apply.mockResolvedValue({data:{updated:3}});
  render(<CompanyLeave management/>);fireEvent.click(screen.getByRole('button',{name:'Policy',exact:true}));fireEvent.click(screen.getByRole('button',{name:'Preview policy'}));await screen.findByRole('region',{name:'Annual policy preview'});expect(companyLeaveAPI.apply).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Apply 2026 policy'}));await screen.findByText('Company leave policy applied.');expect(companyLeaveAPI.apply).toHaveBeenCalledWith(12,preview);
});
it('blocks self-review and reviews only another company member request with its revision',async()=>{
  companyLeaveAPI.decide.mockResolvedValue({data:{}});render(<CompanyLeave management/>);
  fireEvent.click(await screen.findByRole('button',{name:'Review leave'}));expect(screen.getByText('Another Company Admin must review your request.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Approve leave'}));await screen.findByText('Leave approved.');expect(companyLeaveAPI.decide).toHaveBeenCalledWith(12,7,true,{version:2,accountingType:null});
});
it('denies platform/company member administration and discards wrong-company records',async()=>{
  context.companyCapabilities={};render(<CompanyLeave management/>);expect(screen.getByRole('alert')).toHaveTextContent('access');expect(companyLeaveAPI.requests).not.toHaveBeenCalled();cleanup();
  companyLeaveAPI.mine.mockResolvedValue({data:[{...draft,companyId:13,notes:'Other-company data'}]});render(<CompanyLeave/>);await screen.findByText('No leave requests in this company.');expect(screen.queryByRole('button',{name:'Submit leave'})).toBeNull();
});
it('drops late records after a company switch',async()=>{
  let old;companyLeaveAPI.mine.mockImplementationOnce(()=>new Promise(resolve=>{old=resolve;})).mockResolvedValue({data:[]});
  const view=render(<CompanyLeave/>);context.currentCompany={id:13,name:'Company B'};view.rerender(<CompanyLeave/>);await screen.findByText('No leave requests in this company.');old({data:[draft]});
  await waitFor(()=>expect(screen.queryByRole('button',{name:'Submit leave'})).toBeNull());expect(companyLeaveAPI.mine).toHaveBeenLastCalledWith(13);
});

it('counts weekdays and previews deductions using the selected company balance',async()=>{
  companyLeaveAPI.balance.mockResolvedValue({data:{companyId:12,configured:true,vacation:{remainingDays:12}}});
  render(<CompanyLeave/>);await screen.findByRole('button',{name:'Submit leave'});
  fireEvent.click(screen.getByRole('button',{name:'New leave request'}));
  fireEvent.change(screen.getByLabelText('Start date'),{target:{value:'2026-10-09'}});
  fireEvent.change(screen.getByLabelText('End date'),{target:{value:'2026-10-12'}});
  expect(screen.getByText(/1 working days selected \(8 hours\)/)).toBeInTheDocument();
  await screen.findByText('Balance after this request');
  await waitFor(()=>expect(companyLeaveAPI.balance).toHaveBeenCalledWith(12,null,2026));
  await screen.findByText('11');
  fireEvent.change(screen.getByLabelText('Leave type'),{target:{value:'SPECIAL'}});
  expect(screen.getByRole('option',{name:'Maternity'})).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Special leave reason'),{target:{value:'Other'}});
  expect(screen.getByLabelText('Notes')).toBeRequired();
});

it('allows the owner to delete a submitted request with its revision',async()=>{
  companyLeaveAPI.mine.mockResolvedValue({data:[{...draft,status:'SUBMITTED'}]});
  companyLeaveAPI.remove.mockResolvedValue({data:{}});
  render(<CompanyLeave/>);fireEvent.click(await screen.findByRole('button',{name:'Delete submitted request'}));
  await screen.findByText('Submitted leave request deleted.');
  expect(companyLeaveAPI.remove).toHaveBeenCalledWith(12,7,2);
});
