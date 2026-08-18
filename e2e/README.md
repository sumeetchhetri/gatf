# GATF end-to-end tests

Two e2e layers, both requiring a browser (so they run on a workstation / PR CI, not the push build):

## 1. `/app` SPA e2e — Playwright (this directory)
Drives the embedded React console against a **live GATF server** seeded with `sample-project`.

```bash
# once: build the shaded jar + install the browser
mvn -q -pl alldep-jar -am package -DskipTests
cd e2e && npm ci && npm run install-browser
# run
npm test
```

`playwright.config.ts`'s `webServer` launches `start-gatf-server.sh`, which resolves the shaded jar,
copies `sample-project` to a scratch dir, and starts `GatfConfigToolUtil -configtool <port> 127.0.0.1 .`
(serving `/app`). Playwright waits for `/app/`, runs the specs, then tears the server down.

Specs: `app-smoke` (shell + primary-tab navigation), `app-testfiles` (the `/testcasefiles` → SPA path),
`app-editor` (open a `.sel` in the CodeMirror editor), `app-config` (edit + save the executor config),
`app-execute` (the Execute controls render). Driving a `.sel` **save**, a full suite **run**, and the
**debugger** from the browser needs the server-side compile toolchain / a Selenium Grid — those paths
are covered by the server ITs, the 066c API-execution IT, and `trace-e2e.sh`.

## 2. Recorder e2e — `tools/smoke/trace-e2e.sh` (Selenium-facing)
Runs the full recorder suite against a real Selenium Grid over a live click, then validates the
produced `trace.zip` with `tools/smoke/validate-trace.py`. This is the run that de-risks the
CDP/BiDi-facing recorder code (screencast, Profiler/Performance, per-action hook) + the `se:cdp`
acquisition path.

```bash
SELENIUM_REMOTE_URL=http://localhost:4444 JAVA_HOME=/path/to/jdk tools/smoke/trace-e2e.sh --build
# BiDi transport instead of CDP: add TRACE_PROTOCOL=bidi
```
