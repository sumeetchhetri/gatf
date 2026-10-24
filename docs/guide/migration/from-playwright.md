---
title: From Playwright
sidebar_position: 2
---

# Migrating from Playwright

`gatf import playwright` reads a Playwright Test spec (TypeScript or JavaScript) and writes a seleasy script with one subtest per `test(...)`:

```bash
gatf import playwright login.spec.ts
```

```text
Imported script → login.sel  {tests=2, actions=14, hooks=1, unsupported=2}
  2 item(s) need review:
   ! #30 await expect(page).toHaveURL(/dashboard/) → assertion 'tohaveurl' has no direct seleasy equivalent
   ! #30 for (let i = 0; i < 3; i++) { → not a page action or expect assertion
```

For a spec like

```ts
test.beforeEach(async ({ page }) => {
  await page.goto('https://demo.gatf.example/login');
});

test('user can sign in and see the dashboard', async ({ page }) => {
  await page.fill('#username', 'sumeet');
  await page.fill('#password', 'demo-pass');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page.locator('nav.app-menu')).toBeVisible();
  await expect(page.getByText('Welcome')).toContainText('Welcome');
});
```

the script starts like this (the `beforeEach` steps are copied into each subtest):

```sel title="login.sel"
// Imported from Playwright by GATF — best-effort; review selectors and assertions.
open chrome
maximize

subtest "user can sign in and see the dashboard"
{
  goto https://demo.gatf.example/login
  type "sumeet" id@username
  type "demo-pass" id@password
  click xpath@"//*[@role='button' or self::button]"
  ?? css@"nav.app-menu"
  ?? text@"Welcome"
}
```

## Review the result

- **Locators.** CSS and `#id` selectors carry over directly. `getByRole` is converted to a generic XPath that matches any button, so replace it with a seleasy semantic locator: `click role@button[name='Sign In']`. seleasy has `role@`, `label@`, `placeholder@`, `testid@`, `alt@`, `title@` and `text@`, close to Playwright's `getBy*` family.
- **Waiting.** Switch on `autoWait` and `webFirstAssertions` in the config to get Playwright-like actionability waits and retrying assertions.
- **Assertions** without an equivalent (`toHaveURL`, …) become `// UNSUPPORTED` comment lines; rewrite them with `??` checks (`?? currentUrl …`, see the [language reference](../../seleasy/REFERENCE.md)).
- **Loops and other code** are listed in the report; seleasy has its own loops (`##`, `#provider`) and embedded code blocks.

## Coming back

`gatf export playwright <file.sel|dir>` goes the other way, writing Playwright Test TypeScript with `getByRole` and web-first `expect`. See [Export seleasy scripts](../../how-to/export-scripts.md).
