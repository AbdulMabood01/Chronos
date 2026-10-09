// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, act, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import TimesheetDetail from './TimesheetDetail';
import { timesheetAPI, projectAPI, reportsAPI, companyAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: employee }) }));
vi.mock('../CompanyContext', () => ({ useCompany: () => ({currentCompany:{id:1,name:'Test Company'},platformAdmin:employee.role==='ADMIN',
  companyCapabilities:{canReviewWork:employee.role==='PROJECT_ADMIN'||employee.canReviewProjects},
  permissionsForProject:()=>({capabilities:{canSubmitWork:employee.role==='EMPLOYEE',canReviewTime:employee.role==='PROJECT_ADMIN'||employee.canReviewProjects}}),
}) }));
vi.mock('../api', () => ({
  timesheetAPI: {
    getTimesheetById: vi.fn(),
    getProjectSubmission: vi.fn(),
    getApprovalPeriod: vi.fn(),
    getApprovalMonth: vi.fn(),
    submitApprovalPeriods: vi.fn(),
    getApprovalPeriodHistory: vi.fn(),
    getMyTimesheets: vi.fn(),
    addTimeEntry: vi.fn(),
    updateTimeEntry: vi.fn(),
    deleteTimeEntry: vi.fn(),
    submitProjectTimesheet: vi.fn(),
    submitApprovalPeriod: vi.fn(),
    approveProjectSubmission: vi.fn(),
    decideApprovalPeriod: vi.fn(),
    getApprovalHistory: vi.fn(),
    getOpeningRequests: vi.fn(),
    requestOpening: vi.fn(),
    requestPeriodOpening: vi.fn(),
  },
  projectAPI: { getAssignedProjects: vi.fn(), getProjects: vi.fn() },
  companyAPI: { mine: vi.fn() },
  reportsAPI: { exportProjectTimesheetPdf: vi.fn(), exportApprovalPeriodPdf: vi.fn() },
}));
const employee = { id: 1, role: 'EMPLOYEE' };
const sheet = { id: 2, companyId:1, userId: 1, userName: 'Test Employee', year: 2026, month: 8, status: 'APPROVED',
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
  timesheetAPI.getApprovalPeriod.mockImplementation(async (_projectId, date) => {
    const { data } = await timesheetAPI.getProjectSubmission();
    const month = date.slice(0, 7);
    const periodStart = `${month}-01`;
    const periodEnd = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).toISOString().slice(0, 10);
    const graceEnd = new Date(`${periodEnd}T23:59:59`);
    graceEnd.setDate(graceEnd.getDate() + 7);
    const correctionOpen = Boolean(data.correctionUntil && new Date(data.correctionUntil) > new Date());
    return { data: { ...data, userId: sheet.userId, periodStart, periodEnd, frequency: 'MONTHLY',
      editable: ['DRAFT', 'REJECTED'].includes(data.status)
        || data.status === 'APPROVED' && correctionOpen,
      reviewAllowed: data.status === 'SUBMITTED' && data.routedApproverId === employee.id && employee.id !== sheet.userId,
      timeEntries: [], } };
  });
  timesheetAPI.getApprovalMonth.mockImplementation(async (projectId,date,userId) => {
    const response=await timesheetAPI.getApprovalPeriod(projectId,date,userId);
    return {data:[{...response.data,selectionDate:response.data.periodStart}]};
  });
  timesheetAPI.submitApprovalPeriods.mockResolvedValue({data:[]});
  timesheetAPI.addTimeEntry.mockResolvedValue({ data: {} });
  timesheetAPI.updateTimeEntry.mockResolvedValue({ data: {} });
  timesheetAPI.deleteTimeEntry.mockResolvedValue({});
  timesheetAPI.getApprovalHistory.mockResolvedValue({ data: [] });
  timesheetAPI.getApprovalPeriodHistory.mockResolvedValue({ data: [] });
  timesheetAPI.getOpeningRequests.mockResolvedValue({ data: [] });
  timesheetAPI.submitApprovalPeriod.mockResolvedValue({ data: {} });
  timesheetAPI.decideApprovalPeriod.mockResolvedValue({ data: {} });
  timesheetAPI.requestPeriodOpening.mockResolvedValue({ data: {} });
  companyAPI.mine.mockResolvedValue({ data: [] });
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId:1, code: 'ATLAS', name: 'Atlas' }] });
  projectAPI.getProjects.mockResolvedValue({ data: [] });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

it('shows the load failure and allows retry instead of claiming the timesheet is missing', async () => {
  timesheetAPI.getTimesheetById.mockRejectedValueOnce({ response: { data: { message: 'Company membership required' } } });
  open();
  expect(await screen.findByText('Company membership required')).toBeTruthy();
  expect(screen.queryByText('Timesheet not found')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await waitFor(() => expect(timesheetAPI.getTimesheetById).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull());
});

it('keeps the selected period visible while refreshing its status', async () => {
  open(false);
  await screen.findByText('Approved');
  const pending=[];
  timesheetAPI.getApprovalPeriod.mockImplementation(()=>new Promise(resolve=>pending.push(resolve)));
  fireEvent.click(screen.getByRole('button',{name:'Refresh status'}));
  expect(screen.getByRole('region',{name:'Selected approval period'}).textContent).toContain('2026-08-01');
  expect(screen.getByRole('button',{name:'Refresh status'}).disabled).toBe(true);
  await act(async()=>{
    pending.forEach(resolve=>resolve({data:{...approval,userId:1,periodStart:'2026-08-01',periodEnd:'2026-08-31',frequency:'MONTHLY',editable:false}}));
  });
  await waitFor(()=>expect(screen.getByRole('button',{name:'Refresh status'}).disabled).toBe(false));
});

it('keeps past draft days editable after the former seven-day cutoff', async () => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 9, 6, 12));
  const septemberSheet = { ...sheet, year: 2026, month: 9, status: 'DRAFT', timeEntries: [] };
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: septemberSheet });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160 } });
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas',
    status: 'ACTIVE', isActive: true, assignments: [{ userId: 1,
      startDate: '2026-09-01', endDate: '2026-09-30', plannedHours: 160, isActive:true }] }] });
  const view = open();
  const input = await screen.findByLabelText('Hours for 2026-09-03');
  await waitFor(()=>expect(input.disabled).toBe(false));
  await waitFor(() => expect(input.disabled).toBe(false));
  expect(screen.getByText(/Past draft days remain editable/)).toBeTruthy();
  view.unmount();

  vi.setSystemTime(new Date(2026, 9, 8, 12));
  open();
  await waitFor(()=>expect(screen.getByLabelText('Hours for 2026-09-03').disabled).toBe(false));
  expect(screen.queryByRole('button', { name: 'Request opening' })).toBeNull();
});
it('lets a project manager enter hours on their own assigned project', async () => {
  employee.canReviewProjects = true;
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const date = `${year}-${String(month).padStart(2, '0')}-10`;
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, year, month, status: 'DRAFT', timeEntries: [] } });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160, routedApproverId: 6 } });
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{
    id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas', projectManagerId: 1, status: 'ACTIVE', isActive: true,
    assignments: [{ userId: 1, isActive: true, startDate: `${year}-01-01`, endDate: `${year}-12-31`, plannedHours: 160 }],
  }] });
  open();
  const input = await screen.findByLabelText(`Hours for ${date}`);
  await waitFor(() => expect(input.disabled).toBe(false));
  fireEvent.change(input, { target: { value: '8' } });
  fireEvent.blur(input);
  await waitFor(() => expect(timesheetAPI.addTimeEntry).toHaveBeenCalledWith(2,
    expect.objectContaining({ entryDate: date, hours: '8', projectId: 4 })));
  expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
});
describe('daily notes and summary', () => {
  it.each(['PROJECT_ADMIN', 'EMPLOYEE'])('lets a %s reviewer open populated and empty notes without editing', async (role) => {
    HTMLDialogElement.prototype.showModal = vi.fn(function () { this.setAttribute('open', ''); });
    employee.id = 6;
    employee.role = role;
    employee.canReviewProjects = true;
    timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'SUBMITTED', timeEntries: [{ ...sheet.timeEntries[0], notes: 'Completed integration' }] } });
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', routedApproverId: 6 } });
    open(false);
    await screen.findByRole('button', { name: 'Approve' });
    expect(screen.queryByRole('button', { name: 'Add login and logout time for 2026-08-03' })).toBeNull();
    expect(screen.getByLabelText('Hours for 2026-08-03').tagName).toBe('DIV');
    fireEvent.click(screen.getByRole('button', { name: 'Notes for 2026-08-03' }));
    expect(screen.getByRole('textbox').value).toBe('Completed integration');
    expect(screen.getByRole('textbox').readOnly).toBe(true);
    expect(screen.queryByRole('button', { name: 'Save notes' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    fireEvent.click(screen.getByRole('button', { name: 'Notes for 2026-08-04' }));
    expect(screen.getByPlaceholderText('No notes recorded for this day.')).toBeTruthy();
  });
  it('shows monthly and cumulative project hours in one summary', async () => {
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, loggedHoursToDate: 120 } });
    open();
    await waitFor(() => expect(screen.getByLabelText('Hours overview').textContent).toContain('120.00 total to date'));
    expect(screen.getByText('Hours This Month')).toBeTruthy();
  });
  it('saves notes using the existing entry hours and sessions', async () => {
    HTMLDialogElement.prototype.showModal = vi.fn(function () { this.setAttribute('open', ''); });
    timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'DRAFT', timeEntries: [{ ...sheet.timeEntries[0], notes: 'Planning' }] } });
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'DRAFT', plannedHours: 160, correctionUntil: '2099-01-01T00:00:00' } });
    open(false);
    await waitFor(() => expect(screen.getByLabelText('Hours for 2026-08-03').disabled).toBe(false));
    fireEvent.click(screen.getByRole('button', { name: 'Notes for 2026-08-03' }));
    fireEvent.change(screen.getByLabelText('Tasks done that day (optional)'), { target: { value: 'Implemented reporting' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save notes' }));
    await waitFor(() => expect(timesheetAPI.updateTimeEntry).toHaveBeenCalledWith(2, 8, expect.objectContaining({ hours: '8', notes: 'Implemented reporting', projectId: 4 })));
  });
});
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
  it('does not allow Admin to submit an existing personal draft', async () => {
    employee.role = 'ADMIN';
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
    reportsAPI.exportApprovalPeriodPdf.mockResolvedValue({ data: blob });
    window.URL.createObjectURL = vi.fn(() => 'blob:test');
    window.URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    open();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(false));
    fireEvent.click(screen.getByRole('button', { name: 'Export PDF' }));
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(reportsAPI.exportApprovalPeriodPdf).toHaveBeenCalledWith(3);
    expect(window.URL.createObjectURL).toHaveBeenCalledWith(blob);
  });
  it('refreshes approval when the employee returns after manager approval', async () => {
    timesheetAPI.getProjectSubmission.mockResolvedValueOnce({ data: { ...approval, status: 'SUBMITTED', pdfExportEligible: false } });
    open();
    await screen.findByText(/This submitted project timesheet is read-only until a Project Manager approves or rejects it/);
    expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(true);
    fireEvent.focus(window);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(false));
  });
  it('keeps a submitted project disabled even if the parent month says approved', async () => {
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', pdfExportEligible: false } });
    open();
    await waitFor(() => expect(timesheetAPI.getProjectSubmission).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Export PDF' }).disabled).toBe(true);
    expect(reportsAPI.exportApprovalPeriodPdf).not.toHaveBeenCalled();
  });
});

it('shows an offboarding notice and prevents further entry', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId: 1, code: 'ATLAS', assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31', plannedHours: 8 }] }] });
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
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas', status: 'COMPLETED', assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31' }] }] });
  open(false);
  expect(await screen.findByText('This project has been completed.')).toBeTruthy();
  expect(screen.getByText('Atlas - Completed')).toBeTruthy();
  expect(screen.queryByText('You have been offboarded from this project.')).toBeNull();
});

it('shows on-hold messaging even when the project and assignment are inactive', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas', status: 'ON_HOLD', isActive: false, assignments: [{ userId: 1, isActive: false, startDate: '2026-08-01', endDate: '2026-08-31' }] }] });
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
    data: [{ id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas', assignments: [{ userId: 1, isActive: true, startDate: '2026-09-01', plannedHours: 160 }] }],
  });
  open(false);

  const input = await screen.findByLabelText('Hours for 2026-09-03');
  await waitFor(()=>expect(input.disabled).toBe(false));
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
    data: [{ id: 4, companyId: 1, code: 'ATLAS', name: 'Atlas', assignments: [{ userId: 1, isActive: true, startDate: '2026-09-01', plannedHours: 160 }] }],
  });
  open(false);

  const input = await screen.findByLabelText('Hours for 2026-09-03');
  await waitFor(()=>expect(input.disabled).toBe(false));
  fireEvent.change(input, { target: { value: '7' } });
  fireEvent.blur(input);

  expect(await screen.findByText('Entry date must be within the project assignment dates')).toBeTruthy();
});

it('hides approval actions from admins even when they are the routed approver', async () => {
  employee.id = 6;
  employee.role = 'ADMIN';
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', routedApproverId: 6 } });
  open(false);
  await screen.findByText('Awaiting Approval');
  expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Reject' })).toBeNull();
});

it('lets an employee request an opening with a comment during the 30-day window', async () => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 9, 15, 12));
  const now = new Date();
  const previous = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: {
    ...sheet, year: previous.getFullYear(), month: previous.getMonth() + 1, status: 'APPROVED', timeEntries: [],
  } });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'APPROVED', correctionUntil: null } });
  timesheetAPI.requestPeriodOpening.mockResolvedValue({ data: {} });
  open(false);
  fireEvent.click(await screen.findByRole('button', { name: 'Request opening' }, {timeout:3000}));
  fireEvent.change(screen.getByLabelText('Why do you need this timesheet opened?'), { target: { value: 'Missed project hours' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send request to Project Admin' }));
  await waitFor(() => expect(timesheetAPI.requestPeriodOpening).toHaveBeenCalledWith('4', expect.any(String), 'Missed project hours'));
});

it('refreshes an approved opening and lets the employee resubmit instead of showing frozen', async () => {
  const now = new Date();
  const previous = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const entryDate = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}-03`;
  const monthSheet = { ...sheet, year: previous.getFullYear(), month: previous.getMonth() + 1,
    approvalFrozen: true, timeEntries: [{ ...sheet.timeEntries[0], entryDate }] };
  let opened = false;
  let requestStatus = 'PENDING';
  timesheetAPI.getTimesheetById.mockImplementation(() => Promise.resolve({ data: {
    ...monthSheet, status: opened ? 'DRAFT' : 'APPROVED',
  } }));
  timesheetAPI.getProjectSubmission.mockImplementation(() => Promise.resolve({ data: opened
    ? { ...approval, status: 'DRAFT', plannedHours: 160, openingActive: true, correctionUntil: '2099-01-01T00:00:00' }
    : { ...approval, status: 'APPROVED', plannedHours: 160, openingActive: false, openingStatus: requestStatus } }));
  timesheetAPI.getOpeningRequests.mockImplementation(() => Promise.resolve({ data: [{ id: 9, status: requestStatus }] }));
  timesheetAPI.submitApprovalPeriod.mockResolvedValue({ data: {} });
  open();
  expect(await screen.findByText('Request pending Project Admin review.')).toBeTruthy();
  opened = true;
  requestStatus = 'APPROVED';
  fireEvent.focus(window);
  const button = await screen.findByRole('button', { name: 'Resubmit for Approval' });
  expect(button.disabled).toBe(false);
  expect(screen.queryByText('TIMESHEET FROZEN')).toBeNull();
  expect(screen.getByText(/Correction window open through/)).toBeTruthy();
  fireEvent.click(button);
  await waitFor(() => expect(timesheetAPI.submitApprovalPeriod).toHaveBeenCalledWith('4', expect.any(String)));
});

it('shows submitted reopened hours as awaiting approval without a frozen banner', async () => {
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'SUBMITTED', approvalFrozen: true } });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', openingActive: false } });
  open();
  expect(await screen.findByText(/read-only until a Project Manager approves or rejects it/)).toBeTruthy();
  expect(screen.queryByText('TIMESHEET FROZEN')).toBeNull();
});

it('shows the expired deadline without an opening action', async () => {
  const now = new Date();
  const old = new Date(now.getFullYear(), now.getMonth() - 2, 1);
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: {
    ...sheet, year: old.getFullYear(), month: old.getMonth() + 1, status: 'APPROVED', timeEntries: [],
  } });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'APPROVED', correctionUntil: null } });
  open(false);
  expect(await screen.findByText(/The request deadline has passed/)).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Request opening' })).toBeNull();
});


describe('favorites and corrections', () => {
  it('sorts project favorites first from the shared project favorites store', async () => {
    localStorage.clear();
    localStorage.setItem('chronos:project-favorites:99', '[4]');
    localStorage.setItem('chronos:project-favorites:1', '["5"]');
    projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 4, companyId: 1, code: 'ATLAS' }, { id: 5, companyId: 1, code: 'ZEBRA' }] });
    open(false);
    await waitFor(() => expect(screen.getByLabelText('Project').options[0].value).toBe('5'));
    expect(screen.queryByRole('button', { name: 'Add to favorites' })).toBeNull();
    expect(localStorage.getItem('chronos:project-favorites:99')).toBe('[4]');
    localStorage.clear();
  });
  it('shows the correction reason and resubmits to the selected project', async () => {
    timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'REJECTED' } });
    timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'REJECTED', plannedHours: 160, correctionUntil: '2099-01-01T00:00:00', rejectionReason: 'Please correct Monday hours', rejectedByName: 'Sam Manager' } });
    timesheetAPI.submitApprovalPeriod.mockResolvedValue({ data: {} });
    open(false);
    expect(await screen.findByRole('region', { name: 'Requested corrections' })).toBeTruthy();
    expect(screen.getByText('Please correct Monday hours')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Resubmit for Approval' }));
    await waitFor(() => expect(timesheetAPI.submitApprovalPeriod).toHaveBeenCalledWith('4', expect.any(String)));
  });
});


it('does not let a PM approve their own hours', async () => {
  employee.canReviewProjects = true;
  timesheetAPI.getTimesheetById.mockResolvedValue({ data: { ...sheet, status: 'SUBMITTED' } });
  timesheetAPI.getProjectSubmission.mockResolvedValue({ data: { ...approval, status: 'SUBMITTED', routedApproverId: 9, projectManagerId: 1 } });
  open(false);
  await screen.findByText('Awaiting Approval');
  expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Reject' })).toBeNull();
});
