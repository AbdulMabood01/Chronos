const {test,expect}=require('@playwright/test');
const {resetFixtures,apiAs}=require('./support/api.cjs');
const {authenticatePage}=require('./support/auth.cjs');
const {json}=require('./support/workflows.cjs');
let f;test.beforeEach(async({request})=>{f=await resetFixtures(request);});
test('platform directory filters companies and exposes only authorized company metadata',async({page,request})=>{
 const platform=await apiAs(request,'platformAdmin'),employee=await apiAs(request,'employee');
 expect((await employee.get('/api/platform/companies/overview')).status()).toBe(403);
 const rows=await json(await platform.get('/api/platform/companies/overview'));expect(rows.length).toBeGreaterThan(0);expect(rows[0].usage).toBeTruthy();expect(rows[0].admin_contacts).toBeTruthy();
 await authenticatePage(page,request,'platformAdmin',f.companyId);await page.goto('/companies');
 const directory=page.getByRole('region',{name:'Company directory'});await expect(directory.getByRole('heading',{name:'Company directory'})).toBeVisible();
 await directory.getByLabel('Search companies').fill('no-matching-company');await expect(directory.getByText('No companies match these filters.')).toBeVisible();
 await directory.getByLabel('Search companies').fill('');await directory.getByLabel('Company status filter').selectOption('SUSPENDED');await expect(directory.getByText('No companies match these filters.')).toBeVisible();
 await directory.getByLabel('Company status filter').selectOption('ALL');await expect(page.getByRole('heading',{name:'Company details'})).toBeVisible();await expect(page.getByRole('heading',{name:'Company platform audit'})).toBeVisible();
 await page.screenshot({path:'../reports/platform-company-overview-desktop.png',fullPage:true});await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await page.screenshot({path:'../reports/platform-company-overview-mobile.png',fullPage:true});
});
test('Free extensions require a date and preserve audit metadata',async({request})=>{
 const platform=await apiAs(request,'platformAdmin'),c=await json(await platform.post('/api/companies',{data:{name:'Dated Free',slug:'dated-free-'+Date.now(),adminEmail:'other-admin@e2e.chronos.test'}}));
 const row=(await json(await platform.get('/api/platform/companies'))).find(r=>r.id===c.id);
 const input={tier:'FREE',projectLimit:1,teamLimit:7,version:row.platform_version,reason:'Approved Free extension',grantType:'COMPLIMENTARY'};
 expect((await platform.put('/api/platform/companies/'+c.id+'/plan',{data:input})).status()).toBe(400);
 expect((await platform.put('/api/platform/companies/'+c.id+'/plan',{data:{...input,endsAt:new Date(Date.now()+90*86400000).toISOString()}})).ok()).toBeTruthy();
 const metadata=await json(await platform.get('/api/platform/companies/'+c.id+'/billing-metadata'));expect(metadata.audit.some(a=>a.reason===input.reason)).toBeTruthy();
});
test('legal pages are public and show seller, retention and refund terms',async({page})=>{for(const [path,title] of [['privacy','Privacy Policy'],['terms','Terms of Use'],['refunds','Refund & Cancellation Policy'],['retention','Data Retention & Deletion'],['dpa','Data Processing Agreement'],['cookies','Cookies & Browser Storage']]){await page.goto('/legal/'+path);await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();await expect(page.getByText('© '+new Date().getFullYear()+' Maxwell IT Solutions. All rights reserved.')).toBeVisible();}});
