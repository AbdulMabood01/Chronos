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
  userAPI.getMyLeaveBalance.mockResolvedValue({ data: balance });
});

afterEach(() => {
  vi.restoreAllMocks();
  cleanup();
});

it('blocks submitting a request that exceeds the available balance', async () => {
  render(<VacationRequests />);

  const submitButtons = await screen.findAllByRole('button', { name: 'Submit' });
  fireEvent.click(submitButtons[0]);

  expect(await screen.findByText(/exceed the available vacation balance/)).toBeTruthy();
  expect(vacationAPI.submitRequest).not.toHaveBeenCalled();
});

it('groups uncommon reasons under Special Leave', async () => {
  render(<VacationRequests />);

  fireEvent.click(await screen.findByRole('button', { name: 'Create request' }));
  const type = await screen.findByLabelText('Type');

  expect(type.textContent).toContain('Special Leave');
  expect(type.textContent).toContain('Bereavement Leave');
  expect(type.textContent).not.toContain('Military Leave');
  fireEvent.change(type, { target: { value: 'SPECIAL' } });
  expect(screen.getByLabelText('Special leave reason').textContent).toContain('Military');
});

it('shows a saved request in the list immediately', async () => {
  vacationAPI.createRequest.mockResolvedValue({ data: { id: 3 } });
  vacationAPI.getMyRequests.mockResolvedValueOnce({ data: [] }).mockResolvedValue({ data: [
    { id: 3, startDate: '2026-10-01', endDate: '2026-10-02', vacationType: 'VACATION', hours: 16, status: 'DRAFT', createdAt: '2026-09-27T12:00:00' },
  ] });
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Create request' }));
  fireEvent.change(screen.getByLabelText('Start Date'), { target: { value: '2026-10-01' } });
  fireEvent.change(screen.getByLabelText('End Date'), { target: { value: '2026-10-02' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
  expect(await screen.findByText('Oct 01, 2026 to Oct 02, 2026')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Submit' })).toBeTruthy();
});

it('submits without a warning when pending and requested hours fit the balance', async () => {
  userAPI.getMyLeaveBalance.mockResolvedValue({ data: { ...balance, vacation: { ...balance.vacation, remainingDays: 3 } } });
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  await waitFor(() => expect(vacationAPI.submitRequest).toHaveBeenCalledWith(2));
});

it('does not silently convert a paid request to unpaid leave', async () => {
  userAPI.getMyLeaveBalance.mockResolvedValue({ data: { ...balance, vacation: { ...balance.vacation, remainingDays: 0, unpaidDays: 5 } } });
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  expect(await screen.findByText(/2 days exceed the available vacation balance/)).toBeTruthy();
  expect(vacationAPI.submitRequest).not.toHaveBeenCalled();
});

it('checks each year of a request against its own allowance', async () => {
  vacationAPI.getMyRequests.mockResolvedValue({ data: [
    { id: 3, startDate: '2026-12-31', endDate: '2027-01-01', vacationType: 'VACATION', hours: 16, status: 'DRAFT', createdAt: '2026-09-02T12:00:00' },
  ] });
  userAPI.getMyLeaveBalance.mockImplementation(year => Promise.resolve({ data: {
    ...balance, year, vacation: { ...balance.vacation, remainingDays: year === 2027 ? 0 : 2 },
  } }));
  render(<VacationRequests />);
  fireEvent.click(await screen.findByRole('button', { name: 'Submit' }));
  expect(await screen.findByText(/2027: 1 days exceed the available vacation balance/)).toBeTruthy();
  expect(vacationAPI.submitRequest).not.toHaveBeenCalled();
});
