---
title: Extend GATF with plugins
sidebar_position: 23
---

# Extend GATF with plugins

## Step plugins

A step plugin adds a seleasy statement.

- **Simple:** map a keyword to any static Java method in `plugins.txt` (`name=com.acme.MyPlugin@method`), then use it as a statement or as `var x plugin name …`.
- **Plugin SDK:** implement `GatfStepPlugin` (name, execute, optional info and init), register it in `META-INF/services`, and drop the jar into `plugins/`. It is loaded in its own class loader without rebuilding GATF, and its documentation shows in the console editor's and VS Code's completion (`GET /api/plugins`).

Built-in plugins: `jsonread`, `jsonwrite`, `jsonpath`, `xmlread`, `xmlwrite`, `xmlpath`, `api` (run a GATF API test from a UI script) and `curl`:

```sel title="plugins-demo.sel"
open chrome
jsonpath '{"a": "abc", "b": 1}' '$.a'
curl get https://api.example.com/health
```

See [Plugins](../seleasy/REFERENCE.md#plugins) in the language reference.

## Load engines

A load engine plugin adds a `perfConfig.type`: implement `com.gatf.executor.perf.PerfEngine`, list it in `META-INF/services/com.gatf.executor.perf.PerfEngine` and put the jar in `plugins/`. See [Your own engine](../load-tools.md#your-own-engine-perf-engine-spi).

## Java hooks, data sources and providers

- `@PreTestCaseExecutionHook` / `@PostTestCaseExecutionHook` on a `public static void m(TestCase tc)` in a package listed in `testCaseHooksPaths` runs around API cases.
- A class implementing `TestDataSource` or `TestDataProvider` can be named as `dataSourceClass` or `providerClass` ([data providers](../guide/concepts/data-providers.md)).

## Integrations

`gatf-logging-api-int.xml` defines API calls that fetch server-side logs for a failed test, and `gatf-issuetracking-api-int.xml` your issue tracker's API for **Raise defect** in reports. Both are edited in the console's **Integrations** view.
