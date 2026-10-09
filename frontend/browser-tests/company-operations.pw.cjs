const {test,expect}=require('@playwright/test');
async function fixture(page,{platform=false}={}){
  const companies=[{id:12,name:'Company A',slug:'company-a',project_limit:10},{id:13,name:'Company B',slug:'company-b',project_limit:10}];
  const requests={12:[{id:7,companyId:12,userId:7,userName:'Sam Member',startDate:'2026-10-06',endDate:'2026-10-06',vacationType:'VACATION',status:'SUBMITTED',version:2}],13:[]};
  const projects=[],writes=[],calls=[],errors=[];let published=false;
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(({platform})=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);if(!platform&&!localStorage.getItem('chronos:company:5'))localStorage.setItem('chronos:company:5','12');},{platform});
  await page.route('**/api/**',async route=>{
    const req=route.request(),url=new URL(req.url()),path=url.pathname;calls.push(path);let body=[];
    if(path==='/api/auth/me')body={id:5,email:'admin@example.com',firstName:'Jordan',lastName:'Admin',role:'EMPLOYEE',profileCompleted:true};
    else if(path==='/api/companies/context')body={platformAdmin:platform,memberships:platform?[]:companies,companies,platformPermissions:{roles:platform?['PLATFORM_ADMIN']:[],capabilities:{canCreateCompanies:platform}}};
    else if(/\/companies\/\d+\/context$/.test(path)){
      const company=companies.find(c=>String(c.id)===path.split('/')[3]);const admin=!platform&&company.id===12;
      body={...company,permissions:{companyId:company.id,companyRoles:admin?['COMPANY_ADMIN']:[],capabilities:admin?{canCreateProjects:true,canViewProjects:true,canManageCompanyPeople:true,canManageLeavePolicy:true,canViewCompanyReports:true}:{canSubmitWork:true},projects:[]}};
    }else if(path==='/api/companies/12/members')body=[{user_id:5,first_name:'Jordan',last_name:'Admin',email:'admin@example.com',status:'ACTIVE',roles:['COMPANY_ADMIN'],account_available:true},{user_id:7,first_name:'Sam',last_name:'Member',email:'sam@example.com',status:'ACTIVE',roles:[],account_available:true}];
    else if(path.startsWith('/api/companies/')&&path.includes('/leave/')){
      const company=Number(path.split('/')[3]);
      if(path.endsWith('/my'))body=requests[company].filter(row=>row.userId===5);
      else if(path.endsWith('/requests')&&req.method()==='GET')body=requests[company];
      else if(path.endsWith('/requests')&&req.method()==='POST'){
        const input=req.postDataJSON();writes.push({company,action:'create',input});body={...input,id:9,companyId:company,userId:5,userName:'Jordan Admin',status:'DRAFT',version:0};requests[company].push(body);
      }else if(/\/requests\/\d+\/(submit|approve|reject)$/.test(path)){
        const parts=path.split('/'),id=Number(parts[6]),action=parts[7],input=action==='submit'?{version:Number(url.searchParams.get('version'))}:req.postDataJSON();
        writes.push({company,action,id,input});body=requests[company].find(r=>r.id===id);body.status=action==='submit'?'SUBMITTED':action==='approve'?'APPROVED':'REJECTED';body.version++;
      }else if(path.includes('/balance/')){
        const bucket={allowanceDays:published?15:0,extraDays:0,usedDays:0,remainingDays:published?15:0,pendingDays:0,unpaidDays:0};body={companyId:company,userId:5,year:2026,configured:published,version:0,source:'POLICY',vacation:bucket,sick:{...bucket,allowanceDays:5,remainingDays:5},bereavement:{...bucket,allowanceDays:3,remainingDays:3}};
      }else if(path.endsWith('/policy/preview'))body={companyId:company,year:2026,settingsVersion:3,policyVersion:-1,vacationDays:15,sickDays:5,bereavementDays:3,newAllowances:2,policyAllowances:0,overrides:1};
      else if(path.endsWith('/policy/apply')){writes.push({company,action:'policy',input:req.postDataJSON()});published=true;body={updated:2};}
      else if(path.endsWith('/calendar'))body=[{id:7,userId:7,userName:'Sam Member',startDate:'2026-10-06',endDate:'2026-10-06'}];
    }else if(path==='/api/companies/12/reports/timesheet-periods')body=[{companyId:12,id:31,projectId:101,projectCode:'OPS',projectName:'Operations',userId:7,userName:'Sam Member',periodStart:'2026-10-01',periodEnd:'2026-10-31',totalHours:8,status:'APPROVED'},{companyId:13,id:32,projectId:201,projectCode:'SECRET',projectName:'Other-company project',userId:9,userName:'Other-company employee',periodStart:'2026-10-01',periodEnd:'2026-10-31',totalHours:8,status:'APPROVED'}];
    else if(path==='/api/projects'&&req.method()==='POST'){
      const input=req.postDataJSON();writes.push({action:'project',input});body={...input,id:101,status:'DRAFT',isActive:false,assignments:[],canManage:input.ownerUserId===5};projects.push(body);
    }else if(path==='/api/projects')body=projects;
    else if(path==='/api/users')body=[{id:5,firstName:'Jordan',lastName:'Admin',name:'Jordan Admin',role:'EMPLOYEE',isActive:true},{id:7,firstName:'Sam',lastName:'Member',name:'Sam Member',role:'EMPLOYEE',isActive:true}];
    else if(path.endsWith('unread-count'))body=0;
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
  });return{writes,calls,errors,requests};
}
test('member creates and submits leave in the selected company',async({page})=>{
  const state=await fixture(page);await page.goto('/vacation');await page.getByRole('button',{name:'New leave request'}).click();
  await page.getByLabel('Start date',{exact:true}).fill('2026-10-06');await page.getByLabel('End date',{exact:true}).fill('2026-10-06');await page.getByRole('button',{name:'Save draft'}).click();await expect(page.getByText('Leave draft saved.',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Submit leave'}).click();await expect(page.getByText('Leave submitted.',{exact:true})).toBeVisible();
  expect(state.writes[0]).toMatchObject({company:12,action:'create'});expect(state.writes[1]).toMatchObject({company:12,action:'submit',id:9,input:{version:0}});
  await page.getByLabel('Current company',{exact:true}).selectOption('13');await expect(page.getByText('No leave requests in this company.',{exact:true})).toBeVisible();expect(state.errors).toEqual([]);
});
test('company admin publishes defaults and reviews another member leave on mobile',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/leave-management');
  await page.getByRole('button',{name:'Policy',exact:true}).click();await page.getByRole('button',{name:'Preview policy'}).click();await expect(page.getByRole('region',{name:'Annual policy preview'})).toContainText('Personal overrides preserved');
  await page.getByRole('button',{name:'Apply 2026 policy'}).click();await expect(page.getByText('Company leave policy applied.',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Requests',exact:true}).click();await page.getByRole('button',{name:'Review leave'}).click();await page.getByRole('button',{name:'Approve leave'}).click();await expect(page.getByText('Leave approved.',{exact:true})).toBeVisible();
  expect(state.writes).toEqual([{company:12,action:'policy',input:{year:2026,settingsVersion:3,policyVersion:-1,confirmed:true}},{company:12,action:'approve',id:7,input:{version:2,accountingType:null}}]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(state.errors).toEqual([]);
});
test('company admin creates a draft with an explicit different project owner',async({page})=>{
  const state=await fixture(page);await page.goto('/projects');await page.getByRole('button',{name:/New Project/}).click();
  await page.getByLabel('Project Code',{exact:true}).fill('NEW');await page.getByLabel('Project Name',{exact:true}).last().fill('Owner-selected project');
  await page.getByLabel('Initial project owner',{exact:true}).selectOption('7');await page.getByRole('button',{name:/Create.*Project|Save.*Project|Create draft/i}).click();
  await expect.poll(()=>state.writes.filter(w=>w.action==='project').length).toBe(1);expect(state.writes.find(w=>w.action==='project').input).toMatchObject({companyId:12,ownerUserId:7,status:'DRAFT'});expect(state.errors).toEqual([]);
});
test('company context restricts operational tools and the calendar exposes dates only',async({page})=>{
  const state=await fixture(page);await page.goto('/team-leave-calendar');await page.getByLabel('Calendar month',{exact:true}).fill('2026-10');await page.getByRole('button',{name:'October 6, 2026: 1 away'}).click();await expect(page.getByText('Sam Member',{exact:true})).toBeVisible();
  await page.getByLabel('Current company',{exact:true}).selectOption('13');await expect(page).toHaveURL(/\/dashboard$/);await page.goto('/leave-management');await expect(page).toHaveURL(/\/dashboard$/);
  expect(state.calls).not.toContain('/api/companies/13/leave/requests');expect(state.errors).toEqual([]);
});
test('platform admin cannot open company leave, projects, or reports',async({page})=>{
  const state=await fixture(page,{platform:true});for(const path of ['/vacation','/leave-management','/projects','/reports']){await page.goto(path);await expect(page).toHaveURL(/\/dashboard$/);}
  expect(state.calls.some(path=>/\/companies\/\d+\/(leave|reports)/.test(path))).toBe(false);expect(state.errors).toEqual([]);
});
test('company reports load their company endpoint and exclude unrelated records',async({page})=>{
  const state=await fixture(page);await page.goto('/reports');await expect(page.getByText('Sam Member',{exact:true})).toBeVisible();await expect(page.getByText('Other-company employee',{exact:true})).toHaveCount(0);
  await page.getByLabel('Select Sam Member',{exact:true}).check();await expect(page.getByRole('button',{name:'Download selected'})).toBeEnabled();
  expect(state.calls).toContain('/api/companies/12/reports/timesheet-periods');expect(state.calls).not.toContain('/api/reports/timesheet-periods');expect(state.errors).toEqual([]);
});
