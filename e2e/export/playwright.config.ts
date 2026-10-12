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

// Δ160: runs the exported e2e-*.spec.ts goldens (copied to .generated/ by prepare.mjs) against the static fixture
// pages in ./fixture. Run from e2e/: npm run test:export. The exported specs read ${baseUrl} as GATF_BASEURL.
delete process.env.SELENIUM_REMOTE_URL;
const PORT = Number(process.env.GATF_EXPORT_PORT ?? 9323);
process.env.GATF_BASEURL ??= `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: './.generated',
  testMatch: 'e2e-*.spec.ts',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  retries: 0,
  reporter: 'list',
  outputDir: './test-results',
  use: {
    headless: true,
    // a pre-installed Chromium can be used instead of `npx playwright install chromium`
    launchOptions: process.env.GATF_EXPORT_CHROMIUM ? { executablePath: process.env.GATF_EXPORT_CHROMIUM } : {},
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `node ./serve-fixture.mjs ${PORT}`,
    url: `http://127.0.0.1:${PORT}/index.html`,
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
