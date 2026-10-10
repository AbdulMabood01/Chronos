const { test, expect } = require('@playwright/test');

test('visitors enter through landing and can sign in or request access', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('In harmony.');
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator('.lp-film video')).toBeAttached();
  await expect(page.locator('.lp-film video')).toHaveAttribute('controls', '');
  await page.getByRole('link', { name: 'Get started' }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel('Work email')).toBeVisible();
  await page.getByRole('link', { name: 'Explore Chronos', exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.locator('.lp-header').getByRole('link', { name: 'Plans' }).click();
  await expect(page).toHaveURL(/\/pricing$/);
  await page.goto('/');
  await page.locator('.lp-final').getByRole('link', { name: 'Request company access' }).click();
  await expect(page).toHaveURL(/\/request-access$/);
  expect(errors).toEqual([]);
});

test('desktop scroll moves through all three demo chapters', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.lp-demo')).toBeVisible();
  for (const [fraction, label, heading] of [[0.1, 'Capture', 'Your week, in focus.'], [0.5, 'Review', 'Decisions, without the chase.'], [0.9, 'Connect', 'The whole team, connected.']]) {
    await page.locator('#demo').evaluate((el, fraction) => window.scrollTo({ top: window.scrollY + el.getBoundingClientRect().top + (el.offsetHeight - innerHeight) * fraction, behavior: 'instant' }), fraction);
    await expect(page.getByRole('button', { name: new RegExp(label) })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.lp-demo').getByRole('heading', { name: heading })).toBeVisible();
  }
  await page.getByRole('button', { name: /Capture/ }).click();
  await expect(page.getByRole('button', { name: /Capture/ })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.lp-demo').getByRole('heading', { name: 'Your week, in focus.' })).toBeVisible();
});

for (const width of [320, 390, 760]) {
  test('mobile demo, FAQ and no horizontal overflow at ' + width, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await page.goto('/');
    await page.getByRole('button', { name: /Review/ }).click();
    await expect(page.locator('.lp-demo').getByRole('heading', { name: 'Decisions, without the chase.' })).toBeVisible();
    await page.getByRole('button', { name: /Connect/ }).click();
    await expect(page.locator('.lp-demo').getByRole('heading', { name: 'The whole team, connected.' })).toBeVisible();
    await page.locator('.lp-faq summary').filter({ hasText: 'What is Chronos?' }).click();
    await expect(page.locator('.lp-faq details').first()).toHaveAttribute('open', '');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test('reduced motion removes pinned scrolling and keeps chapter controls working', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.getByRole('button', { name: /Connect/ }).click();
  await expect(page.locator('.lp-demo').getByRole('heading', { name: 'The whole team, connected.' })).toBeVisible();
  expect(await page.locator('.lp-demo-sticky').evaluate(el => getComputedStyle(el).position)).toBe('relative');
  expect(await page.locator('.lp-demo-screen').evaluate(el => getComputedStyle(el).transform)).toBe('none');
});

test('authenticated visitors go directly to the dashboard', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('authToken', 'landing-test-session'));
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname === '/api/auth/me') data = { id: 1, firstName: 'Jordan', lastName: 'Davis', profileCompleted: true, platformAdmin: true };
    else if (pathname === '/api/companies/context') data = { companies: [], platformAdmin: true, platformPermissions: { capabilities: {} } };
    else if (pathname.includes('unread-count')) data = 0;
    await route.fulfill({ json: data });
  });
  await page.goto('/');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('.lp')).toHaveCount(0);
});

test('configured promo media failure returns to the film frame', async ({ page }) => {
  await page.route('**/src/pages/Landing.jsx*', async route => {
    const response = await route.fetch();
    const body = (await response.text()).replace(/const promoVideo = [^;]+;/, 'const promoVideo = "/media/landing-missing-test.mp4";');
    await route.fulfill({ response, body });
  });
  await page.route('**/media/landing-missing-test.mp4', route => route.fulfill({ status: 404, body: '' }));
  await page.goto('/');
  await expect(page.locator('.lp-film')).toContainText('The film is temporarily unavailable.');
  await expect(page.locator('.lp-film video')).toHaveCount(0);
});

test('promo loads, plays and seeks; policies live in one grouped footer', async ({ page }) => {
  await page.goto('/');
  const video = page.locator('.lp-film video');
  await video.scrollIntoViewIfNeeded();
  await expect.poll(() => video.evaluate(el => el.readyState)).toBeGreaterThanOrEqual(1);
  const metadata = await video.evaluate(el => ({ duration: el.duration, width: el.videoWidth, height: el.videoHeight, autoplay: el.autoplay }));
  expect(metadata.duration).toBeCloseTo(30, 0);
  expect(metadata.width).toBe(1920);
  expect(metadata.height).toBe(1080);
  expect(metadata.autoplay).toBe(false);
  await video.evaluate(async el => { el.muted = true; await el.play(); });
  await expect.poll(() => video.evaluate(el => el.currentTime)).toBeGreaterThan(0.1);
  await video.evaluate(el => { el.pause(); el.currentTime = 15; });
  await expect.poll(() => video.evaluate(el => el.currentTime)).toBeCloseTo(15, 0);
  await expect(page.locator('footer')).toHaveCount(1);
  const footer = page.locator('footer');
  await expect(footer.getByRole('navigation', { name: 'Legal', exact: true })).toBeAttached();
  await expect(footer.getByRole('navigation', { name: 'Privacy & support' })).toBeAttached();
  await expect(footer.locator('a[href^="/legal/"]')).toHaveCount(8);
  await footer.getByRole('link', { name: 'Privacy Policy', exact: true }).click();
  await expect(page).toHaveURL(/\/legal\/privacy$/);
  await expect(page.getByRole('heading', { name: 'Privacy Policy', exact: true })).toBeVisible();
});

for (const width of [1440, 390]) {
  test('continuous scroll reverses without replacing screens at ' + width, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    await page.locator('.lp-demo-screen').evaluate(el => { window.originalDemoScreen = el; window.originalDemoLayers = [...el.children]; });
    for (const progress of [0.1, 0.34, 0.5, 0.9, 0.34, 0.1]) {
      await page.locator('#demo').evaluate((el, progress) => window.scrollTo({ top: el.offsetTop + (el.offsetHeight - innerHeight) * progress, behavior: 'instant' }), progress);
      await expect.poll(() => page.locator('#demo').evaluate(el => Number(el.style.getPropertyValue('--demo-scroll')))).toBeCloseTo(progress, 2);
      expect(await page.locator('.lp-demo-screen').evaluate(el => el === window.originalDemoScreen && [...el.children].every((child, i) => child === window.originalDemoLayers[i]))).toBe(true);
    }
    await expect(page.getByRole('button', { name: /Capture/ })).toHaveAttribute('aria-pressed', 'true');
    expect(await page.locator('.lp-demo-screen').evaluate(el => getComputedStyle(el).animationName)).toBe('none');
    expect(await page.locator('.lp').evaluate(el => getComputedStyle(el).getPropertyValue('--lp-ink').trim())).toBe(await page.locator('body').evaluate(el => getComputedStyle(el).getPropertyValue('--text-primary').trim()));
    const aligned = await page.evaluate(() => {
      const left = selector => document.querySelector(selector).getBoundingClientRect().left;
      return Math.abs(left('.lp-hero-copy') - left('.lp-demo-copy')) < 2 && Math.abs(left('.lp-hero-copy') - left('.lp-film-section .lp-section-heading')) < 2;
    });
    expect(aligned).toBe(true);
  });
}

test('3D planes travel in depth and hero responds to pointer movement', async ({ page }) => {
  await page.goto('/');
  const hero = page.locator('.lp-hero-scene');
  const box = await hero.boundingBox();
  await page.mouse.move(box.x + box.width * .8, box.y + box.height * .4);
  await expect.poll(() => hero.evaluate(el => Number(el.style.getPropertyValue('--pointer-x')))).toBeGreaterThan(.5);
  expect(await page.locator('.lp-clock').evaluate(el => getComputedStyle(el).animationName)).toBe('lp-clock-turn');
  const incoming = page.locator('.lp-screen-layer').nth(1);
  const sample = async progress => {
    await page.locator('#demo').evaluate((el, progress) => window.scrollTo({ top: el.offsetTop + (el.offsetHeight - innerHeight) * progress, behavior: 'instant' }), progress);
    await expect.poll(() => page.locator('#demo').evaluate(el => Number(el.style.getPropertyValue('--demo-scroll')))).toBeCloseTo(progress, 2);
    return incoming.evaluate(el => { const matrix = new DOMMatrixReadOnly(getComputedStyle(el).transform); return { z: matrix.m43, yaw: matrix.m13 }; });
  };
  const before = await sample(.26);
  const after = await sample(.45);
  expect(before.z).toBeLessThan(-300);
  expect(Math.abs(before.yaw)).toBeGreaterThan(.3);
  expect(Math.abs(after.z)).toBeLessThan(1);
  expect(await page.locator('.lp-demo-screen').evaluate(el => getComputedStyle(el).transformStyle)).toBe('preserve-3d');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(() => page.locator('.lp-clock').evaluate(el => getComputedStyle(el).animationName)).toBe('none');
  expect(await incoming.evaluate(el => getComputedStyle(el).transform)).toBe('none');
});

test('short desktop previews retain 3D; compact mobile controls show only the selected screen', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 600 });
  await page.goto('/');
  await page.locator('#demo').evaluate(el => window.scrollTo({ top: el.offsetTop + (el.offsetHeight - innerHeight) * .26, behavior: 'instant' }));
  await expect.poll(() => page.locator('#demo').evaluate(el => Number(el.style.getPropertyValue('--demo-scroll')))).toBeCloseTo(.26, 2);
  expect(await page.locator('.lp-screen-layer').nth(1).evaluate(el => new DOMMatrixReadOnly(getComputedStyle(el).transform).m43)).toBeLessThan(-300);
  expect(await page.locator('.lp-demo-sticky').evaluate(el => getComputedStyle(el).position)).toBe('sticky');
  await page.setViewportSize({ width: 390, height: 600 });
  await page.goto('/');
  await page.getByRole('button', { name: /Review/ }).click();
  expect(await page.locator('.lp-screen-layer').nth(1).evaluate(el => getComputedStyle(el).opacity)).toBe('1');
  expect(await page.locator('.lp-screen-layer').nth(2).evaluate(el => getComputedStyle(el).opacity)).toBe('0');
  expect(await page.locator('.lp-demo-sticky').evaluate(el => getComputedStyle(el).position)).toBe('relative');
});
