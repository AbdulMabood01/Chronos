// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,fireEvent,cleanup,waitFor} from '@testing-library/react';
import LetterSettings from './LetterSettings';
import {companyLetterRequestAPI} from '../api';
vi.mock('../api');
vi.mock('../CompanyContext',()=>({useCompany:()=>({currentCompany:{id:12,name:'Example Company'}})}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('collects company and HR details once without template editors',async()=>{
  const configuration={version:2,employers:[{id:'e',name:'Example Company',address:'100 Street',email:'hr@example.com'}],signatories:[{id:'h',name:'HR Name',title:'HR Director'}],templates:[]};
  const api={configuration:vi.fn().mockResolvedValue({data:configuration}),saveConfiguration:vi.fn().mockResolvedValue({data:configuration})};
  companyLetterRequestAPI.mockReturnValue(api);
  render(<LetterSettings/>);
  await screen.findByLabelText('Legal employer name');
  expect(screen.getAllByLabelText('HR signatory name')).toHaveLength(1);
  expect(screen.queryByText('Letter templates')).toBeNull();
  expect(screen.queryByLabelText('Letter wording')).toBeNull();
  fireEvent.change(screen.getByLabelText('HR signatory name'),{target:{value:'Casey HR'}});
  fireEvent.click(screen.getByRole('button',{name:'Save letter settings'}));
  await waitFor(()=>expect(api.saveConfiguration).toHaveBeenCalledWith(expect.objectContaining({version:2,signatories:[expect.objectContaining({name:'Casey HR'})]})));
});
