#!/usr/bin/env bash
#
# run-smoke.sh -- Phase-0 smoke guardrail for GATF / seleasy.
#
# Stages, cheapest first. Each stage only runs if its prerequisites are present,
# so the same script works in CI (lint only) and on a full workstation (lint +
# build + real browser run against a Selenium endpoint).
#
#   1. LINT     (always) offline structural check of sample/*.sel via sel-lint.py
#               -- no JDK, no build, no browser. This is the CI gate.
#   2. BUILD    (opt-in) build the shaded fat jar with Maven, unless a jar is
#               already provided/resolved.  Enable with --build or BUILD=1.
#   3. RUN      (opt-in) drive GATF over the sample scripts end-to-end
#               (parse -> transpile -> compile -> headless run). Requires a full
#               JDK and, per decision D-1, a Selenium endpoint in
#               SELENIUM_REMOTE_URL (official selenium/standalone-* or a Grid).
#               Skipped with a clear notice when no endpoint is configured.
#
# Env / flags:
#   GATF_JAR=<path>            explicit shaded jar (else auto-resolved / built)
#   JAVA_HOME=<path>           full JDK (needed for the in-process javac in RUN)
#   SELENIUM_REMOTE_URL=<url>  e.g. http://localhost:4444  (enables RUN)
#   BUILD=1 | --build          force the Maven build stage
#   --strict                   fail the lint on unrecognised keywords too
#   --lint-only                stop after stage 1
#
# Exit non-zero if any enabled stage fails.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

STRICT=""
LINT_ONLY=0
BUILD="${BUILD:-0}"
for arg in "$@"; do
  case "$arg" in
    --strict)    STRICT="--strict" ;;
    --lint-only) LINT_ONLY=1 ;;
    --build)     BUILD=1 ;;
    -h|--help)   sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

log()  { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
note() { printf '   %s\n' "$*"; }

# ---------------------------------------------------------------------------
log "Stage 1/3  LINT  (offline structural check)"
python3 tools/smoke/sel-lint.py --regen ${STRICT} sample/*.sel
note "lint passed"

if [ "${LINT_ONLY}" = "1" ]; then
  log "done (--lint-only)"; exit 0
fi

# ---------------------------------------------------------------------------
log "Stage 2/3  BUILD  (resolve or build the shaded fat jar)"
resolve_jar() {
  if [ -n "${GATF_JAR:-}" ] && [ -f "${GATF_JAR}" ]; then echo "${GATF_JAR}"; return; fi
  # newest shaded jar under the module target dir
  local cand
  cand="$(ls -t alldep-jar/target/*-shaded.jar alldep-jar/target/*alldep*.jar 2>/dev/null | head -1 || true)"
  [ -n "${cand}" ] && echo "${cand}"
}

GATF_JAR="$(resolve_jar || true)"
if [ -z "${GATF_JAR:-}" ] || [ "${BUILD}" = "1" ]; then
  if command -v mvn >/dev/null 2>&1; then
    note "running: mvn -q -pl alldep-jar -am package -DskipTests"
    mvn -q -pl alldep-jar -am package -DskipTests
    GATF_JAR="$(resolve_jar || true)"
  else
    note "mvn not found and no prebuilt jar -- cannot build."
    note "install Maven or set GATF_JAR=<path to shaded jar>."
    exit 1
  fi
fi
[ -n "${GATF_JAR:-}" ] || { echo "no shaded jar produced" >&2; exit 1; }
note "using jar: ${GATF_JAR}"

# ---------------------------------------------------------------------------
log "Stage 3/3  RUN  (parse -> transpile -> compile -> headless run)"
if [ -z "${SELENIUM_REMOTE_URL:-}" ]; then
  note "SELENIUM_REMOTE_URL not set -- skipping the browser run stage."
  note "Per D-1, point this at an official Selenium endpoint to run headlessly, e.g.:"
  note "  docker run -d -p 4444:4444 --shm-size=2g selenium/standalone-chrome:latest"
  note "  SELENIUM_REMOTE_URL=http://localhost:4444 JAVA_HOME=/path/to/jdk \\"
  note "    tools/smoke/run-smoke.sh --build"
  log "done (lint + build only; run stage skipped)"
  exit 0
fi

: "${JAVA_HOME:?RUN stage needs a full JDK in JAVA_HOME (for the generated-test javac)}"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
mkdir -p "${WORK}/data" "${WORK}/out"
cp sample/*.sel "${WORK}/data/"

# scripts list for the config
SCRIPTS_XML=""
for f in "${WORK}"/data/*.sel; do
  SCRIPTS_XML="${SCRIPTS_XML}    <string>$(basename "${f}")</string>"$'\n'
done

CONFIG="${WORK}/gatf-smoke-config.xml"
cat > "${CONFIG}" <<EOF
<gatf-execute-config>
  <testCasesBasePath>${WORK}</testCasesBasePath>
  <testCaseDir>data</testCaseDir>
  <outFilesBasePath>${WORK}</outFilesBasePath>
  <outFilesDir>out</outFilesDir>
  <authEnabled>false</authEnabled>
  <numConcurrentExecutions>1</numConcurrentExecutions>
  <enabled>true</enabled>
  <loadTestingEnabled>false</loadTestingEnabled>
  <debugEnabled>false</debugEnabled>
  <isSeleniumExecutor>true</isSeleniumExecutor>
  <seleniumScripts>
${SCRIPTS_XML}  </seleniumScripts>
  <seleniumDriverConfigs>
    <seleniumDriverConfig>
      <name>chrome</name>
      <gridUrl>${SELENIUM_REMOTE_URL}</gridUrl>
      <arguments>--headless=new --no-sandbox --disable-dev-shm-usage</arguments>
    </seleniumDriverConfig>
  </seleniumDriverConfigs>
  <javaHome>${JAVA_HOME}/</javaHome>
  <gatfJarPath>${GATF_JAR}</gatfJarPath>
  <selDebugger>false</selDebugger>
</gatf-execute-config>
EOF

note "endpoint: ${SELENIUM_REMOTE_URL}"
note "workdir:  ${WORK}"
"${JAVA_HOME}/bin/java" -jar "${GATF_JAR}" "${CONFIG}"
note "run complete -- reports under ${WORK}/out"
log "done (lint + build + run)"
