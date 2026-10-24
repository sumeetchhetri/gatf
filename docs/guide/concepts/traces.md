---
title: Traces
sidebar_position: 8
---

# Traces

A trace is a recording of one UI script run: every step with a screenshot, the DOM, network traffic, console output and performance data, in a single `*-trace.zip` you can replay in the Trace Viewer.

## Switching it on

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["checkout.sel"],
  "traceMode": "retain-on-failure",
  "traceDomSnapshots": "full"
}
```

| `traceMode` | Keeps |
|---|---|
| `off` | nothing |
| `on` | a trace for every script |
| `retain-on-failure` | traces of failed scripts only |
| `on-first-retry` | traces from the first retry round (with `seleniumScriptRetryCount`) |

`gatf test --trace <mode>` overrides it for one run. Without `traceMode`, `enableTrace: true` means `on`. Traces are recorded for functional runs only, not for load runs or runs with several simulated users.

## What is recorded

Each recorder has its own switch: network (`traceNetwork`, on), console (`traceConsole`, on), screenshots (`traceSnapshots`, on), per-step DOM snapshots for time travel (`traceDomSnapshots`: `off`, `lite` or `full`), metrics and web vitals (on), and the heavier CPU profile, JS/CSS coverage and screencast video (off). Limits keep traces small: `traceMaxBodyKb`, `traceMaxTotalMb`, `traceDomSnapshotMaxMb`.

## Browsers and transports

Traces are streamed over the browser's DevTools protocol (CDP, the default `traceProtocol`) or WebDriver BiDi (`bidi`, or `hybrid` for BiDi plus CDP-only data). Chrome and Edge support everything. Firefox and Safari record what their BiDi support allows; without CDP or a BiDi socket no trace is written. On a Selenium Grid the trace works when the Grid advertises `se:cdp`. See [browser modes](../../browser-modes.md).

## Replaying

Open the web console (`gatf ui`), click **◫ Trace Viewer** and drop the zip on the page, or open it from the run in **Reports**. The viewer shows the timeline of actions with tabs for events, network, console, performance, coverage and CPU, plus video, DOM and screenshot panes when the trace recorded them (`traceVideo`, `traceDomSnapshots`). A failed run's console output prints the path of its trace.

## Related

- [First UI test](../getting-started/first-ui-test.md#4-replay-the-trace)
- [Trace fields in the configuration reference](../../reference/configuration.md#gatfexecutorconfig)
- [Tier 3 release notes](../../release-notes/tier-3.md): DOM time travel, distributed traces and the other trace features added in 5.0.0
