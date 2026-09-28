// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Register from './Register';

const { register } = vi.hoisted(() => ({ register: vi.fn() }));
vi.mock('../api', () => ({ authAPI: { register } }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('registers an employee and shows the activation email confirmation', async () => {
  register.mockResolvedValue({ data: { message: 'Check your work email for an activation link.' } });
  render(<MemoryRouter><Register /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Alice' } });
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Smith' } });
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'alice@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Register' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Check your work email');
  expect(register).toHaveBeenCalledWith({ firstName: 'Alice', lastName: 'Smith', email: 'alice@example.com' });
});
