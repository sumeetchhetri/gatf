# Running GATF on Windows

Status: **supported.** GATF's server, executor, and Selenium/seleasy engine run on Windows
(10/11, Server 2019+) on a JDK 21 runtime. This note records the portability audit behind that
statement and the one real constraint (wrk/wrk2).

## Audit findings (what was already portable)

The codebase was already largely OS-neutral; the audit found no blocking Windows issues in the
server or executor paths:

- **Paths** — `java.io.File` and `File.separator` are used throughout; there are no hard-coded
  POSIX path separators in the request/execution paths.
- **Sub-process launches** — `GatfFunctionHandler.executeCmd(...)` already branches
  `cmd.exe /C <cmd>` on Windows vs `bash -c <cmd>` elsewhere via `SystemUtils.IS_OS_WINDOWS`.
- **Native libs** — the Unix-only `jna.library.path` (`/usr/local/lib:/usr/lib`) is already guarded
  by `if(!SystemUtils.IS_OS_WINDOWS)`, so it is never set on Windows.
- **Temp/space** — report/trace writing uses the JVM temp dir and the configured output paths, not
  `/tmp`.

## The one real gap — external load-testing binaries — now handled

The load-test executors shell out to `wrk`, `wrk2`, `vegeta`, and `autocannon`. Previously these
were taken verbatim from the configured path with no OS awareness. `PerfToolProvisioner`
(`com.gatf.executor.ext`) now resolves each tool per-OS, adding the correct executable suffix and
auto-provisioning where a cross-platform distribution exists:

| tool       | Windows behaviour                                                                 |
|------------|-----------------------------------------------------------------------------------|
| vegeta     | auto-downloaded — GitHub release `vegeta_<ver>_windows_<arch>.zip`, unpacked to `vegeta.exe` |
| autocannon | auto-installed — `npm i --prefix <cache> autocannon@<ver>` → `autocannon.cmd` (needs Node.js) |
| wrk / wrk2 | **not available natively** — C programs with no Windows build (see below)          |

Resolution order for every tool: configured path → tools cache → system PATH (`where`) →
download/install. Configured paths always win, so existing setups are unchanged.

### wrk / wrk2 on Windows

`wrk` and `wrk2` are epoll/kqueue-based C programs with no official Windows binaries and no
buildable Windows target. Options, in order of preference:

1. **Use vegeta or autocannon** for load tests on Windows — both are auto-provisioned and cover the
   same connections/duration/rate parameters GATF passes.
2. **Run GATF (or just the load step) under WSL2**, where `apt-get install wrk` works and
   `PerfToolProvisioner` will also build `wrk`/`wrk2` from source if a toolchain is present.

If a `wrk`/`wrk2` test is requested on native Windows, the provisioner logs a clear diagnostic and
returns no binary rather than failing silently.

## Pre-provisioning (optional, good for CI / locked-down hosts)

Download/install the cross-platform tools ahead of time:

```bash
java -cp gatf.jar com.gatf.executor.ext.PerfToolProvisioner vegeta autocannon
```

Cache dir: `-Dgatf.tools.dir=<path>` or `%GATF_TOOLS_DIR%`, else
`%USERPROFILE%\.gatf\tools\win-<arch>`. Pin versions with `-Dgatf.vegeta.version` /
`-Dgatf.autocannon.version`.

## How to validate on a Windows box

1. `java -version` → 21.x.
2. Start the config server / run a suite as usual (same JAR, same `gatf-config.json`).
3. Load tools: `java -cp gatf.jar com.gatf.executor.ext.PerfToolProvisioner` → confirms
   `vegeta -> ...\vegeta.exe` and `autocannon -> ...\autocannon.cmd`.
4. Run a vegeta- or autocannon-typed perf test; confirm the process launches and a report is written.
5. Portal crawler (Phase 12): `java -cp gatf.jar com.gatf.crawler.PortalCrawler --seed http://host/`
   → writes `crawl-suite.sel` + `crawl-coverage.json` (pure JVM + HTTP, no OS-specific deps).
