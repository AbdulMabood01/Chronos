// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import Reports from './Reports';
import { reportsAPI, projectAPI } from '../api';
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { role: 'PROJECT_ADMIN', canManageProjects: true } }) }));
vi.mock('../api', () => ({ reportsAPI: { companyPeriods: vi.fn(), companyExportPeriods: vi.fn() }, projectAPI: { getProjects: vi.fn() } }));
vi.mock('../CompanyContext',()=>({useCompany:()=>({currentCompany:{id:12},platformAdmin:false,companyCapabilities:{},projectPermissions:[{roles:['PROJECT_ADMIN']}]})}));
const period = { companyId:12,id: 4, projectId: 1, projectCode: 'TEST', projectName: 'Test', userId: 1,
  userName: 'Alice', periodStart: '2026-09-01', periodEnd: '2026-09-30', totalHours: 8, status: 'APPROVED' };
beforeEach(() => { vi.resetAllMocks(); projectAPI.getProjects.mockResolvedValue({data:[]}); reportsAPI.companyPeriods.mockResolvedValue({ data: [period] }); });
afterEach(cleanup);
describe('report filters', () => {
  it('keeps company projects available when they have no submissions', async () => {
    projectAPI.getProjects.mockResolvedValue({data:[{id:9,companyId:12,code:'NEW',name:'New project'}]});
    reportsAPI.companyPeriods.mockResolvedValue({data:[]});
    render(<Reports />);
    expect(await screen.findByRole('option',{name:'NEW - New project'})).toBeTruthy();
    expect(screen.getByText('No timesheet submissions for this project in the selected period.')).toBeTruthy();
  });
  it('excludes assignment-only rows and clears members and exports for an empty year', async () => {
    reportsAPI.companyPeriods.mockResolvedValueOnce({ data: [period] }).mockResolvedValue({ data: [] });
    render(<Reports />);
    expect(await screen.findByText('Alice')).toBeTruthy();
    expect(screen.queryByText('Bob')).toBeNull();
    fireEvent.click(screen.getByLabelText('Select Alice'));
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2040' } });
    expect(await screen.findByText('No project timesheets for this period.')).toBeTruthy();
    expect(screen.queryByText('Alice')).toBeNull();
    expect(screen.getByRole('button', { name: 'Download selected' }).disabled).toBe(true);
    expect(reportsAPI.companyPeriods).toHaveBeenLastCalledWith(12,2040, expect.any(Number));
  });
  it('ignores a late response from an earlier period', async () => {
    let resolveOld;
    reportsAPI.companyPeriods.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve; }))
      .mockResolvedValue({ data: [] });
    render(<Reports />);
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2040' } });
    await screen.findByText('No project timesheets for this period.');
    await act(async () => resolveOld({ data: [period] }));
    expect(screen.queryByText('Alice')).toBeNull();
  });
  it('filters employee data by the search text and clears stale rows on failure', async () => {
    reportsAPI.companyPeriods.mockResolvedValueOnce({ data: [period] }).mockRejectedValue(new Error('Unavailable'));
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
