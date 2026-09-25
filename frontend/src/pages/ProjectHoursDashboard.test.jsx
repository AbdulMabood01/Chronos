// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProjectHoursDashboard from './ProjectHoursDashboard';
import { projectAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: currentUser }) }));

const currentUser = { id: 3, role: 'EMPLOYEE', canReviewProjects: true };
const dashboard = [{
  projectId: 10,
  projectCode: 'P1',
  projectName: 'Atlas',
  projectManagerName: 'Employee Manager',
  plannedHours: 40,
  totalLoggedHours: 8,
  submittedHours: 0,
  approvedHours: 8,
  employees: [{ userId: 4, userName: 'Employee', email: 'employee@example.test', plannedHours: 40, totalLoggedHours: 8, submittedHours: 0, approvedHours: 8, rejectedHours: 0, status: 'APPROVED' }],
}];

beforeEach(() => {
  vi.resetAllMocks();
  currentUser.role = 'EMPLOYEE';
  currentUser.canReviewProjects = true;
  projectAPI.getHoursDashboard.mockResolvedValue({ data: dashboard });
});

afterEach(cleanup);

function renderDashboard() {
  render(<MemoryRouter><ProjectHoursDashboard /></MemoryRouter>);
}

it('lets employee project reviewers view project hours without editing planned hours', async () => {
  renderDashboard();
  const input = await screen.findByLabelText('Planned hours for Employee');
  expect(input.disabled).toBe(true);
  fireEvent.blur(input);
  expect(projectAPI.updatePlannedHours).not.toHaveBeenCalled();
});

it('lets Project Admin users edit planned project hours', async () => {
  currentUser.role = 'PROJECT_ADMIN';
  projectAPI.updatePlannedHours.mockResolvedValue({});
  renderDashboard();
  const input = await screen.findByLabelText('Planned hours for Employee');
  expect(input.disabled).toBe(false);
  fireEvent.change(input, { target: { value: '48' } });
  fireEvent.blur(input);
  await waitFor(() => expect(projectAPI.updatePlannedHours).toHaveBeenCalledWith(10, 4, '48', expect.any(Number), expect.any(Number)));
});
