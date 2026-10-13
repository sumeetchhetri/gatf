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
- **In the console:** *Play* runs a case as defined. *Replay* re-sends a captured request from a report. Every report carries the equivalent `curl` command (copy it, paste it into a shell, or import it back).

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
- **Load testing:** time-boxed suite replay on the embedded engine, with live charts. A test case can instead delegate to **vegeta**, **autocannon**, **wrk/wrk2** or **wperfk** (`perfConfig`), or to your own engine from `plugins/`; the tools are auto-provisioned and checksum-verified, with a Docker fallback for wrk ([details](docs/load-tools.md)).
- **UI load:** the same concurrency applies to seleasy suites.
- **Flow → load:** in the Trace Viewer, **⚡ Load test this flow** turns a UI run's API and document requests (static assets, preflights and cookies dropped, as in the HAR import) into `<script>-load.xml`: one case per step, named `METHOD /path`, each with a `perfConfig` (vegeta, 10 users, 30 s). The file is saved in the test directory and the console starts it right away in Execute. Beside it, `<script>-load.targets.jsonl` holds the whole flow as vegeta JSON targets (`vegeta attack -format=json -targets=…`). From the CLI: `gatf import flow network.har [--users N] [--duration S] [--rate R] [--engine vegeta|autocannon|wrk|wrk2|wperfk]`, which also prints the `loadConfig` keys for a concurrent-users run of the flow.
- **Distributed load:** `gatf -listener` agents on other machines, over a TLS, shared-secret-authenticated transport. The console shows aggregated live results per node.

### Comparative (multi-environment) testing
Replay the same suite against several base URLs (`compareEnvBaseUrls`) to compare environments, such as staging against production. A report view and a file-compare view show side-by-side diffs.

### UI automation and RPA with seleasy
- **Multi-browser:** multiple browser sessions per script (Chrome, Firefox, Edge, Safari, IE, and Appium Android/iOS). Local (Selenium Manager downloads the driver and Chrome for Testing), the lean chrome-headless-shell, or any Selenium Grid ([browser modes](docs/browser-modes.md)).
- **Locating elements:** id, name, class, CSS, XPath, text, link text and jQuery selectors, with smart waits (`??10`, `??+`, `??-`) and assertions on text, attributes, CSS values and state.
- **Auto-wait** (`autoWait` config, on in the sample and dogfood configs): before every element action (click, type, select, hover, clear, submit, upload, double-click, drag) GATF waits until the element is attached, visible, enabled, stable (same box over two animation frames) and not covered (the click point hits it); `type` fails only on a readonly text field and `clear` needs an editable one. The wait is bounded by `actionTimeout` (ms, default 5000), and a failure names the unmet condition, e.g. `covered by <div.modal>`. Opt a single step out with `click! …` or `nowait …`. Configs without `autoWait` behave as before; projects created from the console get `autoWait` and `webFirstAssertions` on.
- **Web-first assertions** (`webFirstAssertions` config, on in the sample and dogfood configs): a value assertion such as `?? css@h1 text ="Welcome"` whose element is already there but whose condition is not yet true is retried until `assertTimeout` (ms, default 5000) instead of failing at once, so late-rendered text passes without an explicit wait. The failure then names the condition, e.g. `text = "Welcome" still false after 5000 ms`. `nowait` opts a line out; configs without the setting fail at once as before.
- **Soft assertions:** `??~ css@h1 text ="Welcome"` records a failed assertion and carries on; the script (or subtest) then fails at the end with every soft failure listed. The console, JUnit XML (`softFailures` property) and `gatf-results.json` show the count.
- **Semantic locators:** `role@button[name=Save]`, `label@'Email address'`, `placeholder@Search`, `testid@save-btn` (attribute set by `testIdAttribute`, default `data-testid`), `alt@`, `title@`, and exact / regex text (`text@'=Order placed'`, `text@'/placed|shipped/i'`). They resolve in the page with implicit ARIA roles and accessible names, pierce open shadow roots, and with `strictLocators` fail when more than one element matches, listing the candidates.
- **Network mocking:** `route **/api/users fulfill 200 users.json`, `route **/*.png abort`, `route **/api/** continue header 'X-Env: test'` and `unroute` intercept the browser's own requests (the first matching route wins; optional method filter); `route from har checkout.har` replays a recording by method + URL. Chromium via CDP `Fetch`, Firefox via WebDriver BiDi.
- **Auth state:** `state save .auth/admin.json` / `state load …` keep cookies (every domain, via CDP or BiDi) and local/session storage in Playwright's `storageState` format. Set `loginOnce` (a script run once before the UI suite) and `storageState` (the file it leaves) in the config and every script starts logged in. `browserApiStorageToken` (e.g. `localStorage:auth#access_token`) sends a token the app keeps in web storage as a bearer header on `api` / `curl` calls from UI scripts.
- **Emulation:** `emulate device "iPhone 13"` (bundled descriptors, plus your own `devices.json`) runs a mobile-viewport test without Appium. `emulate geolocation|locale|timezone|permissions|colorscheme|offline|network slow3g|reset` cover the rest, over CDP on Chromium and WebDriver BiDi elsewhere ([reference](docs/seleasy/REFERENCE.md#emulate-a-device-location-locale-time-zone-permissions-colour-scheme-or-network-cdp--webdriver-bidi)).
- **Clock control:** `clock install 2026-01-01T09:00:00Z`, `clock fastforward 5m`, `clock set …` fake `Date`, timers and animation frames in the page (and on every new document), so time-dependent UI is tested deterministically.
- **Visual regression:** `screenshot compare home [0.1] [maxdiff 0.5%] [mask id@clock]` compares the full page with `<testCaseDir>/__screenshots__/<browser>/<script>/home.png` (perceptual threshold, anti-aliasing tolerated, masks). A mismatch fails the step with expected / actual / diff images attached; `gatf test --update-snapshots` accepts changes, and a missing baseline fails under `CI=true` ([reference](docs/seleasy/REFERENCE.md#compare-the-page-with-its-baseline-screenshot-visual-regression)).
- **Accessibility:** `a11y check [include css@…] [exclude css@…] [impact>=serious] [report]` runs the bundled axe-core (unmodified, MPL-2.0) in the page. Violations fail the step, listed with their selectors, and show in the report's Checks tab and the attachments gallery. Failed `screenshot compare` steps get a before/after **diff slider** in the report.
- **Hooks and markers:** `beforeAll` / `beforeEach` / `afterEach` / `afterAll` blocks run around the script and each subtest (afterEach/afterAll also after a failure); `skip subtest …` / `fixme subtest …` skip that subtest and `only subtest …` skips the others in the script, reported as skipped with the reason in the console, report and JUnit XML. `globalSetup` / `globalTeardown` in the config run once before and after the UI suite.
- **Per-test browser contexts** (`browserContexts: subtest`, opt-in): every top-level subtest runs in a fresh browser context in the same browser (WebDriver BiDi `browser.createUserContext`, or CDP `Target.createBrowserContext`): its own cookies, storage, cache, routes, emulation and clock, disposed when the subtest ends however it ends, with `storageState` loaded into each one. Unsupported sessions keep the shared context and say so in the run log. Matrix: [docs/browser-modes.md](docs/browser-modes.md#per-test-browser-contexts-h3).
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
  - **Plugin SDK v2:** implement `GatfStepPlugin` (name, execute, optional info/init), register it via `META-INF/services`, and drop the jar into `plugins/`. It is loaded in its own class loader and picked up without a rebuild. Its docs appear in the web editor's and VS Code's completion (`GET /api/plugins`). See [the reference](docs/seleasy/REFERENCE.md#plugins).
  - Built-ins: `jsonread` / `jsonwrite` / `jsonpath`, `xmlread` / `xmlwrite` / `xmlpath`, `api` (call a GATF API test from a UI script and use its response) and `curl` (ad-hoc HTTP).
    Both share the browser's session: the browser's cookies for the target URL are sent, and response Set-Cookies are
    stored back in the browser, so a script can log in through the UI and call the API authenticated (or the reverse).
    `browserApiSession`: `sync` (default), `send` (browser → API only), `off`.
- **API hooks, custom data sources and custom providers:** plain Java classes referenced from the config.
- **Integrations:**
  - fetch server-side logs through your own APIs when a test fails (`gatf-logging-api-int.xml`);
  - define your issue tracker's APIs (`gatf-issuetracking-api-int.xml`).

  Both are edited in the console's **Integrations** view.

### Authoring on-ramps
- **Web editor:** syntax highlighting, autocomplete, diagnostics, go-to-definition across files, and multi-tab editing. Saving validates the script by parsing and compiling it.
- **Recorder:** opens a browser (Chrome, Firefox or Edge) and turns your clicks and typing into seleasy, with timing pauses preserved. Each step uses the first locator that is unique on the page: `testid@`, then `role@…[name=…]`, then `label@`, then id / name / CSS / XPath.
- **Crawler:** logs into an application (including JavaScript SPAs), explores it in a real browser, and proposes a seleasy suite plus a coverage map (the SPA crawler ranks locators the same way as the recorder).
- **Importers:** Playwright specs become seleasy; JMeter plans become GATF API tests, and their thread-group load settings are shown to apply.
  cURL commands (bash/cmd/PowerShell "Copy as cURL", or a report's cURL line), Postman collections (v2.x and v1) and HAR
  recordings become GATF API tests: headers, bodies (raw, urlencoded, multipart, GraphQL), auth, Postman variables
  (`{{x}}` → `${x}`) and recorded/asserted status codes carry over. What cannot be converted is listed, never dropped
  silently. In the console (**Import**) or `gatf import`.
  OpenAPI 3.0/3.1 and Swagger 2.0 specs (JSON or YAML) become one API test per operation: parameters and bodies from the
  spec's examples/defaults or a value built from the schema, the first 2xx as the expected status, `$ref`s (local and
  relative files — in the Import view, multi-select the spec with the files it references) resolved, security schemes
  as `${authToken}`/API-key placeholders, and operation tags as `@tags`.
  Each imported case also keeps a `contract` pointer (`<spec>#<operation>`, the spec is saved beside the .xml), so a run
  fails a response whose status or Content-Type the operation does not declare, or whose JSON body breaks the response
  schema; the failure names the operation and the JSON pointer (`/photoUrls: required property missing`). `--negative`
  (or the Import view checkbox) adds cases that drop a required parameter or send a wrong-typed body property and expect
  400 (422 when only that is declared). Re-importing over an existing file lists the operations added, removed or changed since.
- **Test generator:** creates API test cases from JAX-RS or Spring controllers and SOAP WSDLs, and can also produce a Postman collection.
- **VS Code extension:** language server (completion, diagnostics, navigation) and a debugger.

### Debugging
- A **step debugger** for seleasy, in the web console and in VS Code: breakpoints, step over and into, continue, and live line highlighting.
- `-validate-sel` compiles a script without running it.
- **Watch:** the editor's **Watch** button runs the script beside the editor and re-runs it on every save, with the live browser screencast next to the step list (the Run and Debug views show it too). The frames are the trace video's, so turn on `traceVideo`. `gatf test --watch` does the same for the CLI.
- **Coding agents (MCP):** `gatf mcp` is a Model Context Protocol server over stdio. An agent (Claude Code, Claude Desktop, IDE agents) can list the tests, lint a script, run the suite (`gatf test` with grep/tag/trace/retries), read the failed tests from the last run, condense a trace zip (last actions, console errors, failed and slow requests, checks, web vitals), and record or crawl new scripts. Register it with, for example, `claude mcp add gatf -- java -jar gatf-alldep-jar-<version>.jar mcp --root /path/to/project`.
- **Healer (core, opt-in AI):** turns a failed step plus its trace's failure snapshot (`traceDomSnapshots` on) into proposed fixes, as a diff; it never edits the script. It tries a wait or a strict locator for the element the old locator meant first, and asks the model configured in `<ai>` only when that finds nothing. Every proposal must match exactly one element in the snapshot. Without `<ai>`, nothing changes.

### Traces and the Trace Viewer
Each UI run can produce a `trace.zip` containing:
- network traffic (HAR), with response bodies;
- console logs;
- DOM snapshots per action (opt-in `traceDomSnapshots`: `lite` = the sanitized DOM without stylesheets, `full` = styled, with the page's stylesheets, a before and an after state per action and the action's element marked; capped by `traceDomSnapshotMaxMb`, default 32), and video;
- web vitals, performance and memory metrics;
- a **CPU profile** and **JS/CSS coverage**.

It is captured over CDP or **WebDriver BiDi** (Chromium, Firefox and Edge) and opens in the built-in Trace Viewer, with a timeline synced to every tab.

With `traceDomSnapshots=full`, the viewer's **DOM** view is a styled time-travel: each action's page is rebuilt from the trace alone (its stylesheets and the captured response bodies) in a sandboxed, script-less frame, with a **Before / After** toggle and the action's element outlined. Images the trace has no body for show as blank placeholders (turn on `traceNetwork` to capture them); nothing is fetched from the live site.

**⌖ Pick locator** (DOM view): hover the snapshot to get the element's strict locator, ranked as the recorder ranks it (`testid@` → `role@` → `label@` → id/name/css), and offered only when it matches exactly one element in that snapshot; click to keep it, then **Send to editor**. In the seleasy editor, **Insert below cursor** pastes it as a `click …` step, and **⌖ Pick in live browser** picks one element in the window of an active recording session (Esc cancels; the picker leaves nothing on the page). Ranking runs on the GATF server; without it the viewer lists the candidates with their match counts and paste is disabled.

**Untested routes and scripts:** in Reports, a run's **Coverage** button merges the JS coverage and visited pages of that run's traces (`traceCoverage` on, Chromium) with the newest saved crawl map (`<name>-coverage.json` in the test dir) and lists the crawled routes no test visited and the scripts none of whose functions ran. `GET /api/coverage?report=<run>` returns the same per-test coverage index (`gatf-coverage-index` v2: URLs without query strings or session ids, offset ranges, and each test's coverage state); `coverage-index.json` in the run folder is its cache. CSS is one unattributed total for now.

`traceMode` decides when a trace is captured and kept (it overrides the older `enableTrace` flag):

| `traceMode` | Captures | Keeps |
|---|---|---|
| `off` | never | — |
| `on` | every script | every trace |
| `retain-on-failure` | every script | only scripts where the script or a subtest failed |
| `on-first-retry` | only in retry round 1 (needs `seleniumScriptRetryCount` ≥ 1) | every trace it captured |

When a step fails, its screenshot is shown in the test's report, next to the "Open in Trace Viewer" button.

**Performance budgets and security checks** run over each captured trace; results appear in the test's report and in `checks.json` inside the trace.
- **Budgets:** set `perfBudgets` in the config (e.g. `lcp<2500, requests<80, jsheap<50mb`) or per script in its leading comments (`// @budget lcp<2000 transfer<2mb`). A script budget overrides the config one for the same metric. A breach fails the script with the measured value. Web-vital budgets (`lcp fcp ttfb dcl load cls`) apply to the worst page the script visited (per-navigation vitals, `webVitalsByPage` in performance.json; under BiDi only pages left within the same origin are captured), else to the final page.
  - Metrics for the page open at the end of the script: `lcp`, `fcp`, `ttfb`, `dcl`, `load` (ms) and `cls`.
  - Metrics over the whole script: `jsheap`, `transfer` (b/kb/mb), `requests`, `failedRequests` and `consoleErrors`.
  - A metric the browser didn't report is shown as "not measured"; it doesn't fail the script.
- **Passive security checks** (`securityChecks`: `report` default, `fail` = fail on medium or high, `off`):
  - HTML pages: missing CSP, HSTS, nosniff, clickjacking protection or Referrer-Policy, and server-version disclosure.
  - First-party cookies without Secure or HttpOnly, and `SameSite=None` without Secure.
  - Mixed content, wildcard CORS with credentials, and CSP violations in the console.

  Findings are listed per page. Nothing extra is sent to the site.

### Reports and analysis
- **HTML reports** per run, with pass/fail summary charts and full request/response detail per test case.
- **PDF and CSV run reports** for UI (seleasy) suites, built with iText and written next to the HTML report as `…-RP-<timestamp>.pdf` / `.csv`. There is one results table per script (session, subtest, time, status, message). The PDF adds an error-details page per failure, with the stack trace and the failure screenshot.
- **Load reports:** live charts and latency percentiles; distributed runs are aggregated per node.
- The console's **report viewer**, **file compare** and **data-source statistics** views.
- **Run insights** (Reports › Latest CLI run › Insights, and every merged report): a duration timeline per worker lane, the slowest tests and files, the flaky list, and status / tag / text filters, from the run's `gatf-results.json`. An **attachments gallery** (screenshots, visual diffs, traces, checks, accessibility results) and **Raise defect** on failed tests.
- **Trends:** every CLI run's results are archived in `<out>/history/` (newest 50 kept; `-Dgatf.history.keep=N`). Reports › Trends charts the last 10 runs (passed / flaky / failed / skipped, duration) and ranks the least stable tests. API: `GET /api/history?limit=N`.
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
| `gatf import <type> <file\|-> [-o out] [--name suite] [--filter regex] [--negative] [--force]` | Convert `playwright`, `jmeter`, `curl`, `postman`, `har` or `openapi` (alias `swagger`) input into a GATF test file; prints what needs review. `--filter` keeps matching HAR URLs. `flow` turns a run's `network.har` into a load test (see Flow → load) |
| `gatf export playwright <file.sel\|dir> [-o out] [--strict-locators] [--force]` | Export seleasy scripts to Playwright Test (TypeScript): semantic locators map to `getByRole`/`getByLabel`/…, waits and assertions to web-first `expect`, subtests to `test.step`; `${name}` values become environment variables (`GATF_NAME`), never their configured values. What has no Playwright equivalent is kept as a `// TODO(gatf-export):` line and listed in the report |
| `gatf export selenium-java <file.sel\|dir> [-o out] [--strict-locators] [--force]` | Export seleasy scripts to Selenium WebDriver + JUnit 5 (Java 11+): one `<Name>Test.java` per script needing only `selenium-java` and `junit-jupiter`. `By` locators are the ones seleasy uses, semantic locators run seleasy's own resolver script, waits poll up to the element timeout (60 s default, `timeout`), subtests become methods; the first `open` picks the driver (`SELENIUM_REMOTE_URL` runs it on a Grid). Same `${name}` → `GATF_NAME` environment rule and `// TODO(gatf-export):` report |
| `gatf docs [config…] [-o dir] [--format xlsx\|csv\|both] [--requirements file] [--requirements-jql JQL] [--results file]` | Test-case documents (steps and expected results), a requirement traceability matrix and coverage, as `gatf-testcases.xlsx` (sheets Test Cases, Traceability, Coverage) and/or CSVs. See below |
| `gatf merge-reports <dir>… [-o out]` | Join the out dirs of a sharded run (`--shard i/n` on several machines) into one report: `gatf-results.json`, `gatf-junit.xml`, each shard's reports copied under `shard-<i>-of-<n>/`, and an `index.html` that opens the merged summary (totals, shards, failures, flaky) in the console (merge under the out dir, e.g. `-o out/merged`, so `gatf ui` lists and serves it). Default out dir `merged-report`; exit code 1 when any test failed |
| `gatf mcp [--root dir]` | MCP stdio server for coding agents: `list_tests`, `lint`, `run`, `get_report`, `summarise_trace`, `record`, `crawl`. stdout carries only the protocol; logs and run output go to stderr |
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
| `--reporter junit,json,github,docs` | CI reports to write (`none` to disable). `docs` also writes the test-case documents, traceability matrix and coverage, with this run's status, to `<out>/docs` |
| `--trace MODE` | Override `traceMode`: `off`, `on`, `retain-on-failure`, `on-first-retry` |
| `--retries N` | Override `seleniumScriptRetryCount` |
| `--workers N` / `-j N` | Override `numConcurrentExecutions`: UI scripts run file-parallel on N workers (no fixed cap; never more than the script count). `N%` = that share of the CPU cores |
| `--update-snapshots` / `-u` | Rewrite the baselines of every `screenshot compare` instead of comparing (config `updateSnapshots`) |
| `--shard i/n` | Run only shard i of n (1-based): files are split deterministically by a path hash, so every machine gets the same split. `relatedTestName` prerequisites come along. An empty shard exits 0 |
| `--watch` | Keep running: after the first run, a save re-runs the changed scripts / API case files plus the last failures. A change to anything else (config, properties, a library script others import) re-runs everything, failures first. Enter re-runs everything (failures first; the rest only once they pass), `f` + Enter only the failures, `q` + Enter quits |
| `--changed RANGE` | Test-impact selection (UI suites): run only the scripts a git range (`main...HEAD`, `HEAD~1..HEAD`, or `HEAD` for uncommitted work) can reach, from the coverage index of an earlier run with trace coverage on, recorded by this version or later, which notes whether each script passed (`--coverage-run dir`, default the newest run folder; `--source-maps dir` = the build output with external `.map` files). Anything unmapped or unsafe (CSS, a file no script maps to, incomplete or stale coverage, a script that failed in that run) runs everything and says why. `--explain` prints the selection with a reason per script and runs nothing |

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
- **In the console:** the Execute view has the same grep / tag / grep-invert fields; they apply within the files selected for the run.

### Test-case documents, traceability and coverage

`gatf docs` turns the configured tests into manual-style test cases. The output is `gatf-testcases.xlsx`, with three sheets, plus matching CSVs (`--format`):
- **Test Cases:** ID, module, source, title, tags, requirements, preconditions, steps and expected results.
  - **API tests:** the request becomes the steps; the status, content type, nodes, validations and captured variables become the expected results.
  - **UI scripts:** each script and each subtest it defines becomes a test case. Commands are written in plain words, `include` and `@call` are expanded, and `??` assertions become expected results.
- **Traceability:** requirement → test cases, with pass/fail/not-run counts and a status (Passing, Failing, Partially run, Not run, Not covered).
- **Coverage:** requirement and test-case totals and percentages, broken down by module.

Requirements are linked with tags: `@REQ-101`, any Jira-style `@PROJ-12`, or `@req:<anything>` (`--req-pattern` overrides). They go in an API case's name or description, a script's header comments, or the comment above a subtest. `--requirements reqs.csv|xlsx` (columns id, title, module) adds the full list, so uncovered requirements show up. Status comes from the last run's `gatf-results.json` (found automatically in the out dir, or `--results`). `gatf test --reporter junit,json,docs` produces the documents after every run. `--requirements-jql '<JQL>'` imports the requirement list from Jira (id = key, title = summary, module = first component or project), using `GATF_JIRA_URL` + `GATF_JIRA_TOKEN` (Bearer; add `GATF_JIRA_USER` for Basic user:token). The console's Reports view has a **Test cases (xlsx)** download.

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

Sharded across machines (each job writes its own `out/`; `gatf-results.json` records the shard):
```yaml
    strategy: { matrix: { shard: [1, 2, 3, 4] } }
    steps:
      - run: java -jar gatf-alldep-jar-<version>.jar test gatf-config.xml --shard ${{ matrix.shard }}/4 --workers 50% --reporter junit,json
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: 'out-${{ matrix.shard }}', path: out/ }
  merge:
    needs: ui-tests
    if: always()
    steps:
      - uses: actions/download-artifact@v4          # out-1 … out-4
      - run: java -jar gatf-alldep-jar-<version>.jar merge-reports out-1 out-2 out-3 out-4 -o merged-report
```

### WebKit and Safari

`.github/workflows/ci-webkit.yml` runs a small CDP-free subset (`dogfood/tests/app-console.sel`, `app-console-semantic.sel` against a local `/app` server) on Safari (macOS runner, `safaridriver --enable`) and, experimentally, on WebKitGTK (`WebKitWebDriver` + MiniBrowser under Xvfb), then a report-only capability probe (`sample-project/tests/trace-smoke.sel` with every recorder on). It is a separate workflow and does not gate `Build gatf`. Locally: `bash tools/ci/webkit-lane.sh safari|webkitgtk` after `mvn -q -pl alldep-jar -am package -DskipTests`.

WebKit has no CDP, so parity with Chromium is partial:

| Feature | Safari / WebKitGTK |
|---|---|
| Locators (incl. semantic), auto-wait, web-first assertions | ✅ exercised by the lane |
| `screenshot compare`, `a11y check` (axe) | expected to work (WebDriver screenshot / JS); not exercised by the lane |
| Tracing (`enableTrace`) | needs CDP or a live WebDriver BiDi socket; without one **no trace.zip is written, silently**. WebKitGTK 2.52 (ubuntu-24.04 apt) refuses `webSocketUrl`; the local `safari` key asks for it only when a script uses `route`/`emulate`/`clock` (if safaridriver refuses it, that session does not start), and Safari's BiDi is partial |
| Trace with a BiDi socket: DOM snapshots (serialized DOM + CSS by script), screenshots, metrics | ✅ (script / WebDriver) |
| Trace with a BiDi socket: network/HAR, response bodies, console | only as far as the browser's BiDi implements them; the probe reports which |
| Trace: video/screencast | BiDi only with the opt-in polled approximation (`videoPollFps` > 0) |
| Trace: CPU profile, JS/CSS coverage, CDP `DOMSnapshot` | ❌ CDP only |
| `route …` | ❌ without BiDi the step fails: `route: this browser session cannot intercept requests (needs CDP on Chromium, or a BiDi session: webSocketUrl)` |
| `emulate …` | ❌ without BiDi the step fails: `emulate <op>: this browser session has neither CDP (local Chromium) nor a WebDriver BiDi socket (webSocketUrl)` |
| `clock install` | without BiDi, current document only; the message ends `(this document only: clock: … neither CDP … nor a WebDriver BiDi socket …)` |
| `state save/load`, `browserApiSession` | WebDriver cookies only (the open page's domain); logged at debug level |

When a trace is recorded, recorders skip a capability the browser lacks **without a log line**; the record is `capabilities` in the trace's `metadata.json`. The probe prints it (`served` / `MISSING <capability> -> <what degrades>`, via `tools/ci/trace-capabilities.mjs`), or says that no trace was recorded, and adds it to the job summary. GATF has no `webkitgtk` browser key yet: that lane runs `open firefox-dkr` (the `-dkr` form honours `SELENIUM_REMOTE_URL`) through `tools/ci/webkit-caps-shim.mjs`, which rewrites the session capabilities for WebKitWebDriver and logs `[webkit-shim] this WebKitWebDriver refused webSocketUrl (no WebDriver BiDi) …` when it has to drop BiDi; the lane fails if no session reached WebKitWebDriver.

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
| [Roadmap](GATF_REVIEW_AND_ROADMAP.md) | Engineering review and Phase 13, Playwright parity+. Status: Tiers 0–2 delivered (auto-wait and web-first assertions, semantic locators, network mocking, auth state and hooks, workers/sharding, report parity, visual and accessibility checks, emulation and clock, plugin and perf-engine SPIs, MCP server, watch mode, GatfAssert, flow → load); Tier 3 next |

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
