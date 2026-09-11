const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  testDir: './browser-tests', testMatch: '**/*.pw.cjs', fullyParallel: false,
  use: { baseURL: 'http://localhost:5173', channel: 'msedge', viewport: { width: 1440, height: 1050 } },
  webServer: { command: 'npm run dev -- --port 5173', url: 'http://localhost:5173', reuseExistingServer: true },
  reporter: 'list',
});
