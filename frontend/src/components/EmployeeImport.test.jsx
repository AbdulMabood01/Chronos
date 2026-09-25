// @vitest-environment jsdom
import React from 'react';
import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import EmployeeImport from './EmployeeImport';
import { userAPI } from '../api';
vi.mock('../api', () => ({ userAPI: { importEmployees: vi.fn() } }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('uploads the selected workbook and reports the imported count', async () => {
  userAPI.importEmployees.mockResolvedValue({ data: { imported: 2 } });
  const onImported = vi.fn();
  render(<EmployeeImport onImported={onImported} />);
  const file = new File(['workbook'], 'employees.xlsx');
  fireEvent.change(screen.getByLabelText('Excel spreadsheet'), { target: { files: [file] } });
  fireEvent.submit(screen.getByRole('button', { name: 'Import spreadsheet' }).closest('form'));
  await vi.waitFor(() => expect(onImported).toHaveBeenCalledWith(2));
  expect(userAPI.importEmployees).toHaveBeenCalledWith(file);
});

it('keeps row validation errors visible so the workbook can be corrected', async () => {
  userAPI.importEmployees.mockRejectedValue({ response: { data: { message: 'Row 3: duplicate email in spreadsheet.' } } });
  render(<EmployeeImport onImported={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('Excel spreadsheet'), { target: { files: [new File(['x'], 'employees.xls')] } });
  fireEvent.submit(screen.getByRole('button', { name: 'Import spreadsheet' }).closest('form'));
  expect((await screen.findByRole('alert')).textContent).toContain('Row 3');
});

it('rejects unsupported files without sending a request', () => {
  render(<EmployeeImport onImported={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('Excel spreadsheet'), { target: { files: [new File(['x'], 'employees.csv')] } });
  expect(screen.getByRole('alert').textContent).toContain('.xlsx or .xls');
  expect(screen.getByRole('button', { name: 'Import spreadsheet' }).disabled).toBe(true);
  expect(userAPI.importEmployees).not.toHaveBeenCalled();
});
