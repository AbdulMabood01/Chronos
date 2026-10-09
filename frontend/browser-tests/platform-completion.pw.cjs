const {test,expect}=require('@playwright/test');
const path=require('node:path');
async function fixture(page,{platform=true}={}){
 const companies=[{id:12,name:'Company A',slug:'company-a',plan_tier:'FREE',project_limit:3,team_limit:6,is_suspended:false,platform_version:0},{id:14,name:'Company B',slug:'company-b',plan_tier:'FREE',project_limit:1,team_limit:7,is_suspended:false,platform_version:0}],writes=[],calls=[],errors=[];
 const accounts=[{id:5,email:'operator@example.com',first_name:'Platform',last_name:'Operator',is_active:true,admin_locked:false,platform_admin:true,active_memberships:0,account_status:'ACTIVE',platform_access_version:0},{id:7,email:'member@example.com',first_name:'Sam',last_name:'Member',is_active:true,admin_locked:false,platform_admin:false,companies:[{id:12,name:'Company A'}],active_memberships:1,account_status:'ACTIVE',platform_access_version:2}];
 const usage={project_count:2,active_users:4,reservations:0,user_capacity:7,project_limit:1,plan:'FREE',source:'FREE',ends_at:null};
 let invitations=[{id:31,invitee_email:'admin@example.com',role_key:'COMPANY_ADMIN',expires_at:'2026-11-05T12:00:00Z',created_at:'2026-10-05T12:00:00Z',delivery_status:'FAILED',attempts:5,last_error:'Delivery unavailable. Check email configuration or resend the invitation.'}];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(({platform})=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);localStorage.setItem('chronos:company:5','12');},{platform});
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;calls.push(path);let body=[];
  if(path==='/api/auth/me')body={id:5,email:'operator@example.com',firstName:'Platform',lastName:'Operator',platformAdmin:platform,profileCompleted:true};
  else if(path==='/api/companies/context')body={platformAdmin:platform,memberships:platform?[]:companies,companies,platformPermissions:{roles:platform?['PLATFORM_ADMIN']:[],capabilities:platform?{canCreateCompanies:true,canManageCompanyPlans:true,canSuspendCompanies:true,canManagePlatformAdmins:true,canViewPlatformAudit:true,canConfigurePlatform:true}:{} }};
  else if(/\/companies\/\d+\/context$/.test(path))body={...companies.find(c=>String(c.id)===path.split('/')[3]),permissions:{companyId:Number(path.split('/')[3]),companyRoles:platform?[]:['COMPANY_ADMIN'],capabilities:platform?{}:{canManageCompanyPeople:true},projects:[]}};
  else if(path==='/api/billing/catalog')body={plans:[{key:'FREE',name:'Free',projects:1,users:7},{key:'PRO',name:'Pro',projects:3,users:75},{key:'PRO_PLUS',name:'Pro Plus',projects:7,users:175},{key:'PRO_MAX',name:'Pro Max',projects:15,users:375}]};
  else if(path==='/api/platform/settings')body={version:0,values:{'platform.timesheet.reminders.enabled':'true'}};
  else if(path.endsWith('/billing-metadata'))body={purchases:[],events:[]};
  else if(path==='/api/platform/companies')body=companies;
  else if(path.endsWith('/usage'))body=usage;
  else if(path.endsWith('/plan')&&req.method()==='DELETE'){writes.push({action:'revoke-plan',input:req.postDataJSON()});Object.assign(usage,{plan:'FREE',source:'FREE',ends_at:null});companies[0].platform_version++;body=null;}
  else if(path.endsWith('/plan')&&req.method()==='PUT'){const input=req.postDataJSON();writes.push({action:'plan',input});Object.assign(usage,{plan:input.tier,source:input.grantType,ends_at:input.endsAt,project_limit:input.projectLimit,user_capacity:input.teamLimit});Object.assign(companies[0],{plan_tier:input.tier,project_limit:input.projectLimit,team_limit:input.teamLimit,platform_version:companies[0].platform_version+1});body=null;}
  else if(path.endsWith('/status')&&req.method()==='PUT'){const input=req.postDataJSON();writes.push({action:'status',input});companies[0].is_suspended=input.suspended;companies[0].platform_version++;body=null;}
  else if(path.endsWith('/admin-invitations'))body=invitations;
  else if(path.endsWith('/resend')){writes.push({action:'resend'});invitations[0].delivery_status='PENDING';body=null;}
  else if(path.includes('/admin-invitations/')&&req.method()==='DELETE'){writes.push({action:'revoke'});invitations[0].revoked_at='2026-10-05T12:00:00Z';invitations[0].delivery_status='CANCELLED';body=null;}
  else if(path==='/api/platform/accounts'){const company=url.searchParams.get('companyId'),search=(url.searchParams.get('query')||'').toLowerCase();body=accounts.filter(account=>(!company||(account.companies||[]).some(c=>String(c.id)===company))&&(!search||(account.email+' '+account.first_name+' '+account.last_name).toLowerCase().includes(search)));}
  else if(path.endsWith('/actions')){const input=req.postDataJSON();writes.push({action:'account',id:Number(path.split('/')[4]),input});accounts[1].admin_locked=input.action==='LOCK';accounts[1].platform_access_version++;body=null;}
  else if(path==='/api/platform/administrators'){writes.push({action:'platform-invite',input:req.postDataJSON()});body={id:9};}
  else if(path==='/api/platform/audit')body=[{id:1,action:'COMPANY_PLAN_UPDATED',actor_email:'operator@example.com',company_name:'Company A',reason:'Growth',created_at:'2026-10-05T12:00:00Z'}];
  else if(path==='/api/companies'&&req.method()==='POST'){const input=req.postDataJSON();writes.push({action:'company',input});companies.push({id:13,name:input.name,slug:input.slug,plan_tier:'FREE',project_limit:3,team_limit:6,platform_version:0});body={id:13};}
  else if(path.endsWith('/unread-count'))body=0;
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });return{calls,writes,errors,usage};
}
test('platform changes company plan with revision and sees only provisioning data',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByLabel('Plan',{exact:true}).selectOption('CUSTOM');await page.getByLabel('Open-project limit',{exact:true}).fill('10');await page.getByLabel('Company user allowance',{exact:true}).fill('12');await page.getByLabel('Reason for grant',{exact:true}).fill('Growth');await page.getByRole('button',{name:'Assign complimentary plan',exact:true}).click();await expect(page.getByText('Complimentary plan assigned. No payment is required.',{exact:true})).toBeVisible();expect(state.writes[0]).toEqual({action:'plan',input:{tier:'CUSTOM',projectLimit:10,teamLimit:12,version:0,reason:'Growth',grantType:'COMPLIMENTARY',endsAt:null}});expect(state.calls).not.toContain('/api/companies/12/members');expect(state.errors).toEqual([]);
});
test('platform suspends and resumes a company without operating company workflows',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByLabel('Reason for availability change',{exact:true}).fill('Review');await page.getByRole('button',{name:'Suspend company',exact:true}).click();await expect(page.getByText('Company suspended.',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Resume company',exact:true}).click();await expect(page.getByText('Company resumed.',{exact:true})).toBeVisible();expect(state.writes).toEqual([{action:'status',input:{suspended:true,version:0,reason:'Review'}},{action:'status',input:{suspended:false,version:1,reason:'Review'}}]);expect(state.errors).toEqual([]);
});
test('platform can see failed admin delivery and safely resend and revoke',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await expect(page.getByText(/Delivery: FAILED/)).toBeVisible();await page.getByRole('button',{name:'Resend invitation',exact:true}).click();await expect(page.getByText('Company Admin invitation queued again.',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Revoke invitation',exact:true}).click();await expect(page.getByText('Company Admin invitation revoked.',{exact:true})).toBeVisible();expect(state.writes).toEqual([{action:'resend'},{action:'revoke'}]);expect(state.errors).toEqual([]);
});
test('platform invitation setup collects identity and leaves password choice to recipient on mobile',async({page})=>{
 const state=await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/platform-accounts');await page.getByLabel('First name',{exact:true}).fill('Taylor');await page.getByLabel('Last name',{exact:true}).fill('Operator');await page.getByLabel('Admin email',{exact:true}).fill('taylor@example.com');await page.getByRole('button',{name:'Create and invite Platform Admin'}).click();await expect(page.getByText('Platform Admin account created and invitation queued.',{exact:true})).toBeVisible();expect(state.writes[0]).toEqual({action:'platform-invite',input:{firstName:'Taylor',lastName:'Operator',email:'taylor@example.com'}});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(state.errors).toEqual([]);
});
test('platform account access actions use the selected account revision and require a reason',async({page})=>{
 const state=await fixture(page);await page.goto('/platform-accounts');await page.getByRole('button',{name:'Manage sign-in access'}).last().click();await page.getByLabel('Account action',{exact:true}).selectOption('LOCK');await page.getByLabel('Reason for account action',{exact:true}).fill('Security review');await page.getByRole('button',{name:'Confirm account action'}).click();await expect(page.getByText('Account action completed.',{exact:true})).toBeVisible();expect(state.writes[0]).toEqual({action:'account',id:7,input:{action:'LOCK',version:2,reason:'Security review'}});expect(state.calls).not.toContain('/api/users/7');expect(state.errors).toEqual([]);
});
test('platform audit contains only platform events',async({page})=>{
 const state=await fixture(page);await page.goto('/platform-audit');await expect(page.getByText('COMPANY PLAN UPDATED',{exact:true})).toBeVisible();await expect(page.getByText('Growth',{exact:true})).toBeVisible();expect(state.calls).toContain('/api/platform/audit');expect(state.calls).not.toContain('/api/audit');expect(state.errors).toEqual([]);
});
test('company admin cannot open platform routes even with account administration in another context',async({page})=>{
 const state=await fixture(page,{platform:false});for(const path of ['/platform-accounts','/platform-audit','/platform-settings']){await page.goto(path);await expect(page).toHaveURL(/\/dashboard$/);}expect(state.calls.some(p=>p.startsWith('/api/platform/'))).toBe(false);expect(state.errors).toEqual([]);
});
test('company creation reports queued delivery instead of claiming an email was sent',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByRole('button',{name:'+ New company',exact:true}).click();await page.getByLabel('Company name',{exact:true}).fill('New Company');await page.getByLabel(/Workspace ID/).fill('new-company');await page.getByLabel('Initial company admin email',{exact:true}).fill('company-admin@example.com');await page.getByRole('button',{name:'Create company and invite admin'}).click();await expect(page.getByText('Company created. The initial company admin invitation is queued for delivery.',{exact:true})).toBeVisible();expect(state.writes[0]).toEqual({action:'company',input:{name:'New Company',slug:'new-company',adminEmail:'company-admin@example.com'}});expect(state.errors).toEqual([]);
});

test('platform assigns a permanent catalog plan and can revoke it with a reason',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByLabel('Plan',{exact:true}).selectOption('PRO_PLUS');
 await expect(page.getByLabel('Open-project limit',{exact:true})).toHaveValue('7');await expect(page.getByLabel('Company user allowance',{exact:true})).toHaveValue('175');
 await page.getByLabel('Reason for grant',{exact:true}).fill('Owned company');await page.getByRole('button',{name:'Assign complimentary plan',exact:true}).click();
 await expect(page.getByText('Complimentary plan assigned. No payment is required.',{exact:true})).toBeVisible();
 expect(state.writes[0]).toEqual({action:'plan',input:{tier:'PRO_PLUS',projectLimit:7,teamLimit:175,version:0,reason:'Owned company',grantType:'COMPLIMENTARY',endsAt:null}});
 await page.getByText('Revoke complimentary plan',{exact:true}).click();await page.getByLabel('Reason for revocation').fill('Allowance ended');await page.getByRole('button',{name:'Confirm revocation'}).click();
 await expect(page.getByText('Complimentary plan revoked. The company now uses Free.',{exact:true})).toBeVisible();expect(state.writes[1]).toEqual({action:'revoke-plan',input:{version:1,reason:'Allowance ended'}});expect(state.errors).toEqual([]);
});
test('platform can choose an expiry and sees paid plan protection',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByLabel('Plan',{exact:true}).selectOption('PRO');await page.getByLabel('Expiry',{exact:true}).selectOption('DATE');await page.getByLabel('End date and time').fill('2030-10-08T12:00');await page.getByLabel('Reason for grant').fill('Temporary allowance');await page.getByRole('button',{name:'Assign complimentary plan',exact:true}).click();await expect(page.getByText('Complimentary plan assigned. No payment is required.',{exact:true})).toBeVisible();expect(state.writes[0].input.endsAt).toMatch(/^2030-10-08T/);
 state.usage.source='PAID';await page.reload();await expect(page.getByRole('button',{name:'Assign complimentary plan',exact:true})).toBeDisabled();expect(state.errors).toEqual([]);
});
for(const width of [1440,390])test('platform screens use shared styling without overflow at '+width,async({page})=>{
 const state=await fixture(page);await page.setViewportSize({width,height:width===390?844:1050});
 for(const route of ['companies','platform-accounts','platform-audit','platform-settings']){
  await page.goto('/'+route);await expect(page.locator('.platform-page')).toBeVisible();await expect(page.locator('.screen-title h1')).toBeVisible();
  if(route==='companies')await expect(page.getByRole('button',{name:'Assign complimentary plan',exact:true})).toBeVisible();
  if(route==='platform-accounts')await expect(page.getByText('operator@example.com',{exact:true})).toBeVisible();
  if(route==='platform-audit')await expect(page.getByText('Growth',{exact:true})).toBeVisible();
  if(route==='platform-settings')await expect(page.getByRole('button',{name:'Reminder delivery'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  await page.screenshot({path:path.resolve(__dirname,'../../reports/platform-ui/'+route+'-'+width+'.png'),fullPage:true,animations:'disabled'});
  await page.evaluate(()=>document.body.classList.add('theme-dark'));
  await expect.poll(()=>page.locator('.platform-page .button-secondary').evaluateAll(buttons=>buttons.every(button=>getComputedStyle(button).backgroundColor==='rgb(23, 40, 53)'&&getComputedStyle(button).color==='rgb(227, 234, 241)'))).toBe(true);
  await page.screenshot({path:path.resolve(__dirname,'../../reports/platform-ui/'+route+'-'+width+'-dark.png'),fullPage:true,animations:'disabled'});
 }
 expect(state.errors).toEqual([]);
});

test('platform account company filter combines search, clears selection and fits mobile',async({page})=>{
 const state=await fixture(page);await page.goto('/platform-accounts');
 const directory=page.getByRole('heading',{name:'Account directory'}).locator('xpath=ancestor::section');
 await expect(directory.getByText('operator@example.com',{exact:true})).toBeVisible();
 await page.getByLabel('Filter accounts by company').selectOption('12');
 await expect(directory.getByText('operator@example.com',{exact:true})).toHaveCount(0);
 await expect(directory.getByText('member@example.com',{exact:true})).toBeVisible();await expect(directory.locator('.platform-account-companies').getByText('Company A',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Manage sign-in access'}).click();await expect(page.getByRole('heading',{name:'Manage member@example.com'})).toBeVisible();
 await page.getByLabel('Filter accounts by company').selectOption('14');await expect(page.getByRole('heading',{name:'Manage member@example.com'})).toHaveCount(0);await expect(directory.getByText('No accounts match this company and search.')).toBeVisible();
 await page.getByLabel('Filter accounts by company').selectOption('12');await page.getByLabel('Search accounts',{exact:true}).fill('operator');await expect(directory.getByText('No accounts match this company and search.')).toBeVisible();
 await page.getByLabel('Filter accounts by company').selectOption('');await expect(directory.getByText('operator@example.com',{exact:true})).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(state.errors).toEqual([]);
});
