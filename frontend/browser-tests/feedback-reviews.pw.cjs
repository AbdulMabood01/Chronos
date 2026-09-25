const { test, expect } = require('@playwright/test');

test('recipient sees incoming feedback only in My Feedback', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('authToken', 'recipient-session'));
  const directions = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id:2, firstName:'Employee', lastName:'B', role:'EMPLOYEE', profileCompleted:true };
    if (url.pathname.endsWith('/notifications/unread-count')) data = 0;
    if (url.pathname.endsWith('/feedback-reviews/feedback')) {
      directions.push(url.searchParams.get('given'));
      if (url.searchParams.get('given') === 'false') data = [{ id:'from-a', content:'Feedback sent by A to B', sender_name:'Employee A', anonymous:false, sender_type:'Employee', submitted_at:'2026-09-23T12:00:00Z' }];
    }
    await route.fulfill({ json:data });
  });
  await page.goto('/feedback');
  await expect(page.getByText('Feedback sent by A to B', {exact:true})).toBeVisible();
  await expect(page.getByText('Submitted by: Employee A', {exact:false})).toBeVisible();
  await page.getByRole('button', {name:"Feedback I've Given", exact:true}).click();
  await expect(page.getByText("You haven't given any feedback yet.")).toBeVisible();
  await expect(page.getByText('Feedback sent by A to B', {exact:true})).toHaveCount(0);
  await page.getByRole('button', {name:'My Feedback', exact:true}).click();
  await expect(page.getByText('Feedback sent by A to B', {exact:true})).toBeVisible();
  // Development StrictMode may repeat the initial request.
  expect(directions.filter((value, index) => index === 0 || value !== directions[index - 1])).toEqual(['false', 'true', 'false']);
});

test('feedback navigation, anonymous submission, and quarterly history on desktop and mobile', async ({ page }) => {
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  await page.addInitScript(() => localStorage.setItem('authToken','test-session'));
  let submitted;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id:1, firstName:'Jordan', lastName:'Rivera', role:'EMPLOYEE', profileCompleted:true };
    if (url.pathname.endsWith('/notifications/unread-count')) data = 0;
    if (url.pathname.endsWith('/feedback-reviews/employees')) data = [{ id:2, first_name:'Alex', last_name:'Chen', email:'alex@example.com' }];
    if (url.pathname.endsWith('/feedback-reviews/feedback') && route.request().method()==='POST') { submitted = route.request().postDataJSON(); data='reference'; }
    if (url.pathname.endsWith('/feedback-reviews/reviews')) data = [{ id:'review', review_year:2026, quarter:3, summary:'Strong collaboration across the team.', published_at:'2026-09-21T12:00:00Z', employee_name:'Jordan Rivera' }];
    await route.fulfill({ json:data });
  });
  await page.goto('/feedback');
  await expect(page.getByRole('link',{name:'Feedback',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Submit Feedback',exact:true}).click();
  await page.getByLabel('Search employee by name or email').fill('alex@example.com');
  await page.getByRole('button',{name:/Alex Chen/}).click();
  await page.getByRole('textbox', {name:'General Feedback'}).fill('Thank you for your help.');
  await page.getByLabel('Areas for Improvement', {exact:true}).fill('Share updates earlier.');
  await page.setViewportSize({width:390,height:844});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:'test-results/feedback-form-mobile.png',fullPage:true});
  await page.setViewportSize({width:1440,height:1050});
  await page.screenshot({path:'test-results/feedback-form-desktop.png',fullPage:true});
  await page.getByLabel('Anonymous Feedback').check();
  await page.getByRole('button',{name:'Submit anonymous feedback'}).click();
  await expect(page.getByText('Feedback submitted.')).toBeVisible();
  expect(submitted.anonymous).toBe(true);
  expect(submitted.employeeId).toBe(2);
  await page.getByRole('link',{name:'Performance Reviews',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Performance Review — Q3 2026',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Edit review'})).toHaveCount(0);
  await page.setViewportSize({width:390,height:844});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:'test-results/performance-review-mobile.png',fullPage:true});
  expect(errors).toEqual([]);
});

test('Admin browses employee reviews and creates reviews in a responsive workspace', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('authToken','test-session'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/auth/me') data = { id:1, firstName:'Jordan', lastName:'Rivera', role:'ADMIN', profileCompleted:true };
    if (url.pathname.endsWith('/notifications/unread-count')) data = 0;
    if (url.pathname.endsWith('/feedback-reviews/reviews')) data = [{ id:'review', employee_id:2, first_name:'Alex', last_name:'Chen', employee_name:'Alex Chen', employee_email:'alex@example.com', review_year:2026, quarter:3, summary:'Strong collaboration across the team.', version:0 }];
    await route.fulfill({ json:data });
  });
  await page.goto('/performance-reviews');
  await page.getByRole('button', {name:/Alex Chen.*View review/}).click();
  await expect(page.getByRole('button', {name:'Publish to employee'})).toBeVisible();
  await page.getByRole('button', {name:'Create quarterly review'}).click();
  await expect(page.getByLabel('Overall performance summary (required)')).toBeVisible();
  await page.screenshot({path:'test-results/admin-review-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:'test-results/admin-review-mobile.png',fullPage:true});
});
