// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LetterRequestReview from './LetterRequestReview';
import { letterRequestAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 2, role: 'ADMIN' } }) }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('requires review and sends the confirmed letter details for approval', async () => {
  letterRequestAPI.getRequest.mockResolvedValue({ data: {
    id: 10, userId: 1, userName: 'Sam Lee', jobTitle: 'Engineer', userJoiningDate: '2023-01-01',
    requestedFullName: 'Sam Lee', requestedJobTitle: 'Engineer', employmentStartDate: '2023-01-01',
    requestType: 'EMPLOYMENT_VERIFICATION', status: 'SUBMITTED', letterPreview: 'Maxwell\nSeptember 28, 2026\nSubject: Employment Verification Letter',
  } });
  letterRequestAPI.approveRequest.mockResolvedValue({ data: {} });
  render(<MemoryRouter initialEntries={['/admin/letter-request/10']}>
    <Routes><Route path="/admin/letter-request/:id" element={<LetterRequestReview />} /></Routes>
  </MemoryRouter>);
  const approve = await screen.findByRole('button', { name: 'Approve' });
  expect(approve.disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Confirmed title'), { target: { value: 'Senior Engineer' } });
  fireEvent.click(screen.getByLabelText('I verified these details for the final PDF'));
  fireEvent.click(approve);
  expect(screen.getByText("Explain changes to the employee's requested details before approval")).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Reason for a correction'), { target: { value: 'Verified with HR' } });
  fireEvent.click(approve);
  await waitFor(() => expect(letterRequestAPI.approveRequest).toHaveBeenCalledWith('10', {
    fullName: 'Sam Lee', jobTitle: 'Senior Engineer', employmentStartDate: '2023-01-01', reviewNote: 'Verified with HR',
  }));
});
