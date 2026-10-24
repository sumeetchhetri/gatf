---
title: Isolate tests with browser contexts
sidebar_position: 15
---

# Isolate tests with browser contexts

Starting a browser per test is slow; sharing one leaks cookies and storage between tests. Browser contexts give each test a clean profile inside one browser.

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["cart.sel", "orders.sel"],
  "browserContexts": "subtest",
  "numConcurrentExecutions": 4
}
```

| `browserContexts` | Effect |
|---|---|
| unset or `off` | One shared context per browser, as before |
| `subtest` | Every top-level subtest runs in a fresh context of the script's browser: its own cookies, storage, cache, routes, emulation and clock, disposed when the subtest ends. `storageState` is loaded into each one |
| `worker` | Each worker keeps one browser and runs every script in a fresh context of it, so 4 workers start 4 browsers instead of one per script |

Contexts use WebDriver BiDi `browser.createUserContext` or CDP `Target.createBrowserContext`. Where a session supports neither, GATF keeps the shared context (or a browser per script) and says so once in the run log. The support matrix per browser is in [Browser modes](../browser-modes.md#per-test-browser-contexts-h3).
