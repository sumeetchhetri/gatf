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
