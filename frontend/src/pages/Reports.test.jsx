// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import Reports from './Reports';
import { projectAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { role: 'PROJECT_ADMIN' } }) }));
vi.mock('../api', () => ({ projectAPI: { getHoursDashboard: vi.fn() }, reportsAPI: {} }));
const project = { projectId: 1, projectCode: 'TEST', projectName: 'Test', employees: [
  { userId: 1, userName: 'Alice', submissionId: 4, totalLoggedHours: 8, status: 'APPROVED' },
  { userId: 2, userName: 'Bob', totalLoggedHours: 0, status: 'DRAFT' },
] };
beforeEach(() => { vi.resetAllMocks(); });
afterEach(cleanup);
describe('report filters', () => {
  it('excludes assignment-only rows and clears members and exports for an empty year', async () => {
    projectAPI.getHoursDashboard.mockResolvedValueOnce({ data: [project] })
      .mockResolvedValue({ data: [{ ...project, employees: [project.employees[1]] }] });
    render(<Reports />);
    expect(await screen.findByText('Alice')).toBeTruthy();
    expect(screen.queryByText('Bob')).toBeNull();
    fireEvent.click(screen.getByLabelText('Select Alice'));
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2040' } });
    expect(await screen.findByText('No project timesheets for this period.')).toBeTruthy();
    expect(screen.queryByText('Alice')).toBeNull();
    expect(screen.getByRole('button', { name: 'Download selected' }).disabled).toBe(true);
    expect(projectAPI.getHoursDashboard).toHaveBeenLastCalledWith(2040, expect.any(Number));
  });
  it('ignores a late response from an earlier period', async () => {
    let resolveOld;
    projectAPI.getHoursDashboard.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve; }))
      .mockResolvedValue({ data: [] });
    render(<Reports />);
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2040' } });
    await screen.findByText('No project timesheets for this period.');
    await act(async () => resolveOld({ data: [project] }));
    expect(screen.queryByText('Alice')).toBeNull();
  });
  it('filters employee data by the search text and clears stale rows on failure', async () => {
    projectAPI.getHoursDashboard.mockResolvedValueOnce({ data: [project] }).mockRejectedValue(new Error('Unavailable'));
    render(<Reports />);
    await screen.findByText('Alice');
    fireEvent.change(screen.getByLabelText('Employee'), { target: { value: 'Nobody' } });
    expect(screen.queryByText('Alice')).toBeNull();
    fireEvent.change(screen.getByLabelText('Employee'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2040' } });
    await waitFor(() => expect(screen.getByText('Failed to load project timesheet summary')).toBeTruthy());
    expect(screen.queryByText('Alice')).toBeNull();
  });
});
