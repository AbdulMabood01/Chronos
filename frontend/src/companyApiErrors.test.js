// @vitest-environment jsdom
import {afterEach,expect,it} from 'vitest';
import {AxiosError} from 'axios';
import apiClient from './api';
const originalAdapter=apiClient.defaults.adapter;
afterEach(()=>{apiClient.defaults.adapter=originalAdapter;});
function fail(message,status=409){
  apiClient.defaults.adapter=async config=>{throw new AxiosError('Request failed',undefined,config,undefined,{status,data:{message},headers:{},config});};
}
it('preserves company and platform settings revision conflicts',async()=>{
  const message='Settings changed. Reload and try again.';fail(message);
  await expect(apiClient.put('/companies/12/settings/company_name',{})).rejects.toMatchObject({userMessage:message});
  await expect(apiClient.put('/platform/settings/platform.timesheet.reminders.enabled',{})).rejects.toMatchObject({userMessage:message});
});
it('preserves actionable company lifecycle conflicts',async()=>{
  const message='Transfer project ownership and reassign manager/approver duties before removing this member';fail(message);
  await expect(apiClient.put('/companies/12/members/7/status',{status:'REMOVED',version:0})).rejects.toMatchObject({userMessage:message,response:{data:{message}}});
});
it('preserves employment revision conflicts',async()=>{
  const message='Employment details changed. Reload and try again.';fail(message);
  await expect(apiClient.put('/companies/12/members/7/employment',{})).rejects.toMatchObject({userMessage:message});
});
it('does not expose unrecognized server details from company APIs',async()=>{
  fail('private database detail');
  await expect(apiClient.put('/companies/12/members/7/status',{})).rejects.toMatchObject({userMessage:'Unable to complete the request. Please try again.',response:{data:{message:'Unable to complete the request. Please try again.'}}});
});
