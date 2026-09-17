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
  assignments: [{ id: 5, userId: 3, userName: 'Employee', isActive: true, startDate: '2026-09-01', endDate: '2026-09-30', billRate: 10, plannedHours: 40 }] };

beforeEach(() => {
  vi.resetAllMocks();
  // jsdom does not implement the browser's modal dialog methods.
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  currentUser.role = 'SUPER_ADMIN';
  currentUser.canReviewProjects = false;
  localStorage.clear();
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

it('shows editable assigned project hours from the assignment instead of monthly dashboard plans', async () => {
  currentUser.role = 'ADMIN';
  projectAPI.getHoursDashboard.mockResolvedValue({ data: [{ projectId: 10, employees: [{ userId: 3, userName: 'Employee', plannedHours: 0, totalLoggedHours: 12, status: 'SUBMITTED' }] }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  const input = screen.getByLabelText('Planned hours for Employee');
  expect(input.value).toBe('40');
  expect(input.closest('fieldset').disabled).toBe(false);
  expect(screen.getByText('12.00')).toBeTruthy();
});

it('persists favorites from project cards and sorts favorite projects first', async () => {
  currentUser.role = 'ADMIN';
  projectAPI.getProjects.mockResolvedValue({ data: [
    { ...project, id: 10, code: 'ATLAS', name: 'Atlas' },
    { ...project, id: 11, code: 'ZEBRA', name: 'Zebra' },
  ] });
  render(<ProjectManagement />);
  await screen.findByText('Atlas');
  const buttons = screen.getAllByRole('button', { name: 'Add to favorites' });
  fireEvent.click(buttons[1]);
  expect(JSON.parse(localStorage.getItem('chronos:project-favorites:1'))).toEqual(['11']);
  const cards = document.querySelectorAll('.pc-project-card');
  expect(cards[0].textContent).toContain('Zebra');
  expect(screen.getByRole('button', { name: 'Remove from favorites' })).toBeTruthy();
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

it('creates a project with routing only and leaves onboarding to Team', async () => {
  currentUser.role = 'ADMIN';
  userAPI.getAllUsers.mockResolvedValue({ data: [
    { id: 3, firstName: 'Project', lastName: 'Manager', isActive: true, hourlyRate: 70 },
    { id: 4, firstName: 'Hours', lastName: 'Approver', isActive: true, hourlyRate: 80 },
  ] });
  projectAPI.createProject.mockResolvedValue({ data: { ...project, id: 11, assignments: [] } });
  render(<ProjectManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'New Project' }));
  fireEvent.change(screen.getByLabelText('Project Code'), { target: { value: 'P2' } });
  fireEvent.change(screen.getAllByLabelText('Project Name')[1], { target: { value: 'New Project' } });
  fireEvent.change(screen.getByLabelText('Project Manager'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('PM Hours Approver'), { target: { value: '4' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create Project' }));
  await waitFor(() => expect(projectAPI.createProject).toHaveBeenCalledWith(expect.objectContaining({ projectManagerId: 3, projectManagerHoursApproverId: 4 })));
  expect(screen.queryByLabelText('PM Start Date')).toBeNull();
  expect(await screen.findByText('Add a team member')).toBeTruthy();
  expect(screen.getByLabelText('Employee').value).toBe('3');

});

it('allows the project manager to also be the PM hours approver', async () => {
  currentUser.role = 'ADMIN';
  userAPI.getAllUsers.mockResolvedValue({ data: [
    { id: 3, firstName: 'Project', lastName: 'Manager', isActive: true, hourlyRate: 70 },
  ] });
  projectAPI.createProject.mockResolvedValue({ data: { ...project, id: 11, projectManagerId: 3, projectManagerHoursApproverId: 3, assignments: [] } });
  render(<ProjectManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'New Project' }));
  fireEvent.change(screen.getByLabelText('Project Code'), { target: { value: 'P2' } });
  fireEvent.change(screen.getAllByLabelText('Project Name')[1], { target: { value: 'New Project' } });
  fireEvent.change(screen.getByLabelText('Project Manager'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('PM Hours Approver'), { target: { value: '3' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create Project' }));
  await waitFor(() => expect(projectAPI.createProject).toHaveBeenCalledWith(expect.objectContaining({ projectManagerId: 3, projectManagerHoursApproverId: 3 })));
  expect(screen.queryByText('Project Manager cannot approve their own hours')).toBeNull();
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

it('restores saved resource hours when the project assignment has no hours', async () => {
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, assignments: [{ ...project.assignments[0], plannedHours: null }] }] });
  projectAPI.getHoursDashboard.mockResolvedValue({ data: [{ projectId: 10, totalLoggedHours: 25, approvedHours: 10, submittedHours: 8, rejectedHours: 2, employees: [{ userId: 3, plannedHours: 80 }] }] });
  await selectProject();
  expect(screen.getByRole('img', { name: 'Total project hours: 80.00, summed across all project resources' })).toBeTruthy();
  const legend = document.querySelector('.project-chart-legend');
  expect(legend.textContent).toBe('Approved10.00Submitted8.00Rejected2.00Draft5.00Remaining55.00');
  expect(screen.getByText('Project hours used')).toBeTruthy();
  expect(document.querySelector('.pc-plan-progress p').textContent).not.toContain('logged in');
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  expect(screen.getByLabelText('Planned hours for Employee').value).toBe('80');
});

it('requires all onboarding fields and sends hours with the assignment', async () => {
  currentUser.role = 'ADMIN';
  userAPI.getAllUsers.mockResolvedValue({ data: [{ id: 9, firstName: 'New', lastName: 'Member', isActive: true }] });
  projectAPI.assignEmployee.mockResolvedValue({ data: project });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  fireEvent.change(screen.getByLabelText('Employee'), { target: { value: '9' } });
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-01' } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-09-30' } });
  fireEvent.change(screen.getByLabelText('Bill rate'), { target: { value: '50' } });
  expect(screen.getByRole('button', { name: 'Assign' }).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Assigned hours'), { target: { value: '80' } });
  fireEvent.click(screen.getByRole('button', { name: 'Assign' }));
  await waitFor(() => expect(projectAPI.assignEmployee).toHaveBeenCalledWith(10, '9', '2026-09-01', '2026-09-30', '50', '80'));
});

it('confirms offboarding and removes the Hours status column', async () => {
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, projectManagerId: 8 }] });
  currentUser.role = 'ADMIN';
  projectAPI.removeEmployee.mockResolvedValue({ data: project });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  expect(screen.queryByRole('columnheader', { name: 'Status' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  fireEvent.click(screen.getByRole('button', { name: 'Offboard Employee' }));
  expect(projectAPI.removeEmployee).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  expect(screen.queryByRole('dialog')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Offboard Employee' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirm offboarding' }));
  await waitFor(() => expect(projectAPI.removeEmployee).toHaveBeenCalledWith(10, 3, undefined));
});

it('freezes ended hours and calculates remaining from lifetime approvals', async () => {
  currentUser.role = 'ADMIN';
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, assignments: [{ ...project.assignments[0], isActive: false, plannedHours: 35, approvedHoursToDate: 35 }] }] });
  await selectProject();
  expect(screen.getByRole('img', { name: /Total project hours: 35.00/ })).toBeTruthy();
  expect(document.querySelector('.project-chart-legend').textContent).toContain('Approved35.00');
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  expect(screen.getByLabelText('Planned hours for Employee').disabled).toBe(true);
  expect(screen.getByRole('columnheader', { name: 'Remaining Hours' })).toBeTruthy();
  expect(screen.getByLabelText('Planned hours for Employee').closest('tr').lastElementChild.textContent).toBe('0.00');
});

it('blocks pending approval and requires a secondary PM', async () => {
  currentUser.role = 'ADMIN';
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, assignments: [{ ...project.assignments[0], pendingApproval: true }] }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  fireEvent.click(screen.getByRole('button', { name: 'Offboard Employee' }));
  expect(screen.getByText('Please approve or reject pending hours before offboarding this employee.')).toBeTruthy();
  expect(screen.getByRole('combobox', { name: /Secondary PM/ })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Confirm offboarding' }).disabled).toBe(true);
});

it('uses the shared project workspace for a project manager with read-only actions', async () => {
  currentUser.role = 'EMPLOYEE'; currentUser.canReviewProjects = true;
  await selectProject();
  expect(screen.queryByRole('button', { name: 'New Project' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  expect(screen.getByLabelText('Start date for Employee').closest('fieldset').disabled).toBe(true);
  expect(userAPI.getAllUsers).not.toHaveBeenCalled();
});
