const { test, expect } = require('@playwright/test');

async function openContact(page, path = '/') {
  await page.goto(path);
  const area = page.locator(path === '/' ? '.lp-header' : '.login-card');
  await area.getByRole('button', { name: /Contact us/ }).click();
  return page.getByRole('dialog');
}
async function fill(dialog) {
  await dialog.getByLabel('Your name').fill('Jordan Davis');
  await dialog.getByLabel('Company', { exact: true }).fill('Acme');
  await dialog.getByLabel('Email address').fill('jordan@example.com');
  await dialog.getByLabel('Phone number', { exact: false }).fill('+1 (312) 555-0100');
  await dialog.getByLabel('How can we help?').fill('Please arrange a product conversation.');
}
for (const path of ['/', '/login']) {
  test('unconfigured contact form preserves a draft and cannot send on ' + path, async ({ page }) => {
    let requests = 0;
    await page.route('**/api/contact/status', route => route.fulfill({ json: { available: false } }));
    await page.route('**/api/contact/inquiries', route => { requests++; return route.fulfill({ json: {} }); });
    const dialog = await openContact(page, path);
    await expect(dialog.getByText(/Inquiries cannot be sent yet/)).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Send inquiry' })).toBeDisabled();
    await fill(dialog);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator(path === '/' ? '.lp-header' : '.login-card').getByRole('button', { name: /Contact us/ })).toBeFocused();
    await page.locator(path === '/' ? '.lp-header' : '.login-card').getByRole('button', { name: /Contact us/ }).click();
    await expect(page.getByRole('dialog').getByLabel('Your name')).toHaveValue('Jordan Davis');
    expect(requests).toBe(0);
  });
  test('ready form submits once and shows success on ' + path, async ({ page }) => {
    const requests = [];
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    await page.route('**/api/contact/status', route => route.fulfill({ json: { available: true } }));
    await page.route('**/api/contact/inquiries', async route => {
      requests.push(route.request().postDataJSON());
      await gate;
      await route.fulfill({ json: { message: 'Your inquiry has been sent.' } });
    });
    const dialog = await openContact(page, path);
    await fill(dialog);
    await dialog.getByRole('button', { name: 'Send inquiry' }).click();
    await expect(dialog.getByRole('button', { name: 'Sending…' })).toBeDisabled();
    await expect.poll(() => requests.length).toBe(1);
    release();
    await expect(dialog.getByRole('status')).toContainText('Your inquiry has been sent');
    expect(requests[0]).toEqual({ name: 'Jordan Davis', company: 'Acme', email: 'jordan@example.com', phone: '+1 (312) 555-0100', message: 'Please arrange a product conversation.', website: '', source: path === '/' ? 'LANDING' : 'LOGIN' });
    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.locator(path === '/' ? '.lp-header' : '.login-card').getByRole('button', { name: /Contact us/ }).click();
    await expect(page.getByRole('dialog').getByLabel('Your name')).toHaveValue('');
    await expect(page.getByRole('dialog').getByRole('button', { name: 'Send inquiry' })).toBeEnabled();
  });
}

test('failed inquiry keeps details and retry succeeds', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/contact/status', route => route.fulfill({ json: { available: true } }));
  await page.route('**/api/contact/inquiries', route => {
    requests++;
    return requests === 1 ? route.fulfill({ status: 503, json: { message: 'Mail unavailable' } }) : route.fulfill({ json: { message: 'Sent' } });
  });
  const dialog = await openContact(page);
  await fill(dialog);
  await dialog.getByRole('button', { name: 'Send inquiry' }).click();
  await expect(dialog.getByRole('alert')).toContainText('could not be sent');
  await expect(dialog.getByLabel('Email address')).toHaveValue('jordan@example.com');
  await expect(dialog.getByText('Thanks for reaching out.')).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Send inquiry' }).click();
  await expect(dialog.getByRole('status')).toContainText('Your inquiry has been sent');
  expect(requests).toBe(2);
});

test('required fields stop submission and mobile dialog fits and traps focus', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/contact/status', route => route.fulfill({ json: { available: true } }));
  let requests = 0;
  await page.route('**/api/contact/inquiries', route => { requests++; return route.fulfill({ json: {} }); });
  const dialog = await openContact(page);
  await dialog.getByRole('button', { name: 'Send inquiry' }).click();
  expect(requests).toBe(0);
  await expect(dialog.getByLabel('Your name')).toBeFocused();
  await dialog.getByRole('button', { name: 'Close contact form' }).focus();
  await page.keyboard.press('Shift+Tab');
  expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
  const bounds = await dialog.boundingBox();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  expect(bounds.height).toBeLessThanOrEqual(844);
  await dialog.screenshot({ path: '../reports/contact-popup-mobile.png' });
});

test('landing footer also opens the contact form', async ({ page }) => {
  await page.route('**/api/contact/status', route => route.fulfill({ json: { available: false } }));
  await page.goto('/');
  await page.locator('footer').getByRole('button', { name: 'Contact us' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
});


test('closing during readiness loading does not show a connection-lost banner', async ({ page }) => {
  await page.route('**/api/contact/status', async route => {
    await new Promise(resolve => setTimeout(resolve, 400));
    await route.fulfill({ json: { available: false } });
  });
  const dialog = await openContact(page);
  await dialog.getByRole('button', { name: 'Close contact form' }).click();
  await page.waitForTimeout(500);
  await expect(page.getByText(/Connection lost\./)).toHaveCount(0);
});
