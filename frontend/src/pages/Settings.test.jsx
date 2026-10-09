// @vitest-environment jsdom
import React from 'react';
import '@testing-library/jest-dom/vitest';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import Settings from './Settings';
import {settingsAPI} from '../api';
vi.mock('../api');
const context={currentCompany:{id:12,name:'Company A'},companyCapabilities:{canManageCompanySettings:true},platformAdmin:false,platformCapabilities:{}};
vi.mock('../CompanyContext',()=>({useCompany:()=>context}));
const values={company_name:'Company A','vacation_days_per_year':'15','sick_days_per_year':'5','bereavement_days_per_year':'3','timesheet.reminders.enabled':'true'};
const record=(companyId=12,version=2)=>({companyId,version,values:{...values,company_name:companyId===12?'Company A':'Company B'}});
beforeEach(()=>{
  vi.resetAllMocks();Object.assign(context,{currentCompany:{id:12,name:'Company A'},companyCapabilities:{canManageCompanySettings:true},platformAdmin:false,platformCapabilities:{},loading:false,switching:false});
  settingsAPI.company.mockResolvedValue({data:record()});
});
afterEach(cleanup);
it('loads and edits only selected-company settings with the expected revision',async()=>{
  settingsAPI.updateCompany.mockResolvedValue({data:{...record(12,3),values:{...values,company_name:'A Limited'}}});
  render(<Settings/>);fireEvent.click(await screen.findByRole('button',{name:'Change Company display name'}));
  fireEvent.change(screen.getByLabelText('Company display name'),{target:{value:'A Limited'}});
  fireEvent.click(screen.getByRole('button',{name:'Save',exact:true}));
  expect(await screen.findByRole('status')).toHaveTextContent('Setting updated.');
  expect(settingsAPI.updateCompany).toHaveBeenCalledWith(12,'company_name','A Limited',2);
  expect(settingsAPI.getAllSettings).not.toHaveBeenCalled();expect(settingsAPI.applyLeaveDefaults).not.toHaveBeenCalled();
  expect(screen.queryByRole('button',{name:/Apply .*policy/})).toBeNull();
});
it('keeps failed drafts and requires reload after a concurrent edit',async()=>{
  settingsAPI.updateCompany.mockRejectedValue({response:{status:409,data:{message:'Settings changed. Reload and try again.'}}});
  render(<Settings/>);fireEvent.click(await screen.findByRole('button',{name:'Change Paid vacation days'}));
  fireEvent.change(screen.getByLabelText('Paid vacation days'),{target:{value:'20.25'}});fireEvent.click(screen.getByRole('button',{name:'Save',exact:true}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Settings changed');
  expect(screen.getByLabelText('Paid vacation days')).toHaveValue(20.25);expect(screen.getByRole('button',{name:'Save',exact:true})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'Reload settings'}));
  await screen.findByRole('button',{name:'Change Paid vacation days'});expect(settingsAPI.company).toHaveBeenCalledTimes(2);
});
it('discards a delayed response after switching companies',async()=>{
  let resolve;settingsAPI.company.mockImplementationOnce(()=>new Promise(r=>{resolve=r;})).mockResolvedValueOnce({data:record(13)});
  const view=render(<Settings/>);context.currentCompany={id:13,name:'Company B'};view.rerender(<Settings/>);
  await screen.findByRole('button',{name:'Change Company display name'});resolve({data:record(12)});
  await waitFor(()=>expect(screen.queryByText('Company A',{exact:true})).toBeNull());expect(screen.getByText('Company B',{selector:'strong'})).toBeInTheDocument();
});
it('does not display or request company settings without scoped permission',()=>{
  context.companyCapabilities={};render(<Settings/>);expect(screen.getByRole('alert')).toBeInTheDocument();expect(settingsAPI.company).not.toHaveBeenCalled();
});
it('platform settings use only the platform API even with a selected company',async()=>{
  context.platformAdmin=true;context.platformCapabilities={canConfigurePlatform:true};
  settingsAPI.platform.mockResolvedValue({data:{version:4,values:{'platform.timesheet.reminders.enabled':'true'}}});
  settingsAPI.updatePlatform.mockResolvedValue({data:{version:5,values:{'platform.timesheet.reminders.enabled':'false'}}});
  render(<Settings platform/>);fireEvent.click(await screen.findByRole('button',{name:'Reminder delivery'}));
  expect(await screen.findByRole('status')).toHaveTextContent('Setting updated');
  expect(settingsAPI.updatePlatform).toHaveBeenCalledWith('platform.timesheet.reminders.enabled','false',4);expect(settingsAPI.company).not.toHaveBeenCalled();
  expect(screen.queryByText('Paid vacation days')).toBeNull();
});
it('rejects a company mismatch in the API response',async()=>{
  settingsAPI.company.mockResolvedValue({data:record(13)});render(<Settings/>);
  await screen.findByRole('alert');expect(screen.queryByRole('button',{name:'Change Company display name'})).toBeNull();
});
it('ignores a delayed save when switching to a company without permission',async()=>{
  let finish;settingsAPI.updateCompany.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  const view=render(<Settings/>);fireEvent.click(await screen.findByRole('button',{name:'Timesheet reminders'}));
  context.currentCompany={id:13,name:'Company B'};context.companyCapabilities={};view.rerender(<Settings/>);
  finish({data:record(12,3)});await waitFor(()=>expect(screen.queryByText('Setting updated.')).toBeNull());
  expect(screen.getByRole('alert')).toHaveTextContent('permission');
});
