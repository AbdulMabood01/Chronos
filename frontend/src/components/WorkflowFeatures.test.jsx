// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MissingTimesheets from './MissingTimesheets';
import LeaveBalancePreview, { calculateLeavePreview } from './LeaveBalancePreview';
import { timesheetAPI, userAPI } from '../api';
vi.mock('../api');
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

it('lists employees without timesheets and reloads when month changes', async () => {
  timesheetAPI.getMissingTimesheets.mockResolvedValue({ data: [{ userId: 1, userName: 'Alice', projectId: 4, projectCode: 'ATLAS', status: 'NOT_STARTED', hours: 0 }, { userId: 2, userName: 'Bob', projectId: 4, projectCode: 'ATLAS', status: 'REJECTED', hours: 8, timesheetId: 6 }] });
  render(<MemoryRouter><MissingTimesheets /></MemoryRouter>);
  expect(await screen.findByText('No timesheet yet')).toBeTruthy();
  expect(screen.getByText('Needs correction')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'View timesheet' }).getAttribute('href')).toBe('/timesheet/6?projectId=4');
  timesheetAPI.getMissingTimesheets.mockResolvedValue({ data: [] });
  fireEvent.change(screen.getByLabelText('Submission month'), { target: { value: '2026-08' } });
  expect(await screen.findByText('No timesheet records for this month.')).toBeTruthy();
  expect(timesheetAPI.getMissingTimesheets).toHaveBeenLastCalledWith(2026, 8);
});
it('does not mistake missing-dashboard errors for an empty queue', async () => {
  timesheetAPI.getMissingTimesheets.mockRejectedValue(new Error('offline'));
  render(<MemoryRouter><MissingTimesheets /></MemoryRouter>);
  expect((await screen.findByRole('alert')).textContent).toContain('Could not load');
  expect(screen.queryByText('No timesheet records for this month.')).toBeNull();
});
it('sorts every timesheet column in both directions and filters projects without counting approved records as outstanding', async () => {
  timesheetAPI.getMissingTimesheets.mockResolvedValue({ data: [
    { userId: 1, userName: 'Zoe', projectId: 4, projectCode: 'ATLAS', status: 'APPROVED', hours: 100, timesheetId: 6 },
    { userId: 2, userName: 'Amy', projectId: 5, projectCode: 'BETA', status: 'NOT_STARTED', hours: 2 },
  ] });
  render(<MemoryRouter><MissingTimesheets /></MemoryRouter>);
  expect(await screen.findByText('Approved')).toBeTruthy();
  expect(screen.getByText('Approved').className).toContain('status-approved');
  expect(screen.getByRole('status').textContent).toContain('2 records / 1 outstanding');
  const names = () => screen.getAllByRole('row').slice(1).map(row => row.children[0].textContent);
  expect(names()).toEqual(['Amy', 'Zoe']);
  fireEvent.click(screen.getByRole('button', { name: 'Employee' }));
  expect(names()).toEqual(['Zoe', 'Amy']);
  fireEvent.click(screen.getByRole('button', { name: 'Employee' }));
  expect(names()).toEqual(['Amy', 'Zoe']);
  for (const [column, ascending] of [['Project', ['Zoe', 'Amy']], ['Status', ['Zoe', 'Amy']], ['Hours', ['Amy', 'Zoe']], ['Details', ['Amy', 'Zoe']]]) {
    fireEvent.click(screen.getByRole('button', { name: column }));
    expect(names()).toEqual(ascending);
    expect(screen.getByRole('columnheader', { name: column }).getAttribute('aria-sort')).toBe('ascending');
    fireEvent.click(screen.getByRole('button', { name: column }));
    expect(names()).toEqual([...ascending].reverse());
  }
  fireEvent.change(screen.getByLabelText('Project (optional)'), { target: { value: '4' } });
  expect(names()).toEqual(['Zoe']);
  expect(screen.getByRole('status').textContent).toContain('1 records / 0 outstanding');
  fireEvent.change(screen.getByLabelText('Project (optional)'), { target: { value: '' } });
  expect(names()).toHaveLength(2);
});
it('splits leave by year, excludes weekends and accounts for pending days', () => {
  const result = calculateLeavePreview({ startDate: '2026-12-31', endDate: '2027-01-04', vacationType: 'VACATION' }, [
    { id: 1, startDate: '2026-12-30', endDate: '2026-12-30', status: 'SUBMITTED', vacationType: 'PERSONAL' },
    { id: 2, startDate: '2026-12-31', endDate: '2027-01-04', status: 'DRAFT', vacationType: 'VACATION' },
  ], [{ year: 2026, balance: { vacation: { remainingDays: 2 } } }, { year: 2027, balance: { vacation: { remainingDays: 1 } } }], 2);
  expect(result[0]).toMatchObject({ requested: 1, pending: 1, remaining: 0, unpaid: 0 });
  expect(result[1]).toMatchObject({ requested: 2, pending: 0, remaining: 0, unpaid: 1 });
});
it('shows an unpaid preview without fetching a paid balance', async () => {
  render(<LeaveBalancePreview userId={1} requests={[]} form={{ startDate: '2026-09-14', endDate: '2026-09-15', vacationType: 'UNPAID_LEAVE' }} />);
  const label = await screen.findByText('Unpaid days in request');
  expect(label.nextElementSibling.textContent).toBe('2');
  expect(userAPI.getLeaveBalance).not.toHaveBeenCalled();
});
