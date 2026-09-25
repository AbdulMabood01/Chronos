// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import UserManagement from './UserManagement';
import { userAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'ADMIN' } }) }));
vi.mock('../api', () => ({ userAPI: { getAllUsersAsAdmin: vi.fn(), deactivateUser: vi.fn(), reactivateUser: vi.fn(), updateJoiningDate: vi.fn(), getLeaveBalance: vi.fn(), updateLeaveAllowance: vi.fn() } }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
it('opens a full profile workspace with employment and leave management', async () => {
  userAPI.getAllUsersAsAdmin.mockResolvedValue({ data: [{ id: 2, firstName: 'Alice', lastName: 'Smith', role: 'EMPLOYEE', isActive: true, dateOfBirth: '1990-01-02', ssnLast4: '0123', addressLine1: '12 Main Street', emergencyContactName: 'Jane Smith', phoneNumber: '555-0100', bloodGroup: 'O+' }] });
  render(<UserManagement />);
  fireEvent.click(await screen.findByRole('button', { name: "View and update Alice Smith's profile" }));
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.getByRole('region', { name: 'Employee profile' }).textContent).toContain('1990-01-02');
  expect(screen.getByRole('button', { name: 'Save joining date' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Leave allowance' })).toBeTruthy();
  expect(screen.queryByText('Hourly rate')).toBeNull();
  for (const value of ['0123', '12 Main Street', 'Jane Smith', '555-0100', 'O+']) expect(screen.getByText(value)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Back to users' }));
  expect(screen.queryByRole('dialog')).toBeNull();
});
it('can retry a failed request and search loaded users', async () => {
  userAPI.getAllUsersAsAdmin.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValue({ data: [{ id: 2, firstName: 'Alice', lastName: 'Smith', email: 'alice@example.test', role: 'EMPLOYEE', isActive: true }] });
  vi.spyOn(console, 'error').mockImplementation(() => {});
  render(<UserManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));
  expect(await screen.findByText('Alice Smith')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Leave allowance' })).toBeNull();
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'nobody' } });
  expect(screen.queryByText('Alice Smith')).toBeNull();
  vi.restoreAllMocks();
});

it('does not show the deactivate control for Admin users', async () => {
  userAPI.getAllUsersAsAdmin.mockResolvedValue({
    data: [
      { id: 1, firstName: 'Sam', lastName: 'Admin', email: 'sam@example.test', role: 'ADMIN', isActive: true },
      { id: 2, firstName: 'Alice', lastName: 'Smith', email: 'alice@example.test', role: 'EMPLOYEE', isActive: true },
    ],
  });

  render(<UserManagement />);

  const systemAdminRow = (await screen.findByText('Sam Admin')).closest('tr');
  const employeeRow = screen.getByText('Alice Smith').closest('tr');

  expect(systemAdminRow.textContent).not.toContain('Deactivate');
  expect(employeeRow.textContent).toContain('Deactivate');
});

it('saves employment details and manages leave inside the profile', async () => {
  const employee = { id: 2, firstName: 'Alice', lastName: 'Smith', role: 'EMPLOYEE', isActive: true };
  userAPI.getAllUsersAsAdmin.mockResolvedValue({ data: [employee] });
  userAPI.updateJoiningDate.mockResolvedValue({ data: { ...employee, joiningDate: '2024-01-15' } });
  const days = { allowanceDays: 15, remainingDays: 15, extraDays: 0, usedDays: 0, unpaidDays: 0 };
  userAPI.getLeaveBalance.mockResolvedValue({ data: { configured: true, vacation: days, sick: days, bereavement: days } });
  userAPI.updateLeaveAllowance.mockResolvedValue({ data: { configured: true, vacation: { ...days, allowanceDays: 20, remainingDays: 20 }, sick: days, bereavement: days } });
  render(<UserManagement />);
  fireEvent.click(await screen.findByRole('button', { name: "View and update Alice Smith's profile" }));
  fireEvent.change(screen.getByLabelText('Employment start date'), { target: { value: '2024-01-15' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save joining date' }));
  expect(await screen.findByText('Employment details saved.')).toBeTruthy();
  expect(userAPI.updateJoiningDate).toHaveBeenCalledWith(2, '2024-01-15');
  expect(screen.getByText('January 15, 2024')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Leave allowance' }));
  fireEvent.change(await screen.findByLabelText('Annual vacation days'), { target: { value: '20' } });
  fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Annual allocation' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save allowance' }));
  expect(await screen.findByText('Leave allowance saved.')).toBeTruthy();
  expect(userAPI.updateLeaveAllowance).toHaveBeenCalledWith(2, expect.objectContaining({ vacationDays: 20, reason: 'Annual allocation' }));
  fireEvent.click(screen.getByRole('button', { name: 'Back to users' }));
  fireEvent.click(screen.getByRole('button', { name: "View and update Alice Smith's profile" }));
  expect(screen.getByText('January 15, 2024')).toBeTruthy();
});
