// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,fireEvent,cleanup} from '@testing-library/react';
import TeamLeaveCalendar from './TeamLeaveCalendar';
import {companyLeaveAPI} from '../api';
vi.mock('../api');
vi.mock('../CompanyContext',()=>({useCompany:()=>({currentCompany:{id:12}})}));
afterEach(cleanup);
it('shows observed holidays without requiring an absence on the holiday',async()=>{
  companyLeaveAPI.calendar.mockResolvedValue({data:[]});
  render(<TeamLeaveCalendar/>);
  fireEvent.change(screen.getByLabelText('Calendar month'),{target:{value:'2026-07'}});
  const holiday=await screen.findByRole('button',{name:'July 3, 2026: 0 away: Independence Day (observed)'});
  expect(holiday.className).toContain('has-holiday');
  fireEvent.click(holiday);
  expect(await screen.findByText(/^: excluded from leave deductions\.$/)).toBeTruthy();
});
