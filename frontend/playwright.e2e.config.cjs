const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.cjs',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'edge', use: { ...devices['Desktop Chrome'], channel: 'msedge' } }],
  webServer: [
    {
      command: 'mvn -f ../backend/pom.xml "-Dspring-boot.run.profiles=e2e" spring-boot:run',
      url: 'http://127.0.0.1:8081/api/health',
      reuseExistingServer: false,
      timeout: 120000,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5174',
      url: 'http://127.0.0.1:5174',
      env: { VITE_PROXY_TARGET: 'http://127.0.0.1:8081' },
      reuseExistingServer: false,
      timeout: 60000,
    },
  ],
});
