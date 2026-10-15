# Load tools (perfConfig.type: wperfk · vegeta · autocannon · wrk · wrk2)

**wperfk is GATF's default load engine** ([github.com/sumeetchhetri/wperfk](https://github.com/sumeetchhetri/wperfk)): wrk/wrk2-compatible flags, output and Lua scripts, on every OS, downloaded and checksum-verified on first use with no setup. Use it unless you need something the others do differently. Flow → load (`gatf import flow`, the Trace Viewer's **⚡ Load test this flow**) writes `perfConfig.type` `wperfk` unless you pass `--engine`.

GATF picks, per OS, the option with the least setup. Explicit host paths always win.

| type | Resolution order | Windows | macOS (Intel / Apple Silicon) | Linux (x64 / arm64) |
|---|---|---|---|---|
| **wperfk** (default) | `wperfkPath` → cache (`<tools>/wperfk/wperfk`) → PATH → auto-download (release [v0.1-alpha](https://github.com/sumeetchhetri/wperfk/releases/tag/v0.1-alpha), SHA-256 checked) → **vegeta fallback** | ✅ auto (x64, arm64) | ✅ auto (Intel, Apple Silicon) | ✅ auto (static x64, arm64) |
| **vegeta** | `vegetaPath` → cache → PATH → auto-download (official release, SHA-256 checked) | ✅ auto | ✅ auto | ✅ auto |
| **autocannon** | `autocannonPath` → Node (`nodePath` → PATH → portable Node LTS from nodejs.org, SHA-256 checked) + `npm i autocannon` into the cache | ✅ auto | ✅ auto | ✅ auto |
| **wrk** | `wrkPath` → cache → PATH → `docker exec` into **gatf-loadgen** → **vegeta fallback** | Docker, else vegeta | brew/Docker, else vegeta | apt/Docker, else vegeta |
| **wrk2** | `wrk2Path` → cache → PATH → `docker exec` into **gatf-loadgen** (amd64 only) → **vegeta fallback** | Docker (x64), else vegeta | Intel: Docker; Apple Silicon: vegeta | x64: Docker; arm64: vegeta |

Tools are cached under `~/.gatf/tools/<os>-<arch>/` (`-Dgatf.tools.dir` / `$GATF_TOOLS_DIR`). Set them up ahead of time (CI images, before going offline) and print what each type resolves to on a machine:
`gatf tools install` (all; or name them, e.g. `gatf tools install wperfk`), or `java -cp gatf-alldep-jar-<ver>.jar com.gatf.executor.ext.PerfToolProvisioner [tool…]`. The installers offer the same step (`--with-tools wperfk,vegeta,autocannon`).

## wperfk releases
Prebuilt binaries are published from tag **v0.1-alpha** (version `0.1-alpha`) on [github.com/sumeetchhetri/wperfk/releases](https://github.com/sumeetchhetri/wperfk/releases/tag/v0.1-alpha):
- Linux amd64 and arm64 (static, no dependencies);
- macOS amd64, arm64 and universal;
- Windows amd64 and arm64;
- FreeBSD amd64.

File names use the version without the tag's `v`:
- archives: `wperfk_0.1-alpha_<os>_<arch>.tar.gz` (`.zip` on Windows), where `<os>_<arch>` is `linux_amd64`, `linux_arm64`, `darwin_amd64`, `darwin_arm64`, `darwin_universal`, `windows_amd64`, `windows_arm64` or `freebsd_amd64`;
- checksums: `wperfk_0.1-alpha_checksums.txt`.

Download URLs keep the `v`: `https://github.com/sumeetchhetri/wperfk/releases/download/v0.1-alpha/<file>`. It's a pre-release, so `/releases/latest` doesn't point to it: always use the tag.

GATF downloads the archive for its OS/arch, checks it against the checksums file (a mismatch aborts; the test then runs on vegeta with a `NOTICE:`), and unpacks it into the tools cache. Pin another release with `-Dgatf.wperfk.version=<ver>` (the version without the `v`). FreeBSD arm64 has no build: there, and on any machine that cannot reach GitHub, a wperfk test falls back to vegeta unless `wperfkPath` or `PATH` supplies one.

### Offline / air-gapped install
Do by hand what GATF does on first use: check the archive and unpack it into the tools cache.

```bash
# Linux / macOS. Set PLAT (release name) and CACHE (GATF's tools dir name) for your machine:
#   Linux x64: linux_amd64 / linux-amd64   Linux arm64: linux_arm64 / linux-arm64
#   macOS Intel: darwin_amd64 / mac-amd64  Apple Silicon: darwin_arm64 / mac-arm64
TAG=v0.1-alpha VER=0.1-alpha PLAT=linux_amd64 CACHE=linux-amd64
base=https://github.com/sumeetchhetri/wperfk/releases/download/$TAG
curl -fLO "$base/wperfk_${VER}_${PLAT}.tar.gz" -fLO "$base/wperfk_${VER}_checksums.txt"
sha256sum -c --ignore-missing "wperfk_${VER}_checksums.txt"        # macOS: shasum -a 256 -c --ignore-missing …
d=~/.gatf/tools/$CACHE/wperfk && mkdir -p "$d" && tar xzf "wperfk_${VER}_${PLAT}.tar.gz" -C "$d"
xattr -d com.apple.quarantine "$d/wperfk" 2>/dev/null || true      # macOS: the binary is not notarized
"$d/wperfk" --version                                               # wperfk 0.1-alpha [...]
```

```powershell
# Windows x64 (on arm64: windows_arm64 / win-arm64)
$Tag = 'v0.1-alpha'; $Ver = '0.1-alpha'; $Plat = 'windows_amd64'; $Cache = 'win-amd64'
$base = "https://github.com/sumeetchhetri/wperfk/releases/download/$Tag"
Invoke-WebRequest "$base/wperfk_${Ver}_${Plat}.zip" -OutFile "wperfk_${Ver}_${Plat}.zip"
Invoke-WebRequest "$base/wperfk_${Ver}_checksums.txt" -OutFile "wperfk_${Ver}_checksums.txt"
$want = (Select-String -SimpleMatch "wperfk_${Ver}_${Plat}.zip" "wperfk_${Ver}_checksums.txt").Line.Split(' ')[0]
if ((Get-FileHash "wperfk_${Ver}_${Plat}.zip" -Algorithm SHA256).Hash -ne $want) { throw 'checksum mismatch' }
$d = "$HOME\.gatf\tools\$Cache\wperfk"; New-Item -ItemType Directory -Force $d | Out-Null
Expand-Archive "wperfk_${Ver}_${Plat}.zip" -DestinationPath $d -Force
& "$d\wperfk.exe" --version
```

Alternatively, put `wperfk` on `PATH` or set `wperfkPath`. `PerfToolProvisioner` (above) prints which binary GATF will use.

## Reduced capabilities (read before choosing)
- **vegeta / autocannon have no Lua.** Method, headers and body come from the test case (`content` / `contentFile`; for vegeta also `perfConfig.filePath` as the body file).
- **wperfk → vegeta fallback** happens only when no wperfk binary can be found or downloaded (see the table).
- **wrk → vegeta fallback** runs an open-loop max-rate attack (`-rate=0 -max-workers=<connections>`), which approximates, but is not, wrk's closed-loop max throughput. **wrk2 → vegeta** runs at a constant `-rate` (same model as wrk2 `-R`; default 1000). A fallback run carries a `NOTICE:` at the top of the result, `tool=vegeta` + `fallback_from` in its stats, and ignores `perfConfig.filePath` (a Lua script).
- **wperfk** (default) gets wrk's flags and Lua script (`-c -d -t [--timeout] [--latency] [-s]`), plus `-R <rate>` only when `perfConfig.rate > 0` (wrk2's constant-rate mode; without it, wrk's closed loop). Its output is parsed as wrk's. **wperfk → vegeta** runs at `-rate` when a rate is set, else like wrk.
- Each tool keeps its own stats (vegeta report JSON, autocannon `--json`, wrk text parse). Every result map (`perfResult`, in the report JSON) also gets the same common keys, whatever the tool: `tool`, `total_requests`, `rps`, `lat_avg_ms`, `lat_p50_ms`, `lat_p90_ms`, `lat_p99_ms`, `lat_max_ms`, `transport_errors`, `non_2xx`. A key the tool does not report is left out. `non_2xx` counts statuses outside 2xx/3xx for every tool, and `rps` counts all responses (vegeta: requests over attack + wait time). Tools still measure differently (open vs closed loop), so compare numbers within one tool.

## Your own engine (perf-engine SPI)
`perfConfig.type` resolves through a registry: the five built-ins (wperfk, vegeta, autocannon, wrk, wrk2), then engines in `plugins/*.jar` (the same folder and class loader as step plugins). To add one:
1. Implement `com.gatf.executor.perf.PerfEngine`. `name()` is the type value (`[a-z][a-z0-9_-]*`). `execute(context, testCase, report, single)` reads `testCase.getPerfConfig()` and the request, adds one map per target to `report.getPerfResult()`, and sets the status and response text.
2. List the class in `META-INF/services/com.gatf.executor.perf.PerfEngine` and drop the jar into `plugins/`.

A built-in name cannot be replaced. An unknown type fails the test case with the list of valid names. GATF adds the common keys above to whatever your engine reports, without overwriting keys it already set.

## Docker (gatf-loadgen)
When Docker is running and wrk/wrk2 has no host binary, GATF `docker exec`s into the container named by `perfToolContainer` / `$GATF_PERF_CONTAINER` (default `gatf-loadgen`). If it doesn't exist GATF builds `gatf/loadgen:1` once from the Dockerfile embedded in the jar (a few minutes) and starts it — `--network host` on Linux. Lua scripts are copied in with `docker cp`; `localhost` targets become `host.docker.internal` unless the container uses the host network.
- Opt out: `-Dgatf.loadgen.autostart=false` (use an existing container only) or `-Dgatf.loadgen.docker=false` (never use Docker). Image override: `-Dgatf.loadgen.image` / `$GATF_LOADGEN_IMAGE`.
- Docker Desktop (macOS/Windows) routes load through its VM: fine for functional load checks, not for headline numbers — use a Linux host with `--network host` for those.
- Same container from a terminal: `. docker/loadgen-aliases.sh` (bash/zsh) or `. .\docker\loadgen-aliases.ps1` (PowerShell) defines `wrk`, `wrk2`, `vegeta` functions that `docker exec` into it.

## Config keys
`vegetaPath`, `autocannonPath`, `nodePath`, `wrkPath`, `wrk2Path`, `wperfkPath`, `perfToolContainer` (Config view → executor). System properties: `gatf.wperfk.version` (0.1-alpha), `gatf.vegeta.version` (12.12.0), `gatf.autocannon.version` (7), `gatf.node.version` / `gatf.node.baseUrl` (mirror, air-gapped), `gatf.docker.bin`.
