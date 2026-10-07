const {test,expect}=require('@playwright/test');
async function fixture(page){
  const companies=[{id:12,name:'Company A',slug:'company-a'},{id:13,name:'Company B',slug:'company-b'}];
  const user={id:5,email:'admin@example.com',firstName:'Jordan',lastName:'Admin',role:'EMPLOYEE',profileCompleted:true,dateOfBirth:'1990-01-01',jobTitle:'Legacy shared title'};
  const records={
    '12:5':{companyId:12,userId:5,employeeId:'ADMIN-A',jobTitle:'Admin A',joiningDate:'2020-01-01',membershipStatus:'ACTIVE',version:0},
    '12:7':{companyId:12,userId:7,employeeId:'A-001',jobTitle:'Engineer A',joiningDate:'2020-01-01',membershipStatus:'ACTIVE',version:2},
    '13:5':{companyId:13,userId:5,employeeId:'B-005',jobTitle:'Manager B',joiningDate:'2018-01-01',membershipStatus:'ACTIVE',version:0},
    '13:7':{companyId:13,userId:7,employeeId:'B-007',jobTitle:'Engineer B',joiningDate:'2019-01-01',membershipStatus:'ACTIVE',version:0},
  };
  const employmentWrites=[],profileWrites=[],errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{localStorage.setItem('authToken',`test.${btoa(JSON.stringify({exp:Math.floor(Date.now()/1000)+3600}))}.test`);localStorage.setItem('chronos:company:5','12');});
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;let body=[];
    if(path==='/api/auth/me')body=user;
    else if(path==='/api/companies/context')body={platformAdmin:false,memberships:companies,companies,platformPermissions:{roles:[],capabilities:{}}};
    else if(/\/companies\/\d+\/context$/.test(path)){
      const company=companies.find(company=>String(company.id)===path.split('/')[3]);
      body={...company,permissions:{companyId:company.id,companyRoles:company.id===12?['COMPANY_ADMIN']:[],
        capabilities:company.id===12?{canManageCompanyPeople:true,canViewProjects:true}:{},projects:[]}};
    }else if(path==='/api/companies/12/members')body=[{user_id:7,email:'member@example.com',first_name:'Sam',last_name:'Member',roles:['USER'],status:'ACTIVE',
      employee_id:records['12:7'].employeeId,job_title:records['12:7'].jobTitle}];
    else if(/\/companies\/\d+\/members\/\d+\/employment$/.test(path)){
      const [, , ,company,,member]=path.split('/');const key=`${company}:${member}`;
      if(route.request().method()==='PUT'){
        const input=route.request().postDataJSON();employmentWrites.push({company:Number(company),member:Number(member),input});
        records[key]={...records[key],...input,version:records[key].version+1};
      }body=records[key];
    }else if(path==='/api/users/me/profile'){
      const input=route.request().postDataJSON();profileWrites.push(input);body={...user,...input};
    }else if(path.endsWith('unread-count'))body=0;
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
  });
  return{records,employmentWrites,profileWrites,errors};
}

test('company admin edits employment in A without changing B or login fields',async({page})=>{
  const state=await fixture(page);await page.goto('/companies');
  await page.getByRole('button',{name:'Employment details',exact:true}).click();
  const editor=page.getByRole('region',{name:'Company employment details'});
  await editor.getByLabel('Job title').fill('Lead A');await editor.getByLabel('Employee ID').fill('A-NEW');
  await editor.getByRole('button',{name:'Save employment details'}).click();
  await expect(editor.getByRole('status')).toContainText('saved');
  expect(state.employmentWrites).toEqual([{company:12,member:7,input:{employeeId:'A-NEW',jobTitle:'Lead A',joiningDate:'2020-01-01',version:2}}]);
  expect(state.records['13:7'].jobTitle).toBe('Engineer B');expect(state.profileWrites).toEqual([]);
  await page.getByLabel('Current company',{exact:true}).selectOption('13');
  await expect(page.getByRole('button',{name:'Employment details',exact:true})).toHaveCount(0);
  expect(state.errors).toEqual([]);
});

test('personal profile remains separate and employment follows the selected company',async({page})=>{
  const state=await fixture(page);await page.goto('/profile');
  await expect(page.getByText('Admin A',{exact:true})).toBeVisible();
  await expect(page.getByLabel('Job Title',{exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'Save Profile',exact:true}).click();
  await expect(page.getByText('Profile saved.',{exact:true})).toBeVisible();
  expect(state.profileWrites.length).toBe(1);
  for(const field of ['employeeId','jobTitle','joiningDate'])expect(state.profileWrites[0]).not.toHaveProperty(field);
  await page.getByLabel('Current company',{exact:true}).selectOption('13');
  await expect(page.getByText('Manager B',{exact:true})).toBeVisible();
  await expect(page.getByText('Admin A',{exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Save employment details'})).toHaveCount(0);
  expect(state.errors).toEqual([]);
});
