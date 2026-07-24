#!/usr/bin/env bash
#
# trace-e2e.sh -- end-to-end trace validation.
#
# Runs GATF with the FULL recorder suite enabled (network, console, snapshots,
# metrics, web-vitals, cpu, coverage, video) against a real Selenium Grid over a
# tiny UI scenario that fires a real click, then validates the produced
# trace.zip with tools/smoke/validate-trace.py.
#
# This is the run that de-risks all the CDP-facing recorder code at once -- the
# screencast ack loop, the Profiler/Performance command shapes, the per-action
# hook -- none of which can be exercised without a live browser. The dev
# container has no browser, so run this on a workstation / CI that has a Grid.
# It also validates the se:cdp acquisition path, which is the prerequisite for
# retiring the Docker override classes (finishing D-1).
#
# Usage:
#   SELENIUM_REMOTE_URL=http://localhost:4444 JAVA_HOME=/path/to/jdk \
#     tools/smoke/trace-e2e.sh --build
#
# To exercise the BiDi transport instead of CDP, add TRACE_PROTOCOL=bidi.
#
# Env:
#   SELENIUM_REMOTE_URL   required, e.g. http://localhost:4444 (official image / Grid)
#   JAVA_HOME             required, a full JDK (the generated-test javac)
#   GATF_JAR              explicit shaded jar (else auto-resolved / built)
#   BUILD=1 | --build     force the Maven build
#   TRACE_PROTOCOL        cdp (default) | bidi
#
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

BUILD="${BUILD:-0}"
for a in "$@"; do case "$a" in --build) BUILD=1 ;; -h|--help) sed -n '2,34p' "$0"; exit 0 ;; esac; done

log()  { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
note() { printf '   %s\n' "$*"; }

if [ -z "${SELENIUM_REMOTE_URL:-}" ]; then
  note "SELENIUM_REMOTE_URL is required (e.g. http://localhost:4444)."
  note "  docker run -d -p 4444:4444 --shm-size=2g selenium/standalone-chrome:latest"
  exit 2
fi
: "${JAVA_HOME:?trace-e2e needs a full JDK in JAVA_HOME (for the generated-test javac)}"

log "resolve / build the shaded fat jar"
resolve_jar() {
  if [ -n "${GATF_JAR:-}" ] && [ -f "${GATF_JAR}" ]; then echo "${GATF_JAR}"; return; fi
  # the shaded (runnable) main artifact -- NOT original-*.jar (pre-shade, no Main-Class)
  ls -t alldep-jar/target/gatf-alldep-jar-*.jar 2>/dev/null | grep -Ev '\-(sources|javadoc)\.jar$' | head -1 || true
}
GATF_JAR="$(resolve_jar || true)"
if [ -z "${GATF_JAR:-}" ] || [ "${BUILD}" = "1" ]; then
  command -v mvn >/dev/null 2>&1 || { note "mvn not found and no prebuilt jar -- install Maven or set GATF_JAR."; exit 1; }
  note "mvn -q -pl alldep-jar -am package -DskipTests"
  mvn -q -pl alldep-jar -am package -DskipTests
  GATF_JAR="$(resolve_jar || true)"
fi
[ -n "${GATF_JAR:-}" ] || { note "no shaded jar produced"; exit 1; }
GATF_JAR="$(cd "$(dirname "${GATF_JAR}")" && pwd)/$(basename "${GATF_JAR}")"   # absolute: we cd into WORK to run
note "jar: ${GATF_JAR}"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
mkdir -p "${WORK}/data" "${WORK}/out"
cp sample-project/tests/trace-smoke.sel "${WORK}/data/"

CONFIG="${WORK}/gatf-trace-config.xml"
cat > "${CONFIG}" <<EOF
<gatf-execute-config>
  <testCasesBasePath>${WORK}</testCasesBasePath>
  <testCaseDir>data</testCaseDir>
  <outFilesBasePath>${WORK}</outFilesBasePath>
  <outFilesDir>out</outFilesDir>
  <authEnabled>false</authEnabled>
  <numConcurrentExecutions>1</numConcurrentExecutions>
  <concurrentUserSimulationNum>0</concurrentUserSimulationNum>
  <enabled>true</enabled>
  <loadTestingEnabled>false</loadTestingEnabled>
  <isSeleniumExecutor>true</isSeleniumExecutor>
  <enableTrace>true</enableTrace>
  <traceProtocol>${TRACE_PROTOCOL:-cdp}</traceProtocol>
  <traceNetwork>true</traceNetwork>
  <traceConsole>true</traceConsole>
  <traceSnapshots>true</traceSnapshots>
  <traceMetrics>true</traceMetrics>
  <traceWebVitals>true</traceWebVitals>
  <traceCpu>true</traceCpu>
  <traceCoverage>true</traceCoverage>
  <traceVideo>true</traceVideo>
  <seleniumScripts>
    <string>trace-smoke.sel</string>
  </seleniumScripts>
  <seleniumDriverConfigs>
    <seleniumDriverConfig>
      <name>chrome</name>
      <arguments>--headless=new --no-sandbox --disable-dev-shm-usage</arguments>
    </seleniumDriverConfig>
  </seleniumDriverConfigs>
  <javaHome>${JAVA_HOME}/</javaHome>
  <gatfJarPath>${GATF_JAR}</gatfJarPath>
  <selDebugger>false</selDebugger>
</gatf-execute-config>
EOF

log "run (all recorders enabled, protocol=${TRACE_PROTOCOL:-cdp}) against ${SELENIUM_REMOTE_URL}"
note "workdir: ${WORK}"
# The -selenium executor resolves testCases/out AND the config file relative to the current
# dir (new File(testCasesBasePath, configFile)), so run from WORK and pass the config by name.
# SELENIUM_REMOTE_URL is already exported, so addWdm drives the official Grid via se:cdp.
if ! ( cd "${WORK}" && "${JAVA_HOME}/bin/java" -jar "${GATF_JAR}" -selenium "$(basename "${CONFIG}")" ); then
  note "GATF exited non-zero -- validating any trace.zip that was still produced."
fi

log "validate the produced trace.zip"
python3 "${REPO_ROOT}/tools/smoke/validate-trace.py" "${WORK}/out"
