// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, act, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import TimesheetDetail from './TimesheetDetail';
import { timesheetAPI, projectAPI, reportsAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: employee }) }));
vi.mock('../api', () => ({
  timesheetAPI: { getTimesheetById: vi.fn(), getProjectSubmission: vi.fn(), getMyTimesheets: vi.fn() },
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
