// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, act } from '@testing-library/react';
import { CompanyProvider, useCompany, companyStorageKey } from './CompanyContext';

const { auth, api } = vi.hoisted(() => ({ auth: { user: { id: 5 } }, api: { context: vi.fn(), validateContext: vi.fn() } }));
vi.mock('./AuthContext', () => ({ useAuth: () => auth }));
vi.mock('./api', () => ({ companyAPI: api }));
const a = { id: 12, name: 'Company A' };
const b = { id: 13, name: 'Company B' };
const response = (companies, platformAdmin = false) => ({ data: { companies, memberships: platformAdmin ? [] : companies, platformAdmin } });

function Probe() {
  const company = useCompany();
  return <>
    <p data-testid="selected">{company.currentCompany?.name || 'none'}</p>
    <p data-testid="mode">{company.platformAdmin ? 'platform' : 'member'}</p>
    <p data-testid="loading">{String(company.loading)}</p>
    <p data-testid="manage-people">{String(company.hasCompanyCapability('canManageCompanyPeople'))}</p>
    <p data-testid="create-companies">{String(company.hasPlatformCapability('canCreateCompanies'))}</p>
    <p data-testid="review-project">{String(company.permissionsForProject(101)?.capabilities.canReviewWork === true)}</p>
    <p>{company.error}</p>
    <button onClick={() => company.selectCompany(13).catch(() => {})}>Select B</button>
    <button onClick={company.refreshCompanies}>Refresh</button>
    <button onClick={() => company.selectCompany('').catch(() => {})}>Platform view</button>
  </>;
}
const tree = () => <CompanyProvider><Probe /></CompanyProvider>;
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear(); auth.user = { id: 5 };
  api.context.mockResolvedValue(response([a]));
  api.validateContext.mockImplementation(id => Promise.resolve({ data: Number(id) === 12 ? a : b }));
});
afterEach(cleanup);

it('automatically validates and selects the only active company', async () => {
  render(tree());
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  expect(api.validateContext).toHaveBeenCalledWith(12);
  expect(localStorage.getItem(companyStorageKey(5))).toBe('12');
});

it('requires a choice for multiple companies, then validates and remembers it', async () => {
  api.context.mockResolvedValue(response([a,b])); render(tree());
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
  fireEvent.click(screen.getByText('Select B'));
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company B'));
  expect(localStorage.getItem(companyStorageKey(5))).toBe('13');
});

it('restores and revalidates selection on a reload', async () => {
  localStorage.setItem(companyStorageKey(5),'13'); api.context.mockResolvedValue(response([a,b])); render(tree());
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company B'));
  expect(api.validateContext).toHaveBeenCalledWith(13);
});

it('shows no company for an account without active memberships', async () => {
  api.context.mockResolvedValue(response([])); render(tree());
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(api.validateContext).not.toHaveBeenCalled();
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
});

it('keeps platform mode until an admin explicitly selects company metadata', async () => {
  api.context.mockResolvedValue(response([a],true)); render(tree());
  await waitFor(() => expect(screen.getByTestId('mode')).toHaveTextContent('platform'));
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
  expect(api.validateContext).not.toHaveBeenCalled();
});

it('discards removed membership without silently selecting another company', async () => {
  render(tree()); await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  api.context.mockResolvedValue(response([b])); fireEvent.click(screen.getByText('Refresh'));
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('none'));
  expect(localStorage.getItem(companyStorageKey(5))).toBeNull();
  expect(screen.getByText(/company access changed/)).toBeInTheDocument();
});

it('rejects unauthorized selection and clears current context', async () => {
  render(tree()); await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  api.validateContext.mockRejectedValue({ response: { status: 403, data: { message: 'Company access denied' } } });
  fireEvent.click(screen.getByText('Select B'));
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('none'));
  expect(screen.getByText('Company access denied')).toBeInTheDocument();
  expect(localStorage.getItem(companyStorageKey(5))).toBeNull();
});

it('handles removal between membership listing and validation', async () => {
  api.validateContext.mockRejectedValue({ response: { status: 403 } }); render(tree());
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
  expect(localStorage.getItem(companyStorageKey(5))).toBeNull();
});

it('clears the remembered company on logout and ignores late requests', async () => {
  let finish;
  render(tree()); await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  api.validateContext.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.click(screen.getByText('Select B'));
  act(() => window.dispatchEvent(new Event('chronos:auth-cleared')));
  await act(async () => finish({ data: b }));
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
  expect(localStorage.getItem(companyStorageKey(5))).toBeNull();
});

it('does not reuse another user\'s company selection', async () => {
  const view = render(tree()); await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  auth.user = { id: 6 }; api.context.mockResolvedValue(response([a,b])); view.rerender(tree());
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(screen.getByTestId('selected')).toHaveTextContent('none');
});

it('ignores a stale background validation when a newer selection wins', async () => {
  render(tree()); await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  let finish;
  api.validateContext.mockImplementation(id => Number(id) === 12 ? new Promise(resolve => { finish = resolve; }) : Promise.resolve({ data:b }));
  fireEvent.click(screen.getByText('Refresh'));
  await waitFor(() => expect(finish).toBeDefined());
  fireEvent.click(screen.getByText('Select B'));
  await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('Company B'));
  await act(async () => finish({ data:a }));
  expect(screen.getByTestId('selected')).toHaveTextContent('Company B');
});

const adminA = { ...a, permissions: { companyId:12, companyRoles:['COMPANY_ADMIN'], capabilities:{ canManageCompanyPeople:true },
  projects:[{ projectId:101, roles:['PROJECT_MANAGER'], capabilities:{ canReviewWork:true } }] } };
const userB = { ...b, permissions: { companyId:13, companyRoles:[], capabilities:{ canManageCompanyPeople:false }, projects:[] } };

it('switches scoped permissions without inheriting flat global roles', async () => {
  auth.user = { id:5, role:'ADMIN', roles:['COMPANY_ADMIN','PROJECT_MANAGER'] };
  api.context.mockResolvedValue(response([a,b]));
  localStorage.setItem(companyStorageKey(5),'12');
  api.validateContext.mockImplementation(id=>Promise.resolve({ data:Number(id)===12 ? adminA:userB }));
  render(tree());
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('true'));
  expect(screen.getByTestId('review-project')).toHaveTextContent('true');
  fireEvent.click(screen.getByText('Select B'));
  await waitFor(()=>expect(screen.getByTestId('selected')).toHaveTextContent('Company B'));
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
  expect(screen.getByTestId('review-project')).toHaveTextContent('false');
  expect(screen.getByTestId('create-companies')).toHaveTextContent('false');
});

it('clears capabilities during switching and on a failed permission refresh', async () => {
  api.validateContext.mockResolvedValue({data:adminA}); render(tree());
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('true'));
  let finish;
  api.validateContext.mockImplementation(()=>new Promise(resolve=>{ finish=resolve; }));
  fireEvent.click(screen.getByText('Select B'));
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
  await act(async()=>finish({data:userB}));
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
  api.context.mockRejectedValue(new Error('network unavailable'));
  fireEvent.click(screen.getByText('Refresh'));
  await screen.findByText(/Unable to load workspaces/);
  expect(screen.getByTestId('review-project')).toHaveTextContent('false');
});

it('role removal updates capabilities without requiring another login', async () => {
  api.validateContext.mockResolvedValue({data:adminA}); render(tree());
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('true'));
  api.validateContext.mockResolvedValue({data:{ ...a, permissions:{companyId:12,companyRoles:[],capabilities:{},projects:[]} }});
  fireEvent.click(screen.getByText('Refresh'));
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('false'));
  expect(screen.getByTestId('selected')).toHaveTextContent('Company A');
});

it('keeps platform capabilities separate and clears them on logout', async () => {
  api.context.mockResolvedValue({data:{ ...response([a],true).data, platformPermissions:{roles:['PLATFORM_ADMIN'],capabilities:{canCreateCompanies:true}} }});
  render(tree());
  await waitFor(()=>expect(screen.getByTestId('create-companies')).toHaveTextContent('true'));
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
  act(()=>window.dispatchEvent(new Event('chronos:auth-cleared')));
  expect(screen.getByTestId('create-companies')).toHaveTextContent('false');
});

it('rejects permissions labeled for a different company', async () => {
  api.validateContext.mockResolvedValue({data:{...a,permissions:userB.permissions}}); render(tree());
  await waitFor(()=>expect(screen.getByTestId('selected')).toHaveTextContent('Company A'));
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
});

it('denies previously granted capabilities when revalidation fails, then recovers', async () => {
  api.validateContext.mockResolvedValue({data:adminA}); render(tree());
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('true'));
  api.context.mockRejectedValueOnce(new Error('network unavailable'));
  fireEvent.click(screen.getByText('Refresh'));
  await screen.findByText(/Unable to load workspaces/);
  expect(screen.getByTestId('manage-people')).toHaveTextContent('false');
  expect(screen.getByTestId('review-project')).toHaveTextContent('false');
  fireEvent.click(screen.getByText('Refresh'));
  await waitFor(()=>expect(screen.getByTestId('manage-people')).toHaveTextContent('true'));
});
