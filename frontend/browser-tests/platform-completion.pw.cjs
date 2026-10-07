const {test,expect}=require('@playwright/test');
async function fixture(page,{platform=true}={}){
 const companies=[{id:12,name:'Company A',slug:'company-a',plan_tier:'FREE',project_limit:3,team_limit:6,is_suspended:false,platform_version:0}],writes=[],calls=[],errors=[];
 const accounts=[{id:5,email:'operator@example.com',first_name:'Platform',last_name:'Operator',is_active:true,admin_locked:false,platform_admin:true,active_memberships:0,account_status:'ACTIVE',platform_access_version:0},{id:7,email:'member@example.com',first_name:'Sam',last_name:'Member',is_active:true,admin_locked:false,platform_admin:false,active_memberships:1,account_status:'ACTIVE',platform_access_version:2}];
 let invitations=[{id:31,invitee_email:'admin@example.com',role_key:'COMPANY_ADMIN',expires_at:'2026-11-05T12:00:00Z',created_at:'2026-10-05T12:00:00Z',delivery_status:'FAILED',attempts:5,last_error:'Delivery unavailable. Check email configuration or resend the invitation.'}];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(({platform})=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);localStorage.setItem('chronos:company:5','12');},{platform});
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;calls.push(path);let body=[];
  if(path==='/api/auth/me')body={id:5,email:'operator@example.com',firstName:'Platform',lastName:'Operator',platformAdmin:platform,profileCompleted:true};
  else if(path==='/api/companies/context')body={platformAdmin:platform,memberships:platform?[]:companies,companies,platformPermissions:{roles:platform?['PLATFORM_ADMIN']:[],capabilities:platform?{canCreateCompanies:true,canManageCompanyPlans:true,canSuspendCompanies:true,canManagePlatformAdmins:true,canViewPlatformAudit:true}:{} }};
  else if(/\/companies\/\d+\/context$/.test(path))body={...companies.find(c=>String(c.id)===path.split('/')[3]),permissions:{companyId:Number(path.split('/')[3]),companyRoles:platform?[]:['COMPANY_ADMIN'],capabilities:platform?{}:{canManageCompanyPeople:true},projects:[]}};
  else if(path==='/api/platform/companies')body=companies;
  else if(path.endsWith('/usage'))body={project_count:2,largest_team:4};
  else if(path.endsWith('/plan')&&req.method()==='PUT'){const input=req.postDataJSON();writes.push({action:'plan',input});Object.assign(companies[0],{plan_tier:input.tier,project_limit:input.projectLimit,team_limit:input.teamLimit,platform_version:companies[0].platform_version+1});body=null;}
  else if(path.endsWith('/status')&&req.method()==='PUT'){const input=req.postDataJSON();writes.push({action:'status',input});companies[0].is_suspended=input.suspended;companies[0].platform_version++;body=null;}
  else if(path.endsWith('/admin-invitations'))body=invitations;
  else if(path.endsWith('/resend')){writes.push({action:'resend'});invitations[0].delivery_status='PENDING';body=null;}
  else if(path.includes('/admin-invitations/')&&req.method()==='DELETE'){writes.push({action:'revoke'});invitations[0].revoked_at='2026-10-05T12:00:00Z';invitations[0].delivery_status='CANCELLED';body=null;}
  else if(path==='/api/platform/accounts')body=accounts;
  else if(path.endsWith('/actions')){const input=req.postDataJSON();writes.push({action:'account',id:Number(path.split('/')[4]),input});accounts[1].admin_locked=input.action==='LOCK';accounts[1].platform_access_version++;body=null;}
  else if(path==='/api/platform/administrators'){writes.push({action:'platform-invite',input:req.postDataJSON()});body={id:9};}
  else if(path==='/api/platform/audit')body=[{id:1,action:'COMPANY_PLAN_UPDATED',actor_email:'operator@example.com',company_name:'Company A',reason:'Growth',created_at:'2026-10-05T12:00:00Z'}];
  else if(path==='/api/companies'&&req.method()==='POST'){const input=req.postDataJSON();writes.push({action:'company',input});companies.push({id:13,name:input.name,slug:input.slug,plan_tier:'FREE',project_limit:3,team_limit:6,platform_version:0});body={id:13};}
  else if(path.endsWith('/unread-count'))body=0;
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });return{calls,writes,errors};
}
test('platform changes company plan with revision and sees only provisioning data',async({page})=>{
 const state=await fixture(page);await page.goto('/companies');await page.getByLabel('Plan',{exact:true}).selectOption('MULTIPLE');await page.getByLabel('Project limit',{exact:true}).fill('10');await page.getByLabel('Team limit per project',{exact:true}).fill('12');await page.getByLabel('Reason for plan change',{exact:true}).fill('Growth');await page.getByRole('button',{name:'Save plan',exact:true}).click();await expect(page.getByText('Company plan updated.',{exact:true})).toBeVisible();expect(state.writes[0]).toEqual({action:'plan',input:{tier:'MULTIPLE',projectLimit:10,teamLimit:12,version:0,reason:'Growth'}});expect(state.calls).not.toContain('/api/companies/12/members');expect(state.errors).toEqual([]);
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
