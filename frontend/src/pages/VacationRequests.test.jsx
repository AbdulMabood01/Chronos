// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import VacationRequests from './VacationRequests';
import { userAPI, vacationAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'EMPLOYEE' } }) }));

const balance = {
  year: 2026,
  configured: true,
  vacation: { allowanceDays: 2, extraDays: 0, usedDays: 0, remainingDays: 2, unpaidDays: 0 },
  sick: { allowanceDays: 5, extraDays: 0, usedDays: 0, remainingDays: 5, unpaidDays: 0 },
};

beforeEach(() => {
  vi.resetAllMocks();
  vacationAPI.getMyRequests.mockResolvedValue({
    data: [
      { id: 1, startDate: '2026-09-01', endDate: '2026-09-01', vacationType: 'VACATION', hours: 8, status: 'SUBMITTED', createdAt: '2026-09-01T12:00:00' },
      { id: 2, startDate: '2026-09-02', endDate: '2026-09-03', vacationType: 'VACATION', hours: 16, status: 'DRAFT', createdAt: '2026-09-02T12:00:00' },
    ],
  });
  userAPI.getLeaveBalance.mockResolvedValue({ data: balance });
});

afterEach(() => {
  vi.restoreAllMocks();
  cleanup();
});

it('warns before submitting a request that exceeds paid leave balance', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  render(<VacationRequests />);

  const submitButtons = await screen.findAllByRole('button', { name: 'Submit' });
  fireEvent.click(submitButtons[0]);

  await waitFor(() => expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('unpaid leave')));
  expect(vacationAPI.submitRequest).not.toHaveBeenCalled();
});

it('shows additional leave categories in the request type list', async () => {
  render(<VacationRequests />);

  const type = await screen.findByLabelText('Type');

  expect(type.textContent).toContain('Paternity Leave');
  expect(type.textContent).toContain('Maternity Leave');
  expect(type.textContent).toContain('Bereavement Leave');
  expect(type.textContent).toContain('Adoption Leave');
});

it('submits without a warning when pending and requested hours fit the balance', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  userAPI.getLeaveBalance.mockResolvedValue({ data: { ...balance, vacation: { ...balance.vacation, remainingDays: 3 } } });
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(vacationAPI.submitRequest).toHaveBeenCalledWith(2));
  expect(window.confirm).not.toHaveBeenCalled();
});

it('limits unpaid hours to the current request when the balance is exhausted', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  userAPI.getLeaveBalance.mockResolvedValue({ data: { ...balance, vacation: { ...balance.vacation, remainingDays: 0, unpaidDays: 5 } } });
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(vacationAPI.submitRequest).toHaveBeenCalledWith(2));
  expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('16 hours (2 days) of this request will be unpaid leave'));
});

it('checks each year of a request against its own allowance', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  vacationAPI.getMyRequests.mockResolvedValue({ data: [
    { id: 3, startDate: '2026-12-31', endDate: '2027-01-01', vacationType: 'VACATION', hours: 16, status: 'DRAFT', createdAt: '2026-09-02T12:00:00' },
  ] });
  userAPI.getLeaveBalance.mockImplementation((id, year) => Promise.resolve({ data: {
    ...balance, year, vacation: { ...balance.vacation, remainingDays: year === 2027 ? 0 : 2 },
  } }));
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('2027: 8 hours requested, 0 paid hours remaining')));
  expect(vacationAPI.submitRequest).not.toHaveBeenCalled();
});
