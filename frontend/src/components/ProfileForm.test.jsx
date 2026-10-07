// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ProfileForm from './ProfileForm';

afterEach(cleanup);

it('loads and saves the employee reminder timezone', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, timezone: 'America/New_York' }} onSave={save} />);
  expect(screen.getByLabelText('Timezone').value).toBe('America/New_York');
  fireEvent.change(screen.getByLabelText('Timezone'), { target: { value: 'Europe/London' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ timezone: 'Europe/London' })));
});

it('loads, updates and clears optional contact details in the saved profile', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, role: 'EMPLOYEE', addressLine1: '12 Main St', bloodGroup: 'O+', emergencyContactName: 'Alex', emergencyContactPhone: '+1 555 0100' }} onSave={save} />);
  expect(screen.getByLabelText('Address line 1').value).toBe('12 Main St');
  expect(screen.getByLabelText('Blood group').value).toBe('O+');
  expect(screen.getByLabelText('Contact phone').value).toBe('+1 555 0100');
  fireEvent.change(screen.getByLabelText('Contact name'), { target: { value: '  Taylor  ' } });
  fireEvent.change(screen.getByLabelText('Address line 1'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Blood group'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Personal email'), { target: { value: 'personal@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({
    emergencyContactName: 'Taylor', emergencyContactPhone: '+1 555 0100',
    addressLine1: '', bloodGroup: '', personalEmail: 'personal@example.com',
  })));
});
const profile = { firstName: 'Test', lastName: 'User', jobTitle: 'Engineer', dateOfBirth: '1990-01-01', ssnLast4: '1234' };

it('does not offer or submit company employment edits in the personal profile',async()=>{
  const save=vi.fn().mockResolvedValue({});render(<ProfileForm user={{...profile,role:'EMPLOYEE'}} onSave={save}/>);
  expect(screen.queryByLabelText('Job Title')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Save Profile'}));await waitFor(()=>expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('jobTitle');
  expect(save.mock.calls[0][0]).not.toHaveProperty('employeeId');
  expect(save.mock.calls[0][0]).toHaveProperty('joiningDate',null);
});

it('omits Admin SSN from the form and saved payload, including legacy values', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, platformAdmin:true }} onSave={save} />);
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

it('locks submitted identity fields while keeping contact details editable', () => {
  render(<ProfileForm user={{...profile, profileCompleted:true}} onSave={vi.fn()}/>);
  expect(screen.getByLabelText('First Name').readOnly).toBe(true);
  expect(screen.getByLabelText('Last Name').readOnly).toBe(true);
  expect(screen.getByLabelText('DOB').readOnly).toBe(true);
  expect(screen.getByLabelText('Personal email').readOnly).toBe(false);
});

it('locks submitted gender, race, ethnicity and joining date', () => {
  render(<ProfileForm user={{...profile,profileDetailsSubmitted:true,gender:'Female',race:'Asian',ethnicity:'Not Hispanic or Latino',joiningDate:'2024-01-01'}} onSave={vi.fn()}/>);
  for(const label of ['Gender','Race','Ethnicity','Joining date']) expect(screen.getByLabelText(label).readOnly).toBe(true);
});
