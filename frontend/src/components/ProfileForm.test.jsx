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

it('updates required contacts while allowing optional address and blood group to clear', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, role: 'EMPLOYEE', addressLine1: '12 Main St', bloodGroup: 'O+', emergencyContactName: 'Alex', emergencyContactPhone: '+1 555 0100' }} onSave={save} />);
  expect(screen.getByLabelText('Address line 1').value).toBe('12 Main St');
  expect(screen.getByLabelText('Blood group').value).toBe('O+');
  expect(screen.getByLabelText('Contact phone').value).toBe('+1 555 0100');
  fireEvent.change(screen.getByLabelText('Contact name'), { target: { value: '  Taylor  ' } });
  fireEvent.change(screen.getByLabelText('Address line 2 (optional)'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Blood group'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Personal email'), { target: { value: 'personal@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({
    emergencyContactName: 'Taylor', emergencyContactPhone: '+1 555 0100',
    addressLine1: '12 Main St', addressLine2: '', bloodGroup: '', personalEmail: 'personal@example.com',
  })));
});
const profile = { firstName: 'Test', lastName: 'User', jobTitle: 'Engineer', dateOfBirth: '1990-01-01', ssnLast4: '1234',
  gender: 'Female', race: 'Asian', ethnicity: 'Not Hispanic or Latino', joiningDate: '2024-01-01',
  phoneNumber: '+1 555 0101', personalEmail: 'test@example.com', addressLine1: '12 Main St', addressLine2: 'Unit 3',
  city: 'Chicago', stateProvince: 'Illinois', postalCode: '60601', country: 'USA',
  emergencyContactName: 'Alex', emergencyContactRelationship: 'Sibling', emergencyContactPhone: '+1 555 0100', emergencyContactEmail: 'alex@example.com',
};

it('does not offer or submit company employment edits in the personal profile',async()=>{
  const save=vi.fn().mockResolvedValue({});render(<ProfileForm user={{...profile,role:'EMPLOYEE'}} onSave={save}/>);
  expect(screen.queryByLabelText('Job Title')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Save Profile'}));await waitFor(()=>expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('jobTitle');
  expect(save.mock.calls[0][0]).not.toHaveProperty('employeeId');
  expect(save.mock.calls[0][0]).toHaveProperty('joiningDate','2024-01-01');
});

it('omits Admin SSN from the form and saved payload, including legacy values', async () => {
  const save = vi.fn().mockResolvedValue({});
  render(<ProfileForm user={{ ...profile, platformAdmin:true }} onSave={save} />);
  expect(screen.queryByLabelText('4 Digits of SSN')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Save Profile' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('ssnLast4');
});

it('omits SSN for employees and removes the profile notes', async () => {
  const save=vi.fn().mockResolvedValue({});render(<ProfileForm user={{...profile,profileCompleted:true}} onSave={save}/>);
  expect(screen.queryByLabelText('4 Digits of SSN')).toBeNull();
  expect(screen.queryByText(/Your company administrator manages/)).toBeNull();
  expect(screen.queryByText(/Your submitted name and date of birth are locked/)).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Save Profile'}));await waitFor(()=>expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('ssnLast4');
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
  for(const label of ['Gender','Race','Ethnicity']) expect(screen.getByLabelText(label).disabled).toBe(true);
  expect(screen.getByLabelText('Joining date').readOnly).toBe(true);
});

it('offers demographic dropdowns and marks contact and address fields required', () => {
  render(<ProfileForm user={profile} onSave={vi.fn()} />);
  expect([...screen.getByRole('combobox', {name:'Gender'}).options].map(o=>o.value)).toEqual(['', 'Male', 'Female', 'Other']);
  expect(screen.getByRole('combobox', {name:'Race'}).options.length).toBeGreaterThan(5);
  expect(screen.getByRole('combobox', {name:'Ethnicity'}).options.length).toBe(5);
  for (const label of ['Gender','Race','Ethnicity','Joining date','Phone number','Personal email','Address line 1','City','State / Province','Postal code','Country','Contact name','Relationship','Contact phone','Contact email']) expect(screen.getByLabelText(label).required).toBe(true);
  expect(screen.getByLabelText('Address line 2 (optional)').required).toBe(false);
});
it('rejects missing required details and preserves entered values', () => {
  const save=vi.fn();render(<ProfileForm user={{...profile, emergencyContactPhone:''}} onSave={save}/>);
  fireEvent.submit(screen.getByRole('button', {name:'Save Profile'}).closest('form'));
  expect(screen.getByText('Contact phone is required.')).toBeTruthy();expect(save).not.toHaveBeenCalled();
  expect(screen.getByLabelText('Phone number').value).toBe(profile.phoneNumber);
});
it('allows completion of previously submitted blank personal details', () => {
  render(<ProfileForm user={{...profile,profileDetailsSubmitted:true,gender:null,joiningDate:null}} onSave={vi.fn()}/>);
  expect(screen.getByLabelText('Gender').disabled).toBe(false);expect(screen.getByLabelText('Joining date').readOnly).toBe(false);
  expect(screen.getByLabelText('Race').disabled).toBe(true);
});

it('allows employee contact updates while identity stays locked after saving',()=>{
 render(<ProfileForm user={{...profile,profileCompleted:true,profileDetailsSubmitted:true}} onSave={vi.fn()}/>);
 expect(screen.getByLabelText('First Name').readOnly).toBe(true);expect(screen.getByLabelText('Gender').disabled).toBe(true);
 expect(screen.getByLabelText('Phone number').readOnly).toBe(false);expect(screen.getByLabelText('Contact phone').readOnly).toBe(false);
});
it('opens locked personal fields only while an approved correction is available',()=>{
 render(<ProfileForm user={{...profile,profileCompleted:true,profileDetailsSubmitted:true,profileCorrectionOpen:true}} onSave={vi.fn()}/>);
 expect(screen.getByLabelText('First Name').readOnly).toBe(false);expect(screen.getByLabelText('Gender').disabled).toBe(false);
});
