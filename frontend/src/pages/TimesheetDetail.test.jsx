// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, act, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import TimesheetDetail from './TimesheetDetail';
import { timesheetAPI, projectAPI, reportsAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: employee }) }));
vi.mock('../api', () => ({
  timesheetAPI: {
    getTimesheetById: vi.fn(),
    getProjectSubmission: vi.fn(),
    getMyTimesheets: vi.fn(),
    addTimeEntry: vi.fn(),
    updateTimeEntry: vi.fn(),
    deleteTimeEntry: vi.fn(),
  },
  projectAPI: { getAssignedProjects: vi.fn() },
  reportsAPI: { exportProjectTimesheetPdf: vi.fn() },
}));
const employee = { id: 1, role: 'EMPLOYEE' };
const sheet = { id: 2, userId: 1, userName: 'Test Employee', year: 2026, month: 8, status: 'APPROVED',
  timeEntries: [{ id: 8, projectId: 4, projectCode: 'ATLAS', projectName: 'Atlas', entryDate: '2026-08-03', hours: 8 }], vacationDays: [] };
const approval = { id: 3, timesheetId: 2, projectId: 4, status: 'APPROVED', pdfExportEligible: true, totalHours: 8 };
function open(lockPastMonths = true) {
  return render(<MemoryRouter initialEntries={['/timesheets/2']}><Routes>
    <Route path="/timesheets/:id" element={<TimesheetDetail lockPastMonths={lockPastMonths} />} />
  </Routes></MemoryRouter>);
}
beforeEach(() => {
  vi.resetAllMocks();
  employee.id = 1;
  employee.role = 'EMPLOYEE';
  employee.canReviewProjects = false;
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: sheet });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: approval });
  timesheetAPI.addTimeEntry.mockResolvedValue({ data: {} });
  timesheetAPI.updateTimeEntry.mockResolvedValue({ data: {} });
  timesheetAPI.deleteTimeEntry.mockResolvedValue({});
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, code: 'ATLAS', name: 'Atlas' }] });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
describe('employee PDF export', () => {
  it('allows an employee designated as approver to review', async () => {
    employee.id = 6;
    employee.canReviewProjects = true;
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', routedApproverId: 6 } });
    open(false);
    expect(await screen.findByRole('button', { name: 'Approve' })).toBeTruthy();
  });
  it('does not show approval actions for a reviewer of a different project', async () => {
    employee.id = 7;
    employee.canReviewProjects = true;
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', routedApproverId: 6 } });
    open(false);
    await waitFor(() => expect(timesheetAPI.getProjectSubmission).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
  });
  it('does not allow SuperAdmin to submit an existing personal draft', async () => {
    employee.role = 'SUPER_ADMIN';
    timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'DRAFT' } });
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160 } });
    open(false);
    await waitFor(() => expect(timesheetAPI.getProjectSubmission).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Submit for Approval' })).toBeNull();
  });
  it('keeps approval available after a delayed reload of the same timesheet', async () => {
    let resolveReload;
    const reload = new Promise(resolve => { resolveReload = resolve; });
    timesheetAPI.getTimesheetById.mockResolvedValueOnce({ data: sheet }).mockReturnValue(reload);
    open();
    await waitFor(() => expect(timesheetAPI.getProjectSubmission).toHaveBeenCalled());
    await act(async () => { resolveReload({ data: { ...sheet } }); });
    expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(false);
  });
  it('allows download for an approved historical project without a current assignment', async () => {
    projectAPI.getAssignedProjects.mockResolvedValue({ data: [] });
    const blob = new Blob(['%PDF-test'], { type: 'application/pdf' });
    reportsAPI.exportProjectTimesheetPdf.mockResolvedValue({ data: blob });
    window.URL.createObjectURL = vi.fn(() => 'blob:test');
    window.URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    open();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(false));
    fireEvent.click(screen.getByRole('button', { name: 'Export PDF' }));
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(reportsAPI.exportProjectTimesheetPdf).toHaveBeenCalledWith(3);
    expect(window.URL.createObjectURL).toHaveBeenCalledWith(blob);
  });
  it('refreshes approval when the employee returns after manager approval', async () => {
    timesheetAPI.getProjectSubmission.mockResolvedValueOnce({ data: { ...approval, status: 'SUBMITTED', pdfExportEligible: false } });
    open();
    await screen.findByText('This submitted project timesheet is read-only until a Project Manager approves or rejects it.');
    expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(true);
    fireEvent.focus(window);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(false));
  });
  it('keeps a submitted project disabled even if the parent month says approved', async () => {
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', pdfExportEligible: false } });
    open();
    await waitFor(() => expect(timesheetAPI.getProjectSubmission).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(true);
    expect(reportsAPI.exportProjectTimesheetPdf).not.toHaveBeenCalled();
  });
});

it('shows an offboarding notice and prevents further entry', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, code: 'ATLAS', assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31', plannedHours: 8 }] }] });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 8 } });
  open(false);
  expect(await screen.findByText('You have been offboarded from this project.')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Submit for Approval' })).toBeNull();
});
it('renders approved hours as text without calendar clocks', async () => {
  open(false);
  await waitFor(() => expect(screen.getByLabelText('Hours for 2026-08-03').tagName).toBe('DIV'));
  expect(screen.queryByRole('button', { name: /Add login and logout/ })).toBeNull();
  expect(screen.getByLabelText('Hours for 2026-08-03').textContent).toBe('8.00hrs');
});

it('prioritizes completed project messaging over offboarding', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, code: 'ATLAS', name: 'Atlas', status: 'COMPLETED', assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31' }] }] });
  open(false);
  expect(await screen.findByText('This project has been completed.')).toBeTruthy();
  expect(screen.getByText('Atlas - Completed')).toBeTruthy();
  expect(screen.queryByText('You have been offboarded from this project.')).toBeNull();
});

it('shows on-hold messaging even when the project and assignment are inactive', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, code: 'ATLAS', name: 'Atlas', status: 'ON_HOLD', isActive: false, assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31' }] }] });
  open(false);
  expect(await screen.findByText('This project is currently on hold.')).toBeTruthy();
  expect(screen.getByText('Atlas - On Hold')).toBeTruthy();
  expect(screen.queryByText('This project has ended.')).toBeNull();
  expect(screen.queryByText('You have been offboarded from this project.')).toBeNull();
  expect(screen.getByText('Hour entry is paused while this project is on hold. Your timesheet history remains available to view.')).toBeTruthy();
});

it('saves typed hour edits as manual hours without stale clock sessions', async () => {
  const draftSheet = {
    ...sheet,
    year: 2026,
    month: 9,
    status: 'DRAFT',
    timeEntries: [{
      id: 8,
      projectId: 4,
      projectCode: 'ATLAS',
      projectName: 'Atlas',
      entryDate: '2026-09-03',
      hours: 8,
      sessions: [{ loginTime: '09:00', logoutTime: '17:00' }],
    }],
  };
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: draftSheet });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160 } });
  projectAPI.getAssignedProjects.mockResolvedValue({
    data: [{ id: 4, code: 'ATLAS', name: 'Atlas', assignments: [{ userId: 1, isActive: true, startDate: '2026-09-01', plannedHours: 160 }] }],
  });
  open(false);

  const input = await screen.findByLabelText('Hours for 2026-09-03');
  fireEvent.change(input, { target: { value: '7' } });
  fireEvent.blur(input);

  await waitFor(() => expect(timesheetAPI.updateTimeEntry).toHaveBeenCalled());
  expect(timesheetAPI.updateTimeEntry.mock.calls[0][2]).toMatchObject({
    entryDate: '2026-09-03',
    hours: '7',
    projectId: 4,
    sessions: [],
  });
});

it('shows backend validation messages when hour saves fail', async () => {
  const draftSheet = {
    ...sheet,
    year: 2026,
    month: 9,
    status: 'DRAFT',
    timeEntries: [{ id: 8, projectId: 4, projectCode: 'ATLAS', projectName: 'Atlas', entryDate: '2026-09-03', hours: 8 }],
  };
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: draftSheet });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160 } });
  timesheetAPI.updateTimeEntry.mockRejectedValue({ response: { data: { message: 'Entry date must be within the project assignment dates' } } });
  projectAPI.getAssignedProjects.mockResolvedValue({
    data: [{ id: 4, code: 'ATLAS', name: 'Atlas', assignments: [{ userId: 1, isActive: true, startDate: '2026-09-01', plannedHours: 160 }] }],
  });
  open(false);

  const input = await screen.findByLabelText('Hours for 2026-09-03');
  fireEvent.change(input, { target: { value: '7' } });
  fireEvent.blur(input);

  expect(await screen.findByText('Entry date must be within the project assignment dates')).toBeTruthy();
});
