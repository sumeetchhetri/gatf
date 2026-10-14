# GATF Tier 3 release notes (Phase 13, Δ145b–Δ163)

Tier 3 adds the "high" items of the Playwright-parity plan:
- DOM time-travel in the Trace Viewer;
- AI-assisted healing and planning;
- browser contexts;
- coverage insight and test-impact selection;
- device clouds and Appium 2;
- a WebKit/Safari CI lane;
- export to Playwright and Selenium Java;
- distributed traces.

Almost everything is **opt-in**: an existing config runs as before unless you switch a feature on. The exceptions are listed under [Behaviour changes](#behaviour-changes). Read that section before you upgrade.

Status at release:
- Every card except Δ152 is in the tree.
- The live checks still open are listed under [Not yet verified live](#not-yet-verified-live).

---

## New features and how to switch them on

### DOM time-travel in traces (H2)
- **Turn it on:** `traceDomSnapshots` in the executor config.
  - `full`: styled snapshots before and after every action.
  - `lite`: sanitised HTML only.
  - `off`: the default.
  - `-Dgatf.trace.domSnapshots=…` sets the default for one JVM.
  - `traceDomSnapshotMaxMb` (default 32) caps the snapshot bytes per trace. A slow or oversized capture degrades full → lite → off, and `metadata.json` records why.
- **What you get:**
  - The Trace Viewer's DOM view rebuilds each snapshot styled, in a sandboxed frame with no scripts and no network.
  - A Before / After toggle per action, with the action's element outlined.
  - Images and fonts come from the trace's HAR bodies, so keep `traceNetwork` with bodies on for full fidelity. Without them you see sized placeholders, not broken images.
  - Passwords are masked, and scripts, event handlers and `javascript:` URLs are stripped at capture.
- **Trace format 2.** Older traces still open, and older viewers still read the v1 fields of new traces.

### Pick locator (H2)
- **In the Trace Viewer's DOM view:** **⌖ Pick locator**. Hover an element to get its ranked locator (testid → role → label → id/name/css), click to keep it, then use **Send to editor**. A locator is offered only when it matches exactly one element in the snapshot.
- **In the editor:**
  - **Insert below cursor** pastes the picked line.
  - **⌖ Pick in live browser** picks from the window of a running recording session.

### AI: provider SPI, healer and planner (H1, opt-in)
- **Turn it on:** add an `<ai>` block to the executor config (see [Configuration](../configuration.md)). With no `<ai>` block nothing calls a model.
- **Providers:** `anthropic`, `openai`, `local` (any OpenAI-compatible endpoint), or a plugin provider dropped into `plugins/`.
- **The key** comes only from the environment variable named by `apiKeyEnv` (default `ANTHROPIC_API_KEY` / `OPENAI_API_KEY`) or from the first line of `apiKeyFile`. A relative `apiKeyFile` is resolved against the config file's folder. The key never appears in reports, traces, logs, MCP replies or CLI output.
- **Budgets:** `maxCallsPerRun`, `maxTokensPerRun`, `maxOutputTokens`, and optionally `maxCostPerRun` with prices.
- **Healer:**
  - **Where:** **🩹 Heal failed step** in the Trace Viewer, **Heal last failure** above an open `.sel`, or the MCP `heal` tool.
  - **How it ranks:** deterministic proposals come first and the model second. Every proposal must resolve strictly against the failure's DOM snapshot.
  - **Accept and reject:** each proposal is a diff you accept or reject. Accept replaces only the healed line, and only after `Command.validateSel` passes. Nothing is written automatically.
  - **Prerequisite:** healing needs a trace recorded with `traceDomSnapshots=full`.
- **Planner:**
  - **Run it:** `gatf plan <crawl>-coverage.json [-o plan.json] [--md plan.md] [--config cfg] [--no-ai] [--max-routes N] [--coverage-run dir] [--force]`, or the read-only MCP `plan` tool.
  - **Output:** a JSON + Markdown test plan that covers every crawled route.
  - **With or without AI:** the model can only enrich the plan; it can't add or drop routes.
  - **Older crawl maps:** SPA maps saved before Tier 3 carry no element data. Re-crawl to get form journeys.
- **Generator (Δ152):** *pending — filled by the orchestrator at merge.*

### Browser contexts and workers on contexts (H3, opt-in)
- **`browserContexts: subtest`** runs every top-level subtest in a fresh browser context of the same browser. Each context gets its own cookies, storage, cache, routes, emulation, clock and `storageState`.
- **`browserContexts: worker`** gives each worker one browser and runs every script in a fresh context of it.
  - `-Dgatf.browserContexts=worker` sets it for one JVM.
  - `-Dgatf.contextRecycle=N` relaunches the browser every N scripts (default 50, `0` = never).
  - With tracing on, a `worker` run switches traces to WebDriver BiDi when the browser offers it, or else falls back to a browser per script; either way it is logged once.
  - A browser that cannot host contexts (Safari, IE, `remote-*`, `appium-*`) falls back to a browser per script, also logged once.
- **Default:** `off`. Set it in `gatf-config.json`; it is not yet in the console's config form. The capability matrix is in [Browser modes](../browser-modes.md).

### Coverage map and test-impact selection (H6)
- **Reports → Coverage** lists, for one run, the crawled routes no test visited and the scripts none of whose functions ran.
  - It needs traces with `traceCoverage` on (Chromium).
  - It uses the newest saved crawl map; there is also a picker.
  - The index (`gatf-coverage-index` v2) is also at `GET /api/coverage?report=…`.
- **`gatf test --changed <git-range> [--explain] [--coverage-run dir] [--source-maps dir]`** runs only the UI scripts a change can reach.
  - It uses the coverage index of an earlier run recorded by this version.
  - Anything it cannot prove safe runs everything, and says why. This covers an unmapped file, CSS, an opaque bundle, a failed or unknown outcome, or a stale index.
  - `--explain` prints the selection and runs nothing.

### Device clouds and Appium 2 (H5, opt-in)
- **Cloud browsers:** `GATF_CLOUD=browserstack|saucelabs|lambdatest` (or `-Dgatf.cloud`) runs the same `.sel` on that vendor's cloud browser.
  - **Credentials** come only from the environment: `BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY`, `SAUCE_USERNAME`/`SAUCE_ACCESS_KEY`, `LT_USERNAME`/`LT_ACCESS_KEY`. They are sent as HTTP Basic auth and never appear in URLs, capabilities or output.
  - **Optional settings:** `GATF_CLOUD_BROWSER_VERSION`, `GATF_CLOUD_PLATFORM`, `GATF_CLOUD_BUILD`, `SAUCE_REGION`, `GATF_CLOUD_DEVICE` (Chrome/Edge mobile emulation) and `GATF_CLOUD_OPTIONS` (JSON merged into the vendor options).
  - **Dashboard:** the session's name and pass/fail are sent to the vendor's dashboard.
  - **Precedence:** an explicit `SELENIUM_REMOTE_URL` wins over `GATF_CLOUD`.
- **Appium 2:** `open appium-android` / `open appium-ios` start an Appium 2 session (UiAutomator2 / XCUITest).
  - **Where:** a local server (`GATF_APPIUM_URL` or `-Dgatf.appium.url`, default `http://127.0.0.1:4723`), or a real cloud device with `GATF_CLOUD` + `GATF_CLOUD_DEVICE` / `GATF_CLOUD_OS_VERSION` / `GATF_CLOUD_APP`.
  - **Gestures:** `tap`, `swipe`, `zoom`, `pinch` and `touch …` are sent as W3C touch actions. `hidekeypad`, `shake` (iOS) and `rotate` use the platform's commands.

### WebKit / Safari CI lane (H4)
- `.github/workflows/ci-webkit.yml` runs a small CDP-free subset on macOS Safari (safaridriver).
- It also runs that subset experimentally on Linux WebKitGTK (through a capabilities shim, as `firefox-dkr`).
- A report-only probe lists which trace capabilities each browser served.
- The workflow is a separate, non-blocking check.
- The parity table is under "WebKit and Safari" in the README.

### Export to Playwright and Selenium Java (H7)
- `gatf export playwright|selenium-java <file.sel|dir> [-o out] [--strict-locators] [--force]`.
- **Playwright** writes `.spec.ts` files. **selenium-java** writes JUnit 5 `<Name>Test.java` classes that need only `selenium-java` and `junit-jupiter`.
- **Semantic locators** map 1:1; the Selenium export embeds seleasy's own resolver.
- **Secrets:** `${x}` becomes an environment lookup (`GATF_X`) and is never resolved into the file.
- **Anything that can't be mapped** becomes a `TODO(gatf-export)` comment and is listed in `export-report.txt`. Nothing is dropped silently.
- **Existing output:** the CLI never overwrites a file without `--force` and refuses symlinked targets.

### Distributed functional traces (H8)
- **Workers send their trace zips** back to the coordinator over the existing authenticated (TLS + shared-secret) channel.
- **Where they land:**
  - The coordinator verifies each one (size, sha256, zip header) and stores it under `<out>/dist-traces/<run>/<node>/`.
  - It links each one from `<out>/distributed-traces.html` and `.json`, with an "open" link into the Trace Viewer.
- **`merge-reports`** carries these links into the merged report.
- **Limits:** `-Dgatf.dist.trace.maxBytes` (default 256 MiB; `0` turns transfer off) and `-Dgatf.dist.trace.maxCount` (default 2000).

---

## Behaviour changes

These apply without any new setting.

**Mobile and drivers**
- **Default tap hold is 100 ms (was 500 ms)** when `tap` is given no duration. A 500 ms hold read as a long-press on Android. A given duration, including `0`, is used exactly.
- **Touch commands on a desktop session are now a step error** naming the command and line, for example `tap failed at tests/m.sel:7: tap needs an Appium session …`. This covers `tap`, `swipe`, `zoom`, `pinch`, `touch`, `hidekeypad`, `shake` and `rotate`.
  - Before, some were silent no-ops and others threw a ClassCastException.
  - Only that step fails; a soft `??~` records it and continues.
- **`tap {element}` taps the element** (its centre, or `x y` from its top-left). Before, the tap went to an off-screen point.
- **`touch … wait N` now waits.** Before, the wait was dropped.
- **`pinch {element}` now pinches.** Before, it zoomed.
- **Safari:**
  - Safari sessions no longer request `acceptInsecureCerts`.
  - A Grid is used for Safari only when `SELENIUM_REMOTE_URL` / `-Dgatf.selenium.remoteUrl` is set explicitly; `GATF_WITH_GRID` never starts one for Safari.
  - `open safari` now goes through the common driver path, so tracing can request a BiDi socket.
- **`open edge` / `open edge-dkr` now compile.** Before, the generated code did not compile.

**seleasy language**
- **`*/` ends a block comment only inside one**, and a one-line `/* … */` is a complete comment.
  - Lines such as `route **/api/users …` or `click xpath@//div/*/a` used to be silently skipped; they now run.
  - A line *starting* with `*/` and no comment open is now a parse error.
- **`else ifnot`** is negated, as written. Before, it ran as `else if` on garbled text.
- **`doubleclick` / `dblclick` double-click and `hoverclick A B` hovers and clicks** under the default global timeouts. Before, they only waited.
- **`?? <locator> hoverclick`** is now a parse error.
- **`timeout`:** a later `timeout` statement no longer changes the retry count or poll interval of waits, frames and tabs written before it.
- **Docs:** `??+` / `wait till visible` are now documented as "wait until no longer found", which is what they always did. The code is unchanged.

**Recorder, crawler and importer**
- **Recorder and SPA crawler** no longer write a page-supplied value that would be templated.
  - A value containing `$` is written as Velocity unparsed content `#[[…]]#`.
  - A locator, value or URL that can't be written safely becomes a fixed `// GATF record: …` or `// GATF crawl: …` skip comment instead of a step. This is common on ASP.NET pages with `name="ctl00$…"` and no testid, role or label.
  - The recorder records only `http(s)` and `file` URLs.
  - A skipped crawled `goto` now skips that page's whole block.
- **Playwright importer wait direction is fixed:** `toBeVisible` / `waitFor` → `??`, and `toBeHidden` / `toBeDisabled` → `??+`. **Re-import suites imported before Tier 3:** their waits point the wrong way.

**Exports**
- **`pass` records and continues** in exported scripts, as seleasy does. It used to `return`.

**Distributed runs**
- **Results zip:** the coordinator no longer extracts a node's results zip as-is. It accepts only:
  - top-level `*.html` / `*.csv` for API runs;
  - top-level `DRun-<index>…` for UI runs.
  
  Paths, links, duplicates and ZIP64 are refused, and each refusal is logged.
- **Caps:** 1 GiB per zip and 10,000 entries. Change the byte cap with `-Dgatf.distributed.resultsMaxBytes` / `GATF_DISTRIBUTED_RESULTS_MAX_BYTES`.
- **Protocol version 2.** A coordinator speaks distributed protocol v2 and retries an older node once with v1; that node then sends no traces, and the fallback is logged once per node.
- **Disk:** trace transfer is on by default for distributed UI runs, so trace zips are stored on the coordinator under `<out>/dist-traces/`. Old run folders are not cleaned up.

**Other**
- **Sample projects:** the sample project and dogfood configs now set `traceDomSnapshots=full`.
- **Generated classes:** the generated-class folder (`$TMP/gatf-code`) is no longer wiped at the start of each run; only files older than 24 h are removed. This fixes a race between parallel debug sessions.
- **Trace `metadata.json`** gains `format: 2`, the snapshot fields and an `outcome` field (passed/failed).

## Codegen goldens

New corpus scripts and their goldens are committed:
- `22-transpiler-fixes`
- `23-transpiler-fixes-2`
- `24-transpiler-fixes-3` (timeout counts captured where written)
- `25-mobile-touch` (W3C touch codegen)

Existing goldens changed in three places:
- `15-route` (lines that used to be skipped);
- `02-elements` / `12-autowait` (double-click, hoverclick);
- every golden's driver line (the Δ154 worker hook `__workerDriver__`, a no-op unless `browserContexts: worker`).

## Upgrade notes

1. **Re-generate goldens.** If you keep your own codegen goldens, re-generate them once with `-Dgatf.golden.update=true` and review the diff. Expect the driver line and any lines listed above.
2. **Re-import Playwright suites** that were imported before Tier 3.
3. **Check recorded and crawled scripts** for `// GATF record:` / `// GATF crawl:` skip comments and add a safe locator by hand where needed.
4. **Desktop scripts using touch commands** now fail those steps. Remove them, or run them under `open appium-android|appium-ios`.
5. **Distributed runs:** upgrade nodes and coordinator together to get traces. A mixed fleet still runs, without traces from old nodes.
6. **Custom result files from nodes:** if a node sent extra files in its results zip, only the allow-listed names are kept now.
7. **AI features** need an `<ai>` block and a key in the environment. Nothing changes without it.

## Not yet verified live

Recorded as open; these do not block an upgrade.
- **Device clouds and real devices:** a live BrowserStack / Sauce Labs / LambdaTest browser run and a real-device Appium run.
- **WebKit / Safari:**
  - The first green Safari CI run.
  - Linux WebKitGTK is not reached as a GATF browser: Selenium Java has no WebKitGTK options class, so the lane runs through a shim.
- **Workers on contexts:**
  - Wall-time and launch-count measurement on a real suite.
  - DevTools re-attach on a reused browser.
