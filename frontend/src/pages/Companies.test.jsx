// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Companies from './Companies';

const { api, workspace } = vi.hoisted(() => ({ workspace: { companies: [], currentCompany: null, error: '', platformAdmin:true,
  platformCapabilities:{canCreateCompanies:true}, companyCapabilities:{}, companyRoles:[], selectCompany: vi.fn(), refreshCompanies: vi.fn() }, api: {
  mine: vi.fn(), create: vi.fn(), members: vi.fn(), moderatorGrants: vi.fn(),
  invitations: vi.fn(), accessRequests: vi.fn(),
} }));
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 1, role: 'ADMIN', roles: ['PLATFORM_ADMIN'] } }) }));
vi.mock('../CompanyContext', () => ({ useCompany: () => workspace }));
vi.mock('../api', () => ({ companyAPI: api, projectAPI: { getProjects: vi.fn().mockResolvedValue({ data: [] }) } }));
beforeEach(() => {
  vi.clearAllMocks();
  api.mine.mockResolvedValue({ data: [] });
  workspace.companies = []; workspace.currentCompany = null;
  workspace.refreshCompanies.mockResolvedValue(); workspace.selectCompany.mockResolvedValue();
  for (const name of ['members', 'moderatorGrants', 'invitations', 'accessRequests']) api[name].mockResolvedValue({ data: [] });
});
afterEach(cleanup);

async function fillCreationForm() {
  render(<MemoryRouter><Companies /></MemoryRouter>);
  await waitFor(() => expect(workspace.refreshCompanies).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '+ New company' }));
  fireEvent.change(screen.getByLabelText('Company name'), { target: { value: 'Acme' } });
  fireEvent.change(screen.getByLabelText(/Workspace ID/), { target: { value: 'ACME' } });
  fireEvent.change(screen.getByLabelText(/Initial company admin email/), { target: { value: 'admin@acme.com' } });
}

it('creates the company and its initial admin invitation together', async () => {
  api.create.mockResolvedValue({ data: { id: 12 } });
  await fillCreationForm();
  expect(screen.getByLabelText(/Workspace ID/).value).toBe('acme');
  fireEvent.click(screen.getByRole('button', { name: 'Create company and invite admin' }));
  await waitFor(() => expect(api.create).toHaveBeenCalledWith('Acme', 'acme', 'admin@acme.com'));
  expect(await screen.findByText('Company created. The initial company admin invitation is queued for delivery.')).toBeInTheDocument();
});

it('keeps entered details when provisioning fails', async () => {
  api.create.mockRejectedValue({ response: { data: { message: 'Unable to create the company. Try again.' } } });
  await fillCreationForm();
  fireEvent.click(screen.getByRole('button', { name: 'Create company and invite admin' }));
  expect(await screen.findByText(/Unable to create the company/)).toBeInTheDocument();
  expect(screen.getByLabelText(/Initial company admin email/).value).toBe('admin@acme.com');
});
