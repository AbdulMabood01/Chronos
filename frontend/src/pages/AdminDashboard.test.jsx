// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminDashboard from './AdminDashboard';
import { letterRequestAPI, timesheetAPI, vacationAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'SUPER_ADMIN' } }) }));

beforeEach(() => {
  vi.resetAllMocks();
  timesheetAPI.getPendingProjectSubmissions.mockResolvedValue({ data: [] });
  letterRequestAPI.getPendingRequests.mockResolvedValue({ data: [] });
  vacationAPI.getPendingRequests.mockResolvedValue({
    data: [{
      id: 7,
      userName: 'Alice Smith',
      startDate: '2026-09-21',
      endDate: '2026-09-22',
      hours: 16,
      vacationType: 'VACATION',
      submittedAt: '2026-09-15T12:00:00',
      status: 'SUBMITTED',
    }],
  });
});

afterEach(cleanup);

it('shows the backend reason when vacation approval is blocked', async () => {
  vacationAPI.approveVacation.mockRejectedValue({
    response: { data: { message: 'Vacation conflicts with submitted or finalized hours; reopen the timesheet first' } },
  });

  render(<MemoryRouter><AdminDashboard /></MemoryRouter>);

  fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));

  await waitFor(() => expect(screen.getByText(/Vacation conflicts with submitted or finalized hours/)).toBeTruthy());
});
