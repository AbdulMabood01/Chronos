# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: expenses.spec.cjs >> expense changes, approval, and project totals stay in sync
- Location: e2e\expenses.spec.cjs:5:1

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Pending approval').first()
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" getByText('Pending approval').first() with timeout 5000ms
  - waiting for getByText('Pending approval').first()

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
    - link "Inbox":
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
  - strong: Expenses
  - time: Oct 6, 2026
  - text: Company
  - combobox "Current company":
    - option "Choose company"
    - option "E2E Company" [selected]
    - option "Other Company"
  - button "Switch to dark theme"
  - button "Email alerts"
  - link "Inbox":
    - /url: /notifications
- main:
  - text: PROJECT REIMBURSEMENT
  - heading "Expenses" [level=1]
  - paragraph: Track your claims and submit project expenses for approval.
  - text: Total claims
  - strong: "0"
  - text: Awaiting approval
  - strong: "0"
  - text: Approved amount
  - strong: $0.00
  - alert: Something went wrong on our side. Please try again.
  - text: YOUR CLAIMS
  - heading "My expenses" [level=2]
  - paragraph: Most recent submissions first
  - text: "0"
  - heading "No expenses yet" [level=3]
  - paragraph: Your submitted expenses will appear here.
  - text: NEW CLAIM
  - heading "Submit an expense" [level=2]
  - paragraph: Add the details and attach a receipt.
  - text: Project
  - combobox "Project":
    - option "Select assigned project"
    - option "E2E-CORE · E2E Core Project" [selected]
    - option "E2E-DAILY · E2E Daily Project"
  - text: Category
  - combobox "Category":
    - option "Travel" [selected]
    - option "Meals"
    - option "Software"
    - option "Equipment"
    - option "Supplies"
    - option "Other"
  - text: Amount
  - spinbutton "Amount": "45.00"
  - text: Expense date
  - textbox "Expense date": 2026-10-06
  - text: Description
  - textbox "Description": Train fare for site visit
  - text: Receipt or document (PDF, PNG, JPG, WebP; 10 MB max)
  - button "Receipt or document (PDF, PNG, JPG, WebP; 10 MB max)"
  - button "Submit for approval"
- contentinfo: Maxwell Network Inc. Your time. Well managed.
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | const { authenticatePage } = require('./support/auth.cjs');
  3  | const { apiAs, resetFixtures } = require('./support/api.cjs');
  4  | 
  5  | test('expense changes, approval, and project totals stay in sync', async ({ page, request }) => {
  6  |   test.setTimeout(120000);
  7  |   const fixture = await resetFixtures(request);
  8  |   const owner = await apiAs(request, 'projectAdmin');
  9  |   const manager = await apiAs(request, 'manager');
  10 |   const project = (await (await manager.get('/api/projects')).json()).find(p => p.id === fixture.projectId);
  11 |   expect((await owner.put(`/api/projects/${fixture.projectId}`, { data: {
  12 |     code: project.code, name: project.name, description: project.description, status: project.status,
  13 |     projectManagerId: project.projectManagerId, projectManagerHoursApproverId: project.projectManagerHoursApproverId,
  14 |     expenseBudget: 100,
  15 |   }})).ok()).toBeTruthy();
  16 | 
  17 |   await authenticatePage(page, request, 'employee', fixture.companyId);
  18 |   await page.goto('/expenses');
  19 |   await page.getByRole('combobox', { name: 'Project', exact: true }).selectOption(String(fixture.projectId));
  20 |   await page.getByRole('combobox', { name: 'Category', exact: true }).selectOption('TRAVEL');
  21 |   await page.getByLabel('Amount').fill('45.00');
  22 |   await page.getByLabel('Description').fill('Train fare for site visit');
  23 |   await page.getByLabel(/Receipt or document/).setInputFiles({ name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n1 0 obj\nendobj\n%%EOF') });
  24 |   await page.getByRole('button', { name: 'Submit for approval' }).click();
  25 |   await expect(page.getByRole('heading', { name: 'My expenses' })).toBeVisible();
> 26 |   await expect(page.getByText('Pending approval').first()).toBeVisible();
     |                                                            ^ Error: expect(locator).toBeVisible() failed
  27 | 
  28 |   const employee = await apiAs(request, 'employee');
  29 |   const expenses = await (await employee.get('/api/expenses/mine')).json();
  30 |   const id = expenses[0].id;
  31 |   expect(expenses[0].status).toBe('PENDING_APPROVAL');
  32 |   expect((await (await manager.get('/api/expenses/pending')).json()).some(e => e.id === id)).toBeTruthy();
  33 |   expect((await (await manager.get(`/api/expenses/projects/${fixture.projectId}/totals`)).json()).pending).toBe(45);
  34 | 
  35 |   const changes = await manager.post(`/api/expenses/${id}/decision`, { data: { status: 'CHANGES_REQUESTED', comment: 'Attach the final fare receipt' } });
  36 |   expect(changes.ok()).toBeTruthy();
  37 |   await page.reload();
  38 |   await expect(page.getByText('Changes requested').first()).toBeVisible();
  39 |   await page.getByRole('button', { name: 'View' }).first().click();
  40 |   await expect(page.getByText('Attach the final fare receipt').first()).toBeVisible();
  41 |   await page.getByRole('button', { name: 'Edit and resubmit' }).click();
  42 |   await page.getByLabel('Amount').fill('50.00');
  43 |   await page.getByRole('button', { name: 'Resubmit for approval' }).click();
  44 |   await expect(page.getByText('Pending approval').first()).toBeVisible();
  45 | 
  46 |   await authenticatePage(page, request, 'manager', fixture.companyId);
  47 |   await page.goto('/expenses');
  48 |   await expect(page.getByRole('heading', { name: 'Expense approvals' })).toHaveCount(0);
  49 |   await page.goto('/admin');
  50 |   await expect(page.getByText('Expense Approval')).toBeVisible();
  51 |   await page.getByText('Expense Approval').locator('xpath=ancestor::article').getByRole('button', { name: 'Review' }).click();
  52 |   await Promise.all([
  53 |     page.waitForResponse(response => response.url().includes(`/api/expenses/${id}/decision`) && response.request().method() === 'POST' && response.ok()),
  54 |     page.getByRole('button', { name: 'Approve' }).click(),
  55 |   ]);
  56 |   const totals = await (await manager.get(`/api/expenses/projects/${fixture.projectId}/totals`)).json();
  57 |   expect(Number(totals.approved)).toBe(50);
  58 |   expect(Number(totals.pending)).toBe(0);
  59 |   expect(Number(totals.remaining)).toBe(50);
  60 |   await authenticatePage(page, request, 'projectAdmin', fixture.companyId);
  61 |   await page.goto('/projects');
  62 |   await page.getByRole('button', { name: /E2E Core Project/ }).click();
  63 |   await expect(page.getByRole('img', { name: /Project expenses: 50.00 approved, 0.00 pending, 50.00 remaining/ })).toBeVisible();
  64 |   await authenticatePage(page, request, 'employee');
  65 |   await page.goto('/expenses');
  66 |   await expect(page.getByText('Approved').first()).toBeVisible();
  67 | });
  68 | 
```