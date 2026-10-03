// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Login from './Login';
const { login, myPendingInvitations } = vi.hoisted(() => ({ login: vi.fn(), myPendingInvitations: vi.fn() }));
vi.mock('../AuthContext', () => ({ useAuth: () => ({ login }) }));
vi.mock('../api', () => ({ companyAPI: { myPendingInvitations } }));
beforeEach(() => { vi.resetAllMocks(); sessionStorage.clear(); myPendingInvitations.mockResolvedValue({ data: [] }); });
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
  expect(screen.getByRole('link', { name: 'Request company access' })).toHaveAttribute('href', '/request-access');
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

it('takes a signed-in person to invitations addressed to their email', async () => {
  login.mockResolvedValue({});
  myPendingInvitations.mockResolvedValue({ data: [{ id: 8, companyName: 'Acme' }] });
  render(<MemoryRouter initialEntries={['/login']}><Routes>
    <Route path="/login" element={<Login />} />
    <Route path="/company-invite" element={<p>Invitations ready</p>} />
  </Routes></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'alice@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
  expect(await screen.findByText('Invitations ready')).toBeInTheDocument();
});
