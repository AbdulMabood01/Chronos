const { test, expect } = require('@playwright/test');

const a = { id: 12, name: 'Company A', slug: 'company-a', plan_tier: 'FREE', project_limit: 3, team_limit: 6 };
const b = { ...a, id: 13, name: 'Company B', slug: 'company-b' };

async function fixture(page, initialCompanies, platformAdmin = false) {
  let companies = initialCompanies;
  const errors = [];
  const requests = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    const claims = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    localStorage.setItem('authToken', `test.${claims}.test`);
  });
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    requests.push(path);
    let body = [];
    if (path === '/api/auth/me') body = { id: 5, email: 'context@example.com', firstName: 'Context', lastName: 'Tester',
      role: platformAdmin ? 'ADMIN' : 'EMPLOYEE', roles: [platformAdmin ? 'PLATFORM_ADMIN' : 'COMPANY_ADMIN'], profileCompleted: true };
    else if (path === '/api/companies/context') body = { platformAdmin, memberships: platformAdmin ? [] : companies, companies,
      platformPermissions:{roles:platformAdmin ? ['PLATFORM_ADMIN']:[],capabilities:{canCreateCompanies:platformAdmin,canManageCompanyPlans:platformAdmin}} };
    else if (/\/companies\/\d+\/context$/.test(path)) {
      body = companies.find(company => String(company.id) === path.split('/')[3]);
      if (!body) return route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ message: 'Company access denied' }) });
      body={...body,permissions:{companyId:body.id,companyRoles:platformAdmin||body.id===13 ? []:['COMPANY_ADMIN'],
        capabilities:platformAdmin ? {} : body.id===13 ? {canSubmitWork:true,canViewProjects:true} : {canManageCompanyPeople:true,canViewProjects:true},
        projects:platformAdmin ? [] : body.id===13 ? [{projectId:201,roles:['USER'],capabilities:{canSubmitWork:true}}] : []}};
    } else if (path.includes('unread-count')) body = 0;
    else if (path.includes('/email-alerts/preferences')) body = {};
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  return { setCompanies: next => { companies = next; }, errors,requests };
}

test('member chooses a company, shares selection with the company screen, and logs out', async ({ page }) => {
  const state = await fixture(page, [a,b]);
  await page.goto('/companies');
  await expect(page.getByRole('heading', { name: 'Choose your company' })).toBeVisible();
  await page.getByRole('button', { name: /Company A/ }).click();
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('12');
  await expect(page.getByRole('heading', { name: 'Company A', exact:true })).toBeVisible();
  await page.getByLabel('Current company', { exact:true }).selectOption('13');
  await expect(page.getByRole('heading', { name: 'Company B', exact:true })).toBeVisible();
  await expect(page.getByLabel('Current company',{exact:true})).toHaveValue('13');
  await page.reload();
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('13');
  await page.getByRole('button', { name: 'Sign Out', exact:true }).click();
  await expect(page.getByRole('button', { name: 'Sign In', exact:true })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('chronos:company:5'))).toBeNull();
  expect(state.errors).toEqual([]);
});

test('removed membership clears selection and requires a fresh choice', async ({ page }) => {
  const state = await fixture(page, [a]);
  await page.goto('/companies');
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('12');
  state.setCompanies([b]);
  await page.evaluate(() => window.dispatchEvent(new Event('chronos:company-memberships-changed')));
  await expect(page.getByRole('heading', { name: 'Choose your company' })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('access changed');
  expect(await page.evaluate(() => localStorage.getItem('chronos:company:5'))).toBeNull();
  await page.getByRole('button', { name: /Company B/ }).click();
  await expect(page.getByRole('heading', { name: 'Company B', exact:true })).toBeVisible();
  expect(state.errors).toEqual([]);
});

test('platform admin keeps platform view and can select company metadata', async ({ page }) => {
  const state = await fixture(page, [a,b], true);
  await page.goto('/companies');
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('');
  await expect(page.getByRole('button', { name: '+ New company' })).toBeVisible();
  await page.getByLabel('Current company', { exact:true }).selectOption('12');
  await expect(page.getByRole('heading', { name: 'Company A', exact:true })).toBeVisible();
  await page.getByLabel('Current company', { exact:true }).selectOption('');
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('');
  expect(state.errors).toEqual([]);
});

test('no active company shows onboarding links on mobile', async ({ page }) => {
  const state = await fixture(page, []);
  await page.setViewportSize({ width:390, height:844 });
  await page.goto('/companies');
  await expect(page.getByRole('heading', { name: 'Your company access' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'My invitations' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Request company access' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(state.errors).toEqual([]);
});

test('current company selector fits in the mobile header', async ({ page }) => {
  const state = await fixture(page, [a]);
  await page.setViewportSize({ width:390, height:844 });
  await page.goto('/companies');
  await expect(page.getByLabel('Current company', { exact:true })).toHaveValue('12');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(state.errors).toEqual([]);
});

test('navigation changes from company admin to user without inherited privileges',async({page})=>{
  const state=await fixture(page,[a,b]);await page.goto('/companies');
  await page.getByRole('button',{name:/Company A/}).click();
  const navigation=page.getByRole('navigation',{name:'Main navigation'});
  await expect(navigation.getByRole('link',{name:'People & access'})).toBeVisible();
  await expect(navigation.getByRole('link',{name:'Projects'})).toBeVisible();
  await expect(navigation.getByRole('link',{name:'Approvals'})).toHaveCount(0);
  await expect(navigation.getByRole('link',{name:'Settings'})).toHaveCount(0);
  await page.getByLabel('Current company',{exact:true}).selectOption('13');
  await expect(navigation.getByRole('link',{name:'People & access'})).toHaveCount(0);
  await expect(navigation.getByRole('link',{name:'Projects'})).toHaveCount(0);
  await expect(navigation.getByRole('link',{name:'Timesheets'})).toBeVisible();
  await page.goto('/projects');
  await expect(page.getByRole('heading',{name:'Company overview'})).toBeVisible();
  expect(state.requests).not.toContain('/api/settings');expect(state.requests).not.toContain('/api/users/all');
  expect(state.errors).toEqual([]);
});

test('platform overview does not load company operational data or expose legacy URLs',async({page})=>{
  const state=await fixture(page,[a,b],true);await page.goto('/dashboard');
  await expect(page.getByRole('heading',{name:'Platform overview'})).toBeVisible();
  const navigation=page.getByRole('navigation',{name:'Main navigation'});
  await expect(navigation.getByRole('link',{name:'Companies'})).toBeVisible();
  for(const name of ['Projects','Approvals','Reports','People & access','Settings'])
    await expect(navigation.getByRole('link',{name})).toHaveCount(0);
  await page.goto('/reports');await expect(page.getByRole('heading',{name:'Platform overview'})).toBeVisible();
  await page.goto('/companies');await page.getByLabel('Current company',{exact:true}).selectOption('12');
  await expect(page.getByRole('heading',{name:'Company A',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Invite a person'})).toHaveCount(0);
  expect(state.requests).not.toContain('/api/projects');expect(state.requests).not.toContain('/api/users/all');
  expect(state.requests.some(path=>path.endsWith('/members'))).toBe(false);
  expect(state.errors).toEqual([]);
});
