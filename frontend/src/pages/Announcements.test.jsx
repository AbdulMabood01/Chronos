// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
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
  expect(screen.queryByText(item.content)).toBeNull();
});
it('keeps the homepage clear when there are no announcements or the feed is unavailable', async () => {
  api.list.mockResolvedValue({ data:[] });
  const view = render(<MemoryRouter><AnnouncementPanel/></MemoryRouter>);
  expect(view.container.innerHTML).toBe('');
  await waitFor(() => expect(api.list).toHaveBeenCalled());
  expect(view.container.innerHTML).toBe('');
  cleanup();
  api.list.mockRejectedValue(new Error('offline'));
  const failed = render(<MemoryRouter><AnnouncementPanel/></MemoryRouter>);
  await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  expect(failed.container.innerHTML).toBe('');
});
it('skips acknowledged announcements and keeps viewed but unacknowledged announcements visible', async () => {
  api.list.mockResolvedValue({ data:[{ ...item, acknowledged_at:'2026-09-23T12:00:00Z' }, { ...item, id:'b', title:'Still pending', viewed_at:'2026-09-23T12:00:00Z' }] });
  render(<MemoryRouter><AnnouncementPanel/></MemoryRouter>);
  const link = await screen.findByRole('link', { name:/Still pending/ });
  expect(link.getAttribute('href')).toBe('/announcements?id=b');
  expect(screen.queryByText('Office update')).toBeNull();
});
it('hides the banner when every announcement has been acknowledged', async () => {
  api.list.mockResolvedValue({ data:[{ ...item, acknowledged_at:'2026-09-23T12:00:00Z' }] });
  render(<MemoryRouter><AnnouncementPanel/></MemoryRouter>);
  await waitFor(() => expect(api.list).toHaveBeenCalled());
  expect(screen.queryByRole('region', { name:'Company announcements' })).toBeNull();
});
it('opens the specific announcement from a single compact homepage banner', async () => {
  api.list.mockResolvedValue({ data:[item, { ...item, id:'b', title:'Another update' }] });
  render(<MemoryRouter initialEntries={['/dashboard']}><Routes>
    <Route path="/dashboard" element={<AnnouncementPanel/>}/>
    <Route path="/announcements" element={<Announcements/>}/>
  </Routes></MemoryRouter>);
  fireEvent.click(await screen.findByRole('link', { name:/Office update/ }));
  await screen.findByRole('heading', { name:'Office update' });
  expect(api.open).toHaveBeenCalledWith('a', false);
  expect(screen.getByText(item.content)).toBeTruthy();
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
it('allows Admins to create drafts and see pending employees', async () => {
  user.role='ADMIN'; mount();
  await waitFor(() => expect(api.list).toHaveBeenCalledWith(true));
  await screen.findByRole('button', { name:/Office update/ });
  expect(screen.queryByRole('button', { name:'Employee view' })).toBeNull();
  expect(screen.getByRole('combobox', { name:'Show' })).toBeTruthy();
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
