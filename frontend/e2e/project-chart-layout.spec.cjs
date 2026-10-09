const { test, expect } = require('@playwright/test');
const { authenticatePage } = require('./support/auth.cjs');
const { apiAs, resetFixtures } = require('./support/api.cjs');

test('project chart labels fit at desktop and mobile widths', async ({ page, request }, testInfo) => {
  const fixture = await resetFixtures(request);
  const owner = await apiAs(request, 'projectAdmin');
  const manager = await apiAs(request, 'manager');
  const project = (await (await manager.get('/api/projects')).json()).find(p => p.id === fixture.projectId);
  expect((await owner.put(`/api/projects/${fixture.projectId}`, { data: {
    code: project.code, name: project.name, description: project.description, status: project.status,
    projectManagerId: project.projectManagerId, projectManagerHoursApproverId: project.projectManagerHoursApproverId,
    expenseBudget: 123456789.99,
  }})).ok()).toBeTruthy();
  const employee = await apiAs(request, 'employee');
  const submitted = await employee.post('/api/expenses', { multipart: {
    expense: { name: 'expense.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({
      projectId: fixture.projectId, category: 'EQUIPMENT', amount: 99999999.99,
      expenseDate: new Date().toISOString().slice(0, 10), description: 'Large chart layout fixture',
    })) },
    receipt: { name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') },
  } });
  expect(submitted.ok(), `Expense submission failed: ${submitted.status()} ${await submitted.text()}`).toBeTruthy();
  const expense = await submitted.json();
  expect((await manager.post(`/api/expenses/${expense.id}/decision`, { data: { status: 'APPROVED' } })).ok()).toBeTruthy();
  const pending = await employee.post('/api/expenses', { multipart: {
    expense: { name: 'expense.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({
      projectId: fixture.projectId, category: 'EQUIPMENT', amount: 12345678.90,
      expenseDate: new Date().toISOString().slice(0, 10), description: 'Pending budget tracker fixture',
    })) },
    receipt: { name: 'receipt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') },
  } });
  expect(pending.ok()).toBeTruthy();

  await authenticatePage(page, request, 'projectAdmin', fixture.companyId);
  for (const width of [1440, 1024, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/projects');
    await page.getByRole('button', { name: /E2E Core Project/ }).click();
    await expect(page.getByRole('heading', { name: 'Project Expenses' })).toBeVisible();
    const budgetMeter = page.getByRole('meter', { name: 'Approved expense budget used' });
    await expect(budgetMeter).toHaveAttribute('aria-valuetext', /\$99,999,999\.99 approved of \$123,456,789\.99 budget; \$12,345,678\.90 pending/);
    const colors = await budgetMeter.evaluate(meter => [...meter.children].map(item => getComputedStyle(item).backgroundColor));
    expect(colors[0]).not.toBe(colors[1]);
    const layout = await page.locator('.project-overview-hero').evaluate(hero => {
      const inside = (inner, outer) => inner.left >= outer.left - 1 && inner.right <= outer.right + 1;
      const overlap = (a, b) => a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
      return [...hero.querySelectorAll('.project-donut-card')].map(card => {
        const bounds = card.getBoundingClientRect();
        const donut = card.querySelector('.project-status-donut').getBoundingClientRect();
        const rows = [...card.querySelectorAll('.project-chart-legend > span')].map(row => {
          const label = [...row.childNodes].find(node => node.nodeType === Node.TEXT_NODE);
          const range = document.createRange();
          range.selectNodeContents(label);
          const amount = row.querySelector('strong').getBoundingClientRect();
          return { amountInside: inside(amount, bounds), labelOverlapsAmount: [...range.getClientRects()].some(rect => overlap(rect, amount)) };
        });
        return { donutInside: inside(donut, bounds), rows };
      });
    });
    expect(layout.every(card => card.donutInside && card.rows.every(row => row.amountInside && !row.labelOverlapsAmount))).toBeTruthy();
    await page.screenshot({ path: testInfo.outputPath(`project-charts-${width}.png`), fullPage: true });
  }
});
