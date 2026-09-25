// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ForgotPassword, ResetPassword } from './PasswordRecovery';
import ChangePassword from '../components/ChangePassword';
const { api, logout } = vi.hoisted(() => ({ api: { forgotPassword: vi.fn(), validatePasswordReset: vi.fn(), resetPassword: vi.fn(), changePassword: vi.fn() }, logout: vi.fn() }));
vi.mock('../api', () => ({ authAPI: api }));
vi.mock('../AuthContext', () => ({ useAuth: () => ({ logout }) }));
beforeEach(() => { vi.resetAllMocks(); window.history.replaceState({}, '', '/'); });
afterEach(cleanup);
const renderPage = element => render(<MemoryRouter>{element}</MemoryRouter>);
const fill = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
it('requests a link and displays the generic email confirmation', async () => {
  api.forgotPassword.mockResolvedValue({ data: { message: 'If an active account matches that email, you will receive a reset link shortly.' } });
  renderPage(<ForgotPassword />);
  fill('Work email', 'alice@example.com');
  fireEvent.click(screen.getByText('Send reset link'));
  expect(await screen.findByRole('status')).toHaveTextContent('If an active account');
  expect(api.forgotPassword).toHaveBeenCalledWith('alice@example.com');
});
it('strips the token from history, validates it and resets the password', async () => {
  window.history.replaceState({}, '', '/reset-password#token=secret');
  api.validatePasswordReset.mockResolvedValue({}); api.resetPassword.mockResolvedValue({});
  renderPage(<ResetPassword />);
  await screen.findByLabelText('New password');
  expect(window.location.hash).toBe('');
  expect(api.validatePasswordReset).toHaveBeenCalledWith('secret');
  fill('New password', 'NewPassword123'); fill('Confirm new password', 'NewPassword123');
  fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Password reset.');
  expect(api.resetPassword).toHaveBeenCalledWith({ token: 'secret', newPassword: 'NewPassword123', confirmation: 'NewPassword123' });
  expect(logout).toHaveBeenCalled();
});
it('rejects expired links and offers a replacement', async () => {
  window.history.replaceState({}, '', '/reset-password#token=expired');
  api.validatePasswordReset.mockRejectedValue({ response: { status: 400, data: { message: 'This reset link is invalid or expired.' } } });
  renderPage(<ResetPassword />);
  expect(await screen.findByRole('alert')).toHaveTextContent('invalid or expired');
  expect(screen.queryByLabelText('New password')).toBeNull();
  expect(screen.getByText('Request a new reset link')).toHaveAttribute('href', '/forgot-password');
});
it('validates confirmation and password rules before sending a change', async () => {
  renderPage(<ChangePassword />);
  fill('Current password', 'OldPassword123'); fill('New password', 'NewPassword123'); fill('Confirm new password', 'Different123');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match');
  fill('New password', 'alllowercase123'); fill('Confirm new password', 'alllowercase123');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('uppercase');
  expect(api.changePassword).not.toHaveBeenCalled();
});
it('retains the session on a wrong current password and signs out after success', async () => {
  api.changePassword.mockRejectedValueOnce({ response: { data: { message: 'Current password is incorrect.' } } }).mockResolvedValueOnce({});
  renderPage(<ChangePassword />);
  fill('Current password', 'OldPassword123'); fill('New password', 'NewPassword123'); fill('Confirm new password', 'NewPassword123');
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Current password is incorrect');
  expect(logout).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  await waitFor(() => expect(logout).toHaveBeenCalled());
});
