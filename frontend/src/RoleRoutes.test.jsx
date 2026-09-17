// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { notificationAPI } from './api';
vi.mock('./api');
vi.mock('./AuthContext', () => ({
  AuthProvider: ({ children }) => children,
  useAuth: () => ({ user: currentUser, loading: false, logout: vi.fn() }),
}));
vi.mock('./pages/Dashboard', () => ({ default: () => <p>Dashboard content</p> }));
const currentUser = { id: 1, role: 'SUPER_ADMIN', profileCompleted: true };
beforeEach(() => {
  currentUser.role = 'SUPER_ADMIN';
  currentUser.canReviewProjects = false;
  currentUser.canManageProjects = false;
  notificationAPI.getUnreadCount.mockResolvedValue({ data: 0 });
});
afterEach(cleanup);

it.each(['/timesheets', '/vacation'])('redirects SuperAdmin away from personal route %s', async (path) => {
  window.history.replaceState({}, '', path);
  render(<App />);
  await screen.findByText('Dashboard content');
  await waitFor(() => expect(window.location.pathname).toBe('/dashboard'));
  expect(screen.queryByRole('link', { name: 'Timesheets' })).toBeNull();
  expect(screen.queryByRole('link', { name: 'Time off' })).toBeNull();
  expect(screen.getByRole('link', { name: 'Projects' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Approvals' })).toBeTruthy();
});

it('shows approvals and project hours to an employee with project permissions', () => {
  currentUser.role = 'EMPLOYEE';
  currentUser.canReviewProjects = true;
  currentUser.canManageProjects = true;
  window.history.replaceState({}, '', '/dashboard');
  render(<App />);
  expect(screen.getByRole('link', { name: 'Approvals' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Project hours' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Timesheets' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Projects' })).toBeTruthy();
});

it('shows team management to an approver without project management access', () => {
  currentUser.role = 'EMPLOYEE';
  currentUser.canReviewProjects = true;
  currentUser.canManageProjects = false;
  window.history.replaceState({}, '', '/dashboard');
  render(<App />);
  expect(screen.getByText('Management')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Missing timesheets' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Team leave calendar' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Approvals' })).toBeTruthy();
  expect(screen.queryByRole('link', { name: 'Projects' })).toBeNull();
  expect(screen.queryByRole('link', { name: 'Project hours' })).toBeNull();
});

it.each(['/projects', '/project-hours'])('blocks project management URL for an approver who is not a project manager: %s', async path => {
  currentUser.role = 'EMPLOYEE';
  currentUser.canReviewProjects = true;
  currentUser.canManageProjects = false;
  window.history.replaceState({}, '', path);
  render(<App />);
  await screen.findByText('Dashboard content');
  expect(window.location.pathname).toBe('/dashboard');
});

it.each(['/projects', '/project-hours', '/admin', '/missing-timesheets', '/team-leave-calendar', '/settings', '/users', '/reports', '/audit'])('hides management and blocks employee URL %s', async path => {
  currentUser.role = 'EMPLOYEE';
  window.history.replaceState({}, '', path);
  render(<App />);
  await screen.findByText('Dashboard content');
  expect(window.location.pathname).toBe('/dashboard');
  expect(screen.queryByText('Management')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Projects' })).toBeNull();
});

it('hides settings from Admin navigation and blocks direct Settings URL', async () => {
  currentUser.role = 'ADMIN';
  window.history.replaceState({}, '', '/settings');
  render(<App />);
  await screen.findByText('Dashboard content');
  expect(window.location.pathname).toBe('/dashboard');
  expect(screen.getByRole('link', { name: 'Reports' })).toBeTruthy();
  expect(screen.queryByRole('link', { name: 'Settings' })).toBeNull();
});
