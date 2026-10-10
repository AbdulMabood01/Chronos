const { test, expect } = require('@playwright/test');
const { apiAs, resetFixtures } = require('./support/api.cjs');
const { authenticatePage } = require('./support/auth.cjs');
const { json, monthOffset } = require('./support/workflows.cjs');

for (const frequency of ['daily', 'weekly']) {
  test(`${frequency}: direct past entry selects its period, preview and batch keep approvals independent`, async ({ page, request }) => {
    const f = await resetFixtures(request), actor = await apiAs(request, 'employee');
    const projectId = frequency === 'daily' ? f.dailyProjectId : f.weeklyProjectId;
    const month = monthOffset(f.today, -2), prefix = month.date.slice(0, 7);
    const weekdays = Array.from({ length: 28 }, (_, i) => `${prefix}-${String(i + 1).padStart(2, '0')}`)
      .filter(date => ![0, 6].includes(new Date(`${date}T12:00:00Z`).getUTCDay()));
    const dates = [weekdays[0], weekdays[5], weekdays[10], weekdays[15]];
    const sheet = await json(await actor.post('/api/timesheets', { params: { year: month.year, month: month.month, companyId: f.companyId } }));
    const view = date => actor.get('/api/timesheet-periods', { params: { projectId, date } }).then(json);
    for (const date of dates.slice(0, 3)) await json(await actor.post(`/api/timesheets/${sheet.id}/time-entries`, {
      data: { entryDate: date, projectId, hours: '4', notes: 'Historical work' },
    }));
    await authenticatePage(page, request, 'employee', f.companyId);
    await page.setViewportSize({ width: 1440, height: 1100 });
    await page.goto(`/timesheet/${sheet.id}?projectId=${projectId}&date=${dates[0]}`);
    const panel = page.getByRole('region', { name: 'Selected approval period' });
    await expect(panel).toContainText((await view(dates[0])).periodStart);
    await expect(page.getByRole('heading', { name: 'Choose a day' })).toHaveCount(0);
    await expect(page.getByRole('group', { name: 'Daily approval periods' })).toHaveCount(0);
    const input = date => page.getByRole('spinbutton', { name: `Hours for ${date}`, exact: true });
    await expect(input(dates[1])).toBeEnabled();
    await input(dates[1]).fill('7');
    await expect(page.getByRole('button', { name: `Select approval period for ${dates[1]}`, exact: true })).toHaveAttribute('aria-pressed', 'true');
    await input(dates[1]).blur();
    await expect.poll(async () => (await view(dates[1])).totalHours).toBe(7);
    await expect(panel).toContainText((await view(dates[1])).periodStart);
    await expect(panel).toContainText('7.00');
    const positions = await page.evaluate(() => ({
      calendar: document.querySelector('.timesheet-calendar-grid').getBoundingClientRect().bottom,
      submit: document.querySelector('.submission-panel').getBoundingClientRect().top,
      approver: getComputedStyle(document.querySelector('.approver-tile strong')).fontSize,
    }));
    expect(positions.submit).toBeGreaterThanOrEqual(positions.calendar);
    expect(parseFloat(positions.approver)).toBeGreaterThanOrEqual(17);
    if (frequency === 'weekly') {
      await expect(page.locator('.period-card.is-selected')).toContainText('Selected week');
      await expect(page.locator('.calendar-day-selected-period').first()).toHaveCSS('outline-width', '3px');
    }
    await page.screenshot({ path: `../reports/timesheet-${frequency}-desktop.png`, fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(await page.locator('.timesheet-calendar-grid').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(2);
    await page.screenshot({ path: `../reports/timesheet-${frequency}-mobile.png`, fullPage: true });
    await page.setViewportSize({ width: 1440, height: 1100 });
    await panel.getByRole('button', { name: /^Submit [A-Z]/ }).click();
    const preview = page.getByRole('dialog', { name: 'Review submission' });
    await expect(preview).toContainText('7.00 hrs');
    expect((await view(dates[1])).status).toBe('DRAFT');
    await preview.getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(input(dates[1])).toBeEnabled();
    await panel.getByRole('button', { name: /^Submit [A-Z]/ }).click();
    await preview.getByRole('button', { name: 'Submit for approval', exact: true }).click();
    await expect.poll(async () => (await view(dates[1])).status).toBe('SUBMITTED');
    await expect(input(dates[1])).toHaveCount(0);
    await expect(input(dates[0])).toBeEnabled();
    await panel.getByRole('button', { name: `Select multiple ${frequency === 'daily' ? 'days' : 'weeks'}`, exact: true }).click();
    for (const date of [dates[0], dates[2]]) {
      const period = await view(date);
      await page.getByRole('checkbox', { name: `Include ${period.periodStart} in batch submission`, exact: true }).check();
    }
    await panel.getByRole('button', { name: `Submit 2 ${frequency === 'daily' ? 'days' : 'weeks'}`, exact: true }).click();
    await expect(preview.locator('li')).toHaveCount(2);
    await expect(preview).toContainText('8.00 hrs');
    await preview.getByRole('button', { name: 'Submit for approval', exact: true }).click();
    for (const date of [dates[0], dates[2]]) await expect.poll(async () => (await view(date)).status).toBe('SUBMITTED');
    await expect(input(dates[3])).toBeEnabled();
    expect((await view(dates[3])).status).toBe('DRAFT');
  });
}
