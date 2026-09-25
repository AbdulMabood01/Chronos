// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import EmailAlertPreferences from './EmailAlertPreferences';
import { notificationAPI } from '../api';
vi.mock('../api', () => ({ notificationAPI: { getEmailPreferences: vi.fn(), saveEmailPreferences: vi.fn() } }));
const defaults = { enabled: true, announcements: true, timesheets: true, vacation: true, letters: true, reports: true, feedback: true, performance: true };
beforeEach(() => {
  vi.resetAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  notificationAPI.getEmailPreferences.mockResolvedValue({ data: { ...defaults } });
  notificationAPI.saveEmailPreferences.mockImplementation(async data => ({ data }));
});
afterEach(cleanup);
async function open() {
  render(<EmailAlertPreferences />);
  fireEvent.click(screen.getByRole('button', { name: 'Email alerts' }));
  return screen.findByRole('switch', { name: 'Enable email alerts' });
}
it('saves category choices and preserves them when the master switch is off', async () => {
  const master = await open();
  expect(screen.getAllByRole('switch')).toHaveLength(8);
  fireEvent.click(screen.getByRole('switch', { name: 'Feedback' }));
  fireEvent.click(master);
  expect(screen.getByRole('switch', { name: 'Feedback' }).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Save preferences' }));
  await screen.findByText('Email preferences saved.');
  expect(notificationAPI.saveEmailPreferences).toHaveBeenCalledWith({ ...defaults, enabled: false, feedback: false });
  fireEvent.click(master);
  expect(screen.getByRole('switch', { name: 'Feedback' }).getAttribute('aria-checked')).toBe('false');
});
it('loads persisted settings on reopening and restores trigger focus', async () => {
  notificationAPI.getEmailPreferences.mockResolvedValue({ data: { ...defaults, vacation: false } });
  await open();
  expect(screen.getByRole('switch', { name: 'Vacation' }).getAttribute('aria-checked')).toBe('false');
  fireEvent.click(screen.getByRole('button', { name: 'Close', exact: true }));
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Email alerts' }));
  fireEvent.click(document.activeElement);
  await screen.findByRole('switch', { name: 'Vacation' });
  expect(notificationAPI.getEmailPreferences).toHaveBeenCalledTimes(2);
});
it('keeps failed saves visible without claiming success', async () => {
  notificationAPI.saveEmailPreferences.mockRejectedValue(new Error('offline'));
  await open();
  fireEvent.click(screen.getByRole('button', { name: 'Save preferences' }));
  expect((await screen.findByRole('alert')).textContent).toContain('have not been saved');
  expect(screen.queryByText('Email preferences saved.')).toBeNull();
});
it('does not overwrite settings when loading fails and offers a retry', async () => {
  notificationAPI.getEmailPreferences.mockRejectedValueOnce(new Error('offline'));
  render(<EmailAlertPreferences />);
  fireEvent.click(screen.getByRole('button', { name: 'Email alerts' }));
  await screen.findByRole('alert');
  expect(screen.queryByRole('button', { name: 'Save preferences' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByRole('switch', { name: 'Enable email alerts' });
});
