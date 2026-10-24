---
title: Performance budgets and security checks
sidebar_position: 24
---

# Performance budgets and passive security checks

Both run over each UI script's trace, so tracing must be on. Results appear in the test's report and in `checks.json` inside the trace.

## Budgets

Set budgets for the whole suite in the config, or per script in its leading comments:

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["home.sel"],
  "traceMode": "on",
  "perfBudgets": "lcp<2500, requests<80, jsheap<50mb"
}
```

```sel title="home.sel"
// Home page @perf
// @budget lcp<2000 transfer<2mb
open chrome
goto https://shop.example.com
```

A script budget overrides the config's for the same metric; a breach fails the script with the measured value.

- Page metrics `lcp`, `fcp`, `ttfb`, `dcl`, `load` (ms) and `cls` apply to the worst page the script visited.
- Script-wide metrics: `jsheap`, `transfer` (`b`, `kb`, `mb`), `requests`, `failedRequests`, `consoleErrors`.
- A metric the browser did not report shows as "not measured" and does not fail.

## Security checks

`securityChecks` is `report` (the default: findings in the report), `fail` (a medium or high finding fails the script) or `off`. It looks, without sending anything extra to the site, for: missing CSP, HSTS, `nosniff`, clickjacking protection or Referrer-Policy and server version disclosure on HTML pages; first-party cookies without `Secure` or `HttpOnly`, or `SameSite=None` without `Secure`; mixed content; wildcard CORS with credentials; and CSP violations in the console.
