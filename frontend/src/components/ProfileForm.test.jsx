// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ProfileForm from './ProfileForm';

afterEach(cleanup);
const profile = { firstName: 'Test', lastName: 'User', jobTitle: 'Engineer', dateOfBirth: '1990-01-01', ssnLast4: '1234' };

it('omits SuperAdmin SSN from the form and saved payload, including legacy values', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, role: 'SUPER_ADMIN' }} onSave={save} />);
  expect(screen.queryByLabelText('4 Digits of SSN')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('ssnLast4');
});

it('keeps SSN available for employees', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, role: 'EMPLOYEE' }} onSave={save} />);
  fireEvent.change(screen.getByLabelText('4 Digits of SSN'), { target: { value: '5678' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ ssnLast4: '5678' })));
});
