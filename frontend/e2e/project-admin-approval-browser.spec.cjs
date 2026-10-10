const {test,expect}=require('@playwright/test');
const {resetFixtures,apiAs}=require('./support/api.cjs');
const {authenticatePage}=require('./support/auth.cjs');
const {json}=require('./support/workflows.cjs');
let f;
test.beforeEach(async({request})=>{f=await resetFixtures(request);});
for(const frequency of ['DAILY','WEEKLY','MONTHLY'])for(const role of ['employee','manager'])test(frequency+': Project Admin approves '+role+' hours in browser',async({page,request})=>{
 const actor=await apiAs(request,role),id=f[frequency==='DAILY'?'dailyProjectId':frequency==='WEEKLY'?'weeklyProjectId':'projectId'],date=new Date(Date.parse(f.today)-14*86400000).toISOString().slice(0,10);
 const sheet=await json(await actor.post('/api/timesheets',{params:{companyId:f.companyId,year:+date.slice(0,4),month:+date.slice(5,7)}}));
 await json(await actor.post('/api/timesheets/'+sheet.id+'/time-entries',{data:{timesheetId:sheet.id,projectId:id,entryDate:date,hours:'4',notes:'Project Admin browser review'}}));
 const item=await json(await actor.post('/api/timesheet-periods/submit',{params:{projectId:id,date}}));
 await authenticatePage(page,request,'projectAdmin',f.companyId);
 await page.goto('/timesheet/'+sheet.id+'?projectId='+id+'&date='+date);
 const approve=page.getByRole('button',{name:'Approve',exact:true});
 if(role==='employee'){await expect(approve).toBeDisabled();await expect(page.getByRole('button',{name:'Reject',exact:true})).toBeDisabled();await page.getByLabel('Reason for Project Admin fallback').fill('Project Manager is unavailable');}
 await approve.click();
 await expect.poll(async()=>(await json(await actor.get('/api/timesheet-periods',{params:{projectId:id,date}}))).status).toBe('APPROVED');
 await expect(page.getByText(/Approved on/)).toBeVisible();
 const history=await json(await actor.get('/api/timesheet-periods/'+item.id+'/history'));
 expect(history.filter(h=>h.event==='APPROVED')).toHaveLength(1);
 if(role==='employee')expect(JSON.stringify(history)).toContain('Project Manager is unavailable');
});

test('Project Admin rejects employee hours with fallback and correction reasons',async({page,request})=>{
 const actor=await apiAs(request,'employee'),id=f.dailyProjectId,date=new Date(Date.parse(f.today)-14*86400000).toISOString().slice(0,10);
 const sheet=await json(await actor.post('/api/timesheets',{params:{companyId:f.companyId,year:+date.slice(0,4),month:+date.slice(5,7)}}));
 const item=await json(await actor.post('/api/timesheet-periods/submit',{params:{projectId:id,date}}));
 await authenticatePage(page,request,'projectAdmin',f.companyId);await page.goto('/timesheet/'+sheet.id+'?projectId='+id+'&date='+date);
 await page.getByLabel('Reason for Project Admin fallback').fill('Manager unavailable');await page.getByRole('button',{name:'Reject',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Reject Timesheet'});await dialog.getByLabel('Reason for rejection').fill('Please correct these hours');await dialog.getByRole('button',{name:'Reject',exact:true}).click();
 await expect.poll(async()=>(await json(await actor.get('/api/timesheet-periods',{params:{projectId:id,date}}))).status).toBe('REJECTED');
 const history=await json(await actor.get('/api/timesheet-periods/'+item.id+'/history'));expect(JSON.stringify(history)).toContain('Please correct these hours');
});
