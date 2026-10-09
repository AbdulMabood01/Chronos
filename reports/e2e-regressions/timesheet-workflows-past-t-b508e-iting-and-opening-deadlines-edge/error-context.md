# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: timesheet-workflows.spec.cjs >> past timesheet (-1 months) respects editing and opening deadlines
- Location: e2e\timesheet-workflows.spec.cjs:59:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('APPROVED', { exact: true }).first()
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" getByText('APPROVED', { exact: true }).first() with timeout 5000ms
  - waiting for getByText('APPROVED', { exact: true }).first()

```

```yaml
- link "Skip to content":
  - /url: "#workspace-main"
- complementary:
  - link "Maxwell Chronos home":
    - /url: /dashboard
    - img "Maxwell Network Inc"
    - text: CHRONOS COMPANY WORKSPACE
  - navigation "Main navigation":
    - paragraph: Workspace
    - link "Overview":
      - /url: /dashboard
    - link "Timesheets":
      - /url: /timesheets
    - link "Expenses":
      - /url: /expenses
    - link "Workspace":
      - /url: /companies
    - link "My leave":
      - /url: /vacation
    - link "My letters":
      - /url: /requests
    - link "Announcements":
      - /url: /announcements
    - link "Feedback":
      - /url: /feedback
    - link "Performance reviews":
      - /url: /performance-reviews
    - link "Workplace reports":
      - /url: /workplace-reports
    - link "My activity":
      - /url: /audit
    - paragraph: Personal
    - link "Inbox 2 unread":
      - /url: /notifications
    - link "My invitations":
      - /url: /company-invite
  - link "TE Test Employee Company member":
    - /url: /profile
    - text: TE
    - strong: Test Employee
    - text: Company member
  - button "Sign out"
- banner:
  - text: Workspace /
  - strong: Timesheet details
  - time: Oct 6, 2026
  - text: Company
  - combobox "Current company":
    - option "Choose company"
    - option "E2E Company" [selected]
    - option "Other Company"
  - button "Switch to dark theme"
  - button "Email alerts"
  - link "Inbox, 2 unread notifications":
    - /url: /notifications
- main:
  - paragraph: Timesheet not found
- contentinfo: Maxwell Network Inc. Your time. Well managed.
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | const { authenticatePage } = require('./support/auth.cjs');
  3   | const { apiAs, resetFixtures } = require('./support/api.cjs');
  4   | const { json, monthOffset } = require('./support/workflows.cjs');
  5   | let fixture;
  6   | test.beforeEach(async ({ request }) => { fixture = await resetFixtures(request); });
  7   | const periodParams = date => ({ projectId: fixture.projectId, date });
  8   | async function createSheet(request, role = 'employee', offset = 0) {
  9   |   const actor = await apiAs(request, role);
  10  |   const period = monthOffset(fixture.today, offset);
  11  |   const sheet = await json(await actor.post('/api/timesheets', { params: { year: period.year, month: period.month, companyId: fixture.companyId } }));
  12  |   return { actor, sheet, period };
  13  | }
  14  | async function addHours(actor, sheet, date, hours = '4') {
  15  |   return json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`, { data: {
  16  |     timesheetId: sheet.id, projectId: fixture.projectId, entryDate: date, hours, notes: 'E2E work',
  17  |   } }));
  18  | }
  19  | async function submit(actor, period) {
  20  |   return json(await actor.post('/api/timesheet-periods/submit', { params: periodParams(period.date) }));
  21  | }
  22  | async function decide(actor, submission, approve = true, comment = null, fallbackReason = null) {
  23  |   return json(await actor.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve, comment, fallbackReason } }));
  24  | }
  25  | async function opening(actor, period) {
  26  |   return json(await actor.post('/api/timesheet-periods/opening', { params: periodParams(period.date), data: { reason: 'Enter missed historical hours' } }));
  27  | }
  28  | async function openingDecision(actor, period, approve) {
  29  |   return json(await actor.post(`/api/timesheet-periods/${period.id}/opening-decision`, { data: { approve, comment: 'Historical correction reviewed' } }));
  30  | }
  31  | async function view(actor, period) {
  32  |   return json(await actor.get('/api/timesheet-periods', { params: periodParams(period.date) }));
  33  | }
  34  | 
  35  | test('month helper handles January to December rollover', () => {
  36  |   expect(monthOffset('2026-01-06', -1)).toEqual({ year: 2025, month: 12, date: '2025-12-01' });
  37  | });
  38  | 
  39  | test('user creates hours in browser and manager approves in browser', async ({ page, request }) => {
  40  |   const { actor, sheet, period } = await createSheet(request);
  41  |   expect(sheet.companyId, 'Timesheet responses must identify their owning company for the UI').toBe(fixture.companyId);
  42  |   await authenticatePage(page, request, 'employee', fixture.companyId);
  43  |   await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  44  |   const hours = page.getByRole('spinbutton', { name: `Hours for ${period.date}`, exact: true });
  45  |   await hours.fill('8'); await hours.blur();
  46  |   await expect(page.getByRole('button', { name: 'Submit for Approval', exact: true })).toBeEnabled();
  47  |   await page.getByRole('button', { name: 'Submit for Approval', exact: true }).click();
  48  |   await expect.poll(async () => (await view(actor, period)).status).toBe('SUBMITTED');
  49  |   await authenticatePage(page, request, 'manager', fixture.companyId);
  50  |   await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  51  |   await page.getByRole('button', { name: 'Approve', exact: true }).click();
  52  |   await expect.poll(async () => (await view(actor, period)).status).toBe('APPROVED');
  53  |   await authenticatePage(page, request, 'employee', fixture.companyId);
  54  |   await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
  55  |   await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
  56  | });
  57  | 
  58  | for (const offset of [-1, -2]) {
  59  |   test(`past timesheet (${offset} months) respects editing and opening deadlines`, async ({ page, request }) => {
  60  |     const { actor, sheet, period } = await createSheet(request, 'employee', offset);
  61  |     expect(sheet.year).toBe(period.year); expect(sheet.month).toBe(period.month);
  62  |     const end = new Date(Date.UTC(period.year, period.month, 0));
  63  |     const daysAfter = Math.round((Date.parse(fixture.today) - end.getTime()) / 86400000);
  64  |     if (daysAfter > 7) {
  65  |       const response = await actor.post(`/api/timesheets/${sheet.id}/time-entries`, { data: {
  66  |         timesheetId: sheet.id, projectId: fixture.projectId, entryDate: period.date, hours: '4', notes: 'Closed period',
  67  |       } });
  68  |       expect(response.status()).toBe(400);
  69  |       if (daysAfter > 30) {
  70  |         expect((await actor.post('/api/timesheet-periods/opening', { params: periodParams(period.date), data: { reason: 'Outside deadline' } })).status()).toBe(400);
  71  |         expect((await view(actor, period)).editable).toBe(false);
  72  |         return;
  73  |       }
  74  |       const correction = await opening(actor, period);
  75  |       const admin = await apiAs(request, 'projectAdmin');
  76  |       await openingDecision(admin, correction, true);
  77  |     }
  78  |     await addHours(actor, sheet, period.date);
  79  |     const submission = await submit(actor, period);
  80  |     const manager = await apiAs(request, 'manager');
  81  |     expect((await decide(manager, submission)).status).toBe('APPROVED');
  82  |     await authenticatePage(page, request, 'employee', fixture.companyId);
  83  |     await page.goto(`/timesheet/${sheet.id}?projectId=${fixture.projectId}`);
> 84  |     await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();
      |                                                                       ^ Error: expect(locator).toBeVisible() failed
  85  |   });
  86  | }
  87  | 
  88  | test('rejection is corrected and resubmitted; unauthorized reviewers cannot decide', async ({ request }) => {
  89  |   const { actor, sheet, period } = await createSheet(request);
  90  |   const entry = await addHours(actor, sheet, period.date);
  91  |   let submission = await submit(actor, period);
  92  |   for (const role of ['employee', 'companyAdmin', 'platformAdmin', 'otherAdmin', 'creator']) {
  93  |     const reviewer = await apiAs(request, role);
  94  |     expect((await reviewer.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  95  |     expect((await view(actor, period)).status).toBe('SUBMITTED');
  96  |   }
  97  |   const manager = await apiAs(request, 'manager');
  98  |   expect((await decide(manager, submission, false, 'Correct the hours')).status).toBe('REJECTED');
  99  |   await json(await actor.put(`/api/timesheets/${sheet.id}/time-entries/${entry.id}`, { data: { hours: '8', notes: 'Corrected hours', projectId: fixture.projectId } }));
  100 |   submission = await submit(actor, period);
  101 |   expect(submission.status).toBe('SUBMITTED');
  102 |   expect((await decide(manager, submission)).status).toBe('APPROVED');
  103 | });
  104 | 
  105 | for (const reviewerRole of ['projectAdmin', 'moderator']) {
  106 |   test(`manager cannot self-approve; ${reviewerRole} reviews manager hours`, async ({ request }) => {
  107 |     const { actor, sheet, period } = await createSheet(request, 'manager');
  108 |     await addHours(actor, sheet, period.date);
  109 |     const submission = await submit(actor, period);
  110 |     expect((await actor.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  111 |     expect((await decide(await apiAs(request, reviewerRole), submission)).status).toBe('APPROVED');
  112 |   });
  113 | }
  114 | 
  115 | test('past approved period opening is declined, approved, corrected and resubmitted', async ({ request }) => {
  116 |   fixture.projectId = fixture.dailyProjectId;
  117 |   const day = new Date(`${fixture.today}T12:00:00Z`); day.setUTCDate(day.getUTCDate() - 10);
  118 |   while ([0, 6].includes(day.getUTCDay())) day.setUTCDate(day.getUTCDate() - 1);
  119 |   const date = day.toISOString().slice(0, 10);
  120 |   const actor = await apiAs(request, 'employee');
  121 |   const sheet = await json(await actor.post('/api/timesheets', { params: { companyId: fixture.companyId, year: day.getUTCFullYear(), month: day.getUTCMonth()+1 } }));
  122 |   const period = { date };
  123 |   const admin = await apiAs(request, 'projectAdmin');
  124 |   expect((await view(actor, period)).editable).toBe(false);
  125 |   await openingDecision(admin, await opening(actor, period), true);
  126 |   const entry = await addHours(actor, sheet, period.date);
  127 |   const manager = await apiAs(request, 'manager');
  128 |   await decide(manager, await submit(actor, period));
  129 |   let correction = await opening(actor, period);
  130 |   expect((await actor.post(`/api/timesheet-periods/${correction.id}/opening-decision`, { data: { approve: true } })).status()).toBe(403);
  131 |   expect((await openingDecision(admin, correction, false)).openingStatus).toBe('DECLINED');
  132 |   expect((await view(actor, period)).editable).toBe(false);
  133 |   correction = await opening(actor, period);
  134 |   await openingDecision(admin, correction, true);
  135 |   await json(await actor.put(`/api/timesheets/${sheet.id}/time-entries/${entry.id}`, { data: { hours: '8', projectId: fixture.projectId, notes: 'Approved correction' } }));
  136 |   expect((await decide(manager, await submit(actor, period))).status).toBe('APPROVED');
  137 | });
  138 | 
  139 | test('project admin fallback requires a recorded reason', async ({ request }) => {
  140 |   const { actor, sheet, period } = await createSheet(request);
  141 |   await addHours(actor, sheet, period.date);
  142 |   const submission = await submit(actor, period);
  143 |   const admin = await apiAs(request, 'projectAdmin');
  144 |   expect((await admin.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  145 |   expect((await decide(admin, submission, true, null, 'Project Manager is unavailable')).status).toBe('APPROVED');
  146 |   const history = await json(await actor.get(`/api/timesheet-periods/${submission.id}/history`));
  147 |   expect(history.some(row => row.event === 'APPROVED')).toBe(true);
  148 | });
  149 | 
  150 | test('Moderator time grant cannot review work outside its date bounds', async ({ request }) => {
  151 |   const { actor, sheet, period } = await createSheet(request);
  152 |   await addHours(actor, sheet, period.date);
  153 |   const submission = await submit(actor, period);
  154 |   const admin = await apiAs(request, 'companyAdmin');
  155 |   const grants = await json(await admin.get(`/api/companies/${fixture.companyId}/moderator-grants`));
  156 |   for (const grant of grants) expect((await admin.delete(`/api/companies/${fixture.companyId}/moderator-grants/${grant.id}`)).ok()).toBe(true);
  157 |   const moderator = await apiAs(request, 'moderator');
  158 |   const me = await json(await moderator.get('/api/auth/me'));
  159 |   const previous = monthOffset(fixture.today, -2);
  160 |   expect((await admin.post(`/api/companies/${fixture.companyId}/moderator-grants`, { data: {
  161 |     projectId: fixture.projectId, userId: me.id, timesheets: true, expenses: false, startsOn: previous.date, endsOn: previous.date,
  162 |   } })).ok()).toBe(true);
  163 |   expect((await moderator.post(`/api/timesheet-periods/${submission.id}/decision`, { data: { approve: true } })).status()).toBe(403);
  164 |   expect((await view(actor, period)).status).toBe('SUBMITTED');
  165 | });
  166 | 
```