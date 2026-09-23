// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Announcements, { AnnouncementPanel } from './Announcements';
import { announcementAPI as api } from '../api';
vi.mock('../api', () => ({ announcementAPI: { list: vi.fn(), open: vi.fn(), save: vi.fn(), status: vi.fn(), remove: vi.fn(), acknowledge: vi.fn(), tracking: vi.fn(), attachment: vi.fn() } }));
const user = { role: 'EMPLOYEE' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user }) }));
const item = { id:'a', title:'Office update', content:'Please read this update.', priority:'IMPORTANT', status:'PUBLISHED', publish_date:'2026-09-22', acknowledgment_required:true, version:0 };
const mount = (path = '/announcements') => render(<MemoryRouter initialEntries={[path]}><Announcements/></MemoryRouter>);
afterEach(cleanup);
beforeEach(() => { vi.resetAllMocks(); user.role = 'EMPLOYEE'; api.list.mockResolvedValue({ data:[item] }); api.open.mockResolvedValue({ data:{ ...item, viewed_at:'2026-09-22T12:00:00Z' } }); });
it('shows recent unread announcements on the dashboard without recording views', async () => {
  render(<MemoryRouter><AnnouncementPanel/></MemoryRouter>);
  await screen.findByText('Office update');
  expect(screen.getByText('Unread')).toBeTruthy();
  expect(screen.getByRole('link', { name:/Office update/ }).getAttribute('href')).toBe('/announcements?id=a');
  expect(api.open).not.toHaveBeenCalled();
});
it('lets employees open and acknowledge the current version without management controls', async () => {
  mount();
  fireEvent.click(await screen.findByRole('button', { name:/Office update/ }));
  await screen.findByRole('button', { name:'Acknowledge announcement' });
  expect(api.open).toHaveBeenCalledWith('a', false);
  expect(screen.queryByRole('button', { name:'Edit' })).toBeNull();
  expect(screen.queryByRole('button', { name:'Manage announcements' })).toBeNull();
  api.acknowledge.mockResolvedValue({}); api.open.mockResolvedValue({ data:{ ...item, viewed_at:'now', acknowledged_at:'now' } });
  fireEvent.click(screen.getByRole('button', { name:'Acknowledge announcement' }));
  await screen.findByText('✓ You acknowledged this announcement.');
  expect(api.acknowledge).toHaveBeenCalledWith('a',0);
});
it('keeps failed acknowledgments available for retry', async () => {
  mount('/announcements?id=a');
  api.acknowledge.mockRejectedValue({ response:{ data:{ message:'Announcement changed; reopen it before continuing' } } });
  fireEvent.click(await screen.findByRole('button', { name:'Acknowledge announcement' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('✓ You acknowledged this announcement.')).toBeNull();
  await waitFor(() => expect(screen.getByRole('button', { name:'Acknowledge announcement' }).disabled).toBe(false));
});
it('allows Super Admins to create drafts and see pending employees', async () => {
  user.role='SUPER_ADMIN'; mount();
  fireEvent.click(screen.getByRole('button', { name:'Manage announcements' }));
  await waitFor(() => expect(api.list).toHaveBeenCalledWith(true));
  await screen.findByRole('button', { name:/Office update/ });
  fireEvent.click(screen.getByRole('button', { name:'New announcement' }));
  fireEvent.change(screen.getByLabelText('Title'), { target:{ value:'New update' } });
  fireEvent.change(screen.getByLabelText('Message'), { target:{ value:'Message for everyone' } });
  fireEvent.click(screen.getByLabelText('Require employee acknowledgment'));
  api.save.mockResolvedValue({ data:'a' });
  fireEvent.click(screen.getByRole('button', { name:'Save announcement' }));
  await waitFor(() => expect(api.save).toHaveBeenCalledWith(undefined, expect.objectContaining({ title:'New update', status:'DRAFT', acknowledgmentRequired:true })));
  api.tracking.mockResolvedValue({ data:{ total:2, viewed:1, acknowledged:1, employees:[{ id:1, name:'Pending Person', email:'pending@example.com' }, { id:2, name:'Done Person', email:'done@example.com', viewed_at:'now', acknowledged_at:'now' }] } });
  fireEvent.click(await screen.findByRole('button', { name:'View tracking' }));
  await screen.findByText('Pending Person');
  expect(screen.queryByText('Done Person')).toBeNull();
  fireEvent.click(screen.getByLabelText('Show only employees who have not acknowledged'));
  expect(screen.getByText('Done Person')).toBeTruthy();
});
it('displays a useful empty state and handles feed failures', async () => {
  api.list.mockResolvedValue({ data:[] }); mount();
  await screen.findByText("You're all caught up");
  cleanup(); api.list.mockRejectedValue(new Error('offline')); mount();
  await screen.findByRole('alert');
  expect(screen.queryByText("You're all caught up")).toBeNull();
});
