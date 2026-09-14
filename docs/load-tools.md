# Load tools (perfConfig.type: vegeta · autocannon · wrk · wrk2)

GATF picks, per OS, the option with the least setup. Explicit host paths always win.

| type | Resolution order | Windows | macOS (Intel / Apple Silicon) | Linux (x64 / arm64) |
|---|---|---|---|---|
| **vegeta** (default) | `vegetaPath` → cache → PATH → auto-download (official release, SHA-256 checked) | ✅ auto | ✅ auto | ✅ auto |
| **autocannon** (default) | `autocannonPath` → Node (`nodePath` → PATH → portable Node LTS from nodejs.org, SHA-256 checked) + `npm i autocannon` into the cache | ✅ auto | ✅ auto | ✅ auto |
| **wrk** | `wrkPath` → cache → PATH → `docker exec` into **gatf-loadgen** → **vegeta fallback** | Docker, else vegeta | brew/Docker, else vegeta | apt/Docker, else vegeta |
| **wrk2** | `wrk2Path` → cache → PATH → `docker exec` into **gatf-loadgen** (amd64 only) → **vegeta fallback** | Docker (x64), else vegeta | Intel: Docker; Apple Silicon: vegeta | x64: Docker; arm64: vegeta |

Tools are cached under `~/.gatf/tools/<os>-<arch>/` (`-Dgatf.tools.dir` / `$GATF_TOOLS_DIR`). Print what each type resolves to on a machine:
`java -cp gatf-alldep-jar-<ver>.jar com.gatf.executor.ext.PerfToolProvisioner`

## Reduced capabilities (read before choosing)
- **vegeta / autocannon have no Lua.** Method, headers and body come from the test case (`content` / `contentFile`; for vegeta also `perfConfig.filePath` as the body file).
- **wrk → vegeta fallback** runs an open-loop max-rate attack (`-rate=0 -max-workers=<connections>`), which approximates, but is not, wrk's closed-loop max throughput. **wrk2 → vegeta** runs at a constant `-rate` (same model as wrk2 `-R`; default 1000). A fallback run carries a `NOTICE:` at the top of the result, `tool=vegeta` + `fallback_from` in its stats, and ignores `perfConfig.filePath` (a Lua script).
- Stats differ per tool: vegeta report JSON, autocannon `--json`, wrk text parse. Compare runs of the same tool.

## Docker (gatf-loadgen)
When Docker is running and wrk/wrk2 has no host binary, GATF `docker exec`s into the container named by `perfToolContainer` / `$GATF_PERF_CONTAINER` (default `gatf-loadgen`). If it doesn't exist GATF builds `gatf/loadgen:1` once from the Dockerfile embedded in the jar (a few minutes) and starts it — `--network host` on Linux. Lua scripts are copied in with `docker cp`; `localhost` targets become `host.docker.internal` unless the container uses the host network.
- Opt out: `-Dgatf.loadgen.autostart=false` (use an existing container only) or `-Dgatf.loadgen.docker=false` (never use Docker). Image override: `-Dgatf.loadgen.image` / `$GATF_LOADGEN_IMAGE`.
- Docker Desktop (macOS/Windows) routes load through its VM: fine for functional load checks, not for headline numbers — use a Linux host with `--network host` for those.
- Same container from a terminal: `. docker/loadgen-aliases.sh` (bash/zsh) or `. .\docker\loadgen-aliases.ps1` (PowerShell) defines `wrk`, `wrk2`, `vegeta` functions that `docker exec` into it.

## Config keys
`vegetaPath`, `autocannonPath`, `nodePath`, `wrkPath`, `wrk2Path`, `perfToolContainer` (Config view → executor). System properties: `gatf.vegeta.version` (12.12.0), `gatf.autocannon.version` (7), `gatf.node.version` / `gatf.node.baseUrl` (mirror, air-gapped), `gatf.docker.bin`.
