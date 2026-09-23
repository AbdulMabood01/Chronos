// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FeedbackPage, PerformanceReviewsPage } from './FeedbackReviews';
import { feedbackReviewsAPI as api } from '../api';
vi.mock('../api', () => ({ feedbackReviewsAPI: { feedback: vi.fn(), submit: vi.fn(), employees: vi.fn(), reviews: vi.fn(), save: vi.fn(), publish: vi.fn(), audit: vi.fn() } }));
const user = { id: 1, role: 'EMPLOYEE' };
vi.mock('../AuthContext', () => ({ useAuth: () => ({ user }) }));
afterEach(cleanup);
beforeEach(() => { vi.clearAllMocks(); user.role = 'EMPLOYEE'; api.feedback.mockResolvedValue({ data: [] }); });
it('shows anonymous identity and filters received feedback by year', async () => {
  api.feedback.mockResolvedValue({ data: [
    { id:'a', content:'Thanks for helping', anonymous:true, sender_name:'Must stay hidden', category:'APPRECIATION', sender_type:'Manager', submitted_at:'2026-08-01T12:00:00Z' },
    { id:'b', content:'Older feedback', anonymous:false, sender_name:'Jane', sender_type:'Employee', submitted_at:'2025-08-01T12:00:00Z' },
  ] });
  render(<FeedbackPage/>);
  await screen.findByText('Thanks for helping');
  expect(screen.queryByText(/Must stay hidden/)).toBeNull();
  fireEvent.change(screen.getByLabelText('Year'), { target: { value:'2026' } });
  expect(screen.queryByText('Older feedback')).toBeNull();
});
it('submits anonymous feedback after employee selection and shows sender history', async () => {
  api.employees.mockResolvedValue({ data: [{ id:2, first_name:'Jane', last_name:'Doe', email:'jane@example.com' }] });
  api.submit.mockResolvedValue({ data:'id' });
  render(<FeedbackPage/>);
  fireEvent.click(screen.getByRole('button', { name:'Submit Feedback' }));
  fireEvent.change(screen.getByLabelText('Search employee by name or email'), { target: { value:'jane@example.com' } });
  fireEvent.click(await screen.findByRole('button', { name:/Jane Doe/ }));
  fireEvent.change(screen.getByLabelText(/General Feedback/), { target: { value:'Thank you!' } });
  fireEvent.click(screen.getByLabelText('Anonymous Feedback'));
  expect(screen.getByText(/your identity will be hidden/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name:'Submit anonymous feedback' }));
  await waitFor(() => expect(api.submit).toHaveBeenCalledWith({ employeeId:2, content:'General Feedback\nThank you!', category:null, anonymous:true }));
  await waitFor(() => expect(api.feedback).toHaveBeenLastCalledWith(true));
});
it('defaults to latest published review and keeps employee review history read-only', async () => {
  api.reviews.mockResolvedValue({ data:[{ id:'a', review_year:2026, quarter:3, summary:'Latest summary', published_at:'2026-09-20T12:00:00Z' }, { id:'b', review_year:2025, quarter:1, summary:'Historical summary', published_at:'2025-04-01T12:00:00Z' }] });
  render(<PerformanceReviewsPage/>);
  await screen.findByText('Performance Review — Q3 2026');
  expect(screen.queryByRole('button', { name:'Edit review' })).toBeNull();
  expect(screen.queryByRole('button', { name:'Create quarterly review' })).toBeNull();
  fireEvent.change(screen.getByLabelText('Year'), { target: { value:'2025' } });
  fireEvent.change(screen.getByLabelText('Quarter'), { target: { value:'1' } });
  expect(screen.getByText('Historical summary')).toBeTruthy();
});
it('lets a Super Admin create a draft and explicitly publish it', async () => {
  user.role = 'SUPER_ADMIN';
  api.employees.mockResolvedValue({ data:[{ id:2, first_name:'Jane', last_name:'Doe', email:'jane@example.com' }] });
  api.reviews.mockResolvedValue({ data:[] });
  api.save.mockResolvedValue({ data:'draft' });
  api.publish.mockResolvedValue({ data:null });
  render(<PerformanceReviewsPage/>);
  fireEvent.change(screen.getByLabelText('Search employee by name or email'), { target:{ value:'Jane' } });
  fireEvent.click(await screen.findByRole('button', { name:/Jane Doe/ }));
  await waitFor(() => expect(screen.getByRole('button', { name:'Create quarterly review' }).disabled).toBe(false));
  fireEvent.click(screen.getByRole('button', { name:'Create quarterly review' }));
  fireEvent.change(screen.getByLabelText('Overall performance summary (required)'), { target:{ value:'Quarterly summary' } });
  api.reviews.mockResolvedValue({ data:[{ id:'draft', employee_id:2, review_year:2026, quarter:3, summary:'Quarterly summary', version:0 }] });
  fireEvent.click(screen.getByRole('button', { name:'Save review' }));
  await screen.findByRole('button', { name:'Publish to employee' });
  expect(api.save).toHaveBeenCalledWith(undefined, expect.objectContaining({ employeeId:2, summary:'Quarterly summary', version:0 }));
  expect(api.publish).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name:'Publish to employee' }));
  await waitFor(() => expect(api.publish).toHaveBeenCalledWith('draft',0));
});
it('retains all sections after an error and prevents duplicate submissions while saving', async () => {
  api.employees.mockResolvedValue({ data: [{ id:2, first_name:'Jane', last_name:'Doe', email:'jane@example.com' }] });
  let rejectSubmission;
  api.submit.mockImplementation(() => new Promise((resolve, reject) => { rejectSubmission = reject; }));
  render(<FeedbackPage/>);
  fireEvent.click(screen.getByRole('button', { name:'Submit Feedback' }));
  expect(screen.getByRole('button', { name:'Submit identified feedback' }).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Search employee by name or email'), { target:{ value:'Jane' } });
  fireEvent.click(await screen.findByRole('button', { name:/Jane Doe/ }));
  const values = ['Thoughts', 'Improve updates', 'Great teamwork', 'Try a demo', 'Thanks'];
  screen.getAllByRole('textbox').filter(input => input.tagName === 'TEXTAREA').forEach((input, index) => fireEvent.change(input, { target:{ value:values[index] } }));
  fireEvent.click(screen.getByRole('button', { name:'Submit identified feedback' }));
  expect(screen.getByRole('button', { name:'Submitting...' }).disabled).toBe(true);
  await waitFor(() => expect(api.submit).toHaveBeenCalledTimes(1));
  expect(api.submit.mock.calls[0][0].content).toBe('General Feedback\nThoughts\n\nAreas for Improvement\nImprove updates\n\nContinue the Great Work\nGreat teamwork\n\nSuggestions / Ideas\nTry a demo\n\nAdditional Comments\nThanks');
  rejectSubmission({ response:{ data:{ message:'Please try again later.' } } });
  await screen.findByRole('alert');
  expect(screen.getByDisplayValue('Try a demo')).toBeTruthy();
  expect(screen.getByRole('button', { name:'Submit identified feedback' }).disabled).toBe(false);
});
it('lets Super Admin browse all employees and open the selected review period', async () => {
  user.role = 'SUPER_ADMIN';
  const review = { id:'review', employee_id:2, first_name:'Jane', last_name:'Doe', employee_name:'Jane Doe', employee_email:'jane@example.com', review_year:2025, quarter:2, summary:'Earlier review', version:0 };
  api.reviews.mockResolvedValue({ data:[review] });
  render(<PerformanceReviewsPage/>);
  fireEvent.click(await screen.findByRole('button', {name:/Jane Doe.*View review/}));
  await waitFor(() => expect(api.reviews).toHaveBeenLastCalledWith(2));
  await screen.findByText('Earlier review');
  expect(screen.getByLabelText('Year').value).toBe('2025');
  expect(screen.getByLabelText('Quarter').value).toBe('2');
  expect(screen.getByRole('button', {name:'Publish to employee'})).toBeTruthy();
});
it.each(['EMPLOYEE', 'ADMIN'])('keeps %s performance reviews personal and read-only', async role => {
  user.role = role;
  api.reviews.mockResolvedValue({ data:[{ id:'own', employee_id:1, review_year:2026, quarter:2, summary:'My published review', published_at:'2026-07-01T12:00:00Z' }] });
  render(<PerformanceReviewsPage/>);
  await screen.findByText('My published review');
  expect(api.reviews).toHaveBeenCalledWith(undefined);
  expect(screen.queryByLabelText('Search employee by name or email')).toBeNull();
  for (const name of ['All employee reviews', 'Create quarterly review', 'Edit review', 'Publish to employee', 'View audit history', 'Save review']) {
    expect(screen.queryByRole('button', {name})).toBeNull();
  }
  expect(screen.queryByRole('textbox')).toBeNull();
});
