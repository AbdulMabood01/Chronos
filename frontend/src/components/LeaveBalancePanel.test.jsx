// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import LeaveBalancePanel from './LeaveBalancePanel';
import { userAPI } from '../api';

vi.mock('../api', () => ({ userAPI: { getLeaveBalance: vi.fn(), updateLeaveAllowance: vi.fn() } }));

const balance = { year: 2026, configured: true, vacation: { allowanceDays: 10, extraDays: 2, usedDays: 13, remainingDays: 0, unpaidDays: 1 }, sick: { allowanceDays: 5, extraDays: 0, usedDays: 1, remainingDays: 4, unpaidDays: 0 } };

beforeEach(() => { vi.resetAllMocks(); userAPI.getLeaveBalance.mockResolvedValue({ data: balance }); userAPI.updateLeaveAllowance.mockResolvedValue({ data: balance }); });
afterEach(cleanup);

it('shows approved usage and unpaid days without employee editing controls', async () => {
  render(<LeaveBalancePanel userId={1} />);
  expect(await screen.findByText('Vacation')).toBeTruthy();
  expect(screen.getAllByText('Approved')).toHaveLength(2);
  expect(screen.getAllByText('Unpaid')).toHaveLength(2);
  expect(screen.getByText('13')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Save allowance' })).toBeNull();
});

it('saves extra grants and resets the grant fields to prevent accidental resubmission', async () => {
  render(<LeaveBalancePanel userId={1} editable />);
  await screen.findByText('Vacation');
  fireEvent.change(screen.getByLabelText('Add extra vacation days'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Additional leave approved' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save allowance' }));
  await waitFor(() => expect(userAPI.updateLeaveAllowance).toHaveBeenCalledWith(1, expect.objectContaining({ vacationDays: 10, sickDays: 5, addVacationDays: 3, addSickDays: 0 })));
  await screen.findByText('Leave allowance saved.');
  expect(screen.getByLabelText('Add extra vacation days').value).toBe('0');
});

it('shows errors instead of a fabricated zero balance', async () => {
  userAPI.getLeaveBalance.mockRejectedValue(new Error('Offline'));
  render(<LeaveBalancePanel userId={1} />);
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect(screen.queryByText('days remaining')).toBeNull();
});
