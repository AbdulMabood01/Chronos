// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Activate from './Activate';
import { authAPI } from '../api';
vi.mock('../api', () => ({ authAPI: { validateInvitation: vi.fn(), activate: vi.fn() } }));
beforeEach(() => {
  vi.resetAllMocks(); window.history.replaceState({}, '', '/activate?token=invitation-secret');
  authAPI.validateInvitation.mockResolvedValue({ data: { firstName: 'Alice', lastName: 'Smith', email: 'alice@example.com' } });
  authAPI.activate.mockResolvedValue({});
});
afterEach(cleanup);
it('validates the token, removes it from the URL, and activates with matching passwords', async () => {
  render(<MemoryRouter><Activate /></MemoryRouter>);
  await screen.findByText(/Welcome, Alice Smith/);
  expect(authAPI.validateInvitation).toHaveBeenCalledWith('invitation-secret');
  expect(window.location.search).toBe('');
  fireEvent.change(screen.getByLabelText('Create password'), { target: { value: 'StrongPassword123' } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'StrongPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Activate account' }));
  await screen.findByRole('link', { name: 'Sign in' });
  expect(authAPI.activate).toHaveBeenCalledWith('invitation-secret', 'StrongPassword123');
});
it('prevents submission when password confirmation differs', async () => {
  render(<MemoryRouter><Activate /></MemoryRouter>);
  await screen.findByText(/Welcome, Alice Smith/);
  fireEvent.change(screen.getByLabelText('Create password'), { target: { value: 'StrongPassword123' } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'DifferentPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Activate account' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Passwords do not match.');
  expect(authAPI.activate).not.toHaveBeenCalled();
});
it.each(['expired', 'already been used', 'revoked', 'invalid'])('shows %s invitation errors without a password form', async reason => {
  authAPI.validateInvitation.mockRejectedValue({ response: { data: { message: `Invitation ${reason}` } } });
  render(<MemoryRouter><Activate /></MemoryRouter>);
  expect((await screen.findByRole('alert')).textContent).toContain(reason);
  expect(screen.queryByLabelText('Create password')).toBeNull();
});
