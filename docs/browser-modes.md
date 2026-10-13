# GATF browser modes

Since delta-072, GATF drives no Docker images of its own. There are three ways to run the browser; pick by whether you need a Grid and how small you want the footprint.

## Mode A — local, Selenium Manager (default, no Docker)
Run a selenium suite with **`SELENIUM_REMOTE_URL` unset**. Selenium Manager (built into Selenium 4.48) resolves the driver and, when the browser is absent, downloads **Chrome for Testing** — both cached in `~/.cache/selenium`. Nothing to install, no Docker.

```bash
# SELENIUM_REMOTE_URL must NOT be set
java -jar gatf-alldep-jar-5.0.0.jar -selenium gatf-config.xml
```
**Footprint:** a few hundred MB (Chrome for Testing + chromedriver), one-time.

## Mode B — local, chrome-headless-shell (leanest, no Docker)
Same as Mode A but on Chrome for Testing's **chrome-headless-shell**. GATF auto-provisions it — shell **and the matching chromedriver** (same CfT version) — on first use:

```bash
export GATF_CHROME_BINARY=headless-shell          # or -Dgatf.chrome.binary=headless-shell
java -jar gatf-alldep-jar-5.0.0.jar -selenium gatf-config.xml
```
Cached under `~/.gatf/tools/<os>-<arch>/chrome-for-testing/<version>/` (override the root with `-Dgatf.tools.dir` / `$GATF_TOOLS_DIR`). Version = current Stable unless pinned with `-Dgatf.cft.version` / `$GATF_CFT_VERSION`; offline, the newest cached version is used. Mirror / air-gapped: `-Dgatf.cft.baseUrl=<mirror>` (same `<ver>/<platform>/*.zip` layout) and `-Dgatf.cft.versionsUrl`, or pre-provision once with `java -cp gatf-alldep-jar-5.0.0.jar com.gatf.selenium.ChromeForTesting` and copy the cache. No linux-arm64 CfT build exists — there, set `GATF_CHROME_BINARY` to a system chromium path. Minimal Linux images still need Chrome's shared libs (libnss3, libgbm, …).

Any other `GATF_CHROME_BINARY` value is used as a binary path (any Chrome/CfT binary; chromedriver then comes from Selenium Manager). Local `chrome` only (ignored for Grid and non-chrome).

**Footprint:** ~150 MB (shell + chromedriver) — Playwright parity. This is the leanest option; the WebDriver-blessed headless path remains Chrome for Testing with `--headless=new` (Mode A), so prefer Mode A if you hit shell-specific quirks.

## Mode C — Grid (distributed / parallel)
Set `SELENIUM_REMOTE_URL`; GATF connects with `RemoteWebDriver` and `se:cdp` resolves as before. GATF ships no image — bring your own endpoint:

```bash
export SELENIUM_REMOTE_URL=http://localhost:4444
java -jar gatf-alldep-jar-5.0.0.jar -selenium gatf-config.xml
```
| Endpoint | Footprint |
|---|---|
| Official `selenium/standalone-chrome` (4.48 / Chrome 152) | ~3.4 GB — JVM + Grid + full Chrome + VNC/desktop |
| Slim node recipe (headless-shell + chromedriver, no VNC) | ~400–500 MB |

Use Mode C only when you actually need a Grid; Modes A/B cover single-machine runs with no container at all.

## Cross-browser support (G9)

| Feature | Chrome / Edge | Firefox | Safari |
|---|---|---|---|
| Test execution | ✅ | ✅ | ✅ |
| Trace — network, console, DOM/screenshot, metrics, PDF | ✅ CDP, or BiDi (`traceProtocol: bidi\|hybrid`) | ✅ BiDi (automatic) | ✅ BiDi, where safaridriver exposes it |
| Trace — response bodies | ✅ | ✅ if the browser supports BiDi data collection | as above |
| Trace — CPU profile, coverage | ✅ (CDP / hybrid) | ❌ (no BiDi equivalent) | ❌ |
| Trace — video | ✅ CDP screencast | opt-in polled (`-Dgatf.trace.videoPollFps=N`) | opt-in polled |
| Network mocking (`route`) | ✅ CDP `Fetch`, page target only (BiDi — every context incl. popups and cross-origin iframes — on a Grid, with `traceProtocol: bidi` or `-Dgatf.routeProtocol=bidi`) | ✅ BiDi `network.addIntercept` | where safaridriver supports BiDi interception |
| Record (Record view) | ✅ | ✅ (BiDi preload script) | — |
| Crawl (Crawl view) | ✅ | ✅ | — |

When tracing, GATF asks the session for a BiDi socket (`webSocketUrl: true`): always for non-Chromium browsers, and for Chrome/Edge when `traceProtocol` is `bidi` or `hybrid`. This works for local drivers and Selenium Grid alike. `metadata.json` in each trace lists the capabilities actually served.

## Selenium Grid on demand (G11, decision D-7)

GATF still manages **no** browser containers by default (D-1). Session resolution order:

1. `SELENIUM_REMOTE_URL` (env) or `-Dgatf.selenium.remoteUrl` → that Grid.
2. `GATF_WITH_GRID=<chrome|firefox|edge|true>` (env) or `-Dgatf.withGrid` → GATF ensures an **official** `selenium/standalone-<browser>` container on `:4444`, reusing any Grid already ready there. If GATF started it, it stops it on exit. `true` means "the session's browser".
3. Otherwise → a local driver via Selenium Manager. If that fails, the error lists the options: `gatf grid up`, `GATF_WITH_GRID`, or the raw `docker run`.

Explicit control (the container keeps running until `down`):
```
gatf grid up [chrome|firefox|edge]     # prints: export SELENIUM_REMOTE_URL=http://localhost:4444
gatf grid status
gatf grid down [browser]
```
- **Images:** `chrome` → `selenium/standalone-chrome`, or `selenium/standalone-chromium` on arm64 hosts (there is no Linux arm64 Google Chrome); `firefox` → `selenium/standalone-firefox` (multi-arch); `edge` → `selenium/standalone-edge` (amd64 only).
- **Knobs:** `-Dgatf.grid.port` (4444), `-Dgatf.grid.tag` (`latest`), `-Dgatf.grid.startTimeoutMs` (180000), `-Dgatf.docker.bin`. Pass them via `GATF_JAVA_OPTS` when using the `gatf` launcher.
- **Limitation:** one standalone container serves one browser. For mixed-browser suites run a multi-browser Grid (see D-1: `selenium/standalone-docker` dynamic Grid) and set `SELENIUM_REMOTE_URL`.

## Per-test browser contexts (H3)

Opt-in: `browserContexts: subtest` (default off = the shared context, unchanged). Each top-level subtest gets a new context in the running browser, so no new browser is launched per test; the context and its tabs are disposed when the subtest ends (pass, fail, timeout or stop), and any left over are disposed at script end. Contexts are created over WebDriver BiDi when the session has a socket (GATF asks Chrome, Edge and Firefox sessions for `webSocketUrl` when contexts are on), else over CDP on a browser-level connection (Chromium refuses `Target.*Context` on the page channel); a protocol refused at run time falls through to the other. `-Dgatf.contextProtocol=cdp|bidi` forces one.

| | Chrome / Edge (local or Grid) | Firefox | Safari / IE |
|---|---|---|---|
| Context create / dispose | ✅ BiDi `browser.createUserContext` / `removeUserContext`; CDP `Target.createBrowserContext` / `disposeBrowserContext` over the session's `se:cdp` endpoint as fallback | ✅ BiDi `browser.createUserContext` | ❌ shared context (logged once per script) |

What a context isolates:

| Feature | Per context? |
|---|---|
| Cookies, localStorage, sessionStorage, HTTP cache | yes (the browser's own partitioning); each context starts with an empty cache, which is not disabled inside it (the script tab's `setCacheDisabled` rides its DevTools session) |
| `state save/load`, `storageState` | yes; `storageState` is loaded into every new context (BiDi storage calls on that browser carry the context's `userContext`) |
| `route` / `unroute` | yes: BiDi interception scoped to the context's tab (needs `webSocketUrl`); with contexts on, a BiDi route in the script body is scoped to its own tab too. Tabs a subtest opens are not intercepted |
| `emulate` | yes: tab-level overrides; permission grants carry `browserContextId` (CDP) / `userContext` (BiDi) |
| `clock` | yes: page script plus preload on the context's tab |
| `tab` / `window` / `close others` | index only the context's tabs, its own first |
| `upload` | on Chromium uses the context tab's CDP |
| Trace network / console / video, `netapix` | CDP: default context only (they ride the script's DevTools session); BiDi: all contexts |

Only the driver that is current when a subtest starts gets a context (a script with several `open` browsers keeps the others shared). A subtest starts on `about:blank`; `beforeAll` / `afterAll` and the script body run in the default context. A refused command at run time falls back to the shared context with a run-log line for that subtest; a context that could not be disposed is retried at script end.

### Workers on contexts (`browserContexts: worker`, Δ154)

Opt-in (default off: every script launches and quits its own browser, unchanged). Each worker launches one browser on its first script and runs every script in a fresh context of it, disposed when the script ends (pass, fail, timeout or stop); the browser is quit at run end, relaunched every `-Dgatf.contextRecycle=N` scripts (default 50, `0` = never), and replaced when it crashes, leaks a context it cannot dispose, or a script asks for another browser or other options. `-Dgatf.browserContexts=worker` sets it for one run. The context isolates what a per-subtest context does (table below), and `storageState` (`loginOnce`: one login, before the pool starts) is loaded into each context.

- **worker and subtest:** contexts cannot nest, so under `worker` a script's subtests run inside the script's context (no context per subtest). Use `subtest` for a context per subtest in a browser per script.
- **Fallback:** where contexts are unsupported (matrix above: Safari, IE, a session without CDP or BiDi) scripts launch their own browser as today; the run log says so once. `remote-*` / `appium-*` configs are never pooled.
- **Traces:** a CDP trace is bound to the default context and would record nothing in a pooled context, so a run with tracing on and `traceProtocol` cdp/hybrid traces over WebDriver BiDi (hybrid loses CPU / coverage / video), or keeps a browser per script when the session has no BiDi; logged once either way.
- **Log:** at run end, `browserContexts: worker: N browser launch(es), M script(s) on a reused browser, …`.
