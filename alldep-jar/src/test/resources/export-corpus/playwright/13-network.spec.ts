// Exported from 13-network.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 23 of 27 statement(s) exported, 4 TODO, 10 note(s): see the export report.
import { test, expect } from '@playwright/test';

test('13-network', async ({ page }) => {
  // Δ160 export corpus: routes, HAR replay, auth state, emulation, clock and screenshots.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.route('**/api/users', async (route) => {
    await route.fulfill({ status: 200, path: 'users.json' });
  });
  // TODO(gatf-export): route **/api/raw fulfill 204
  await page.route('**/api/orders', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    await route.fulfill({ status: 201, body: '{"id": 7}', contentType: 'application/json', headers: { 'X-Mock': 'again' } });
  });
  await page.route('**/*.{png,jpg}', async (route) => {
    await route.abort('blockedbyclient');
  });
  await page.route('**/api/**', async (route) => {
    await route.continue({ headers: { ...route.request().headers(), 'x-env': 'test' } });
  });
  await page.routeFromHAR('checkout.har', { url: '**/api/**', notFound: 'abort' });
  await page.goto('https://shop.test/');
  await page.unroute('**/api/users');
  await page.unrouteAll();
  await page.context().storageState({ path: '.auth/admin.json' });
  // TODO(gatf-export): state load .auth/admin.json
  await page.setViewportSize({ width: 1024, height: 768 });
  await page.context().setGeolocation({ latitude: 51.5072, longitude: -0.1276 });
  await page.context().grantPermissions(['geolocation', 'notifications']);
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.context().setOffline(true);
  await page.context().setOffline(false);
  // TODO(gatf-export): emulate locale de-DE
  await page.clock.install({ time: 1767258000000 }); // install 2026-01-01T09:00:00Z
  await page.clock.fastForward(5400000); // fastforward 1h30m
  await page.clock.setSystemTime(1798761590000); // set 2026-12-31T23:59:50Z
  // TODO(gatf-export): clock pause
  await page.clock.resume();
  await page.screenshot({ path: test.info().outputPath('screenshot-line26.png') });
  await page.locator('#cart').first().screenshot({ path: 'cart.png' });
  await expect(page).toHaveScreenshot('checkout step 2' + '.png', { mask: [page.locator('#clock'), page.locator('.ad-banner')], maxDiffPixelRatio: 0.005 });
});
