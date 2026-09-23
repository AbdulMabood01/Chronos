// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import WorkspaceLayout from './WorkspaceLayout';

const currentUser = { id: 1, role: 'EMPLOYEE' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: currentUser, logout: vi.fn() }) }));
vi.mock('../api', () => ({ notificationAPI: { getUnreadCount: vi.fn().mockResolvedValue({ data: 0 }) } }));
afterEach(cleanup);

it.each([false, true])('hides Management from a non-PM employee (approver: %s)', (approver) => {
  currentUser.canReviewProjects = approver;
  currentUser.canManageProjects = false;
  render(<MemoryRouter><WorkspaceLayout><p>Content</p></WorkspaceLayout></MemoryRouter>);
  expect(screen.queryByText('Management')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Projects' })).toBeNull();
  expect(screen.queryByRole('link', { name: 'Missing timesheets' })).toBeNull();
  expect(screen.queryByRole('link', { name: 'Team leave calendar' })).toBeNull();
  expect(screen.getByRole('link', { name: 'Reports' }).getAttribute('href')).toBe('/workplace-reports');
  expect(screen.queryByRole('link', { name: 'Employee Reports' })).toBeNull();
  expect(Boolean(screen.queryByRole('link', { name: 'Approvals' }))).toBe(approver);
});

it('keeps Management and Approvals available to a PM', () => {
  currentUser.canReviewProjects = true;
  currentUser.canManageProjects = true;
  render(<MemoryRouter><WorkspaceLayout><p>Content</p></WorkspaceLayout></MemoryRouter>);
  expect(screen.getByText('Management')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Approvals' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Projects' })).toBeTruthy();
});
