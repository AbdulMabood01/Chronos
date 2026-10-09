const { test, expect } = require('./support/company-fixture.cjs');

async function setup(page, role) {
  await page.clock.setFixedTime(new Date('2026-09-24T12:00:00'));
  await page.addInitScript(() => localStorage.setItem('authToken', 'dashboard-test'));
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const assignments = [{ userId: 7, userName: 'Alex Morgan', isActive: true, plannedHours: 88, startDate: '2026-09-01', endDate: '2026-09-30' }];
    let data = [];
    if (path === '/api/auth/me') data = { id: 7, role, firstName: 'Alex', lastName: 'Morgan', profileCompleted: true };
    if (path === '/api/notifications/unread-count') data = 0;
    if (path === '/api/projects' || path === '/api/projects/assigned') data = [{ id: 10, code: 'ATLAS', name: 'Client portal', status: 'ACTIVE', isActive: true, assignments }, { id: 11, code: 'NORTH', name: 'Operations platform', status: 'ACTIVE', isActive: true, assignments: [] }];
    if (path === '/api/projects/health') data = [
      { projectId: 10, projectCode: 'ATLAS', projectName: 'Client portal', status: 'AT_RISK', loggedHours: 184, allocatedHours: 180, pendingApprovals: 2, signals: [{ code: 'HOURS_EXCEEDED', message: '4 hours over the allocated budget' }, { code: 'MISSING_SUBMISSIONS', message: '2 project timesheets awaiting submission for completed months' }] },
      { projectId: 11, projectCode: 'NORTH', projectName: 'Operations platform', status: 'HEALTHY', loggedHours: 40, allocatedHours: 160, pendingApprovals: 0, signals: [] },
    ];
    if (path === '/api/timesheets/my') data = [{ id: 20, year: 2026, month: 9, status: 'DRAFT', totalHours: 22, timeEntries: [{ projectId: 10, entryDate: '2026-09-21', hours: 8 }, { projectId: 10, entryDate: '2026-09-22', hours: 7 }, { projectId: 10, entryDate: '2026-09-23', hours: 7 }] }, { id: 19, year: 2026, month: 8, status: 'REJECTED', totalHours: 150, rejectionReason: 'Please check the project allocation.', timeEntries: [] }];
    if (path.endsWith('/leave-balance')) data = { configured: true, vacation: { remainingDays: 12.5 } };
    if (path === '/api/timesheets/project-submissions/pending') data = [{ id: 2, timesheetId: 19, projectId: 10, userName: 'Jamie Chen', projectCode: 'ATLAS', year: 2026, month: 8, totalHours: 150 }];
    if (path === '/api/users') data = [{ id: 7, role: 'EMPLOYEE', isActive: true }, { id: 8, role: 'PROJECT_ADMIN', isActive: true }];
    if (path === '/api/vacation/pending') data = [{ id: 1 }];
    if (path === '/api/announcements') data = [{ id: 'news', title: 'Quarterly team meeting', status: 'PUBLISHED', priority: 'IMPORTANT', publish_date: '2026-09-23', acknowledgment_required: true }, { id: 'office', title: 'Office access this weekend', status: 'PUBLISHED', priority: 'NORMAL', publish_date: '2026-09-22', acknowledgment_required: false }];
    await route.fulfill({ json: data });
  });
}

for (const role of ['EMPLOYEE','PROJECT_ADMIN','ADMIN']) {
 test(role + ' company overview respects navigation and fits desktop/mobile themes', async ({page}) => {
   const errors=[];page.on('pageerror',e=>errors.push(e.message));await setup(page,role);await page.goto('/dashboard');
   await expect(page.getByRole('heading',{name:'Company overview',exact:true})).toBeVisible();
   await expect(page.getByRole('heading',{name:'Your workspace tools'})).toBeVisible();
   await expect(page.getByRole('link',{name:'Timesheets',exact:true})).toHaveCount(role==='EMPLOYEE'?2:0);
   for(const width of [1440,768,390,320]) {await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
   await page.setViewportSize({width:1440,height:900});await page.getByRole('button',{name:'Switch to dark theme'}).click();await expect(page.locator('body')).toHaveClass('theme-dark');
   await page.getByRole('button',{name:'Switch to light theme'}).click();expect(errors).toEqual([]);
 });
}
