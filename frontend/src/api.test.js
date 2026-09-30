// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import axios from 'axios';
import apiClient from './api';

afterEach(() => { vi.restoreAllMocks(); window.__chronosConnectionLost = false; });

it('shows safe API messages and marks sessions expired', async () => {
  const expired = vi.fn();
  window.addEventListener('chronos:session-expired', expired, { once: true });
  await expect(apiClient.get('/private', { adapter: config => Promise.reject(new axios.AxiosError(
    'database details', 'ERR_BAD_RESPONSE', config, null,
    { status: 401, data: { message: 'internal token details' }, config, headers: {} }
  )) })).rejects.toMatchObject({ userMessage: 'Your session has expired. Please sign in again.' });
  expect(expired).toHaveBeenCalledOnce();
  await expect(apiClient.get('/broken', { adapter: config => Promise.reject(new axios.AxiosError(
    'SQL exception', 'ERR_BAD_RESPONSE', config, null,
    { status: 500, data: { message: 'SQL exception' }, config, headers: {} }
  )) })).rejects.toMatchObject({ message: 'Something went wrong on our side. Please try again.' });
});

it('announces connection loss and recovery', async () => {
  const lost = vi.fn(); const restored = vi.fn();
  window.addEventListener('chronos:connection-lost', lost);
  window.addEventListener('chronos:connection-restored', restored);
  await expect(apiClient.get('/offline', { adapter: config => Promise.reject(new axios.AxiosError('Network Error', 'ERR_NETWORK', config)) }))
    .rejects.toMatchObject({ userMessage: 'Unable to connect. Check your internet connection and try again.' });
  expect(lost).toHaveBeenCalledOnce();
  await apiClient.get('/online', { recoveryProbe: true, adapter: config => Promise.resolve({ status: 200, data: {}, config, headers: {} }) });
  expect(restored).toHaveBeenCalledOnce();
  window.removeEventListener('chronos:connection-lost', lost);
  window.removeEventListener('chronos:connection-restored', restored);
});

it('distinguishes request timeout from loss of connectivity', async () => {
  const lost = vi.fn();
  window.addEventListener('chronos:connection-lost', lost);
  await expect(apiClient.get('/slow', { adapter: config => Promise.reject(new axios.AxiosError('timeout', 'ECONNABORTED', config)) }))
    .rejects.toMatchObject({ userMessage: 'The request is taking longer than expected. Please try again.' });
  expect(lost).not.toHaveBeenCalled();
  window.removeEventListener('chronos:connection-lost', lost);
});

it.each([[403, "You don't have permission to perform this action."], [404, 'The requested item was not found.']])
  ('maps HTTP %i to a safe message', async (status, expected) => {
    await expect(apiClient.get('/failure', { adapter: config => Promise.reject(new axios.AxiosError(
      'internal exception', 'ERR_BAD_RESPONSE', config, null,
      { status, data: { message: 'database table name' }, config, headers: {} }
    )) })).rejects.toMatchObject({ userMessage: expected });
  });

it('shows leave validation details without exposing other request errors', async () => {
  const rejectWithMessage = (message) => config => Promise.reject(new axios.AxiosError(
    'request failed', 'ERR_BAD_REQUEST', config, null,
    { status: 400, data: { message }, config, headers: {} }
  ));
  await expect(apiClient.post('/approvals/vacation/12/approve', {}, {
    adapter: rejectWithMessage('Insufficient vacation balance for 2026.')
  })).rejects.toMatchObject({ userMessage: 'Insufficient vacation balance for 2026.' });
  await expect(apiClient.post('/settings/leave-defaults/apply', {}, {
    adapter: rejectWithMessage('Leave defaults changed. Refresh the preview and try again.')
  })).rejects.toMatchObject({ userMessage: 'Leave defaults changed. Refresh the preview and try again.' });
  await expect(apiClient.post('/another-action', {}, {
    adapter: rejectWithMessage('database column name')
  })).rejects.toMatchObject({ userMessage: 'Please check your entries and try again.' });
});

it('explains a closed timesheet month without exposing arbitrary backend errors', async () => {
  const rejectWithMessage = message => config => Promise.reject(new axios.AxiosError(
    'request failed', 'ERR_BAD_REQUEST', config, null,
    { status: 400, data: { message }, config, headers: {} }
  ));
  await expect(apiClient.post('/timesheets/4/time-entries', {}, {
    adapter: rejectWithMessage('This timesheet month is closed; request an opening from your Project Admin')
  })).rejects.toMatchObject({ userMessage: 'This timesheet month is closed. Request an opening from your Project Admin.' });
  await expect(apiClient.post('/timesheets/4/time-entries', {}, {
    adapter: rejectWithMessage('database column name')
  })).rejects.toMatchObject({ userMessage: 'Please check your entries and try again.' });
});

it('shows actionable password errors while hiding unexpected server details', async () => {
  const rejectWithMessage = message => config => Promise.reject(new axios.AxiosError(
    'request failed', 'ERR_BAD_REQUEST', config, null,
    { status: 400, data: { message }, config, headers: {} }
  ));
  await expect(apiClient.post('/auth/reset-password/validate', { token: 'expired' }, {
    publicAuth: true,
    adapter: rejectWithMessage('This reset link is invalid or expired. Request a new reset link.')
  })).rejects.toMatchObject({ userMessage: 'This reset link is invalid or expired. Request a new reset link.' });
  await expect(apiClient.post('/auth/reset-password', {}, {
    publicAuth: true,
    adapter: rejectWithMessage('Choose a password different from your current password.')
  })).rejects.toMatchObject({ userMessage: 'Choose a password different from your current password.' });
  await expect(apiClient.post('/auth/reset-password', {}, {
    publicAuth: true,
    adapter: rejectWithMessage('internal database details')
  })).rejects.toMatchObject({ userMessage: 'Please check your entries and try again.' });
});

it('shows an administrator lock message only for locked sign-in responses', async () => {
  const locked = config => Promise.reject(new axios.AxiosError(
    'request failed', 'ERR_BAD_RESPONSE', config, null,
    { status: 423, data: { message: 'internal details' }, config, headers: {} }
  ));
  await expect(apiClient.post('/auth/login', {}, { publicAuth: true, adapter: locked }))
    .rejects.toMatchObject({ userMessage: 'Your account is locked. Contact an administrator.' });
  await expect(apiClient.post('/another-action', {}, { adapter: locked }))
    .rejects.toMatchObject({ userMessage: 'Unable to complete the request. Please try again.' });
});
