// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import EmployeeReports from './EmployeeReports';
import { employeeReportsAPI } from '../api';
vi.mock('../api', () => ({ employeeReportsAPI: { submit: vi.fn(), list: vi.fn(), detail: vi.fn(), review: vi.fn(), download: vi.fn() } }));
const user = { role: 'EMPLOYEE' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user }) }));
afterEach(() => { cleanup(); vi.useRealTimers(); });
beforeEach(() => { vi.clearAllMocks(); user.role = 'EMPLOYEE'; });
it('explains anonymous retention and submits a receipt without exposing stored data', async () => {
  employeeReportsAPI.submit.mockResolvedValue({ data: { reportId: 'private-reference', status: 'SUBMITTED' } });
  render(<EmployeeReports />);
  fireEvent.change(screen.getByLabelText('Report category'), { target: { value: 'SEXUAL_HARASSMENT' } });
  fireEvent.change(screen.getByLabelText('Report title / subject'), { target: { value: 'Concern' } });
  fireEvent.change(screen.getByLabelText('Incident description'), { target: { value: 'Details' } });
  fireEvent.click(screen.getByLabelText('Report Anonymously'));
  expect(screen.getByText(/will not store a link to your account/)).toBeTruthy();
  expect(screen.getByText(/Original filenames are replaced/)).toBeTruthy();
  fireEvent.click(screen.getByLabelText('I understand what information is retained.'));
  fireEvent.click(screen.getByRole('button', { name: 'Submit confidential report' }));
  await screen.findByText('private-reference');
  const body = employeeReportsAPI.submit.mock.calls[0][0];
  expect(body.get('report').type).toBe('application/json');
  expect(screen.queryByLabelText('Incident description')).toBeNull();
});
it.each(['EMPLOYEE','ADMIN'])('does not load management data for %s', role => {
  user.role = role; render(<EmployeeReports management />);
  expect(employeeReportsAPI.list).not.toHaveBeenCalled();
  expect(screen.getByText(/Only HR Super Admins/)).toBeTruthy();
});
it('loads HR filters and displays anonymous reports without identity', async () => {
  user.role = 'SUPER_ADMIN';
  employeeReportsAPI.list.mockResolvedValue({ data: [{ id: 'one', subject: 'Concern', category: 'SEXUAL_HARASSMENT', status: 'SUBMITTED', submitted_at: '2026-09-21T12:00:00Z' }] });
  employeeReportsAPI.detail.mockResolvedValue({ data: { id: 'one', subject: 'Concern', description: 'Details', anonymous: true, category: 'SEXUAL_HARASSMENT', status: 'SUBMITTED', submitted_at: '2026-09-21T12:00:00Z', attachments: [], history: [] } });
  render(<EmployeeReports management />);
  fireEvent.click(await screen.findByRole('button', { name: 'Review' }));
  await screen.findByText('Submitted By: Anonymous');
  fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'SEXUAL_HARASSMENT' } });
  await waitFor(() => expect(employeeReportsAPI.list).toHaveBeenLastCalledWith({ category: 'SEXUAL_HARASSMENT', page: 0 }));
  fireEvent.change(screen.getByLabelText('Search Report ID'), { target: { value: 'abcdef' } });
  fireEvent.change(screen.getByLabelText('Submitted by'), { target: { value: 'false' } });
  await waitFor(() => expect(employeeReportsAPI.list).toHaveBeenLastCalledWith({ category: 'SEXUAL_HARASSMENT', reportId: 'abcdef', anonymous: 'false', page: 0 }));
});

it('automatically discovers new reports without manual synchronization', async () => {
  vi.useFakeTimers(); user.role = 'SUPER_ADMIN';
  employeeReportsAPI.list.mockResolvedValueOnce({ data: [] }).mockResolvedValue({ data: [{ id: 'new-report', subject: 'New case', category: 'SAFETY_CONCERN', anonymous: true, status: 'SUBMITTED', submitted_at: '2026-09-21T12:00:00Z' }] });
  render(<EmployeeReports management />);
  await act(async () => { await Promise.resolve(); });
  expect(screen.getByText('No reports match these filters.')).toBeTruthy();
  await act(async () => { await vi.advanceTimersByTimeAsync(15000); });
  expect(screen.getByText('New case')).toBeTruthy();
  expect(employeeReportsAPI.list).toHaveBeenCalledTimes(2);
});

it('saves internal notes and renders status, administrator, and time in the tracker', async () => {
  user.role = 'SUPER_ADMIN';
  const report = { id: 'case', subject: 'Case subject', anonymous: true, category: 'SAFETY_CONCERN', status: 'SUBMITTED', submitted_at: '2026-09-21T12:00:00Z', attachments: [], history: [] };
  employeeReportsAPI.list.mockResolvedValue({ data: [report] });
  employeeReportsAPI.detail.mockResolvedValueOnce({ data: report }).mockResolvedValue({ data: { ...report, status: 'UNDER_REVIEW', history: [{ id: 1, action: 'SUBMITTED → UNDER_REVIEW', status: 'UNDER_REVIEW', note: 'Confidential interview', first_name: 'HR', last_name: 'Reviewer', created_at: '2026-09-21T13:00:00Z' }] } });
  employeeReportsAPI.review.mockResolvedValue({});
  render(<EmployeeReports management />);
  fireEvent.click(await screen.findByRole('button', { name: 'Review' }));
  await screen.findByRole('heading', { name: 'Report Tracker' });
  fireEvent.change(screen.getByLabelText('Internal note'), { target: { value: 'Confidential interview' } });
  fireEvent.change(screen.getAllByLabelText('Status')[1], { target: { value: 'UNDER_REVIEW' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save review' }));
  await screen.findByText(/Updated by: HR Reviewer/);
  expect(employeeReportsAPI.review).toHaveBeenCalledWith('case', { status: 'UNDER_REVIEW', note: 'Confidential interview', actionsTaken: '', resolution: '' });
  expect(screen.getByText('Confidential interview')).toBeTruthy();
});
