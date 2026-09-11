// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ProjectManagement from './ProjectManagement';
import { projectAPI, userAPI } from '../api';
vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: currentUser }) }));
const currentUser = { id: 1, role: 'SUPER_ADMIN' };
const project = { id: 10, code: 'P1', name: 'Atlas', status: 'ACTIVE', projectManagerId: 3, projectManagerHoursApproverId: 4,
  assignments: [{ id: 5, userId: 3, userName: 'Employee', isActive: true, startDate: '2026-09-01', endDate: '2026-09-30', billRate: 10 }] };

beforeEach(() => {
  vi.resetAllMocks();
  currentUser.role = 'SUPER_ADMIN';
  projectAPI.getProjects.mockResolvedValue({ data: [project] });
  userAPI.getAllUsers.mockResolvedValue({ data: [] });
  projectAPI.getHoursDashboard.mockResolvedValue({ data: [{ projectId: 10, employees: [{ userId: 3, userName: 'Employee', plannedHours: 40, timesheetId: 20 }] }] });
});
afterEach(cleanup);

async function selectProject() {
  render(<ProjectManagement />);
  fireEvent.change(await screen.findByLabelText('Project Name'), { target: { value: 'Atlas' } });
  fireEvent.click(await screen.findByRole('button', { name: /Atlas/ }));
}

it('lets SuperAdmin read all project tabs without write controls', async () => {
  await selectProject();
  expect(screen.queryByRole('button', { name: 'New Project' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.getByLabelText('Project Code').closest('fieldset').disabled).toBe(true);
  expect(screen.queryByRole('button', { name: 'Save Project' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  expect(screen.queryByRole('button', { name: 'Assign' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'End' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  const plan = screen.getByLabelText('Planned hours for Employee');
  expect(plan.closest('fieldset').disabled).toBe(true);
  fireEvent.blur(plan);
  expect(projectAPI.updatePlannedHours).not.toHaveBeenCalled();
  expect(screen.queryByRole('link', { name: 'Open' })).toBeNull();
  expect(screen.queryByRole('columnheader', { name: 'Action' })).toBeNull();
});

it('retains Admin project editing', async () => {
  currentUser.role = 'ADMIN';
  await selectProject();
  expect(screen.getByRole('button', { name: 'New Project' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.getByLabelText('Project Code').closest('fieldset').disabled).toBe(false);
  expect(screen.getByRole('button', { name: 'Save Project' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
});

it('sums all resource assignments instead of the manual allocation or monthly plan', async () => {
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, totalAllocatedHours: 999,
    assignments: [
      { ...project.assignments[0], plannedHours: 40 },
      { id: 6, userId: 4, userName: 'Second resource', isActive: false, plannedHours: 60 },
      { id: 7, userId: 5, userName: 'Unplanned resource', isActive: true, plannedHours: null },
    ] }] });
  await selectProject();
  expect(screen.getByRole('img', { name: 'Total project hours: 100.00, summed across all project resources' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.queryByLabelText('Total Project Hours')).toBeNull();
});

it('saves resource hours for the project without a monthly period', async () => {
  currentUser.role = 'ADMIN';
  projectAPI.updatePlannedHours.mockResolvedValue({ data: project });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  const input = screen.getByLabelText('Planned hours for Employee');
  fireEvent.change(input, { target: { value: '80' } });
  fireEvent.blur(input);
  await waitFor(() => expect(projectAPI.updatePlannedHours).toHaveBeenCalledWith(10, 3, '80'));
});
