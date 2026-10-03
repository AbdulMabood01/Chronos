// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import RequestAccess from './RequestAccess';

const { requestAccess } = vi.hoisted(() => ({ requestAccess: vi.fn() }));
vi.mock('../api', () => ({ companyAPI: { requestAccess } }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('requests access without creating an account or granting access', async () => {
  requestAccess.mockResolvedValue({ data: { message: 'Request received.' } });
  render(<MemoryRouter><RequestAccess /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Workspace ID'), { target: { value: 'acme' } });
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Alice' } });
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Smith' } });
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'alice@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send request' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Request received.');
  expect(requestAccess).toHaveBeenCalledWith({ slug: 'acme', firstName: 'Alice', lastName: 'Smith', email: 'alice@example.com' });
});
