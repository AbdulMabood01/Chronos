// @vitest-environment jsdom
import React from 'react';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import Expenses, { ProjectExpenseHistory } from './Expenses';
vi.mock('../CompanyContext', () => ({ useCompany:()=>({currentCompany:{id:1},projectPermissions:[{projectId:3}],
  permissionsForProject:()=>({capabilities:{canSubmitWork:true}})}) }));
import { expenseAPI, projectAPI } from '../api';

vi.mock('../api');
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user: { id: 4, role: 'EMPLOYEE', canReviewProjects: false } }) }));

beforeEach(() => {
  vi.resetAllMocks();
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [{ id: 3, companyId:1, code: 'ATLAS', name: 'Atlas', isActive: true }] });
  expenseAPI.mine.mockResolvedValue({ data: [] });
  expenseAPI.pending.mockResolvedValue({ data: [] });
  expenseAPI.project.mockResolvedValue({ data: [] });
  expenseAPI.totals.mockResolvedValue({ data: { budget: 100, approved: 40, pending: 20, remaining: 60 } });
});
afterEach(cleanup);

it('submits an assigned project expense with its receipt', async () => {
  expenseAPI.save.mockResolvedValue({ data: {} });
  render(<Expenses />);
  fireEvent.change(await screen.findByLabelText('Project', { exact: true }), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '25.50' } });
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Train to client site' } });
  const receipt = new File(['%PDF-1.4'], 'receipt.pdf', { type: 'application/pdf' });
  fireEvent.change(screen.getByLabelText(/Receipt or document/), { target: { files: [receipt] } });
  fireEvent.submit(screen.getByRole('button', { name: 'Submit for approval' }).closest('form'));
  await waitFor(() => expect(expenseAPI.save).toHaveBeenCalledWith(undefined,
    expect.objectContaining({ projectId: 3, amount: 25.5, category: 'TRAVEL' }), receipt));
});

it('shows a newly submitted expense first in My expenses', async () => {
  expenseAPI.mine.mockResolvedValueOnce({ data: [] }).mockResolvedValue({ data: [
    { id: 1, project_id:3, project_code: 'ATLAS', category: 'MEALS', amount: 12, expense_date: '2026-09-20', status: 'APPROVED', description: 'Older claim', submitted_at: '2026-09-20T12:00:00Z' },
    { id: 2, project_id:3, project_code: 'ATLAS', category: 'TRAVEL', amount: 25.5, expense_date: '2026-09-27', status: 'PENDING_APPROVAL', description: 'New claim', submitted_at: '2026-09-27T12:00:00Z' },
  ] });
  expenseAPI.save.mockResolvedValue({ data: {} });
  render(<Expenses />);
  fireEvent.change(await screen.findByLabelText('Project', { exact: true }), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '25.50' } });
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'New claim' } });
  fireEvent.change(screen.getByLabelText(/Receipt or document/), { target: { files: [new File(['receipt'], 'receipt.pdf')] } });
  fireEvent.submit(screen.getByRole('button', { name: 'Submit for approval' }).closest('form'));
  await screen.findByText('Older claim');
  expect(document.querySelector('.expense-card-list article').textContent).toContain('New claim');
});

it('shows the no-project assignment state only after assignments load', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [] });
  render(<Expenses />);
  expect((await screen.findByRole('status')).textContent).toContain('No projects assigned');
  expect(screen.getByText(/Contact your Project Admin/)).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Submit for approval' }).disabled).toBe(true);
});

it('treats ended and inactive assignments as unassigned', async () => {
  projectAPI.getAssignedProjects.mockResolvedValue({ data: [
    { id: 3, code: 'ATLAS', isActive: true, status: 'ACTIVE', assignments: [{ userId: 4, isActive: false }] },
    { id: 5, code: 'OLD', isActive: true, status: 'ACTIVE', assignments: [{ userId: 4, isActive: true, endDate: '2020-01-01' }] },
  ] });
  render(<Expenses />);
  expect((await screen.findByRole('status')).textContent).toContain('No projects assigned');
});

it('keeps expense form entries after a failed request', async () => {
  expenseAPI.save.mockRejectedValue({ userMessage: 'Something went wrong on our side. Please try again.' });
  render(<Expenses />);
  fireEvent.change(await screen.findByLabelText('Project', { exact: true }), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '25.50' } });
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Train to client site' } });
  fireEvent.change(screen.getByLabelText(/Receipt or document/), { target: { files: [new File(['receipt'], 'receipt.pdf')] } });
  fireEvent.submit(screen.getByRole('button', { name: 'Submit for approval' }).closest('form'));
  expect((await screen.findByRole('alert')).textContent).toContain('Something went wrong on our side');
  expect(screen.getByLabelText('Amount').value).toBe('25.50');
  expect(screen.getByLabelText('Description').value).toBe('Train to client site');
});

it('shows pending separately from approved spending and filters project history', async () => {
  expenseAPI.project.mockResolvedValue({ data: [
    { id: 1, employee_id: 4, employee_name: 'Alex', project_code: 'ATLAS', category: 'MEALS', amount: 20, expense_date: '2026-09-12', status: 'APPROVED', submitted_at: '2026-09-12' },
    { id: 2, employee_id: 5, employee_name: 'Sam', project_code: 'ATLAS', category: 'TRAVEL', amount: 30, expense_date: '2026-09-15', status: 'PENDING_APPROVAL', submitted_at: '2026-09-15' },
  ] });
  render(<ProjectExpenseHistory projectId={3} />);
  expect(await screen.findByText('$40.00')).toBeTruthy();
  expect(screen.getAllByText('$20.00').length).toBeGreaterThan(0);
  expect(screen.getByText('$60.00')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'PENDING_APPROVAL' } });
  expect(screen.getByRole('cell', { name: 'Sam' })).toBeTruthy();
  expect(screen.queryByRole('cell', { name: 'Alex' })).toBeNull();
});
