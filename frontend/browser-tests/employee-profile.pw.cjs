const { test, expect } = require('./support/company-fixture.cjs');
const path = require('path');
const fs = require('fs');

test('personal profile keeps company employment outside the shared account on desktop and mobile',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('authToken','profile-test'));await page.route('**/api/**',route=>route.fulfill({json:new URL(route.request().url()).pathname==='/api/auth/me'?{id:2,firstName:'Alice',lastName:'Smith',email:'alice@example.test',role:'EMPLOYEE',profileCompleted:true}:[]}));
 await page.goto('/profile');await expect(page.getByRole('heading',{name:'Profile',exact:true})).toBeVisible();await expect(page.getByLabel('First Name',{exact:true})).toHaveValue('Alice');
 await expect(page.getByLabel('Job Title',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Save employment details'})).toHaveCount(0);
 for(const width of [1440,390]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
});
