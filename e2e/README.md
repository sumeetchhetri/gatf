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
`app-execute` (the Execute controls render). These 6 run by default.

### Grid workflows (`app-workflow-grid`, T4c)
Three heavier specs — saving a `.sel` (server-side transpile+compile parse-validation), running a
selenium script to a terminal state, and opening the online debugger — need the server-side toolchain /
a Selenium Grid. They **skip** unless `GATF_SELENIUM_GRID` is set (the launcher maps it to
`SELENIUM_REMOTE_URL` for the GATF server only — Playwright reserves `SELENIUM_REMOTE_URL` to route its
*own* browser through a Grid, so don't set that one):

```bash
export GATF_SELENIUM_GRID=http://localhost:4444   # e.g. selenium/standalone-chrome
cd e2e && npm test
```

## 2. Recorder e2e — `tools/smoke/trace-e2e.sh` (Selenium-facing)
Runs the full recorder suite against a real Selenium Grid over a live click, then validates the
produced `trace.zip` with `tools/smoke/validate-trace.mjs` (node). This is the run that de-risks the
CDP/BiDi-facing recorder code (screencast, Profiler/Performance, per-action hook) + the `se:cdp`
acquisition path.

```bash
SELENIUM_REMOTE_URL=http://localhost:4444 JAVA_HOME=/path/to/jdk tools/smoke/trace-e2e.sh --build
# BiDi transport instead of CDP: add TRACE_PROTOCOL=bidi
```
