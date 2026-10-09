# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: project-chart-layout.spec.cjs >> project chart labels fit at desktop and mobile widths
- Location: e2e\project-chart-layout.spec.cjs:5:1

# Error details

```
Error: expect(received).toBeTruthy()

Received: false
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | const { authenticatePage } = require('./support/auth.cjs');
  3  | const { apiAs, resetFixtures } = require('./support/api.cjs');
  4  | 
  5  | test('project chart labels fit at desktop and mobile widths', async ({ page, request }, testInfo) => {
  6  |   const fixture = await resetFixtures(request);
  7  |   const owner = await apiAs(request, 'projectAdmin');
  8  |   const manager = await apiAs(request, 'manager');
  9  |   const project = (await (await manager.get('/api/projects')).json()).find(p => p.id === fixture.projectId);
  10 |   expect((await owner.put(`/api/projects/${fixture.projectId}`, { data: {
  11 |     code: project.code, name: project.name, description: project.description, status: project.status,
  12 |     projectManagerId: project.projectManagerId, projectManagerHoursApproverId: project.projectManagerHoursApproverId,
  13 |     expenseBudget: 123456789.99,
  14 |   }})).ok()).toBeTruthy();
  15 |   const employee = await apiAs(request, 'employee');
  16 |   const submitted = await employee.post('/api/expenses', { multipart: {
  17 |     expense: { name: 'expense.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({
  18 |       projectId: fixture.projectId, category: 'EQUIPMENT', amount: 99999999.99,
  19 |       expenseDate: new Date().toISOString().slice(0, 10), description: 'Large chart layout fixture',
  20 |     })) },
  21 |     receipt: { name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') },
  22 |   } });
> 23 |   expect(submitted.ok()).toBeTruthy();
     |                          ^ Error: expect(received).toBeTruthy()
  24 |   const expense = await submitted.json();
  25 |   expect((await manager.post(`/api/expenses/${expense.id}/decision`, { data: { status: 'APPROVED' } })).ok()).toBeTruthy();
  26 |   const pending = await employee.post('/api/expenses', { multipart: {
  27 |     expense: { name: 'expense.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({
  28 |       projectId: fixture.projectId, category: 'EQUIPMENT', amount: 12345678.90,
  29 |       expenseDate: new Date().toISOString().slice(0, 10), description: 'Pending budget tracker fixture',
  30 |     })) },
  31 |     receipt: { name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') },
  32 |   } });
  33 |   expect(pending.ok()).toBeTruthy();
  34 | 
  35 |   await authenticatePage(page, request, 'projectAdmin', fixture.companyId);
  36 |   for (const width of [1440, 1024, 390, 320]) {
  37 |     await page.setViewportSize({ width, height: 900 });
  38 |     await page.goto('/projects');
  39 |     await page.getByRole('button', { name: /E2E Core Project/ }).click();
  40 |     await expect(page.getByRole('heading', { name: 'Project Expenses' })).toBeVisible();
  41 |     const budgetMeter = page.getByRole('meter', { name: 'Approved expense budget used' });
  42 |     await expect(budgetMeter).toHaveAttribute('aria-valuetext', /\$99,999,999\.99 approved of \$123,456,789\.99 budget; \$12,345,678\.90 pending/);
  43 |     const colors = await budgetMeter.evaluate(meter => [...meter.children].map(item => getComputedStyle(item).backgroundColor));
  44 |     expect(colors[0]).not.toBe(colors[1]);
  45 |     const layout = await page.locator('.project-overview-hero').evaluate(hero => {
  46 |       const inside = (inner, outer) => inner.left >= outer.left - 1 && inner.right <= outer.right + 1;
  47 |       const overlap = (a, b) => a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
  48 |       return [...hero.querySelectorAll('.project-donut-card')].map(card => {
  49 |         const bounds = card.getBoundingClientRect();
  50 |         const donut = card.querySelector('.project-status-donut').getBoundingClientRect();
  51 |         const rows = [...card.querySelectorAll('.project-chart-legend > span')].map(row => {
  52 |           const label = [...row.childNodes].find(node => node.nodeType === Node.TEXT_NODE);
  53 |           const range = document.createRange();
  54 |           range.selectNodeContents(label);
  55 |           const amount = row.querySelector('strong').getBoundingClientRect();
  56 |           return { amountInside: inside(amount, bounds), labelOverlapsAmount: [...range.getClientRects()].some(rect => overlap(rect, amount)) };
  57 |         });
  58 |         return { donutInside: inside(donut, bounds), rows };
  59 |       });
  60 |     });
  61 |     expect(layout.every(card => card.donutInside && card.rows.every(row => row.amountInside && !row.labelOverlapsAmount))).toBeTruthy();
  62 |     await page.screenshot({ path: testInfo.outputPath(`project-charts-${width}.png`), fullPage: true });
  63 |   }
  64 | });
  65 | 
```