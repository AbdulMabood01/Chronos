// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LetterRequestReview from './LetterRequestReview';
import { letterRequestAPI,companyLetterRequestAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 2, role: 'ADMIN' } }) }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('requires review and sends the confirmed letter details for approval', async () => {
  companyLetterRequestAPI.mockReturnValue(letterRequestAPI);
  const employer={id:'e',name:'Employer',address:'100 Street',email:'hr@example.com'},hr={id:'h',name:'HR Name',title:'HR Director'};
  const configuration={version:3,employers:[employer],signatories:[hr],templates:[{type:'EMPLOYMENT_VERIFICATION',enabled:true,employerId:'e',signatoryId:'h',body:'Employee {{employee_name}}',signatureRequired:false}]};
  letterRequestAPI.configuration=vi.fn().mockResolvedValue({data:configuration});
  letterRequestAPI.previewReview=vi.fn().mockResolvedValue({data:new Blob(['PDF'])});
  URL.createObjectURL=vi.fn().mockReturnValue('blob:test');URL.revokeObjectURL=vi.fn();
  letterRequestAPI.getRequest.mockResolvedValue({ data: {
    id: 10,companyId:12,version:0, userId: 1, userName: 'Sam Lee', jobTitle: 'Engineer', userJoiningDate: '2023-01-01',
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
  expect(screen.getByLabelText('I verified these details for the final PDF').disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Reason for a correction'), { target: { value: 'Verified with HR' } });
  fireEvent.click(screen.getByRole('button',{name:'Preview final letter'}));
  await screen.findByTitle('Final letter PDF preview');
  fireEvent.click(screen.getByLabelText('I verified these details for the final PDF'));
  fireEvent.click(approve);
  await waitFor(() => expect(letterRequestAPI.approveRequest).toHaveBeenCalledWith('10', expect.objectContaining({
    fullName: 'Sam Lee', jobTitle: 'Senior Engineer', employmentStartDate: '2023-01-01', reviewNote: 'Verified with HR',
    configurationVersion:3,letter:expect.objectContaining({name:'Employer',hrName:'HR Name'}),
  }),0));
});

vi.mock('../CompanyContext',()=>({useCompany:()=>({currentCompany:{id:12},companyCapabilities:{canReviewLeaveAndLetters:true}})}));
