// @vitest-environment jsdom
import React from 'react';
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
});
