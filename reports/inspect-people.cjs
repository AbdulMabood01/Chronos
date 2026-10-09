const {chromium}=require('../frontend/node_modules/playwright');
async function fixture(page){
  const companies=[{id:12,name:'Company A',slug:'company-a'},{id:13,name:'Company B',slug:'company-b'}];
  let members=[{user_id:5,email:'admin@example.com',first_name:'Admin',last_name:'One',status:'ACTIVE',roles:['COMPANY_ADMIN'],account_available:true,platform_account:false,membership_version:0},
    {user_id:7,email:'member@example.com',first_name:'Sam',last_name:'Member',job_title:'Engineer',status:'ACTIVE',roles:[],account_available:true,platform_account:false,membership_version:0},
    {user_id:8,email:'former@example.com',first_name:'Former',status:'REMOVED',roles:[],account_available:true,platform_account:false,membership_version:1}];
  const mutations=[],errors=[];let conflict=false;
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);localStorage.setItem('chronos:company:5','12');});
  await page.route('**/api/**',async route=>{
    const url=new URL(route.request().url()),path=url.pathname,method=route.request().method();let body=[];
    if(path==='/api/auth/me')body={id:5,email:'admin@example.com',firstName:'Admin',lastName:'One',role:'EMPLOYEE',roles:['COMPANY_ADMIN'],profileCompleted:true};
    else if(path==='/api/companies/context')body={platformAdmin:false,companies,memberships:companies,platformPermissions:{roles:[],capabilities:{}}};
    else if(/\/companies\/\d+\/context$/.test(path)){
      const company=companies.find(item=>String(item.id)===path.split('/')[3]);const admin=company.id===12&&members.find(member=>member.user_id===5).roles.includes('COMPANY_ADMIN');
      body={...company,permissions:{companyId:company.id,companyRoles:admin?['COMPANY_ADMIN']:[],capabilities:admin?{canManageCompanyPeople:true,canViewProjects:true}:{},projects:[]}};
    }else if(path==='/api/companies/12/members')body=members;
    else if(/\/companies\/\d+\/members\/\d+\/(status|roles|roles\/[^/]+|password-reset)$/.test(path)){
      const pieces=path.split('/'),company=Number(pieces[3]),user=Number(pieces[5]);
      if(company!==12)return route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({message:'Company permission required'})});
      const input=method==='DELETE'?{role:pieces[7],version:Number(url.searchParams.get('version'))}:route.request().postDataJSON();
      mutations.push({company,user,operation:pieces[6],method,input});
      if(conflict)return route.fulfill({status:409,contentType:'application/json',body:JSON.stringify({message:'Transfer project ownership and reassign manager/approver duties before removing this member'})});
      if(pieces[6]==='status')members=members.map(member=>member.user_id===user?{...member,status:input.status,roles:[],membership_version:member.membership_version+1}:member);
      if(pieces[6]==='roles')members=members.map(member=>member.user_id===user?{...member,roles:method==='DELETE'?member.roles.filter(role=>role!==input.role):[...member.roles,input.role],membership_version:member.membership_version+1}:member);
      body={userId:user,status:members.find(member=>member.user_id===user).status,version:members.find(member=>member.user_id===user).membership_version};
    }else if(path.endsWith('/unread-count'))body=0;
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
  });
  return{mutations,errors,setConflict:()=>{conflict=true;},member:id=>members.find(member=>member.user_id===id)};
}

(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});for(const width of [1440,390]){const page=await browser.newPage({viewport:{width,height:1000}});await fixture(page);await page.goto('http://localhost:5173/companies');await page.getByRole('group',{name:'Member member@example.com'}).waitFor();for(const tab of ['People','Invitations','Project roles','Moderator access']){await page.getByRole('button',{name:tab,exact:true}).click();console.log(width,tab,await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth})));await page.screenshot({path:'reports/people-'+width+'-'+tab.replaceAll(' ','-')+'.png',fullPage:true});}await page.close();}await browser.close();})();