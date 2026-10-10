const {test,expect}=require('@playwright/test');
async function fixture(page){
 const companies=[{id:12,name:'Company A',slug:'company-a'},{id:13,name:'Company B',slug:'company-b'}];
 let configuration={version:-1,employers:[],signatories:[],templates:['EMPLOYMENT_VERIFICATION','TRAVEL','VACATION'].map(type=>({type,enabled:false,body:'This confirms {{employee_name}} works at {{employer_name}} as {{job_title}} since {{joining_date}}.',employerId:'',signatoryId:'',signatureRequired:false}))};
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);localStorage.setItem('chronos:company:5','12');});
 await page.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let body=[];
  if(path==='/api/auth/me')body={id:5,email:'admin@example.com',firstName:'Jordan',lastName:'Admin',profileCompleted:true};
  else if(path==='/api/companies/context')body={platformAdmin:false,companies,memberships:companies,platformPermissions:{}};
  else if(/\/companies\/\d+\/context$/.test(path)){const c=companies.find(c=>String(c.id)===path.split('/')[3]);body={...c,permissions:{companyId:c.id,companyRoles:['COMPANY_ADMIN'],capabilities:{canReviewLeaveAndLetters:true,canManageCompanyPeople:true},projects:[]}};}
  else if(path.endsWith('/letter-settings/availability'))body={companyId:Number(path.split('/')[3]),templates:path.includes('/12/')?configuration.templates.filter(t=>t.enabled).map(t=>({type:t.type,definition:{...configuration.employers[0],hrName:configuration.signatories[0].name,hrTitle:configuration.signatories[0].title,body:t.body}})):[]};
  else if(path.endsWith('/letter-settings')){if(request.method()==='PUT'){const input=request.postDataJSON();configuration={...input,version:configuration.version+1,templates:input.templates.map(t=>({...t,enabled:true,employerId:input.employers[0].id,signatoryId:input.signatories[0].id}))};};body=configuration;}
  else if(path.endsWith('/preview')){await route.fulfill({contentType:'application/pdf',body:'%PDF-1.4 unsigned preview'});return;}
  else if(path.endsWith('/employment'))body={companyId:12,jobTitle:'Engineer',joiningDate:'2025-01-01'};
  else if(path.endsWith('/my'))body=[{id:90,companyId:12,requestType:'EMPLOYMENT_VERIFICATION',status:'APPROVED',requestedFullName:'Issued employee',submittedAt:'2026-10-01'}];
  else if(path.endsWith('/unread-count'))body=0;
  await route.fulfill({json:body});
 });return {errors,getConfiguration:()=>configuration};
}
test('shared company setup enables all letter types and keeps history available before setup',async({page})=>{
 const state=await fixture(page);await page.goto('/requests');await expect(page.getByRole('heading',{name:'Your company is setting up employee letters'})).toBeVisible();await expect(page.getByRole('button',{name:'Download PDF'})).toBeVisible();await expect(page.getByRole('button',{name:'Submit for Approval'})).toHaveCount(0);
 await page.goto('/letter-management');await page.getByRole('button',{name:'Company & HR details'}).click();await page.getByLabel('Legal employer name',{exact:true}).fill('Company A Legal');await page.getByLabel('Employer address',{exact:true}).fill('100 Business Street');await page.getByLabel('HR contact email',{exact:true}).fill('hr@company-a.example');
 await page.getByLabel('HR signatory name',{exact:true}).fill('Casey HR');await page.getByLabel('HR signatory title',{exact:true}).fill('HR Director');
 await expect(page.getByRole('heading',{name:'Letter templates',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Save letter settings'}).click();await expect(page.getByText('Company and HR details saved for all letters.')).toBeVisible();expect(state.getConfiguration().templates.filter(t=>t.enabled)).toHaveLength(3);
 for(const width of [1440,390]){await page.setViewportSize({width,height:1000});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../reports/letter-settings-${width}.png`,fullPage:true});}
 await page.goto('/requests');await expect(page.getByRole('button',{name:'Submit for Approval'})).toBeVisible();await expect(page.getByRole('button',{name:'Travel Letter',exact:true})).toBeVisible();await expect(page.getByText('Draft · Unsubmitted · Unsigned',{exact:true})).toBeVisible();await expect(page.getByText('Casey HR',{exact:false})).toBeVisible();await page.getByLabel('Current company',{exact:true}).selectOption('13');await expect(page.getByRole('heading',{name:'Your company is setting up employee letters'})).toBeVisible();await expect(page.getByRole('button',{name:'Submit for Approval'})).toHaveCount(0);expect(state.errors).toEqual([]);
});
