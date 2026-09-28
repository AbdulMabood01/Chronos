// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Login from './Login';
const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock('../AuthContext', () => ({ useAuth: () => ({ login }) }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
it('submits email and password without a development or token login', async () => {
  login.mockResolvedValue({});
  render(<MemoryRouter><Login /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'alice@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
  await waitFor(() => expect(login).toHaveBeenCalledWith('alice@example.com', 'StrongPassword123'));
  expect(screen.queryByText('Dev Sign In')).toBeNull();
  expect(screen.queryByLabelText('Company access token')).toBeNull();
  expect(screen.getByRole('link', { name: 'Register as an employee' })).toHaveAttribute('href', '/register');
  expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password');
});

it('shows the administrator lock message returned after sign in', async () => {
  login.mockRejectedValue({ response: { status: 423 }, userMessage: 'Your account is locked. Contact an administrator.' });
  render(<MemoryRouter><Login /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'alice@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Your account is locked. Contact an administrator.');
});
