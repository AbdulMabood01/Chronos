// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import Settings from './Settings';
import { settingsAPI } from '../api';
vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { role: 'ADMIN' } }) }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
it('previews the annual policy before applying it and preserves overrides', async () => {
  settingsAPI.getAllSettings.mockResolvedValue({ data: [] });
  settingsAPI.previewLeaveDefaults.mockResolvedValue({ data: { year: new Date().getFullYear(), vacationDays: 15, sickDays: 5, bereavementDays: 3, newAllowances: 2, policyAllowances: 1, overrides: 1 } });
  settingsAPI.applyLeaveDefaults.mockResolvedValue({ data: { updated: 4 } });
  render(<Settings />);
  const button = await screen.findByRole('button', { name: 'Preview policy' });
  expect(settingsAPI.applyLeaveDefaults).not.toHaveBeenCalled();
  fireEvent.click(button);
  expect(await screen.findByText('Personal overrides preserved')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /Apply .* policy/ }));
  expect(await screen.findByRole('status')).toHaveProperty('textContent', expect.stringContaining('Personal overrides and extra grants were kept'));
});
