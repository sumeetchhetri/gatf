#!/usr/bin/env bash
#    Copyright 2013-2026, Sumeet Chhetri
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
# webkit-lane.sh <safari|webkitgtk> -- CI glue for .github/workflows/ci-webkit.yml (Δ159).
#
# Runs a small, explicit, CDP-free subset of GATF's own scripts on a WebKit engine:
#   1. functional (gates the job): SUBSET below, trace off, against a local GATF /app server only
#   2. capability probe (report-only): trace-smoke.sel (example.com) with every recorder on, plus a
#      never-matching `route` line so GATF asks the driver for a WebDriver BiDi socket (webSocketUrl).
#      GATF traces only with CDP or a live BiDi socket; trace-capabilities.mjs prints what was served,
#      or that no trace was recorded at all.
# Scripts are copied, never edited in place: the first `open chrome` becomes `open <key>`.
#
#   safari     macOS: local safaridriver (run `sudo safaridriver --enable` first). key=safari.
#   webkitgtk  Linux, EXPERIMENTAL: WebKitWebDriver + MiniBrowser under xvfb. GATF has no webkitgtk
#              key, so the scripts run as `open firefox-dkr` through tools/ci/webkit-caps-shim.mjs, which
#              rewrites the new-session capabilities for WebKitWebDriver (SELENIUM_REMOTE_URL -> shim).
#
# bash 3.2-safe (macOS runner default). The jar comes from alldep-jar/target, the same one
# e2e/start-gatf-server.sh serves. Env: JAVA_HOME (a JDK), WEBKIT_LANE_DIR (default: a new temp dir; CI sets it to upload)
set -euo pipefail
LANE="${1:-}"
# webkitgtk uses firefox-dkr: a bare `firefox` builds a local FirefoxDriver and ignores SELENIUM_REMOTE_URL;
# the -dkr form goes through BrowserDrivers.create, which honours it (addWdm strips the suffix).
case "${LANE}" in safari) KEY=safari ;; webkitgtk) KEY=firefox-dkr ;; *) echo "usage: $0 <safari|webkitgtk>" >&2; exit 2 ;; esac
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${HERE}/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(command -v java)")")}"
GATF_JAR="$(ls -t "${REPO_ROOT}"/alldep-jar/target/gatf-alldep-jar-*.jar 2>/dev/null | grep -Ev '\-(sources|javadoc)\.jar$' | head -1 || true)"
[ -n "${GATF_JAR}" ] && [ -f "${GATF_JAR}" ] || { echo "No shaded jar in alldep-jar/target (mvn -q -pl alldep-jar -am package -DskipTests)." >&2; exit 1; }
GATF_JAR="$(cd "$(dirname "${GATF_JAR}")" && pwd)/$(basename "${GATF_JAR}")"
WORK="${WEBKIT_LANE_DIR:-$(mktemp -d)}"
APP_PORT=9223

# The explicit subset: no route / emulate / printpdf / CDP-only recorders. Order = run order.
SUBSET=(app-console.sel app-console-semantic.sel)

PIDS=()
cleanup() { for p in ${PIDS[@]+"${PIDS[@]}"}; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT
log() { printf '\n== %s ==\n' "$*"; }

[ -z "$(ls -A "${WORK}" 2>/dev/null)" ] || { echo "WEBKIT_LANE_DIR ${WORK} is not empty" >&2; exit 2; }
mkdir -p "${WORK}/func/tests" "${WORK}/probe/tests"
copy_sel() {   # <src> <dest dir>: first `open chrome` -> `open ${KEY}`
  awk -v k="${KEY}" '!d && /^open chrome/ { sub(/^open chrome/, "open " k); d=1 } { print }' "$1" > "$2/$(basename "$1")"
  grep -q "^open ${KEY}" "$2/$(basename "$1")" || { echo "copy_sel: no 'open chrome' rewritten in $1" >&2; exit 1; }
}
copy_sel "${REPO_ROOT}/dogfood/tests/app-console.sel" "${WORK}/func/tests"
copy_sel "${REPO_ROOT}/dogfood/tests/app-console-semantic.sel" "${WORK}/func/tests"
copy_sel "${REPO_ROOT}/sample-project/tests/trace-smoke.sel" "${WORK}/probe/tests"
printf '\nroute **/__gatf_webkit_probe_never__ abort\n' >> "${WORK}/probe/tests/trace-smoke.sel"
# 127.0.0.1, not localhost: the /app server binds IPv4 only and some WebKit builds try ::1 first
echo "appUrl=http://127.0.0.1:${APP_PORT}/app/" > "${WORK}/func/tests/app.props"

if [ "${LANE}" = "safari" ]; then
  log "safari: $(sw_vers -productVersion 2>/dev/null || echo '?') / $(safaridriver --version 2>/dev/null || echo 'safaridriver not found')"
  unset SELENIUM_REMOTE_URL   # local SafariDriver: since Δ145d Safari uses a Grid only with an explicit SELENIUM_REMOTE_URL
else
  command -v WebKitWebDriver >/dev/null || { echo "WebKitWebDriver not found (apt-get install webkit2gtk-driver)." >&2; exit 1; }
  MINI="${WEBKIT_MINIBROWSER:-$(find /usr/lib /usr/libexec -type f -name MiniBrowser -path '*webkit2gtk-4.1*' 2>/dev/null | head -1 || true)}"
  [ -n "${MINI}" ] || { echo "MiniBrowser not found under /usr/lib or /usr/libexec (set WEBKIT_MINIBROWSER)." >&2; exit 1; }
  log "webkitgtk: driver=$(command -v WebKitWebDriver) browser=${MINI}"
  WebKitWebDriver --port=4445 > "${WORK}/webkitwebdriver.log" 2>&1 & PIDS+=($!)
  node "${HERE}/webkit-caps-shim.mjs" 4446 4445 "${MINI}" > "${WORK}/webkit-shim.log" 2>&1 & PIDS+=($!)
  for _ in $(seq 1 15); do curl -sf http://127.0.0.1:4446/status >/dev/null && break; sleep 1; done
  curl -sf http://127.0.0.1:4446/status >/dev/null \
    || { echo "WebKitWebDriver/shim not ready"; tail -20 "${WORK}/webkitwebdriver.log" "${WORK}/webkit-shim.log"; exit 1; }
  export SELENIUM_REMOTE_URL="http://127.0.0.1:4446"
fi

log "GATF /app server on :${APP_PORT}"
( unset SELENIUM_REMOTE_URL GATF_SELENIUM_GRID; exec bash "${REPO_ROOT}/e2e/start-gatf-server.sh" "${APP_PORT}" ) > "${WORK}/gatf-server.log" 2>&1 & SPID=$!; PIDS+=("${SPID}")
for _ in $(seq 1 90); do curl -sf "http://127.0.0.1:${APP_PORT}/app/" >/dev/null && break; kill -0 "${SPID}" 2>/dev/null || break; sleep 2; done
curl -sf "http://127.0.0.1:${APP_PORT}/app/" >/dev/null || { echo "GATF /app server did not come up"; tail -50 "${WORK}/gatf-server.log"; exit 1; }

gen_config() {   # <dir> <trace true|false> <scripts...>
  local dir="$1" trace="$2"; shift 2
  {
    echo "<gatf-execute-config>"
    echo "  <testCasesBasePath>${dir}</testCasesBasePath><testCaseDir>tests</testCaseDir>"
    echo "  <outFilesBasePath>${dir}</outFilesBasePath><outFilesDir>out</outFilesDir>"
    echo "  <authEnabled>false</authEnabled><numConcurrentExecutions>1</numConcurrentExecutions>"
    echo "  <concurrentUserSimulationNum>0</concurrentUserSimulationNum><enabled>true</enabled>"
    echo "  <loadTestingEnabled>false</loadTestingEnabled><isSeleniumExecutor>true</isSeleniumExecutor>"
    echo "  <autoWait>true</autoWait><webFirstAssertions>true</webFirstAssertions><strictLocators>true</strictLocators>"
    echo "  <enableTrace>${trace}</enableTrace>"
    if [ "${trace}" = "true" ]; then
      echo "  <traceProtocol>bidi</traceProtocol><traceNetwork>true</traceNetwork><traceConsole>true</traceConsole>"
      echo "  <traceSnapshots>true</traceSnapshots><traceMetrics>true</traceMetrics><traceWebVitals>true</traceWebVitals>"
      echo "  <traceCpu>true</traceCpu><traceCoverage>true</traceCoverage><traceVideo>true</traceVideo>"
    fi
    echo "  <seleniumScripts>"
    for s in "$@"; do echo "    <string>${s}</string>"; done
    echo "  </seleniumScripts>"
    echo "  <seleniumDriverConfigs><seleniumDriverConfig><name>${KEY%-dkr}</name></seleniumDriverConfig></seleniumDriverConfigs>"
    echo "  <javaHome>${JAVA_HOME}/</javaHome><gatfJarPath>${GATF_JAR}</gatfJarPath><selDebugger>false</selDebugger>"
    echo "</gatf-execute-config>"
  } > "${dir}/gatf-config.gen.xml"
}

echo "lane dir: ${WORK}"
log "functional subset (${LANE}, open ${KEY}): ${SUBSET[*]}"
gen_config "${WORK}/func" false "${SUBSET[@]}"
set +e
( cd "${WORK}/func" && "${JAVA_HOME}/bin/java" -jar "${GATF_JAR}" -selenium gatf-config.gen.xml ) 2>&1 | tee "${WORK}/func.log"
RC=${PIPESTATUS[0]}
set -e
echo "functional subset: exit ${RC}"
if [ "${LANE}" = "webkitgtk" ] && ! grep -q "new session ->" "${WORK}/webkit-shim.log"; then
  echo "functional subset: no session reached WebKitWebDriver (the run did not use WebKit) -> failing the lane"
  RC=1
fi

log "capability probe (report-only): trace-smoke.sel, every recorder on"
gen_config "${WORK}/probe" true trace-smoke.sel
set +e
( cd "${WORK}/probe" && "${JAVA_HOME}/bin/java" -jar "${GATF_JAR}" -selenium gatf-config.gen.xml ) > "${WORK}/probe.log" 2>&1
PRC=$?
node "${REPO_ROOT}/tools/ci/trace-capabilities.mjs" "${WORK}/probe/out" | tee "${WORK}/capabilities.txt"
set -e
echo "capability probe: GATF exit ${PRC} (not gating)"
exit "${RC}"
