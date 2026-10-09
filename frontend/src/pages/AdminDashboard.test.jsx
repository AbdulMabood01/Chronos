// @vitest-environment jsdom
import React from 'react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import AdminDashboard from './AdminDashboard';
import {expenseAPI,letterRequestAPI,timesheetAPI,vacationAPI} from '../api';
vi.mock('../api');
vi.mock('../AuthContext',()=>({useAuth:()=>({user:{id:1,role:'ADMIN',canReviewProjects:true}})}));
const workspace={currentCompany:{id:12},platformAdmin:false,companyCapabilities:{canReviewWork:true,canManageProjects:true},
  projectPermissions:[{projectId:4,capabilities:{canReviewTime:true,canReviewExpenses:true}}]};
vi.mock('../CompanyContext',()=>({useCompany:()=>workspace}));
beforeEach(()=>{
  vi.resetAllMocks();workspace.projectPermissions=[{projectId:4,capabilities:{canReviewTime:true,canReviewExpenses:true}}];
  timesheetAPI.getPendingApprovalPeriods.mockResolvedValue({data:[]});timesheetAPI.getPendingPeriodOpenings.mockResolvedValue({data:[]});
  expenseAPI.pending.mockResolvedValue({data:[]});
});
afterEach(cleanup);
const mount=()=>render(<MemoryRouter><AdminDashboard/></MemoryRouter>);
it('reviews a submission while excluding other companies and global leave/letter APIs',async()=>{
  timesheetAPI.getPendingApprovalPeriods.mockResolvedValue({data:[{id:22,projectId:4,userId:2,userName:'Company A member',status:'SUBMITTED',periodStart:'2026-09-01',periodEnd:'2026-09-30',totalHours:8},
    {id:23,projectId:99,userId:3,userName:'Other company member',status:'SUBMITTED',totalHours:8}]});
  timesheetAPI.decideApprovalPeriod.mockResolvedValue({data:{}});mount();
  await screen.findByText('Company A member');expect(screen.queryByText('Other company member')).toBeNull();
  expect(vacationAPI.getPendingRequests).not.toHaveBeenCalled();expect(letterRequestAPI.getPendingRequests).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Approve'}));
  await waitFor(()=>expect(timesheetAPI.decideApprovalPeriod).toHaveBeenCalledWith(22,true,null,null));
});
it('respects an expenses-only moderator grant',async()=>{
  workspace.projectPermissions=[{projectId:4,capabilities:{canReviewTime:false,canReviewExpenses:true}}];
  timesheetAPI.getPendingApprovalPeriods.mockResolvedValue({data:[{id:22,projectId:4,userName:'Time submitter',status:'SUBMITTED',totalHours:8}]});
  expenseAPI.pending.mockResolvedValue({data:[{id:42,project_id:4,employee_name:'Expense submitter',project_code:'ATLAS',category:'TRAVEL',amount:25,expense_date:'2026-09-15',status:'PENDING_APPROVAL'}]});
  mount();await screen.findByText('Expense submitter');expect(screen.queryByText('Time submitter')).toBeNull();
});
