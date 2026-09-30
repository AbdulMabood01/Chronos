// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminDashboard from './AdminDashboard';
import { expenseAPI, letterRequestAPI, timesheetAPI, vacationAPI } from '../api';

vi.mock('../api');
const currentUser = { id: 1, role: 'ADMIN' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: currentUser }) }));

beforeEach(() => {
  vi.resetAllMocks();
  currentUser.role = 'ADMIN';
  currentUser.canReviewProjects = false;
  currentUser.canManageProjects = false;
  timesheetAPI.getPendingApprovalPeriods.mockResolvedValue({ data: [] });
  timesheetAPI.getPendingPeriodOpenings.mockResolvedValue({ data: [] });
  letterRequestAPI.getPendingRequests.mockResolvedValue({ data: [] });
  expenseAPI.pending.mockResolvedValue({ data: [] });
  vacationAPI.getPendingRequests.mockResolvedValue({
    data: [{
      id: 7,
      userName: 'Alice Smith',
      startDate: '2026-09-21',
      endDate: '2026-09-22',
      hours: 16,
      vacationType: 'VACATION',
      submittedAt: '2026-09-15T12:00:00',
      status: 'SUBMITTED',
    }],
  });
});

it('reviews a pending expense from Approvals', async () => {
  expenseAPI.pending.mockResolvedValueOnce({ data: [{ id: 42, employee_name: 'Sam Lee', project_code: 'ATLAS', category: 'TRAVEL', amount: 25, expense_date: '2026-09-15', description: 'Client visit', receipt_name: 'ticket.pdf' }] }).mockResolvedValue({ data: [] });
  expenseAPI.decide.mockResolvedValue({ data: {} });
  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
  expect(await screen.findByText('Sam Lee')).toBeTruthy();
  const expenseTask = screen.getByText('Sam Lee').closest('article');
  expect(within(expenseTask).getByText('Expense Approval')).toBeTruthy();
  expect(within(expenseTask).getByText('$25.00', { exact: false })).toBeTruthy();
  fireEvent.click(within(expenseTask).getByRole('button', { name: 'Review' }));
  const dialog = screen.getByRole('dialog', { name: 'Review expense' });
  fireEvent.click(within(dialog).getByRole('button', { name: 'Approve' }));
  await waitFor(() => expect(expenseAPI.decide).toHaveBeenCalledWith(42, 'APPROVED', '', null));
  await waitFor(() => expect(screen.queryByText('Sam Lee')).toBeNull());
  expect(document.querySelectorAll('.admin-pending-section .task-row')).toHaveLength(1);
});

it('lets a Project Admin review and approve an employee opening request', async () => {
  currentUser.role = 'PROJECT_ADMIN';
  currentUser.canManageProjects = true;
  timesheetAPI.getPendingPeriodOpenings.mockResolvedValueOnce({ data: [{
    id: 9, timesheetId: 4, projectId: 3, projectCode: 'P1', userName: 'Abdul Mabood',
    periodStart: '2026-08-01', periodEnd: '2026-08-31', openingStatus: 'PENDING', openingReason: 'I missed project hours',
    openingRequestedAt: '2026-09-01T12:00:00',
  }] }).mockResolvedValue({ data: [] });
  timesheetAPI.decidePeriodOpening.mockResolvedValue({ data: {} });
  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
  const task = (await screen.findByText('Abdul Mabood')).closest('article');
  fireEvent.click(within(task).getByRole('button', { name: 'Review' }));
  const dialog = screen.getByRole('dialog', { name: 'Review timesheet opening' });
  expect(within(dialog).getByText('I missed project hours')).toBeTruthy();
  fireEvent.click(within(dialog).getByRole('button', { name: 'Approve for 7 days' }));
  await waitFor(() => expect(timesheetAPI.decidePeriodOpening).toHaveBeenCalledWith(9, true, ''));
});

afterEach(cleanup);

it('shows the backend reason when vacation approval is blocked', async () => {
  vacationAPI.approveVacation.mockRejectedValue({
    response: { data: { message: 'Vacation conflicts with submitted or finalized hours; reopen the timesheet first' } },
  });

  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);

  fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));

  await waitFor(() => expect(screen.getByText(/Vacation conflicts with submitted or finalized hours/)).toBeTruthy());
});

it('requires a classification before approving special leave', async () => {
  vacationAPI.getPendingRequests.mockResolvedValue({ data: [{ id: 8, userName: 'Pat Doe', startDate: '2026-09-21', endDate: '2026-09-22', hours: 16, vacationType: 'SPECIAL', specialReason: 'Military', status: 'SUBMITTED' }] });
  vacationAPI.approveVacation.mockResolvedValue({ data: {} });
  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
  const task = (await screen.findByText('Pat Doe')).closest('article');
  expect(within(task).queryByRole('button', { name: 'Approve' })).toBeNull();
  fireEvent.click(within(task).getByRole('button', { name: 'Review' }));
  const dialog = screen.getByRole('dialog', { name: 'Classify special leave' });
  expect(within(dialog).getByRole('button', { name: 'Approve leave' }).disabled).toBe(true);
  fireEvent.change(within(dialog).getByLabelText('How should this leave be counted?'), { target: { value: 'PAID_NO_QUOTA' } });
  fireEvent.click(within(dialog).getByRole('button', { name: 'Approve leave' }));
  await waitFor(() => expect(vacationAPI.approveVacation).toHaveBeenCalledWith(8, 'PAID_NO_QUOTA'));
});


it('lets a PM approve another employee submission on the Approvals screen', async () => {
  currentUser.role = 'EMPLOYEE';
  currentUser.canReviewProjects = true;
  timesheetAPI.getPendingApprovalPeriods.mockResolvedValue({ data: [{
    id: 22, timesheetId: 12, projectId: 4, projectCode: 'ATLAS', projectName: 'Atlas',
    userId: 2, userName: 'Team Member', status: 'SUBMITTED', periodStart: '2026-09-01', periodEnd: '2026-09-30',
    totalHours: 8,
  }] });
  timesheetAPI.decideApprovalPeriod.mockResolvedValue({ data: {} });
  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
  expect(await screen.findByText('Team Member')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
  await waitFor(() => expect(timesheetAPI.decideApprovalPeriod).toHaveBeenCalledWith(22, true, null, null));
});
