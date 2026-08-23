#!/usr/bin/env bash
#
# start-gatf-server.sh <port> -- launch the GATF config-tool server (serving the built /app SPA)
# against a scratch copy of sample-project, for the Playwright e2e webServer.
#
# Requires the shaded jar built first:  mvn -q -pl alldep-jar -am package -DskipTests
# (jar resolution mirrors tools/smoke/trace-e2e.sh.)
#
set -euo pipefail
PORT="${1:-9223}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

JAR="$(ls -t "${REPO_ROOT}"/alldep-jar/target/gatf-alldep-jar-*.jar 2>/dev/null | grep -Ev '\-(sources|javadoc)\.jar$' | head -1 || true)"
if [ -z "${JAR:-}" ]; then
  echo "No shaded jar found. Build it first:  mvn -q -pl alldep-jar -am package -DskipTests" >&2
  exit 1
fi
JAR="$(cd "$(dirname "${JAR}")" && pwd)/$(basename "${JAR}")"   # absolute (we cd into the scratch project)

PROJ="$(mktemp -d)"
cp -r "${REPO_ROOT}/sample-project/." "${PROJ}/"
cd "${PROJ}"
echo "GATF e2e server: jar=${JAR} proj=${PROJ} port=${PORT}"
# The GATF server uses SELENIUM_REMOTE_URL for selenium runs (T4c). Take it from GATF_SELENIUM_GRID so
# it reaches GATF *without* Playwright seeing it (Playwright reserves SELENIUM_REMOTE_URL to route its
# own browser through a Grid). JAVA_HOME is inherited as-is.
if [ -n "${GATF_SELENIUM_GRID:-}" ]; then
  export SELENIUM_REMOTE_URL="${GATF_SELENIUM_GRID}"
  echo "GATF e2e: Selenium Grid = ${SELENIUM_REMOTE_URL}"
fi
# main(): -configtool <port> <ip> <rootdir>  ('.' == cwd). Blocks until killed (Playwright teardown).
exec java -cp "${JAR}" com.gatf.ui.GatfConfigToolUtil -configtool "${PORT}" 127.0.0.1 .
