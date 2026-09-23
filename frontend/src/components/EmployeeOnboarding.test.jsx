// @vitest-environment jsdom
import React from 'react';
import { afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import EmployeeOnboarding from './EmployeeOnboarding';
import { userAPI } from '../api';
vi.mock('../api', () => ({ userAPI: { createEmployee: vi.fn() } }));
afterEach(cleanup);
it('creates an employee with identity fields and refreshes the directory', async () => {
  userAPI.createEmployee.mockResolvedValue({}); const refresh=vi.fn();
  render(<EmployeeOnboarding onCreated={refresh} />);
  for (const [label, value] of [['First name','Alice'],['Last name','Smith'],['Work email','alice@example.com']])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(screen.getByRole('button', { name: 'Create employee' }));
  await waitFor(() => expect(refresh).toHaveBeenCalled());
  expect(userAPI.createEmployee).toHaveBeenCalledWith({ firstName: 'Alice', lastName: 'Smith', email: 'alice@example.com' });
});
