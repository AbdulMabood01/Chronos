// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from './Dashboard';
import * as api from '../api';
import { buildDashboard, capacityExceptions } from '../utils/dashboard';

vi.mock('../api');
const user = { id: 7, role: 'EMPLOYEE', firstName: 'Alex' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user }) }));
const now = new Date(2026, 8, 2, 12);
const project = { id: 10, code: 'ATLAS', name: 'Atlas delivery', status: 'ACTIVE', isActive: true, assignments: [{ userId: 7, userName: 'Alex', isActive: true, plannedHours: 176, startDate: '2026-09-01', endDate: '2026-09-30' }] };
const base = () => ({ projects: [project], sheets: [], balance: { configured: true, vacation: { remainingDays: 12 } }, reviews: [], announcements: [], notifications: [] });
beforeEach(() => {
  vi.resetAllMocks();
  Object.assign(user, { id: 7, role: 'EMPLOYEE', canManageProjects: false, canReviewProjects: false });
  for (const group of Object.values(api)) if (group && typeof group === 'object') for (const method of Object.values(group)) if (vi.isMockFunction(method)) method.mockResolvedValue({ data: [] });
  api.userAPI.getLeaveBalance.mockResolvedValue({ data: base().balance });
  api.projectAPI.getAssignedProjects.mockResolvedValue({ data: [project] });
});
afterEach(cleanup);
const mount = () => render(<MemoryRouter><Dashboard /></MemoryRouter>);

it('counts this week across month boundaries and excludes future hours', () => {
  const model = buildDashboard({ ...base(), sheets: [
    { year: 2026, month: 8, status: 'APPROVED', timeEntries: [{ entryDate: '2026-08-31', hours: 7 }, { entryDate: '2026-08-28', hours: 8 }] },
    { year: 2026, month: 9, status: 'DRAFT', timeEntries: [{ entryDate: '2026-09-01', hours: 6 }, { entryDate: '2026-09-03', hours: 8 }] },
  ] }, user, now);
  expect(model.metrics[0].value).toBe('13');
  expect(model.attention).toHaveLength(0);
});

it('prioritizes corrections and completed-month drafts, not current drafts or approved sheets', () => {
  const model = buildDashboard({ ...base(), sheets: [
    { id: 1, year: 2026, month: 8, status: 'REJECTED', totalHours: 20 },
    { id: 2, year: 2026, month: 7, status: 'DRAFT', totalHours: 20 },
    { id: 3, year: 2026, month: 9, status: 'DRAFT', totalHours: 20 },
    { id: 4, year: 2026, month: 6, status: 'APPROVED', totalHours: 20 },
  ] }, user, now);
  expect(model.attention.map(item => item.to)).toEqual(['/timesheet/1', '/timesheet/2']);
  expect(model.metrics.find(item => item.label === 'Pending Actions').value).toBe(2);
});

it('shows accurate personal summaries and hides empty sections without requesting management data', async () => {
  mount();
  await screen.findByText('12 days');
  expect(screen.getByRole('heading', { name: 'Needs Attention' })).toBeTruthy();
  expect(screen.getByRole('heading', { name: 'My Projects' })).toBeTruthy();
  expect(screen.queryByRole('heading', { name: 'Team Capacity' })).toBeNull();
  expect(screen.queryByRole('region', { name: 'Company announcements' })).toBeNull();
  expect(api.projectAPI.getHealth).not.toHaveBeenCalled();
  expect(api.userAPI.getAllUsers).not.toHaveBeenCalled();
  expect(screen.getByRole('link', { name: /ATLAS/ }).getAttribute('href')).toBe('/timesheets?projectId=10');
});

it('keeps successful sections usable after failure and retries without showing a false all-clear', async () => {
  api.timesheetAPI.getMyTimesheets.mockRejectedValueOnce(new Error('offline'));
  mount();
  await screen.findByRole('alert');
  expect(screen.queryByText(/You’re all caught up/)).toBeNull();
  expect(screen.getByText('12 days')).toBeTruthy();
  expect(within(screen.getByRole('link', { name: /Hours This Week/ })).getByText('Unavailable')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByText(/You’re all caught up/);
  expect(screen.queryByRole('alert')).toBeNull();
});

it('gives project admins their approval queue and permission-scoped projects', async () => {
  user.role = 'PROJECT_ADMIN';
  api.projectAPI.getProjects.mockResolvedValue({ data: [project] });
  api.projectAPI.getHealth.mockResolvedValue({ data: [{ projectId: 10, projectCode: 'ATLAS', projectName: 'Atlas delivery', status: 'AT_RISK', loggedHours: 180, allocatedHours: 176, signals: [{ code: 'HOURS_EXCEEDED', message: '4 hours over budget' }] }] });
  api.timesheetAPI.getPendingProjectSubmissions.mockResolvedValue({ data: [{ id: 2, timesheetId: 3, projectId: 10, projectCode: 'ATLAS', userName: 'Jamie', month: 8, year: 2026, totalHours: 8 }] });
  mount();
  const approval = await screen.findByRole('link', { name: /Jamie · Timesheet approval/ });
  expect(approval.getAttribute('href')).toBe('/timesheet/3?projectId=10');
  expect(screen.getByRole('link', { name: /Pending Approvals/ }).textContent).toContain('1');
  expect(screen.queryByRole('heading', { name: 'Projects', exact: true })).toBeNull();
  expect(screen.queryByRole('heading', { name: 'Project Health' })).toBeNull();
  expect(api.userAPI.getAllUsers).not.toHaveBeenCalled();
  expect(api.timesheetAPI.getMyTimesheets).not.toHaveBeenCalled();
});

it('keeps employee project managers in the project view and reviewers in their personal view', async () => {
  user.canManageProjects = true;
  user.canReviewProjects = true;
  mount();
  await screen.findByText(/You’re all caught up/);
  expect(api.projectAPI.getProjects).toHaveBeenCalled();
  cleanup();
  user.canManageProjects = false;
  mount();
  await screen.findByText('12 days');
  expect(api.projectAPI.getAssignedProjects).toHaveBeenCalled();
  expect(api.timesheetAPI.getPendingProjectSubmissions).toHaveBeenCalledTimes(2);
});

it('gives admins organization summaries and announcement management without project health', async () => {
  user.role = 'ADMIN';
  api.projectAPI.getProjects.mockResolvedValue({ data: [project] });
  api.userAPI.getAllUsers.mockResolvedValue({ data: [{ id: 7, role: 'EMPLOYEE', isActive: true }, { id: 8, role: 'EMPLOYEE', isActive: false }] });
  mount();
  await screen.findByRole('link', { name: 'Manage announcements' });
  expect(screen.queryByRole('heading', { name: 'Project Health' })).toBeNull();
  expect(screen.getByRole('link', { name: /Employees/ }).textContent).toContain('1');
  expect(screen.getByRole('link', { name: 'Manage announcements' }).getAttribute('href')).toBe('/announcements?manage=true');
  expect(api.timesheetAPI.getMyTimesheets).not.toHaveBeenCalled();
  expect(api.timesheetAPI.getPendingProjectSubmissions).not.toHaveBeenCalled();
  expect(api.userAPI.getLeaveBalance).not.toHaveBeenCalled();
});

it('limits announcements to three recent relevant items and keeps acknowledgment actions', () => {
  const announcements = Array.from({ length: 5 }, (_, index) => ({ id: index, title: `Update ${index}`, status: 'PUBLISHED', publish_date: `2026-08-${20 + index}`, priority: 'IMPORTANT' }));
  announcements[0].acknowledgment_required = true;
  announcements.push({ id: 'old', title: 'Expired', status: 'PUBLISHED', expiration_date: '2026-08-01', acknowledgment_required: true });
  announcements.push({ id: 'future', status: 'PUBLISHED', publish_date: '2027-01-01', acknowledgment_required: true });
  const model = buildDashboard({ ...base(), announcements }, user, now);
  expect(model.announcements.map(item => item.id)).toEqual([4, 3, 2]);
  expect(model.attention).toHaveLength(1);
  expect(model.attention[0].to).toBe('/announcements?id=0');
});

it('estimates capacity from dated assignments and does not infer underutilization from restricted visibility', () => {
  const under = { ...project, assignments: [{ ...project.assignments[0], plannedHours: 88 }] };
  expect(capacityExceptions([under], now, true)[0].detail).toContain('50%');
  expect(capacityExceptions([under], now, false)).toEqual([]);
  expect(capacityExceptions([project], now, true)).toEqual([]);
  expect(capacityExceptions([project, { ...project, id: 11 }], now, false)[0].detail).toContain('200%');
  expect(capacityExceptions([{ ...under, assignments: [{ ...under.assignments[0], plannedHours: null }] }], now, true)).toEqual([]);
});

it('excludes ended and inactive personal assignments and shows only recent published reviews', () => {
  const model = buildDashboard({ ...base(), projects: [project,
    { ...project, id: 11, assignments: [{ ...project.assignments[0], isActive: false }] },
    { ...project, id: 12, assignments: [{ ...project.assignments[0], endDate: '2026-08-31' }] },
  ], reviews: [{ id: 'draft', review_year: 2026, quarter: 4 }, { id: 'published', review_year: 2026, quarter: 3, published_at: '2026-09-01T12:00:00' }] }, user, now);
  expect(model.projects.map(item => item.id)).toEqual([10]);
  expect(model.metrics.find(item => item.label === 'Active Projects').value).toBe(1);
  expect(model.attention.find(item => item.id === 'recent-review').title).toBe('Read your Q3 2026 review');
  expect(buildDashboard({ ...base(), reviews: [{ published_at: '2025-01-01T12:00:00' }] }, user, now).attention).toEqual([]);
});

it('does not discard stale responses into another user’s dashboard', async () => {
  let resolve;
  api.timesheetAPI.getMyTimesheets.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
  const view = mount();
  await waitFor(() => expect(resolve).toBeTruthy());
  user.role = 'ADMIN';
  view.rerender(<MemoryRouter><Dashboard /></MemoryRouter>);
  await screen.findByRole('link', { name: 'Manage announcements' });
  resolve({ data: [{ id: 77, year: 2026, month: 1, status: 'REJECTED' }] });
  await waitFor(() => expect(screen.queryByText('Timesheet needs correction')).toBeNull());
});
