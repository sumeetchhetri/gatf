# Selenium overrides & browser-infra version strategy

Status: Phase 0 (hygiene/guardrails). This documents the pinned Selenium/CDP
versions, the Selenium classpath overrides GATF ships, why they exist, and the
migration path ratified in decisions D-1 and D-2. It is a companion to the
Phase-0 delta; nothing here changes runtime behaviour.

## Pinned versions

| Thing | Value | Where |
|-------|-------|-------|
| Selenium | `4.48.0` | `pom.xml` → `<selenium.version>` |
| DevTools/CDP binding | `latest` (Δ163k; was `v150`) | imports `org.openqa.selenium.devtools.latest.*` (`selenium-devtools-latest`, a `selenium-java` dependency) |
| Appium client | `io.appium:java-client` | `pom.xml` |
| Java target | `11` | build target (compiles under a newer JDK) |

The CDP domain classes (`network`, `page`, `fetch`, `log`, `dom`, `security`,
`target`, …) used to be **version-pinned** to a `devtools.vNNN` package, which each Selenium
release drops after ~3 Chrome versions, so every Selenium bump meant re-pointing every import.
**Δ163k** moved them to `devtools.latest`, the package Selenium republishes in every release
with its newest CDP binding. A Selenium bump is now the `selenium.version` property alone,
unless a CDP command GATF calls changed its signature (the compiler says so). Δ163k checked
v150 → latest(v152) for every class in the domains GATF uses: only additions, plus two changed
signatures (`DOM.forceShowPopover`, `Page.createIsolatedWorld`) GATF does not call. Decision
**D-2** (BiDi behind the same interface) still removes the CDP dependency over time.

## The overrides GATF ships (Selenium classpath shadowing)

GATF places same-package copies of a few Selenium classes on the classpath so
they win over the jar versions. All exist for **one** reason: when Selenium runs
against a browser inside a Docker container, the container advertises a
container-internal address (`localhost:<port>` / the container Docker IP) for
its HTTP and CDP endpoints, which is not reachable from where GATF runs. The
overrides rewrite that advertised host to a reachable `host:port`.

| Override (under `alldep-jar/src/main/java/…`) | Role |
|---|---|
| `org/openqa/selenium/devtools/CdpEndpointFinder.java` | rewrites the CDP `webSocketDebuggerUrl` / `debuggerAddress` host |
| `org/openqa/selenium/remote/http/jdk/JdkHttpMessages.java` | rewrites outgoing `http://localhost:<port>` request URLs |
| `org/openqa/selenium/remote/http/jdk/JdkHttpClient.java` | seeds/propagates the rewrite state across worker threads |
| `org/openqa/selenium/remote/service/DriverCommandExecutor.java` | same, around driver-service command execution |
| `org/openqa/selenium/remote/RemoteWebDriver.java` | full-class copy carried for the above to compose against |
| `org/jdiscript/util/VMLauncher.java` | JDI launcher tweak used by `gatfjdb` (the seleasy debugger) |
| `io/github/bonigarcia/wdm/*` | WebDriverManager copies (driver resolution / docker) |

### The rewrite mechanism (what D-1 consolidates)

The rewrite is driven by a single thread-local:

    com.gatf.selenium.SeleniumTest.IN_DOCKER   // ImmutableTriple<Boolean, String, String[]>
                                               //   left   = running in docker?
                                               //   middle = reachable host[:port] to swap in
                                               //   right  = aux

It is consulted at three sites — `CdpEndpointFinder` (CDP url), `JdkHttpMessages`
(HTTP url), and `JdkHttpClient`/`DriverCommandExecutor` (propagation). Together
with the old `dyn-proxy.js` + Selenoid image machinery, this is the
"dyn-proxy + address-override dance".

**D-1** replaces the whole dance with a single, config-driven `se:cdp` host
rewrite in one place: given a `gridUrl`, GATF connects to an official Selenium
endpoint and applies one internal-IP → reachable-host mapping. GATF then builds
no browser images and orchestrates no browser containers.

## Migration path (D-1 / D-2) and what Phase 0 does

Phase 0 (this delta) — **remove the dead browser-infra artifacts only**, since
they are unreferenced by CI and superseded by "official images only":

- deleted: `build_browsers.sh`, `build_selenoid.sh`, `dyn-proxy.js`,
  `Dockerfile-selenoid-{browser,chrome,firefox,opera}.tmpl`

Phase 0 explicitly **keeps**:

- the Selenium override classes above — they still back the current CDP transport
  and are removed only **after** they are repointed (Phase 1, per D-1);
- the custom browser-image Dockerfiles (`Dockerfile-Gatf-Chrome`,
  `Dockerfile-Gatf-FF`, `Dockerfile-Gatf-FF-Chrome-VNC`) and the `ci-release.yml`
  image build/push — retiring these is the deployment-coupled half of D-1 and is
  a dedicated change (tracked below), not hygiene.

Later phases:

- **Phase 1** — introduce the protocol-neutral `BrowserControl` transport
  interface; port current CDP usage behind `CdpTransport`; add the single
  config-driven `se:cdp` host rewrite; then delete the override classes.
- **Phase 2+** — implement `BiDiTransport` incrementally; flip per-capability via
  config as BiDi parity lands, removing the `devtools.vNNN` pinning.

### Tracked follow-ups (not Phase 0)

- Retire the custom browser images + `ci-release.yml` image push; document the
  Selenium dynamic-Grid deployment recipe from D-1 instead.
- Remove the override classes once the transport abstraction repoints CDP.
