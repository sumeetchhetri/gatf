/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
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
  // Δ163f: files also run one at a time on CI: the Grid-gated specs (debugger, workflow) share one Grid, and a
  // second file's session made the debugger's `open chrome` wait in the Grid queue past the step timeout
  workers: process.env.CI ? 1 : undefined,
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
