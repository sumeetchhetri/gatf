import { defineConfig, devices } from '@playwright/test';

// Playwright itself treats SELENIUM_REMOTE_URL as "route my browser through this Selenium Grid".
// That's NOT what we want — the UI browser is local chromium; only the GATF *server* needs a Grid
// (for the T4c specs). Strip it here so Playwright launches locally; the Grid reaches GATF via
// GATF_SELENIUM_GRID (see start-gatf-server.sh), which Playwright ignores.
delete process.env.SELENIUM_REMOTE_URL;

// A fixed port for the GATF server the webServer block launches. The SPA is served at /app.
const PORT = Number(process.env.GATF_E2E_PORT ?? 9223);
const BASE = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false, // one shared GATF server instance
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: BASE,
    headless: true,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Launch the real GATF config-tool server (serving the built /app) against a scratch copy of
  // sample-project. Requires the shaded jar to be built first (mvn -pl alldep-jar -am package).
  webServer: {
    command: `bash ./start-gatf-server.sh ${PORT}`,
    url: `${BASE}/app/`,
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
