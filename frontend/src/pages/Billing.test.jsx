// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Billing, { Pricing } from './Billing';

const { api, people, workspace } = vi.hoisted(() => ({
  api: Object.fromEntries(['catalog', 'summary', 'profile', 'quote', 'checkout', 'purchase', 'trial', 'renewalPreference', 'employeeAccess', 'receipt', 'reissue', 'refund'].map(k => [k, vi.fn()])),
  people: vi.fn(), workspace: { currentCompany: { id: 12, name: 'Atlas' }, refreshCompanies: vi.fn(), selectCompany: vi.fn() },
}));
vi.mock('../api', () => ({ billingAPI: api, companyAPI: { members: people } }));
vi.mock('../CompanyContext', () => ({ useCompany: () => workspace }));
const catalog = { version: '2026-10-v1', plans: [
  { key: 'FREE', name: 'Free', projects: 1, users: 7, monthlyCents: 0 },
  { key: 'PRO', name: 'Pro', projects: 3, users: 75, monthlyCents: 14900 },
  { key: 'PRO_PLUS', name: 'Pro Plus', projects: 7, users: 175, monthlyCents: 29900 },
  { key: 'PRO_MAX', name: 'Pro Max', projects: 15, users: 375, monthlyCents: 59900 },
], discounts: { 3: 0, 6: 10, 12: 20 } };
let summary;
beforeEach(() => {
  vi.clearAllMocks(); workspace.currentCompany = { id: 12, name: 'Atlas' };
  workspace.refreshCompanies.mockResolvedValue(); workspace.selectCompany.mockResolvedValue();
  summary = { entitlement: { plan: 'FREE', source: 'FREE', projects: 1, includedUsers: 7, extraSeats: 0, activeUsers: 1, reservations: 0, openProjects: 0 },
    profile: { revision: 0, trial_used: false }, checkoutEnabled: false, terms: [], purchases: [], activity: [] };
  api.catalog.mockResolvedValue({ data: catalog }); api.summary.mockImplementation(async () => ({ data: summary }));
  people.mockResolvedValue({ data: [] });
  api.quote.mockResolvedValue({ data: { id: 'quote-1', plan_key: 'PRO', kind: 'PLAN', term_months: 12, extra_seats: 5,
    subtotal_cents: 202800, discount_cents: 40560, amount_cents: 162240, starts_at: '2026-10-07T00:00:00Z', ends_at: '2027-10-07T00:00:00Z', expires_at: '2026-10-07T02:00:00Z' } });
});
afterEach(cleanup);
const open = () => render(<MemoryRouter><Billing /></MemoryRouter>);
it('shows company pricing and actual prepaid totals rather than a monthly charge', async () => {
  render(<MemoryRouter><Pricing /></MemoryRouter>); await screen.findByText('Pro Max');
  expect(screen.getByText('$1,430.40')).toBeInTheDocument(); expect(screen.getByText('$2,870.40')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Prepaid term'), { target: { value: '6' } });
  expect(screen.getByText('$804.60')).toBeInTheDocument(); expect(screen.getByText('$1,614.60')).toBeInTheDocument();
  expect(screen.getByText(/One-off payments. No automatic renewal/)).toBeInTheDocument();
});
it('requests an explicit quote with only company-scoped selections and keeps unconfigured checkout disabled', async () => {
  open(); await screen.findByText('Current plan: Free');
  fireEvent.change(screen.getByLabelText('Extra employee seats beyond the selected plan'), { target: { value: '5' } });
  fireEvent.click(screen.getByRole('button', { name: 'Review prepaid quote' }));
  const quote = await screen.findByRole('region', { name: 'Purchase quote' });
  expect(api.quote).toHaveBeenCalledWith(12, { plan: 'PRO', months: 12, extraSeats: 5, kind: 'PLAN' });
  expect(within(quote).getByText(/Pay once: \$1,622.40/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Continue to secure payment' })).toBeDisabled(); expect(api.checkout).not.toHaveBeenCalled();
});
it('starts a trial only after an explicit administrator action', async () => {
  api.trial.mockResolvedValue({}); open(); await screen.findByText('Current plan: Free'); expect(api.trial).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Start 30-day Pro Plus trial' }));
  await waitFor(() => expect(api.trial).toHaveBeenCalledWith(12)); expect(await screen.findByText('Your 30-day Pro Plus trial has started.')).toBeInTheDocument();
});
it('quotes additional seats against the active term without renewing it', async () => {
  summary.entitlement = { ...summary.entitlement, plan: 'PRO', source: 'PAID', termId: 'term-1', projects: 3, includedUsers: 75, endsAt: '2027-01-07T00:00:00Z' };
  summary.terms = [{ id: 'term-1', term_months: 3 }]; open(); await screen.findByText('Current plan: Pro');
  fireEvent.change(screen.getByLabelText('Additional seats'), { target: { value: '10' } }); fireEvent.click(screen.getByRole('button', { name: 'Review prorated seat quote' }));
  await waitFor(() => expect(api.quote).toHaveBeenCalledWith(12, { plan: 'PRO', months: 3, extraSeats: 10, kind: 'SEATS' }));
});
it('employee access changes use the selected company and current membership revision', async () => {
  people.mockResolvedValue({ data: [{ user_id: 4, first_name: 'Taylor', last_name: 'Lee', status: 'ACTIVE', membership_version: 3, workforce_enabled: false }] }); api.employeeAccess.mockResolvedValue({});
  open(); fireEvent.click(await screen.findByRole('button', { name: 'Enable employee access' }));
  await waitFor(() => expect(api.employeeAccess).toHaveBeenCalledWith(12, 4, { enabled: true, version: 3 }));
});
it('shows a billing permission denial without leaking other company data', async () => {
  api.summary.mockRejectedValue({ response: { data: { message: 'Company Admin billing permission required' } } });
  open(); expect(await screen.findByRole('alert')).toHaveTextContent('Company Admin billing permission required'); expect(screen.queryByText('Billing details')).not.toBeInTheDocument();
});
it('supports billing-only suspended access without requesting the operational roster', async () => {
  workspace.currentCompany.is_suspended = true; open(); await screen.findByText('Current plan: Free');
  expect(people).not.toHaveBeenCalled(); expect(screen.getByText(/Payment cannot lift suspension/i)).toBeInTheDocument();
});
