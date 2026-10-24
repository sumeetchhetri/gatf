---
title: Configuration
sidebar_position: 1
---

# Configuration

## The project folder

A GATF project is a folder. It holds:

- a **config file**, `gatf-config.json` or `gatf-config.xml` (JSON wins when both exist), or any other name you pass to `gatf test <config>`;
- the **test files** under the folder named by `testCaseDir`: API test cases (`.xml`, `.json`, `.csv`, `.xls`, `.xlsx`) and seleasy scripts (`.sel`);
- the **output** under `outFilesDir` (default `out`): one folder per run, plus `gatf-results.json` and `gatf-junit.xml` for the latest run;
- `logs/gatf.log`, the full log of the last run (the previous ten are kept, compressed).

Relative paths in the config are relative to the folder you run `gatf` in. `testCasesBasePath` and `outFilesBasePath` move the base elsewhere.

## One file, three kinds of run

The same config format drives every run; a few fields decide what kind it is:

| You want | Set |
|---|---|
| An API suite | `baseUrl`, `testCaseDir` (every test file under it runs) |
| A UI (seleasy) suite | `isSeleniumExecutor: true`, `seleniumScripts` (the `.sel` files to run, relative to `testCaseDir`), and usually `seleniumDriverConfigs` |
| An API suite replayed by many simulated users | `loadTestingEnabled: true`, `concurrentUserSimulationNum`, `loadTestingTime` |

A load-engine test (`perfConfig` on a test case) needs nothing special in the config. Most projects keep one config per kind of run (`gatf-config.json`, `gatf-config-ui.json`, …) and pass the name to `gatf test`.

A small UI config:

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["login.sel", "checkout.sel"],
  "autoWait": true,
  "webFirstAssertions": true,
  "traceMode": "retain-on-failure",
  "numConcurrentExecutions": 2
}
```

The same in XML. In XML every field is an element, and a list is a wrapper element whose children can have any name:

```xml title="gatf-config-ui.xml"
<gatf-execute-config>
  <testCaseDir>tests</testCaseDir>
  <outFilesDir>out</outFilesDir>
  <isSeleniumExecutor>true</isSeleniumExecutor>
  <seleniumScripts>
    <string>login.sel</string>
    <string>checkout.sel</string>
  </seleniumScripts>
  <autoWait>true</autoWait>
  <webFirstAssertions>true</webFirstAssertions>
  <traceMode>retain-on-failure</traceMode>
  <numConcurrentExecutions>2</numConcurrentExecutions>
</gatf-execute-config>
```

## Defaults that matter

- **`autoWait` and `webFirstAssertions`** are off when absent, so older configs keep their behaviour. Switch both on for new projects: actions then wait until the element is actionable, and assertions retry for `assertTimeout` ms (default 5000).
- **`traceMode`** (`off`, `on`, `retain-on-failure`, `on-first-retry`) wins over the older `enableTrace` flag.
- **`numConcurrentExecutions`** is the number of parallel workers; `gatf test --workers` overrides it for one run.
- Command-line flags of `gatf test` override their config fields for that run: `--trace`, `--retries`, `--workers`, `--forbid-only`, `--update-snapshots`.

## Editing it in the console

`gatf ui` has a **Config** view with a form for every field. The console writes the full config back to `gatf-config.json`, with every default filled in, when it loads the project and when you save.

## Reference

- [Configuration reference](../../reference/configuration.md): every field, its type, default and meaning, generated from the source.
- [Configuration and test cases](../../configuration.md): a fully commented XML sample, data providers, the Maven plugin and the test generator.
