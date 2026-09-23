// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ProjectHealthCard, ProjectHealthOverview, useProjectHealth } from './ProjectHealth';
import { projectAPI } from '../api';
vi.mock('../api');
afterEach(() => { cleanup(); vi.resetAllMocks(); });
const health = { projectId: 1, projectCode: 'ONE', projectName: 'Atlas', status: 'AT_RISK', monitored: true,
  loggedHours: 110, allocatedHours: 100, plannedHours: 100, remainingHours: -10, hoursUtilization: 110,
  activeResources: 2, signals: [{ code: 'HOURS_EXCEEDED', severity: 'AT_RISK', message: '10 hours over the allocated budget' }],
  coverageNotes: ['Expense budgets are not tracked.'], evaluatedOn: '2026-09-22' };
const state = { projects: [health], loading: false, error: '', refresh: vi.fn() };

it('shows reasons, actual overrun values and accessible bounded progress', () => {
  render(<ProjectHealthCard health={health} state={state} />);
  expect(screen.getByText('At Risk')).toBeTruthy();
  expect(screen.getByText('10 hours over the allocated budget')).toBeTruthy();
  expect(screen.getByText('110%')).toBeTruthy();
  const progress = screen.getByRole('progressbar');
  expect(progress.getAttribute('value')).toBe('100');
  expect(progress.getAttribute('aria-valuetext')).toBe('110% used');
  expect(screen.getByText('Expense budgets are not tracked.')).toBeTruthy();
});
it('overview displays flagged projects and opens the selected project', () => {
  const open = vi.fn();
  render(<ProjectHealthOverview state={{ ...state, projects: [health, { ...health, projectId: 2, projectName: 'Healthy project', status: 'HEALTHY', signals: [] }] }} onOpen={open} />);
  fireEvent.click(screen.getByRole('button', { name: /Atlas/ }));
  expect(open).toHaveBeenCalledWith(1);
  expect(screen.queryByRole('button', { name: /Healthy project/ })).toBeNull();
});
it('does not present errors or missing health as healthy', () => {
  render(<ProjectHealthCard state={{ ...state, error: 'Unable to load health' }} />);
  expect(screen.getByRole('alert').textContent).toContain('Unable to load health');
  expect(screen.queryByText('Healthy')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Retry health' }));
  expect(state.refresh).toHaveBeenCalled();
});
it('refreshes after changes and recovers from an API failure', async () => {
  projectAPI.getHealth.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ data: [health] });
  function Harness({ revision }) {
    const data = useProjectHealth(true, revision);
    return <ProjectHealthOverview state={data} onOpen={() => {}} />;
  }
  const view = render(<Harness revision={1} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Retry health' }));
  expect(await screen.findByRole('button', { name: /Atlas/ })).toBeTruthy();
  view.rerender(<Harness revision={2} />);
  await waitFor(() => expect(projectAPI.getHealth).toHaveBeenCalledTimes(3));
});
