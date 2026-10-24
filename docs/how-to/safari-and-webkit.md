---
title: Safari and WebKit
sidebar_position: 27
---

# Safari and WebKit

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

See also [Browser modes](../browser-modes.md#cross-browser-support-g9) for the full cross-browser matrix.
