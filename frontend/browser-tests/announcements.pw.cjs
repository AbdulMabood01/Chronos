const { test, expect } = require('@playwright/test');
const announcement = { id:'news', title:'A new chapter for our team', content:'Our fall company meeting is coming up.\n\nJoin us on Friday to hear what is next, celebrate recent milestones, and meet the people behind our newest projects.', priority:'IMPORTANT', status:'PUBLISHED', publish_date:'2026-09-22', acknowledgment_required:true, version:0 };

async function setup(page, role) {
  await page.addInitScript(() => localStorage.setItem('authToken','test-session'));
  let acknowledged = false;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id:1, firstName:'Jordan', lastName:'Rivera', role, profileCompleted:true };
    if (url.pathname.endsWith('/notifications/unread-count')) data = 0;
    if (url.pathname === '/api/announcements') data = [announcement, { ...announcement, id:'urgent', title:'Office access this weekend', content:'Please use the north entrance during scheduled maintenance.', priority:'URGENT', acknowledgment_required:false }, { ...announcement, id:'normal', title:'Welcome to our newest colleagues', content:'Get to know the newest members of the Maxwell team.', priority:'NORMAL', acknowledgment_required:false, viewed_at:'now' }];
    if (url.pathname.endsWith('/open')) data = { ...announcement, viewed_at:'now', acknowledged_at:acknowledged ? 'now' : null };
    if (url.pathname.endsWith('/acknowledge')) acknowledged = true;
    if (url.pathname.endsWith('/tracking')) data = { total:2, viewed:1, acknowledged:0, employees:[{ id:1, name:'Alex Chen', email:'alex@example.com', viewed_at:'now' }, { id:2, name:'Taylor Morgan', email:'taylor@example.com' }] };
    if (url.pathname === '/api/announcements' && acknowledged) data[0] = { ...data[0], acknowledged_at:'now' };
    await route.fulfill({ json:data });
  });
}
test('employee dashboard and acknowledgment work on desktop and mobile', async ({ page }) => {
  const errors=[]; page.on('pageerror', e => errors.push(e.message));
  await setup(page,'EMPLOYEE');
  await page.goto('/dashboard');
  const banner = page.getByRole('region', { name:'Company announcements' });
  await expect(banner).toBeVisible();
  await expect(banner.getByRole('link')).toHaveCount(2);
  expect((await banner.boundingBox()).height).toBeLessThan(300);
  await page.setViewportSize({ width:390, height:844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await banner.boundingBox()).height).toBeLessThan(400);
  await page.setViewportSize({ width:1440, height:1050 });
  await page.screenshot({ path:'test-results/announcements-dashboard-desktop.png', fullPage:true });
  await page.getByRole('region', { name:'Needs Attention' }).getByRole('link', { name:/A new chapter for our team/ }).click();
  await expect(page).toHaveURL(/\/announcements\?id=news$/);
  await expect(page.getByRole('heading', { name:announcement.title })).toBeVisible();
  await page.getByRole('button', { name:'Acknowledge announcement' }).click();
  await expect(page.getByText('✓ You acknowledged this announcement.')).toBeVisible();
  await page.setViewportSize({ width:390, height:844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path:'test-results/announcements-detail-mobile.png', fullPage:true });
  await page.getByRole('button', { name:'All announcements' }).click();
  await expect(page.getByRole('button', { name:/A new chapter for our team/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);
  await page.goto('/dashboard');
  await expect(page.getByRole('region', { name:'Company announcements' }).getByRole('link', { name:/Office access this weekend/ })).toBeVisible();
  await expect(page.getByRole('link', { name:/A new chapter for our team/ })).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole('region', { name:'Company announcements' }).getByRole('link', { name:/Office access this weekend/ })).toBeVisible();
});
test('homepage has no announcement banner when the feed is empty', async ({ page }) => {
  await setup(page,'ADMIN');
  await page.route('**/api/announcements?*', route => route.fulfill({ json:[] }));
  const feed = page.waitForResponse(response => new URL(response.url()).pathname === '/api/announcements');
  await page.goto('/dashboard');
  await feed;
  await expect(page.getByRole('region', { name:'Company announcements' })).toHaveCount(0);
  await expect(page.locator('.admin-announcement-banner')).toHaveCount(0);
  await page.goto('/announcements');
  await expect(page.getByRole('heading', { name:"You're all caught up" })).toBeVisible();
});
test('Admin management form and tracking fit mobile and dark theme', async ({ page }) => {
  await setup(page,'ADMIN');
  await page.goto('/announcements');
  await page.getByRole('button', { name:'Manage announcements' }).click();
  await page.getByRole('button', { name:/A new chapter for our team/ }).click();
  await page.getByRole('button', { name:'View tracking' }).click();
  await expect(page.getByText('Alex Chen')).toBeVisible();
  await page.screenshot({ path:'test-results/announcements-tracking-desktop.png', fullPage:true });
  await page.getByRole('button', { name:'Edit', exact:true }).click();
  await expect(page.getByLabel('Title', { exact:true })).toHaveValue(announcement.title);
  await page.getByRole('button', { name:'Switch to dark theme' }).click();
  await page.setViewportSize({ width:390, height:844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path:'test-results/announcements-editor-mobile-dark.png', fullPage:true });
});
