// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CompanyInvitation } from './Companies';

const { auth, previewInvitation, myPendingInvitations, accept, acceptInvitation, claimInvitation } = vi.hoisted(() => ({
  auth: { user: null, login: vi.fn(), logout: vi.fn(), refreshUser: vi.fn() },
  previewInvitation: vi.fn(), myPendingInvitations: vi.fn(), accept: vi.fn(), acceptInvitation: vi.fn(), claimInvitation: vi.fn(),
}));
vi.mock('../AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../api', () => ({ companyAPI: { previewInvitation, myPendingInvitations, accept, acceptInvitation, claimInvitation }, projectAPI: {} }));

beforeEach(() => {
  vi.resetAllMocks();
  auth.user = null;
  auth.refreshUser.mockResolvedValue();
  sessionStorage.clear();
  window.history.replaceState({}, '', '/company-invite');
  myPendingInvitations.mockResolvedValue({ data: [] });
});
afterEach(cleanup);

it('shows the invited company and email before account creation', async () => {
  window.history.replaceState({}, '', '/company-invite#token=invitation-secret');
  previewInvitation.mockResolvedValue({ data: {
    id: 8, companyName: 'Acme', projectName: 'Atlas', role: 'PROJECT_MANAGER',
    email: 'alice@example.com', status: 'PENDING',
  } });
  render(<MemoryRouter><CompanyInvitation /></MemoryRouter>);
  expect(await screen.findByRole('heading', { name: 'Join Acme' })).toBeInTheDocument();
  expect(screen.getByText('Project Manager · Atlas')).toBeInTheDocument();
  expect(screen.getByText('Sent to alice@example.com')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Create an account' }));
  expect(screen.getByRole('heading', { name: 'Create your account and join' })).toBeInTheDocument();
  expect(screen.getByText(/This invitation verifies alice@example.com/)).toBeInTheDocument();
  expect(window.location.hash).toBe('');
});

it('accepts an invitation found by verified account email after sign-in', async () => {
  auth.user = { email: 'alice@example.com' };
  myPendingInvitations.mockResolvedValueOnce({ data: [{
    id: 8, companyName: 'Acme', projectName: null, role: 'COMPANY_ADMIN', email: 'alice@example.com',
  }, {
    id: 9, companyName: 'Beta', projectName: 'Apollo', role: 'USER', email: 'alice@example.com',
  }] }).mockResolvedValue({ data: [{
    id: 9, companyName: 'Beta', projectName: 'Apollo', role: 'USER', email: 'alice@example.com',
  }] });
  acceptInvitation.mockResolvedValue({});
  render(<MemoryRouter><CompanyInvitation /></MemoryRouter>);
  expect(await screen.findByText('Acme')).toBeInTheDocument();
  expect(screen.getByText('Beta')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: 'Accept' })[0]);
  expect(await screen.findByText('Invitation accepted. Your company access is ready.')).toBeInTheDocument();
  expect(acceptInvitation).toHaveBeenCalledWith(8);
  expect(auth.refreshUser).toHaveBeenCalled();
  expect(screen.getByText('Beta')).toBeInTheDocument();
});

it('creates an account and accepts access from the invitation link', async () => {
  window.history.replaceState({}, '', '/company-invite#token=invitation-secret');
  previewInvitation.mockResolvedValue({ data: {
    id: 8, companyName: 'Acme', projectName: 'Atlas', role: 'USER',
    email: 'alice@example.com', status: 'PENDING',
  } });
  claimInvitation.mockResolvedValue({ data: { email: 'alice@example.com' } });
  auth.login.mockResolvedValue({});
  render(<MemoryRouter><CompanyInvitation /></MemoryRouter>);
  await screen.findByRole('heading', { name: 'Join Acme' });
  fireEvent.click(screen.getByRole('button', { name: 'Create an account' }));
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Alice' } });
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Smith' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPassword123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create account and join' }));
  await waitFor(() => expect(claimInvitation).toHaveBeenCalledWith({
    token: 'invitation-secret', firstName: 'Alice', lastName: 'Smith', password: 'StrongPassword123',
  }));
  await waitFor(() => expect(auth.login).toHaveBeenCalledWith('alice@example.com', 'StrongPassword123'));
  expect(sessionStorage.getItem('chronos:company-invite')).toBeNull();
});
