// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import Settings from './Settings';
import { settingsAPI } from '../api';
vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { role: 'SUPER_ADMIN' } }) }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
it('requires confirmation before applying saved leave defaults', async () => {
  settingsAPI.getAllSettings.mockResolvedValue({ data: [] });
  settingsAPI.applyLeaveDefaults.mockResolvedValue({ data: { updated: 4 } });
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
  render(<Settings />);
  const button = await screen.findByRole('button', { name: 'Apply Defaults to All Employees' });
  fireEvent.click(button);
  expect(settingsAPI.applyLeaveDefaults).not.toHaveBeenCalled();
  confirm.mockReturnValue(true);
  fireEvent.click(button);
  expect(await screen.findByRole('status')).toHaveProperty('textContent', expect.stringContaining('Defaults applied to 4 employees'));
});
