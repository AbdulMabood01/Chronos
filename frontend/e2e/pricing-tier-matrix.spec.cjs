const { test, expect } = require('@playwright/test');
const { resetFixtures, apiAs } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
let f;
test.beforeEach(async ({ request }) => { f = await resetFixtures(request); });
const route = suffix => `/api/companies/${f.companyId}/billing${suffix}`;
for(const [plan,monthly,projects,users] of [['PRO',14900,3,75],['PRO_PLUS',29900,7,175],['PRO_MAX',59900,15,375]]) {
  for(const [months,discount] of [[3,0],[6,10],[12,20]]) test(`${plan} ${months}-month quote uses authoritative price, seats and discount`,async({request})=>{
    const admin = await apiAs(request,'companyAdmin');
    const before = await json(await admin.get(route('')));
    const quote = await json(await admin.post(route('/quotes'),{data:{plan,months,extraSeats:7,kind:'PLAN',amount_cents:1,project_limit:999,included_users:999}}));
    const subtotal = (monthly+7*400)*months;
    expect(quote.subtotal_cents).toBe(subtotal); expect(quote.amount_cents).toBe(subtotal*(100-discount)/100);
    expect(quote.discount_cents).toBe(subtotal*discount/100); expect(quote.project_limit).toBe(projects); expect(quote.included_users).toBe(users);
    expect(quote.status).toBe('QUOTED'); expect(quote.currency).toBe('usd');
    const after = await json(await admin.get(route('')));
    expect(after.entitlement).toEqual(before.entitlement);
    expect((await admin.get(route(`/receipts/${quote.id}`))).status()).toBe(404);
  });
}
test('invalid plan, term, seat and purchase kind create no quotes or entitlements',async({request})=>{
  const admin = await apiAs(request,'companyAdmin');
  const valid={plan:'PRO',months:3,extraSeats:0,kind:'PLAN'};
  for(const input of [{plan:'FREE'},{plan:'CUSTOM'},{plan:'UNKNOWN'},{months:1},{months:4},{months:13},{extraSeats:-1},{extraSeats:100001},{kind:'UPGRADE'},{kind:'SEATS'}]) {
    const response = await admin.post(route('/quotes'),{data:{...valid,...input}});
    expect(response.status(),JSON.stringify(input)).toBe(400);
  }
  const state=await json(await admin.get(route(''))); expect(state.purchases).toHaveLength(0); expect(state.entitlement.source).toBe('CONTRACT');
});
test('every non-billing role is denied quote, trial and purchase mutations',async({request})=>{
  for(const role of ['employee','manager','projectAdmin','creator','moderator','platformAdmin','otherAdmin']) {
    const actor=await apiAs(request,role);
    expect((await actor.get(route(''))).status(),role).toBe(403);
    expect((await actor.post(route('/quotes'),{data:{plan:'PRO',months:3,extraSeats:0,kind:'PLAN'}})).status(),role).toBe(403);
    expect((await actor.post(route('/trial'))).status(),role).toBe(403);
  }
});
test('quote cancellation preserves current plan and cannot be used across companies',async({request})=>{
  const admin=await apiAs(request,'companyAdmin'), other=await apiAs(request,'otherAdmin');
  const quote=await json(await admin.post(route('/quotes'),{data:{plan:'PRO',months:3,extraSeats:0,kind:'PLAN'}}));
  expect((await other.get(route(`/purchases/${quote.id}`))).status()).toBe(403);
  expect((await other.post(route(`/purchases/${quote.id}/cancel`))).status()).toBe(403);
  expect((await admin.post(route(`/purchases/${quote.id}/cancel`))).ok()).toBe(true);
  expect((await json(await admin.get(route(`/purchases/${quote.id}`)))).status).toBe('CANCELED');
  expect((await json(await admin.get(route('')))).entitlement.source).toBe('CONTRACT');
});


for(const [tier,limit,users] of [['FREE',1,7],['PRO',3,75],['PRO_PLUS',7,175],['PRO_MAX',15,375]]) test(`${tier}: open-project limit is enforced under concurrent creation and archive releases capacity`,async({request})=>{
  const platform=await apiAs(request,'platformAdmin');
  const admin=await apiAs(request,tier==='FREE'?'otherAdmin':'companyAdmin');
  const owner=await apiAs(request,tier==='FREE'?'otherAdmin':'projectAdmin');
  const companyId=tier==='FREE'?f.otherCompanyId:f.companyId;
  const ownerId=tier==='FREE'?(await json(await owner.get('/api/auth/me'))).id:f.projectAdminId;
  expect((await platform.put(`/api/platform/companies/${companyId}/plan`,{data:{tier,projectLimit:limit,teamLimit:users,version:0,reason:'Isolated capacity regression grant'}})).ok()).toBe(true);
  let count=tier==='FREE'?0:3;
  if(count===limit){expect((await owner.post(`/api/projects/${f.weeklyProjectId}/archive`)).ok()).toBe(true);count--;}
  const input=i=>({companyId,ownerUserId:ownerId,code:`CAP-${i}`,name:`Capacity ${i}`,status:'DRAFT'});
  for(let i=count;i<limit-1;i++)await json(await admin.post('/api/projects',{data:input(i)}));
  const responses=await Promise.all([100,101].map(i=>admin.post('/api/projects',{data:input(i)})));
  expect(responses.map(r=>r.status()).sort((a,b)=>a-b)).toEqual([200,409]);
  const winner=await json(responses.find(r=>r.ok()));
  let projects=await json(await admin.get('/api/projects',{params:{companyId}}));
  expect(projects.filter(p=>!['ARCHIVED','COMPLETED'].includes(p.status))).toHaveLength(limit);
  expect((await owner.post(`/api/projects/${winner.id}/archive`)).ok()).toBe(true);
  await json(await admin.post('/api/projects',{data:input(102)}));
  projects=await json(await admin.get('/api/projects',{params:{companyId}}));
  expect(projects.find(p=>p.id===winner.id).status).toBe('ARCHIVED');
  expect(projects.filter(p=>!['ARCHIVED','COMPLETED'].includes(p.status))).toHaveLength(limit);
});
