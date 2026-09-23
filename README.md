# GATF — Generic Automated Test Framework

[![Build gatf](https://github.com/sumeetchhetri/gatf/actions/workflows/ci-build.yml/badge.svg)](https://github.com/sumeetchhetri/gatf/actions/workflows/ci-build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**One tool for API, workflow, performance, load, distributed, comparative and UI/RPA testing.** GATF is a single
Java 21 jar with a web console, a trace viewer, a step debugger and a VS Code extension.

- **API tests are data** (XML, JSON or CSV), chained into workflows and fed by SQL, MongoDB or file data sources.
- **UI tests are written in [seleasy](docs/seleasy/REFERENCE.md)**, a terse automation language compiled to
  Selenium Java in-process.
- **The same suites scale** from one run to concurrent users, load runs and distributed agents.

> The primary goal of GATF is automation with simplicity.

- [Capabilities](#capabilities)
- [Install](#install) · [Quick start](#quick-start) · [Commands](#commands)
- [Console output, logs and exit codes](#console-output-logs-and-exit-codes) · [Running in CI](#running-in-ci)
- [Documentation](#documentation) · [Developing GATF](#developing-gatf)

## Capabilities

### API testing
- **Test case formats:** XML, JSON or CSV files, one or many cases per file, auto-discovered under `testCaseDir`. XML is the most expressive.
- **Protocols:** REST over HTTP/1.1 and HTTP/2 (the JDK `java.net.http` client). SOAP via WSDL keys and operations. Multipart uploads, request bodies from files, custom headers and cookies.
- **Assertions:** status code, content type and body. Node assertions use JSONPath for JSON and XPath for XML/SOAP. **Logical validations** are Velocity expressions over the response and variables.
- **Auto-authentication:** log in once through a configured auth call; `secure` cases get the token as a header or query parameter. SOAP auth is also supported.
- **In the console:** *Play* runs a case as defined. *Replay* re-sends a captured request from a report. Every report carries the equivalent `curl` command.

### Workflow (chained) API testing
- **Capture values** from one response into variables for later tests: response nodes, `#responseHeader[..]`, `#responseCookie[..]`, `#responseMappedValue[..]` and `#responseMappedCount[..]` (`workflowContextParameterMap`).
- **Reuse them anywhere** as `${var}` placeholders in URLs, headers and bodies. Global, workflow and provider variables share one namespace.
- **Scenarios:** repeat a case once per scenario row, inline or from a data provider. Run a named case as a **dependency** (`relatedTestName`). Add pre/post waits. Abort the suite on a hard failure.
- **Conditional execution:** `executeOnCondition` is a Velocity expression over the workflow variables. When it is false, the case is skipped, and the report records the condition as the reason.
- **Hooks:** Java pre/post-execution hooks (`@PreTestCaseExecutionHook` / `@PostTestCaseExecutionHook`), and data-source hooks such as SQL setup/cleanup around a case.

### Data-driven testing
- **Data sources:** any JDBC database, MongoDB, or your own class.
- **Providers:**
  - query rows, or files (CSV, JSON, XML, XLS, XLSX, ODT);
  - inline values, random values, or a custom provider class;
  - "live" providers that re-query on each use.
- **The same data drives both kinds of test:** API scenarios, and seleasy `#provider`, `#sql`, `#mongo`, `#file` and `#counter` loops. In a script, `#dsq` runs a data-source query directly.

### Performance, concurrent users, load and distributed testing
- **Performance mode:** run a case N times (`numberOfExecutions`) with timing statistics and percentile ("TA") reports.
- **Concurrent-user simulation:** N users with ramp-up, optionally each with its own credentials from a data provider (`authDataProvider`). Runs on virtual threads.
- **Load testing:** time-boxed suite replay on the embedded engine, with live charts. A test case can instead delegate to **vegeta**, **autocannon** or **wrk/wrk2** (`perfConfig`); the tools are auto-provisioned and checksum-verified, with a Docker fallback for wrk ([details](docs/load-tools.md)).
- **UI load:** the same concurrency applies to seleasy suites.
- **Distributed load:** `gatf -listener` agents on other machines, over a TLS, shared-secret-authenticated transport. The console shows aggregated live results per node.

### Comparative (multi-environment) testing
Replay the same suite against several base URLs (`compareEnvBaseUrls`) to compare environments, such as staging against production. A report view and a file-compare view show side-by-side diffs.

### UI automation and RPA with seleasy
- **Multi-browser:** multiple browser sessions per script (Chrome, Firefox, Edge, Safari, IE, and Appium Android/iOS). Local (Selenium Manager downloads the driver and Chrome for Testing), the lean chrome-headless-shell, or any Selenium Grid ([browser modes](docs/browser-modes.md)).
- **Locating elements:** id, name, class, CSS, XPath, text, link text and jQuery selectors, with smart waits (`??10`, `??+`, `??-`) and assertions on text, attributes, CSS values and state.
- **Actions:**
  - type (including random data), click, double-click, right-click, hover;
  - **drag and drop**, click-and-hold/release, move-by-offset and key chords, chained with `actions`;
  - dropdowns, uploads, OS-level keys (Robot), canvas drawing;
  - alerts and confirms; windows, tabs and frames (scoped blocks); scrolling.
- **Structure:** subtests (individually reported), functions with arguments, `include` and `import` of other scripts, `if`/`else if`/`else`, loops with `break`/`continue`, variables, properties files, and pass/fail control.
- **Embedded code:** Java blocks (full access to the driver and context), plus **JavaScript, Groovy, Ruby and Python** blocks, and JavaScript executed in the browser.
- **Mobile:** tap, swipe, pinch, zoom, rotate, shake and touch sequences via Appium.
- **RPA:** long-running business processes with the same data sources and variables as API tests.

### OCR, QR/barcodes, PDF and files
- **OCR:** read the text of an element image (for example a captcha) with Tesseract (`ocr` / `readimgtext`).
- **QR and barcodes:** decode them from the page (`qrcode` / `barcode` / `readqrbar`, via ZXing).
- **PDF:** `printpdf <file> [text]` prints the current page to PDF (Chromium via CDP, Firefox via BiDi, or W3C print, locally or on a Grid). If the page is already showing a PDF blob, it saves the document itself. `wopensave` captures documents the page opens with `window.open`. With `text`, both extract the text and tables (PDFBox) to `<file>.txt`; embedded code can read the tables with `pdfTable(path)`.
- **Screenshots and files:** full page and per element, element images saved to files, files read line by line into variables.

### Mail and HTTP simulators
GATF can start an embedded **SMTP/IMAP mail server** and an **HTTP server** during a run. seleasy can then assert that an email arrived (sender, recipient, subject, body regex, timeout) or that an HTTP request was received (for example an SMS gateway callback) with `mail` and `http`.

### Plugin and extension framework
- **seleasy plugins:** map a keyword to any static Java method in `plugins.txt` (`name=com.acme.MyPlugin@method`), then use it as a statement or `var x plugin name ...`.
  - Built-ins: `jsonread` / `jsonwrite` / `jsonpath`, `xmlread` / `xmlwrite` / `xmlpath`, `api` (call a GATF API test from a UI script and use its response) and `curl` (ad-hoc HTTP).
- **API hooks, custom data sources and custom providers:** plain Java classes referenced from the config.
- **Integrations:**
  - fetch server-side logs through your own APIs when a test fails (`gatf-logging-api-int.xml`);
  - define your issue tracker's APIs (`gatf-issuetracking-api-int.xml`).

  Both are edited in the console's **Integrations** view.

### Authoring on-ramps
- **Web editor:** syntax highlighting, autocomplete, diagnostics, go-to-definition across files, and multi-tab editing. Saving validates the script by parsing and compiling it.
- **Recorder:** opens a browser (Chrome, Firefox or Edge) and turns your clicks and typing into seleasy, with timing pauses preserved.
- **Crawler:** logs into an application (including JavaScript SPAs), explores it in a real browser, and proposes a seleasy suite plus a coverage map.
- **Importers:** Playwright specs become seleasy; JMeter plans become GATF API tests, and their thread-group load settings are shown to apply.
- **Test generator:** creates API test cases from JAX-RS or Spring controllers and SOAP WSDLs, and can also produce a Postman collection.
- **VS Code extension:** language server (completion, diagnostics, navigation) and a debugger.

### Debugging
- A **step debugger** for seleasy, in the web console and in VS Code: breakpoints, step over and into, continue, and live line highlighting.
- `-validate-sel` compiles a script without running it.

### Traces and the Trace Viewer
Each UI run can produce a `trace.zip` containing:
- network traffic (HAR), with response bodies;
- console logs;
- DOM snapshots per action, and video;
- web vitals, performance and memory metrics;
- a **CPU profile** and **JS/CSS coverage**.

It is captured over CDP or **WebDriver BiDi** (Chromium, Firefox and Edge) and opens in the built-in Trace Viewer, with a timeline synced to every tab.

`traceMode` decides when a trace is captured and kept (it overrides the older `enableTrace` flag):

| `traceMode` | Captures | Keeps |
|---|---|---|
| `off` | never | — |
| `on` | every script | every trace |
| `retain-on-failure` | every script | only scripts where the script or a subtest failed |
| `on-first-retry` | only in retry round 1 (needs `seleniumScriptRetryCount` ≥ 1) | every trace it captured |

When a step fails, its screenshot is shown in the test's report, next to the "Open in Trace Viewer" button.

### Reports and analysis
- **HTML reports** per run, with pass/fail summary charts and full request/response detail per test case.
- **PDF and CSV run reports** for UI (seleasy) suites, built with iText and written next to the HTML report as `…-RP-<timestamp>.pdf` / `.csv`. There is one results table per script (session, subtest, time, status, message). The PDF adds an error-details page per failure, with the stack trace and the failure screenshot.
- **Load reports:** live charts and latency percentiles; distributed runs are aggregated per node.
- The console's **report viewer**, **file compare** and **data-source statistics** views.
- **CLI runs** print Playwright-style ✓/✘ output, write a detailed log file, and return a CI-friendly exit code (see [below](#console-output-logs-and-exit-codes)).

### The web console (`gatf ui`)
One page for everything above:
- **Config**, **Test Files** (tree, editor, compare) and **Execute** (files or whole folders, live status);
- **Reports** and traces;
- **Import**, **Record** and **Crawl**;
- **Data Sources** and **Integrations**.

Optional login; light and dark themes.

## Install

macOS / Linux:
```sh
curl -fsSL https://github.com/sumeetchhetri/gatf/releases/latest/download/install.sh | bash
```
Windows (PowerShell):
```powershell
irm https://github.com/sumeetchhetri/gatf/releases/latest/download/install.ps1 | iex
```
**Offline:** download `gatf-<version>.zip` from [Releases](https://github.com/sumeetchhetri/gatf/releases), unzip it, and run `./install.sh` (or `powershell -ExecutionPolicy Bypass -File .\install.ps1`).

**Java:** needs Java 21+. A JRE is enough; only the online debugger needs a JDK. The installer:
- detects an installed Java (preferring a JDK), or offers a private Eclipse Temurin 21 (a JDK by default, `--jre` for the smaller runtime);
- installs per user, with no admin rights;
- can set up the load tools (`--with-tools`).

**From source:** `mvn -B install`, then use `alldep-jar/target/gatf-alldep-jar-<version>.jar`.

## Quick start

```sh
gatf ui                                  # web console → http://127.0.0.1:9080/  (project = current dir)
gatf ui 9080 ./sample-project            # ...or point it at a project folder
gatf test                                # run the suite in ./gatf-config.json (or .xml) from the CLI
gatf test --tag smoke                    # ...only tests tagged @smoke
```
Without the installer, replace `gatf` with `java -jar gatf-alldep-jar-<version>.jar`, and `gatf ui` with `-configtool 9080 127.0.0.1 .`.

[`sample-project/`](sample-project/) is runnable as is. It has API tests (a token-capturing workflow), seleasy UI tests, a load suite and a local target server.

A minimal API test (`tests/api/health.xml`):
```xml
<TestCases>
  <TestCase>
    <url>/health</url>
    <name>Health</name>
    <method>GET</method>
    <expectedResCode>200</expectedResCode>
    <expectedNodes><string>status</string></expectedNodes>
  </TestCase>
</TestCases>
```

A minimal seleasy script (`tests/ui/login.sel`):
```
open chrome
goto https://example.com/login
??10 id@username type "user"
??10 id@password type "password"
??10 class@loginBtn click
?? css@"h1" text ="Welcome"
```

## Commands

| Command | What it does |
|---|---|
| `gatf ui [port] [dir]` | Web console: config, test files, editor, recorder, crawler, importer, execution, reports, traces, debugger. Default port 9080; bind host via `GATF_UI_HOST` |
| `gatf test [config] [options]` | Run the suite (API or UI, per the config's `isSeleniumExecutor`). The config defaults to `gatf-config.json`, else `gatf-config.xml`. Options below |
| `gatf -executor <config>` / `-selenium <config>` | Older form of `gatf test <config>`: same run, no options |
| `gatf -validate-sel <file.sel> <config>` | Parse and compile a script without running it |
| `gatf -generator <config>` | Generate API test cases from JAX-RS / Spring controllers or WSDL |
| `gatf -listener` | Start a distributed-execution agent |
| `gatf tools install …` | Provision the load tools (vegeta, autocannon, wrk/wrk2) |
| `gatf version` | Print the installed version |

JVM flags go in `GATF_JAVA_OPTS` (for example `GATF_JAVA_OPTS="-Dgatf.log.level=debug"`).

### `gatf test` options

| Option | Effect |
|---|---|
| `--grep RE` / `-g RE` | Run only tests whose title matches the regex (repeatable; any match) |
| `--grep-invert RE` | Skip tests whose title matches (repeatable; wins over `--grep`) |
| `--tag T` / `-t T` | Run only tests tagged `@T` (repeatable; combined with `--grep` as OR) |
| `--reporter junit,json,github` | CI reports to write (`none` to disable) |
| `--trace MODE` | Override `traceMode`: `off`, `on`, `retain-on-failure`, `on-first-retry` |
| `--retries N` | Override `seleniumScriptRetryCount` |
| `--workers N` / `-j N` | Override `numConcurrentExecutions` |

Both `--opt value` and `--opt=value` work. If nothing matches, the run stops with `No API tests match …` or `No UI scripts match …` and exit code 1.

**Titles and tags.** Filters match a test's title:
- **API test:** `<file> › <name>`, plus any `@tags` in its description. A tag can also go in the name itself, e.g. `List users @smoke`.
- **UI script:** its path, plus the `@tags` in the `//` comment lines at the top of the `.sel` file:
  ```
  // Checkout happy path @smoke @checkout
  open chrome
  ```
  A script's subtests always run together.
- **Dependencies:** an API test's `relatedTestName` dependency is included automatically. For chains that capture values (e.g. a login token), tag the whole chain, or select it with `--grep <file>`.

## Console output, logs and exit codes

CLI runs (`-executor`, `-selenium`) print one line per finished test, then a summary:
```
Running GATF UI (seleasy) tests

  ✓  1 [chrome] › ui/login-flow.sel (4.2s)
  ✘  2 [chrome] › ui/checkout.sel › pay (3.1s)
       Element not found: id@payBtn
  -  3 [chrome] › ui/checkout.sel › refund

  1 failed
    [chrome] › ui/checkout.sel › pay
  1 skipped
  1 passed (9.8s)

  Report: /work/project/out
  Log:    /work/project/logs/gatf.log
```

- **Console:** only the pass/fail lines above, plus warnings and errors. Passes are green, failures red, skips yellow.
  - Colour is automatic on terminals and GitHub Actions.
  - `NO_COLOR` or `-Dgatf.color=never` disables it; `FORCE_COLOR` or `-Dgatf.color=always` forces it.
  - Load and multi-user runs show only the summary; the report has the per-request detail.
- **Log file:** `logs/gatf.log` in the working directory holds everything:
  - every result with its request, response code and error detail;
  - the engine's step output;
  - stack traces.

  Each run starts a fresh file. The previous run's log is kept as `gatf.1.log.gz`, and the last 10 are retained.
- **Flaky tests:** with `seleniumScriptRetryCount`, failed scripts are retried. A script that fails and then passes is reported as **flaky** (yellow), and it doesn't fail the run.
- **Failure artifacts:** a failed line is followed by the paths of its screenshot and, if one was kept, its trace.
- **CI reports:** `gatf-junit.xml` (JUnit XML; flaky tests carry `<flakyFailure>`) and `gatf-results.json` are written to the run's out dir. In the JUnit XML, a failure's screenshot and trace are attached with the `[[ATTACHMENT|path]]` convention that Jenkins and GitLab display. On GitHub Actions each failure is also an `::error` annotation, and each flaky test a `::warning`.
- **Exit code:** `0` when every test passed or was flaky; `1` if any test failed, the run aborted, or the command line was invalid. The summary always prints, even when failures end the run with an exception.

| Setting (`-D…`) | Default | Effect |
|---|---|---|
| `gatf.log.dir` | `logs` | Where `gatf.log` is written |
| `gatf.log.level` | `info` | Detail in the log file (`debug` for more) |
| `gatf.console.level` | `warn` for CLI runs, `info` otherwise | Log events shown on the console |
| `gatf.capture.stdout` | `true` | `false` also shows the engine's raw output on the console |
| `gatf.color` | `auto` | `always` / `never` |
| `gatf.reporters` | `junit,json,github` | Which CI reports to write; `none` disables them |

## Running in CI

```yaml
jobs:
  ui-tests:
    runs-on: ubuntu-latest
    services:
      selenium:
        image: selenium/standalone-chrome:latest
        ports: ['4444:4444']
        options: --shm-size=2g
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: java -jar gatf-alldep-jar-<version>.jar test gatf-config.xml --reporter junit,json,github   # fails the job on any failed test
        env: { SELENIUM_REMOTE_URL: 'http://localhost:4444' }
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: gatf-results
          path: |
            out/
            logs/
```

## Documentation

| Guide | What it covers |
|---|---|
| [seleasy language reference](docs/seleasy/REFERENCE.md) | Every statement, grouped by task, with examples |
| [Configuration and test cases](docs/configuration.md) | Executor config (all options), API test case fields and formats, data sources and providers, Maven plugin, test generator |
| [Browser modes](docs/browser-modes.md) | Local, chrome-headless-shell, or Selenium Grid |
| [Load tools](docs/load-tools.md) | vegeta, autocannon, wrk/wrk2 and the embedded engine |
| [Windows support](docs/WINDOWS-SUPPORT.md) | Windows-specific notes |
| [VS Code extension](vscode-extension/README.md) | Language server and debugger |
| [End-to-end tests](e2e/README.md) | How GATF tests itself |
| [Roadmap](GATF_REVIEW_AND_ROADMAP.md) | Engineering review and Phase 13, Playwright parity+ |

Related: [test-html-generator-plugin](https://github.com/sumeetchhetri/test-html-generator-plugin) generates HTML pages for your REST endpoints.

## Developing GATF

```sh
mvn -B install                         # build + unit tests + integration tests + codegen goldens + JaCoCo report
cd frontend && npm ci && npx vitest run --coverage   # SPA unit tests (coverage floors enforced)
cd e2e && npm ci && npx playwright test              # SPA e2e against a live server (see e2e/README.md)
```
- **Codegen goldens:** refresh with `mvn -pl alldep-jar -am test -Dtest=CodegenGoldenTest -Dsurefire.failIfNoSpecifiedTests=false -Dgatf.golden.update=true`, then commit the files under `alldep-jar/src/test/resources/codegen-golden/`. CI fails on a missing golden.
- **Coverage:** the JaCoCo report is at `alldep-jar/target/site/jacoco/index.html`, with its floor set by `gatf.coverage.line.min` in `pom.xml`.
- **Snapshot of the source tree** (for reviews): `tools/package-source.sh`.

## License

Apache License 2.0. See [LICENSE](LICENSE).
