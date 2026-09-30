// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ProjectManagement from './ProjectManagement';
import { projectAPI, userAPI, expenseAPI, companyAPI } from '../api';
vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: currentUser }) }));
const currentUser = { id: 1, role: 'ADMIN' };
const project = { id: 10, companyId: 1, canManage: true, code: 'P1', name: 'Atlas', status: 'ACTIVE', projectManagerId: 3, projectManagerHoursApproverId: 4,
  assignments: [{ id: 5, userId: 3, userName: 'Employee', isActive: true, startDate: '2026-09-01', endDate: '2026-09-30', billRate: 10, plannedHours: 40 }] };

beforeEach(() => {
  vi.resetAllMocks();
  // jsdom does not implement the browser's modal dialog methods.
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  currentUser.role = 'ADMIN';
  currentUser.canReviewProjects = false;
  currentUser.canManageProjects = false;
  currentUser.canCreateProjects = false;
  localStorage.clear();
  projectAPI.getProjects.mockResolvedValue({ data: [project] });
  userAPI.getAllUsers.mockResolvedValue({ data: [] });
  projectAPI.getHoursDashboard.mockResolvedValue({ data: [{ projectId: 10, employees: [{ userId: 3, userName: 'Employee', plannedHours: 40, timesheetId: 20 }] }] });
  expenseAPI.totals.mockResolvedValue({ data: { budget: 100, approved: 20, pending: 10, remaining: 80 } });
  expenseAPI.project.mockResolvedValue({ data: [] });
  companyAPI.mine.mockResolvedValue({ data: [{ id: 1, name: 'Test Company' }] });
  companyAPI.members.mockResolvedValue({ data: [
    { user_id: 3, status: 'ACTIVE', roles: ['PROJECT_MANAGER'] },
    { user_id: 4, status: 'ACTIVE', roles: ['PROJECT_ADMIN'] },
    { user_id: 9, status: 'ACTIVE', roles: ['USER'] },
  ] });
  companyAPI.projectRoles.mockResolvedValue({ data: [] });
});
afterEach(cleanup);

async function selectProject() {
  render(<ProjectManagement />);
  fireEvent.change(await screen.findByLabelText('Project Name'), { target: { value: 'Atlas' } });
  fireEvent.click(await screen.findByRole('button', { name: /Atlas/ }));
}

it('lets Admin read all project tabs without write controls', async () => {
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

it('shows pending expenses separately from the approved-spend chart and warns the Project Admin', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  expenseAPI.totals.mockResolvedValue({ data: { budget: 100, approved: 30, pending: 55, remaining: 70 } });
  await selectProject();
  expect(await screen.findByRole('img', { name: /Project expenses: 30.00 approved, 55.00 pending, 70.00 remaining/ })).toBeTruthy();
  expect(screen.getByText(/Approved and pending expenses are approaching the expense budget/)).toBeTruthy();
  const budgetMeter = screen.getByRole('meter', { name: 'Approved expense budget used' });
  expect(budgetMeter.getAttribute('aria-valuenow')).toBe('30');
  expect(budgetMeter.querySelector('.pc-expense-approved').style.width).toBe('30%');
  expect(budgetMeter.querySelector('.pc-expense-pending').style.width).toBe('55%');
  expect(screen.getByText('Pending $55.00')).toBeTruthy();
});

it('shows editable assigned project hours from the assignment instead of monthly dashboard plans', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  projectAPI.getHoursDashboard.mockResolvedValue({ data: [{ projectId: 10, employees: [{ userId: 3, userName: 'Employee', plannedHours: 0, totalLoggedHours: 12, status: 'SUBMITTED' }] }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  const input = screen.getByLabelText('Planned hours for Employee');
  expect(input.value).toBe('40');
  expect(input.closest('fieldset').disabled).toBe(false);
  expect(screen.getByText('12.00')).toBeTruthy();
});

it('persists favorites from project cards and sorts favorite projects first', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
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
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  await selectProject();
  expect(screen.getByRole('button', { name: 'New Project' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.getByLabelText('Project Code').closest('fieldset').disabled).toBe(false);
  expect(screen.getByRole('button', { name: 'Save Project' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
});

it('shows useful project context in Details and keeps budget editing in Expenses', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, expenseBudget: 100, createdAt: '2026-09-01T12:00:00', updatedAt: '2026-09-12T12:00:00', pendingApprovalCount: 2 }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.getByText('Ownership & approvals')).toBeTruthy();
  expect(screen.getByText('Lifecycle')).toBeTruthy();
  expect(screen.getByText('Sep 1, 2026')).toBeTruthy();
  expect(screen.getByText('Sep 12, 2026')).toBeTruthy();
  expect(screen.queryByLabelText('Expense Budget')).toBeNull();

  fireEvent.click(screen.getByRole('button', { name: 'Expenses' }));
  expect(screen.getByLabelText('Expense Budget').value).toBe('100');
  expect(screen.getByRole('button', { name: 'Save Budget' }).disabled).toBe(true);
});

it('saves the expense budget without changing project details or reverting it later', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  userAPI.getAllUsers.mockResolvedValue({ data: [
    { id: 3, firstName: 'Project', lastName: 'Manager', role: 'EMPLOYEE', isActive: true },
    { id: 4, firstName: 'Hours', lastName: 'Approver', role: 'EMPLOYEE', isActive: true },
  ] });
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, expenseBudget: 100 }] });
  projectAPI.updateProject.mockResolvedValue({ data: { ...project, expenseBudget: 250 } });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Expenses' }));
  fireEvent.change(screen.getByLabelText('Expense Budget'), { target: { value: '250' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Budget' }));
  await waitFor(() => expect(projectAPI.updateProject).toHaveBeenCalledWith(10, expect.objectContaining({ code: 'P1', name: 'Atlas', expenseBudget: 250, projectManagerId: 3 })));
  await screen.findByText('Expense budget saved.');
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  fireEvent.click(screen.getByRole('button', { name: 'Save Project' }));
  await waitFor(() => expect(projectAPI.updateProject).toHaveBeenCalledTimes(2));
  expect(projectAPI.updateProject.mock.calls[1][1].expenseBudget).toBe(250);
});

it('validates budget precision and warns before leaving an unsaved budget', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, expenseBudget: 100 }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Expenses' }));
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Expense Budget' }), { target: { value: '100.123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save Budget' }));
  expect(screen.getByText('Enter a nonnegative amount with no more than two decimal places.')).toBeTruthy();
  expect(projectAPI.updateProject).not.toHaveBeenCalled();
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.getByRole('spinbutton', { name: 'Expense Budget' })).toBeTruthy();
  confirm.mockReturnValue(true);
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  expect(screen.queryByRole('spinbutton', { name: 'Expense Budget' })).toBeNull();
  confirm.mockRestore();
});

it('creates a draft with essentials and guides setup before activation', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  const draft = { ...project, id: 11, code: 'P2', name: 'New Project', status: 'DRAFT', isActive: false, projectManagerId: null, projectManagerHoursApproverId: null, assignments: [] };
  projectAPI.createProject.mockResolvedValue({ data: draft });
  render(<ProjectManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'New Project' }));
  fireEvent.change(screen.getByLabelText('Company'), { target: { value: '1' } });
  fireEvent.change(screen.getByLabelText('Project Code'), { target: { value: 'P2' } });
  fireEvent.change(screen.getAllByLabelText('Project Name')[1], { target: { value: 'New Project' } });
  expect(screen.queryByLabelText('Primary Project Manager')).toBeNull();
  expect(screen.queryByLabelText('Status')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Create Draft' }));
  await waitFor(() => expect(projectAPI.createProject).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT', projectManagerId: null, projectManagerHoursApproverId: null })));
  expect(await screen.findByText('Prepare this draft for activation')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Activate Project' }).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Add team' }));
  expect(await screen.findByText('Add a team member')).toBeTruthy();
});

it('requires a distinct Project Admin to approve the PM hours during draft setup', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  userAPI.getAllUsers.mockResolvedValue({ data: [{ id: 3, firstName: 'Project', lastName: 'Manager', isActive: true }] });
  projectAPI.createProject.mockResolvedValue({ data: { ...project, id: 11, code: 'P2', name: 'New Project', status: 'DRAFT', projectManagerId: null, projectManagerHoursApproverId: null, assignments: [] } });
  render(<ProjectManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'New Project' }));
  fireEvent.change(screen.getByLabelText('Company'), { target: { value: '1' } });
  fireEvent.change(screen.getByLabelText('Project Code'), { target: { value: 'P2' } });
  fireEvent.change(screen.getAllByLabelText('Project Name')[1], { target: { value: 'New Project' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create Draft' }));
  expect(await screen.findByLabelText('Primary Project Manager')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Primary Project Manager'), { target: { value: '3' } });
  expect(screen.getByLabelText("Approver for the PM's own hours").value).toBe('');
  expect(Array.from(screen.getByLabelText("Approver for the PM's own hours").options).map(option => option.value)).toEqual(['']);
});

it('suggests a code, catches duplicates, and protects unsaved draft changes', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  render(<ProjectManagement />);
  fireEvent.click(await screen.findByRole('button', { name: 'New Project' }));
  fireEvent.change(screen.getByLabelText('Company'), { target: { value: '1' } });
  fireEvent.change(screen.getAllByLabelText('Project Name')[1], { target: { value: 'Atlas' } });
  expect(screen.getByLabelText('Project Code').value).toBe('ATLAS');
  fireEvent.change(screen.getByLabelText('Project Code'), { target: { value: 'P1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create Draft' }));
  expect(screen.getByText('This project code is already in use.')).toBeTruthy();
  expect(projectAPI.createProject).not.toHaveBeenCalled();
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  expect(screen.getByRole('button', { name: 'Create Draft' })).toBeTruthy();
  confirm.mockRestore();
});

it('activates a ready draft after routing and team assignment are saved', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  const draft = { ...project, id: 11, status: 'DRAFT' };
  projectAPI.getProjects.mockResolvedValue({ data: [draft] });
  projectAPI.updateProject.mockResolvedValue({ data: { ...draft, status: 'ACTIVE', isActive: true } });
  render(<ProjectManagement />);
  fireEvent.change(await screen.findByLabelText('Filter'), { target: { value: 'DRAFT' } });
  fireEvent.change(await screen.findByLabelText('Project Name'), { target: { value: 'Atlas' } });
  fireEvent.click(screen.getByRole('button', { name: /Atlas/ }));
  fireEvent.click(screen.getByRole('button', { name: 'Activate Project' }));
  await waitFor(() => expect(projectAPI.updateProject).toHaveBeenCalledWith(11, expect.objectContaining({ status: 'ACTIVE', isActive: true, projectManagerId: 3, projectManagerHoursApproverId: 4 })));
  expect(await screen.findByText('Project activated. The team can now use it.')).toBeTruthy();
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
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
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
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
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
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
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
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, assignments: [{ ...project.assignments[0], isActive: false, plannedHours: 35, approvedHoursToDate: 35 }] }] });
  await selectProject();
  expect(screen.getByRole('img', { name: /Total project hours: 35.00/ })).toBeTruthy();
  expect(document.querySelector('.project-chart-legend').textContent).toContain('Approved35.00');
  fireEvent.click(screen.getByRole('button', { name: 'Hours' }));
  expect(screen.getByLabelText('Planned hours for Employee').disabled).toBe(true);
  expect(screen.getByRole('columnheader', { name: 'Remaining Hours' })).toBeTruthy();
  expect(screen.getByLabelText('Planned hours for Employee').closest('tr').lastElementChild.textContent).toBe('0.00');
});

it('blocks pending approval and requires a replacement PM', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, assignments: [{ ...project.assignments[0], pendingApproval: true }] }] });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Team' }));
  fireEvent.click(screen.getByRole('button', { name: 'Offboard Employee' }));
  expect(screen.getByText('Please approve or reject pending hours before offboarding this employee.')).toBeTruthy();
  expect(screen.getByRole('combobox', { name: /Replacement PM/ })).toBeTruthy();
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


it('excludes Admin and inactive users from ownership selections and previews a handover', async () => {
  currentUser.role = 'PROJECT_ADMIN'; currentUser.canManageProjects = true; currentUser.canCreateProjects = true;
  userAPI.getAllUsers.mockResolvedValue({ data: [
    { id: 3, firstName: 'Original', lastName: 'PM', role: 'EMPLOYEE', isActive: true },
    { id: 4, firstName: 'Next', lastName: 'PM', role: 'PROJECT_ADMIN', isActive: true },
    { id: 7, firstName: 'Backup', lastName: 'Admin', role: 'PROJECT_ADMIN', isActive: true },
    { id: 5, firstName: 'Super', lastName: 'Admin', role: 'ADMIN', isActive: true },
    { id: 6, firstName: 'Inactive', lastName: 'User', role: 'EMPLOYEE', isActive: false },
  ] });
  companyAPI.members.mockResolvedValue({ data: [
    { user_id: 3, status: 'ACTIVE', roles: ['PROJECT_MANAGER'] },
    { user_id: 4, status: 'ACTIVE', roles: ['PROJECT_ADMIN'] },
    { user_id: 7, status: 'ACTIVE', roles: ['PROJECT_ADMIN'] },
  ] });
  projectAPI.getProjects.mockResolvedValue({ data: [{ ...project, projectManagerName: 'Original PM', pendingApprovalCount: 2 }] });
  projectAPI.updateProject.mockResolvedValue({ data: project });
  await selectProject();
  fireEvent.click(screen.getByRole('button', { name: 'Project Details' }));
  const managers = screen.getByLabelText('Primary Project Manager');
  expect(Array.from(managers.options).map((option) => option.value)).toEqual(['', '3', '4', '7']);
  expect(Array.from(screen.getByLabelText("Approver for the PM's own hours" ).options).map((option) => option.value)).toEqual(['', '4', '7']);
  fireEvent.change(managers, { target: { value: '4' } });
  fireEvent.change(screen.getByLabelText("Approver for the PM's own hours"), { target: { value: '7' } });
  expect(screen.getByText('Approval handover')).toBeTruthy();
  expect(screen.getByText(/2 pending submissions/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Save and transfer pending approvals' }));
  await waitFor(() => expect(projectAPI.updateProject).toHaveBeenCalledWith(10, expect.objectContaining({ projectManagerId: 4, projectManagerHoursApproverId: 7 })));
});
