const { test, expect } = require('@playwright/test');
const { loginByApi, authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { json } = require('./support/workflows.cjs');
let fixture;
test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });

test('company admin creates a project with explicit owner without inheriting editing rights', async ({ page, request }) => {
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/projects');
  await page.getByRole('button', { name: 'New Project', exact: true }).click();
  await page.getByLabel('Project Name', { exact: true }).last().fill('E2E Owner Project');
  await page.getByLabel('Project Code', { exact: true }).fill('E2E-OWNER');
  await page.getByLabel('Initial project owner', { exact: true }).selectOption(String(fixture.projectAdminId));
  await page.getByRole('button', { name: /Create Draft/i }).click();
  const admin = await apiAs(request, 'companyAdmin');
  await expect.poll(async () => (await json(await admin.get('/api/projects', { params: { companyId: fixture.companyId } }))).some(p => p.code === 'E2E-OWNER')).toBe(true);
  const project = (await json(await admin.get('/api/projects', { params: { companyId: fixture.companyId } }))).find(p => p.code === 'E2E-OWNER');
  expect(project.status).toBe('DRAFT'); expect(project.ownerUserId).toBe(fixture.projectAdminId); expect(project.canManage).toBe(false);
  expect((await admin.put(`/api/projects/${project.id}`, { data: { ...project, name: 'Unauthorized edit' } })).status()).toBe(403);
  const owner = await apiAs(request, 'projectAdmin');
  const roles = await json(await admin.get(`/api/companies/${fixture.companyId}/projects/${project.id}/roles`));
  expect(roles.some(r => r.user_id === fixture.projectAdminId && r.role_key === 'PROJECT_ADMIN')).toBe(true);
  await authenticatePage(page, request, 'projectAdmin', fixture.companyId);
  await page.goto('/projects');
  await page.getByLabel('Filter', { exact: true }).selectOption('DRAFT');
  await page.getByRole('button', { name: /E2E Owner Project/ }).click();
  await expect(page.getByRole('button', { name: 'Activate Project' })).toBeDisabled();
  // Set up manager and team via real API; activation remains a browser action.
  await json(await owner.put(`/api/projects/${project.id}`, { data: {
    code: project.code, name: project.name, status: 'DRAFT', projectManagerId: fixture.managerId,
    projectManagerHoursApproverId: fixture.projectAdminId,
    projectManagerStartDate: fixture.today, projectManagerEndDate: `${Number(fixture.today.slice(0,4))+1}-12-31`,
    projectManagerBillRate: 100, projectManagerPlannedHours: 160,
  } }));
  await json(await owner.post(`/api/projects/${project.id}/assignments/${fixture.employeeId}`, { params: {
    startDate: fixture.today, endDate: `${Number(fixture.today.slice(0,4))+1}-12-31`, billRate: 100, plannedHours: 160,
  } }));
  await page.reload();
  await page.getByLabel('Filter', { exact: true }).selectOption('DRAFT');
  await page.getByRole('button', { name: /E2E Owner Project/ }).click();
  await page.getByRole('button', { name: 'Activate Project' }).click();
  await expect(page.getByText('Project activated. The team can now use it.')).toBeVisible();
  const employee = await apiAs(request, 'employee');
  expect((await json(await employee.get('/api/projects/assigned'))).some(p => p.id === project.id)).toBe(true);
});

test('company project admin creates for self; project-only roles and platform cannot create', async ({ request }) => {
  const admin = await apiAs(request, 'companyAdmin');
  const before = await json(await admin.get('/api/projects', { params: { companyId: fixture.companyId } }));
  const creator = await apiAs(request, 'creator');
  const input = { companyId: fixture.companyId, code: 'E2E-CREATOR', name: 'Creator project', status: 'DRAFT' };
  const created = await json(await creator.post('/api/projects', { data: input }));
  expect(created.ownerUserId).toBeTruthy();
  const context = await json(await creator.get(`/api/companies/${fixture.companyId}/context`));
  expect(context.permissions.projects.find(p => p.projectId === created.id).capabilities.canManageProject).toBe(true);
  expect((await creator.post('/api/projects', { data: { ...input, code: 'E2E-OTHER-OWNER', ownerUserId: fixture.employeeId } })).ok()).toBeFalsy();
  for (const role of ['employee', 'manager', 'projectAdmin', 'platformAdmin', 'otherAdmin']) {
    const actor = await apiAs(request, role);
    expect((await actor.post('/api/projects', { data: { ...input, code: `E2E-${role}`, ownerUserId: fixture.projectAdminId } })).status()).toBe(403);
  }

  expect((await json(await admin.get('/api/projects', { params: { companyId: fixture.companyId } }))).length).toBe(before.length + 1);
});

test('company admin invites user; user creates account, joins project and signs in', async ({ page, request, browser }) => {
  const email = 'new.employee@e2e.chronos.test';
  await authenticatePage(page, request, 'companyAdmin', fixture.companyId);
  await page.goto('/companies');
  await page.getByRole('button', { name: 'Invitations', exact: true }).click();
  await page.getByLabel('Email address', { exact: true }).fill(email);
  await page.getByRole('combobox', { name: 'Role', exact: true }).selectOption('USER');
  await page.getByRole('combobox', { name: 'Project', exact: true }).selectOption(String(fixture.projectId));
  await page.getByRole('button', { name: 'Send invitation', exact: true }).click();
  await expect(page.getByText('Invitation sent.', { exact: true })).toBeVisible();
  const admin = await apiAs(request, 'companyAdmin');
  const invitation = (await json(await admin.get(`/api/companies/${fixture.companyId}/invitations`))).find(i => i.invitee_email === email);
  expect(invitation).toBeTruthy();
  const { token } = await json(await admin.post(`/api/e2e/company-invitation-token/${invitation.id}`));
  const context = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const join = await context.newPage();
    await join.goto(`/company-invite#token=${token}`);
    await join.getByRole('button', { name: 'Create an account', exact: true }).click();
    await join.getByLabel('First name', { exact: true }).fill('New');
    await join.getByLabel('Last name', { exact: true }).fill('Employee');
    await join.getByLabel('Password', { exact: true }).fill('Activated-E2E-123!');
    await join.getByRole('checkbox').check();
    await join.getByRole('button', { name: 'Create account and join', exact: true }).click();
    await expect(join).toHaveURL(/\/dashboard$/);
    const bearer = await loginByApi(request, { email, password: 'Activated-E2E-123!' });
    const selected = await json(await request.get(`/api/companies/${fixture.companyId}/context`, { headers: { Authorization: `Bearer ${bearer}` } }));
    expect(selected.permissions.projects.some(p => p.projectId === fixture.projectId && p.roles.includes('USER'))).toBe(true);
    expect((await request.post('/api/companies/invitations/claim', { data: { token, firstName: 'Again', lastName: 'User', password: 'Activated-E2E-123!' } })).ok()).toBeFalsy();
  } finally { await context.close(); }
});

test('unauthorized invitations and company access are denied', async ({ page, request }) => {
  for (const role of ['employee', 'manager', 'platformAdmin', 'otherAdmin']) {
    const actor = await apiAs(request, role);
    expect((await actor.post(`/api/companies/${fixture.companyId}/invitations`, { data: { email: `${role}.invite@e2e.chronos.test`, role: 'USER', projectId: fixture.projectId } })).status()).toBe(403);
  }
  const projectAdmin = await apiAs(request, 'projectAdmin');
  expect((await projectAdmin.post(`/api/companies/${fixture.companyId}/invitations`, { data: { email: 'admin.invite@e2e.chronos.test', role: 'COMPANY_ADMIN' } })).status()).toBe(403);
  await authenticatePage(page, request, 'employee', fixture.companyId);
  await page.goto('/leave-management'); await expect(page).toHaveURL(/\/dashboard$/);
  await authenticatePage(page, request, 'platformAdmin');
  await page.goto('/projects'); await expect(page).toHaveURL(/\/dashboard$/);
});
