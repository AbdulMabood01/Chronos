// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import TeamLeaveCalendar from './TeamLeaveCalendar';
import ProjectBudget from './ProjectBudget';
import ProfileForm from './ProfileForm';
import ApprovalHistory from './ApprovalHistory';
import { vacationAPI, timesheetAPI } from '../api';
vi.mock('../api');
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
it('shows cross-month leave on each date and changes the displayed month', async () => {
  vacationAPI.getTeamCalendar.mockResolvedValue({ data: [{ id: 1, userId: 2, userName: 'Alice Smith', startDate: '2026-08-31', endDate: '2026-09-02' }] });
  render(<TeamLeaveCalendar />);
  fireEvent.change(screen.getByLabelText('Calendar month'), { target: { value: '2026-09' } });
  fireEvent.click(await screen.findByRole('button', { name: 'September 1, 2026: 1 away' }));
  expect(screen.getByText('Alice Smith')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'September 2, 2026: 1 away' })).toBeTruthy();
  vacationAPI.getTeamCalendar.mockResolvedValue({ data: [] });
  fireEvent.change(screen.getByLabelText('Calendar month'), { target: { value: '2026-10' } });
  expect(await screen.findByText('No approved absences this month.')).toBeTruthy();
  expect(screen.queryByText('Alice Smith')).toBeNull();
  expect(vacationAPI.getTeamCalendar).toHaveBeenLastCalledWith(2026,10);
});
it('reports calendar errors without claiming there are no absences', async () => {
  vacationAPI.getTeamCalendar.mockRejectedValue(new Error('offline'));
  render(<TeamLeaveCalendar />);
  expect((await screen.findByRole('alert')).textContent).toContain('Could not load');
  expect(screen.queryByText('No approved absences this month.')).toBeNull();
});
it.each([[79, 'Within budget'], [80, 'Approaching budget'], [100, 'Budget reached'], [110, 'Budget reached']])('shows the correct budget warning at %s percent', (logged, label) => {
  render(<ProjectBudget budget={100} logged={logged} />);
  expect(screen.getByText(label)).toBeTruthy();
  expect(screen.getByRole('progressbar').value).toBe(Math.min(logged,100));
  if (logged > 100) expect(screen.getByText(/10.00 hours over budget/)).toBeTruthy();
});
it('handles an unset budget without a misleading zero percent', () => {
  render(<ProjectBudget budget={null} logged={40} />);
  expect(screen.getByText(/No hours budget set/)).toBeTruthy();
  expect(screen.queryByRole('progressbar')).toBeNull();
});
it('focuses missing fields and updates checklist progress while editing', () => {
  render(<ProfileForm user={{ firstName: 'Alice', lastName: 'Smith', jobTitle: 'Engineer', dateOfBirth: '1990-01-01' }} onSave={vi.fn()} />);
  expect(screen.getByRole('progressbar', { name: 'Profile completeness' }).value).toBe(1);
  fireEvent.click(screen.getByRole('button', { name: 'Review phone number' }));
  expect(document.activeElement).toBe(screen.getByLabelText('Phone number'));
  fireEvent.change(screen.getByLabelText('Phone number'), { target: { value: '555-0101' } });
  expect(screen.getByRole('progressbar', { name: 'Profile completeness' }).value).toBe(2);
  fireEvent.click(screen.getByRole('button', { name: 'Continue profile' }));
  expect(document.activeElement).toBe(screen.getByLabelText('Address line 1'));
  fireEvent.click(screen.getByRole('button', { name: 'Review personal details' }));
  expect(document.activeElement).toBe(screen.getByLabelText('First Name'));
  fireEvent.click(screen.getByRole('button', { name: 'Review emergency contact' }));
  expect(document.activeElement).toBe(screen.getByLabelText('Contact name'));
});
it('loads approval history and refreshes after a status change', async () => {
  timesheetAPI.getApprovalHistory.mockResolvedValue({ data: [{ id: 1, action: 'TIMESHEET_REJECTED', userName: 'Sam Manager', createdAt: '2026-09-15T10:00:00', details: { message: 'Reason: Correct Monday hours' } }] });
  const view = render(<ApprovalHistory timesheetId={2} projectId={4} revision="REJECTED" />);
  await waitFor(() => expect(screen.getByRole('button', { name: 'View approval history' }).disabled).toBe(false));
  fireEvent.click(screen.getByRole('button', { name: 'View approval history' }));
  expect(await screen.findByText('Reason: Correct Monday hours')).toBeTruthy();
  timesheetAPI.getApprovalHistory.mockResolvedValue({ data: [{ id: 2, action: 'TIMESHEET_SUBMITTED', userName: 'Alice' }] });
  view.rerender(<ApprovalHistory timesheetId={2} projectId={4} revision="SUBMITTED" />);
  expect(await screen.findByText('Submitted for approval')).toBeTruthy();
  expect(timesheetAPI.getApprovalHistory).toHaveBeenLastCalledWith(2,4);
});
