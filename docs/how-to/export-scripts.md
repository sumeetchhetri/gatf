---
title: Export seleasy scripts
sidebar_position: 9
---

# Export seleasy scripts to Playwright or Selenium

```bash
gatf export playwright login.sel        # → login.spec.ts (Playwright Test, TypeScript)
gatf export selenium-java login.sel     # → LoginTest.java (Selenium WebDriver + JUnit 5)
```

```text
Exported login.sel → /home/you/project/login.spec.ts
login → login.spec.ts  {statements=18, exported=18, todo=0, notes=5}
  5 note(s) on behaviour that differs:
   ~ #2 open chrome — the browser is chosen by the Playwright project, not the script
   ~ #3 maximize — maximize has no page-level equivalent; set use.viewport in playwright.config
```

- **Playwright**: semantic locators map to `getByRole`, `getByLabel` and friends, waits and assertions to web-first `expect`, subtests to `test.step`.
- **Selenium Java**: one `<Name>Test.java` per script that needs only `selenium-java` and `junit-jupiter`; `By` locators are the ones seleasy uses, waits poll up to the element timeout, subtests become methods, and `SELENIUM_REMOTE_URL` runs it on a Grid.
- `${name}` values become environment variables (`GATF_NAME`), never their configured values, so no secret is copied into the code.
- Anything without an equivalent is kept as a `// TODO(gatf-export):` line and listed in the report; the notes (`~`) explain behaviour that differs.

A directory exports every script with an `open` statement and writes `export-report.txt`. Existing files are only overwritten with `--force`; `--strict-locators` makes semantic locators strict in the exported code. All flags: [CLI reference](../reference/cli.md#gatf-export).
