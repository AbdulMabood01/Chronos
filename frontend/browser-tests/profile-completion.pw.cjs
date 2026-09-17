const { test, expect } = require('@playwright/test');

for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }, { width: 844, height: 390 }]) {
  test(`first-login profile can scroll and save at ${viewport.width}x${viewport.height}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const employee = { id: 1, firstName: 'Alice', lastName: 'Smith', role: 'EMPLOYEE', jobTitle: 'Engineer', dateOfBirth: '1990-01-01', profileCompleted: false };
    await page.addInitScript(() => localStorage.setItem('authToken', 'profile-completion-test'));
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      let data = [];
      if (url.pathname.endsWith('/auth/me')) data = employee;
      else if (url.pathname.endsWith('/users/me/profile')) data = { ...employee, ...route.request().postDataJSON(), profileCompleted: true };
      else if (url.pathname.includes('unread-count')) data = 0;
      await route.fulfill({ json: data });
    });
    await page.goto('/notifications');
    const dialog = page.getByRole('dialog', { name: 'Complete Your Profile' });
    await expect(dialog).toBeVisible();
    const bounds = await dialog.boundingBox();
    expect(bounds.y).toBeGreaterThanOrEqual(0);
    expect(bounds.y + bounds.height).toBeLessThanOrEqual(viewport.height);
    expect(await dialog.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true);
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await dialog.getByLabel('Contact email', { exact: true }).fill('contact@example.test');
    const submit = dialog.getByRole('button', { name: 'Continue', exact: true });
    await submit.scrollIntoViewIfNeeded();
    await expect(submit).toBeInViewport();
    expect(await dialog.evaluate(el => el.scrollTop)).toBeGreaterThan(0);
    await submit.click();
    await expect(dialog).toHaveCount(0);
  });
}
