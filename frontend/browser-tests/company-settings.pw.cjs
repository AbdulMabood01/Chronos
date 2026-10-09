const {test,expect}=require('@playwright/test');
async function fixture(page,{platform=false,adminB=true}={}){
  const companies=[{id:12,name:'Company A',slug:'company-a'},{id:13,name:'Company B',slug:'company-b'}];
  const records={12:{companyId:12,version:2,values:{company_name:'Company A','vacation_days_per_year':'15','sick_days_per_year':'5','bereavement_days_per_year':'3','timesheet.reminders.enabled':'true'}},13:{companyId:13,version:0,values:{company_name:'Company B','vacation_days_per_year':'10','sick_days_per_year':'4','bereavement_days_per_year':'3','timesheet.reminders.enabled':'false'}}};
  const platformRecord={version:0,values:{'platform.timesheet.reminders.enabled':'true'}};
  const writes=[],requests=[],errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(({platform})=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);if(!platform&&!localStorage.getItem('chronos:company:5'))localStorage.setItem('chronos:company:5','12');},{platform});
  await page.route('**/api/**',async route=>{
    const request=route.request(),path=new URL(request.url()).pathname;requests.push(path);let body=[];
    if(path==='/api/auth/me')body={id:5,email:'settings@example.com',firstName:'Settings',lastName:'Tester',role:'EMPLOYEE',profileCompleted:true};
    else if(path==='/api/companies/context')body={platformAdmin:platform,memberships:platform?[]:companies,companies,platformPermissions:{roles:platform?['PLATFORM_ADMIN']:[],capabilities:{canCreateCompanies:platform,canConfigurePlatform:platform}}};
    else if(/\/companies\/\d+\/context$/.test(path)){
      const company=companies.find(c=>String(c.id)===path.split('/')[3]),admin=!platform&&(company.id===12||adminB);
      body={...company,permissions:{companyId:company.id,companyRoles:admin?['COMPANY_ADMIN']:[],capabilities:{canManageCompanySettings:admin},projects:[]}};
    }else if(/\/companies\/\d+\/settings/.test(path)){
      const company=path.split('/')[3];body=records[company];
      if(request.method()==='PUT'){const input=request.postDataJSON(),key=decodeURIComponent(path.split('/')[5]);writes.push({company:Number(company),key,input});records[company]={...body,version:body.version+1,values:{...body.values,[key]:input.value}};body=records[company];}
    }else if(path.startsWith('/api/platform/settings')){
      body=platformRecord;if(request.method()==='PUT'){const input=request.postDataJSON();writes.push({platform:true,input});platformRecord.version++;platformRecord.values['platform.timesheet.reminders.enabled']=input.value;}
    }else if(path.endsWith('unread-count'))body=0;
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
  });return{records,writes,requests,errors};
}
test('company settings edits are isolated and follow the selected company',async({page})=>{
  const state=await fixture(page);await page.goto('/settings');
  await expect(page.getByRole('heading',{name:'Company settings',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Change Paid vacation days'}).click();await page.getByLabel('Paid vacation days',{exact:true}).fill('20.25');await page.getByRole('button',{name:'Save',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('Setting updated');
  expect(state.writes).toEqual([{company:12,key:'vacation_days_per_year',input:{value:'20.25',version:2}}]);expect(state.records[13].values['vacation_days_per_year']).toBe('10');
  await page.getByLabel('Current company',{exact:true}).selectOption('13');await expect(page.getByText('10 days',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Timesheet reminders'})).toHaveAttribute('aria-pressed','false');
  expect(state.requests.some(path=>path.startsWith('/api/settings'))).toBe(false);expect(state.errors).toEqual([]);
});
test('switching to a member-only company removes the menu and denies direct settings URLs',async({page})=>{
  const state=await fixture(page,{adminB:false});await page.goto('/settings');await expect(page.getByRole('heading',{name:'Company settings',exact:true})).toBeVisible();
  await page.getByLabel('Current company',{exact:true}).selectOption('13');await expect(page).toHaveURL(/\/dashboard$/);await expect(page.getByRole('link',{name:'Company settings',exact:true})).toHaveCount(0);
  await page.goto('/settings');await expect(page).toHaveURL(/\/dashboard$/);expect(state.requests).not.toContain('/api/companies/13/settings');expect(state.errors).toEqual([]);
});
test('platform configuration has no company policy controls or company requests',async({page})=>{
  const state=await fixture(page,{platform:true});await page.goto('/platform-settings');await expect(page.getByRole('heading',{name:'Platform settings',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Reminder delivery'}).click();await expect(page.getByRole('status')).toContainText('Setting updated');
  expect(state.writes).toEqual([{platform:true,input:{value:'false',version:0}}]);await expect(page.getByText('Paid vacation days',{exact:true})).toHaveCount(0);
  await page.goto('/settings');await expect(page).toHaveURL(/\/dashboard$/);expect(state.requests.some(path=>/\/companies\/\d+\/settings/.test(path))).toBe(false);expect(state.errors).toEqual([]);
});
test('company settings remain usable on a narrow screen',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/settings');await page.getByRole('button',{name:'Change Company display name'}).click();
  await page.getByLabel('Company display name',{exact:true}).fill('Company A Limited');await page.getByRole('button',{name:'Save',exact:true}).click();await expect(page.getByRole('status')).toContainText('Setting updated');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(state.errors).toEqual([]);
});
