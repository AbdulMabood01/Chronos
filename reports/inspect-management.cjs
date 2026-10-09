const {chromium}=require('../frontend/node_modules/playwright');
async function fixture(page,{platform=false,handler=true,reviewer=true}={}){
 const companies=[{id:12,name:'Company A',slug:'company-a'},{id:13,name:'Company B',slug:'company-b'}],calls=[],writes=[],errors=[];
 let news=[],grants=[],reviews=[],cases=[{id:'case-a',subject:'Private A case',description:'Confidential A details',category:'OTHER_INCIDENT',status:'SUBMITTED',anonymous:true,version:3,submitted_at:'2026-10-01T12:00:00Z',attachments:[],history:[]}];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(({platform})=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);if(!platform&&!localStorage.getItem('chronos:company:5'))localStorage.setItem('chronos:company:5','12');},{platform});
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname;calls.push(path);let body=[];const company=Number(path.split('/')[3]);
  if(path==='/api/auth/me')body={id:5,email:'admin@example.com',firstName:'Jordan',lastName:'Admin',role:'EMPLOYEE',profileCompleted:true};
  else if(path==='/api/companies/context')body={platformAdmin:platform,memberships:platform?[]:companies,companies,platformPermissions:{roles:platform?['PLATFORM_ADMIN']:[],capabilities:{canCreateCompanies:platform}}};
  else if(/\/companies\/\d+\/context$/.test(path)){const c=companies.find(c=>c.id===company),admin=!platform&&company===12;body={...c,permissions:{companyId:company,companyRoles:admin?['COMPANY_ADMIN']:[],projects:[],capabilities:admin?{canManageCompanyPeople:true,canManageCompanyAnnouncements:true,canReviewLeaveAndLetters:true,canManageSensitiveGrants:true,canViewCompanyAudit:true,canManagePerformanceReviews:reviewer,canHandleConfidentialReports:handler}:{}}};}
  else if(path.endsWith('/members'))body=[{user_id:5,first_name:'Jordan',last_name:'Admin',email:'admin@example.com',status:'ACTIVE',account_available:true},{user_id:7,first_name:'Case',last_name:'Handler',email:'handler@example.com',status:'ACTIVE',account_available:true},{user_id:8,first_name:'Sam',last_name:'Member',email:'sam@example.com',status:'ACTIVE',account_available:true},{user_id:10,first_name:'Platform',last_name:'Admin',email:'platform@example.com',status:'ACTIVE',account_available:true,platform_account:true}];
  else if(path.endsWith('/employment'))body={companyId:company,userId:5,jobTitle:'Engineer',joiningDate:'2025-01-01'};
  else if(path.includes('/announcements')){
   if(path.endsWith('/announcements')&&req.method()==='GET')body=company===12?news:[];
   else if(path.endsWith('/announcements')&&req.method()==='POST'){const input=req.postDataJSON();writes.push({company,action:'announcement',input});news=[{id:'news-a',company_id:company,title:input.title,content:input.content,publish_date:input.publishDate,priority:input.priority,status:input.status,version:0,acknowledgment_required:input.acknowledgmentRequired}];body='news-a';}
   else if(path.endsWith('/open')){if(company!==12){await route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({message:'Announcement not found'})});return;}body=news[0];}
   else if(path.endsWith('/tracking'))body={total:2,viewed:1,acknowledged:0,employees:[{id:7,name:'Case Handler',email:'handler@example.com'}]};
  }else if(path.endsWith('/sensitive-grants')&&req.method()==='GET')body=grants;
  else if(path.endsWith('/sensitive-grants')&&req.method()==='POST'){const input=req.postDataJSON();writes.push({company,action:'grant',input});grants.push({...input,id:'grant-a',permission:input.permission,user_name:'Case Handler',subject_name:'Sam Member',starts_on:input.startsOn,ends_on:input.endsOn,version:0});body='grant-a';}
  else if(path.includes('/sensitive-grants/')&&req.method()==='DELETE'){writes.push({company,action:'revoke',version:Number(url.searchParams.get('version'))});grants[0].revoked_at='2026-10-05T12:00:00Z';body=null;}
  else if(path.endsWith('/confidential-configuration'))body={companyId:company,handlerConfigured:false};
  else if(path.endsWith('/feedback-reviews/employees'))body=[{id:8,first_name:'Sam',last_name:'Member',email:'sam@example.com',can_review:company===12}];
  else if(path.endsWith('/feedback-reviews/feedback')){
   if(req.method()==='POST'){writes.push({company,action:'feedback',input:req.postDataJSON()});body='feedback-a';}
   else body=company===12?[{id:'feedback-a',content:'Private A recognition',anonymous:true,sender_name:null,sender_type:'Employee',submitted_at:'2026-10-01T12:00:00Z'}]:[];
  }else if(path.includes('/feedback-reviews/reviews')){
   if(path.endsWith('/publish')){writes.push({company,action:'publish',version:Number(url.searchParams.get('version'))});reviews[0].published_at='2026-10-05T12:00:00Z';body=null;}
   else if(req.method()==='POST'){const input=req.postDataJSON();writes.push({company,action:'review',input});reviews=[{...input,id:'review-a',employee_id:input.employeeId,first_name:'Sam',last_name:'Member',employee_name:'Sam Member',employee_email:'sam@example.com',review_year:input.year,can_manage:true,created_at:'2026-10-05T12:00:00Z',modified_at:'2026-10-05T12:00:00Z'}];body='review-a';}
   else body=company===12?reviews:[];
  }else if(path.includes('/employee-reports')){
   if(path.endsWith('/mine'))body=[];
   else if(path.endsWith('/recuse')){writes.push({company,action:'recuse'});cases=[];body=null;}
   else if(req.method()==='PATCH'){const input=req.postDataJSON();writes.push({company,action:'case-review',input});cases[0]={...cases[0],status:input.status,version:4};body=null;}
   else if(req.method()==='POST'){writes.push({company,action:'case-submit'});body={reportId:'receipt-a',status:'SUBMITTED'};}
   else body=path.endsWith('/employee-reports')?cases:cases[0];
  }else if(path.includes('/letter-requests')){
   const row={id:9,companyId:12,userId:8,userName:'Sam Member',requestedFullName:'Sam Member',requestedJobTitle:'Engineer',employmentStartDate:'2025-01-01',requestType:'EMPLOYMENT_VERIFICATION',status:'SUBMITTED',version:2,submittedAt:'2026-10-01T12:00:00Z',letterPreview:'Company A\nOctober 5, 2026\n\nSubject: Employment letter\n\nEmployment details'};
   if(path.endsWith('/pending'))body=[row];else if(path.endsWith('/my'))body=[];else if(path.endsWith('/approve')){writes.push({company,action:'letter-approve',input:req.postDataJSON()});body={...row,status:'APPROVED'};}else body=row;
  }else if(path.endsWith('/audit'))body=[{id:'event-a',userName:'Jordan Admin',action:'SENSITIVE_ACCESS_GRANTED',entityType:'SensitiveGrant',entityId:'grant-a',createdAt:'2026-10-05T12:00:00Z'}];
  else if(path.endsWith('/unread-count'))body=0;
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });return{calls,writes,errors};
}

(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});for(const width of [1440,390]){const page=await browser.newPage({viewport:{width,height:1000}});await fixture(page);await page.route('**/api/companies/12/context',async route=>{await route.fulfill({json:{id:12,name:'Company A',permissions:{companyId:12,companyRoles:['COMPANY_ADMIN'],projects:[],capabilities:{canManageCompanyPeople:true,canReviewLeaveAndLetters:true,canManageSensitiveGrants:true,canViewCompanyAudit:true,canManageLeavePolicy:true}}}})});for(const path of ['letter-management','sensitive-access','audit','leave-management']){await page.goto('http://localhost:5173/'+path);await page.getByRole('heading',{level:1}).waitFor();await page.locator('.loading-indicator').waitFor({state:'hidden'});console.log(path,width,await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth})));await page.screenshot({path:'reports/ui-'+path+'-'+width+'.png',fullPage:true});}await page.close();}await browser.close();})();