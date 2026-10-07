// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import LeaveBalancePanel from './LeaveBalancePanel';
import { companyLeaveAPI } from '../api';

vi.mock('../api', () => ({ companyLeaveAPI: { balance: vi.fn(), allowance: vi.fn() } }));

vi.mock('../CompanyContext',()=>({useCompany:()=>({currentCompany:{id:12},companyCapabilities:{canManageLeavePolicy:true},platformAdmin:false})}));
const balance = { companyId:12,version:0,year: 2026, configured: true, vacation: { allowanceDays: 10, extraDays: 2, usedDays: 13, remainingDays: 0, unpaidDays: 1 }, sick: { allowanceDays: 5, extraDays: 0, usedDays: 1, remainingDays: 4, unpaidDays: 0 } };

beforeEach(() => { vi.resetAllMocks(); companyLeaveAPI.balance.mockResolvedValue({ data: balance }); companyLeaveAPI.balance.mockResolvedValue({ data: balance }); companyLeaveAPI.allowance.mockResolvedValue({ data: balance }); });
afterEach(cleanup);

it('shows approved and pending days without employee editing controls', async () => {
  render(<LeaveBalancePanel userId={1} />);
  expect(await screen.findByText('Vacation')).toBeTruthy();
  expect(screen.getAllByText('Approved')).toHaveLength(2);
  expect(screen.getAllByText('Pending')).toHaveLength(2);
  expect(screen.getByText('13')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Save allowance' })).toBeNull();
});

it('saves extra grants and resets the grant fields to prevent accidental resubmission', async () => {
  render(<LeaveBalancePanel userId={1} editable />);
  await screen.findByText('Vacation');
  fireEvent.change(screen.getByLabelText('Add extra vacation days'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Additional leave approved' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save allowance' }));
  await waitFor(() => expect(companyLeaveAPI.allowance).toHaveBeenCalledWith(12,1, expect.objectContaining({ vacationDays: 10, sickDays: 5, addVacationDays: 3, addSickDays: 0 })));
  await screen.findByText('Leave allowance saved.');
  expect(screen.getByLabelText('Add extra vacation days').value).toBe('0');
});

it('shows errors instead of a fabricated zero balance', async () => {
  companyLeaveAPI.balance.mockRejectedValue(new Error('Offline'));
  render(<LeaveBalancePanel userId={1} />);
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect(screen.queryByText('days remaining')).toBeNull();
});

it('reads the signed in employee balance without relying on a user ID in the request path', async () => {
  render(<LeaveBalancePanel userId={1} ownBalance />);
  await screen.findByText('Vacation');
  expect(companyLeaveAPI.balance).toHaveBeenCalled();
  expect(companyLeaveAPI.balance).toHaveBeenCalledWith(12,null,expect.any(Number));
});

it('charts entitlement with pending days separated from available days',async()=>{
  companyLeaveAPI.balance.mockResolvedValue({data:{...balance,vacation:{allowanceDays:10,extraDays:2,usedDays:3,pendingDays:2,remainingDays:9}}});
  render(<LeaveBalancePanel userId={1}/>);
  const chart=await screen.findByRole('img',{name:'Vacation: 12 days entitlement, 3 approved, 2 pending, 7 available after pending requests'});
  expect(chart.querySelector('.used').style.width).toBe('25%');
  expect(chart.querySelector('.pending').style.width).toBe(`${2/12*100}%`);
});
