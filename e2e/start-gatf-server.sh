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

JAR="${GATF_E2E_JAR:-}"   # Δ163n: run the suite against a given jar (e.g. the base build, to show a spec fails there)
[ -n "${JAR}" ] || JAR="$(ls -t "${REPO_ROOT}"/alldep-jar/target/gatf-alldep-jar-*.jar 2>/dev/null | grep -Ev '\-(sources|javadoc)\.jar$' | head -1 || true)"
if [ -z "${JAR:-}" ]; then
  echo "No shaded jar found. Build it first:  mvn -q -pl alldep-jar -am package -DskipTests" >&2
  exit 1
fi
JAR="$(cd "$(dirname "${JAR}")" && pwd)/$(basename "${JAR}")"   # absolute (we cd into the scratch project)

PROJ="$(mktemp -d)"
cp -r "${REPO_ROOT}/sample-project/." "${PROJ}/"
cd "${PROJ}"
# Isolation guard: a config that pins absolute base paths would make the server read/write THAT tree
# (this is how e2e edits once landed in the repo). Drop them so everything resolves to the scratch copy.
node -e 'const fs=require("fs"),f="gatf-config.json";if(fs.existsSync(f)){const c=JSON.parse(fs.readFileSync(f,"utf8"));delete c.testCasesBasePath;delete c.outFilesBasePath;fs.writeFileSync(f,JSON.stringify(c,null,2))}'
# Δ163n: a LOCAL headless Chrome instead of a Grid for the selenium specs (debugger, runs). GATF_E2E_CHROMEDRIVER is a
# chromedriver matching the Chrome at GATF_E2E_CHROME_BINARY (optional; default: the one Selenium Manager finds).
if [ -n "${GATF_E2E_CHROMEDRIVER:-}" ]; then
  # headless + no-sandbox (containers run as root) go in the chrome driver config's arguments
  node -e 'const fs=require("fs"),f="gatf-config.json";const c=JSON.parse(fs.readFileSync(f,"utf8"));const d=(c.seleniumDriverConfigs||[]).find(x=>x.name==="chrome");
    if(d){d.path=process.env.GATF_E2E_CHROMEDRIVER;d.arguments=((d.arguments||"")+" --headless=new --no-sandbox --disable-dev-shm-usage").trim();}
    fs.writeFileSync(f,JSON.stringify(c,null,2))'
  [ -n "${GATF_E2E_CHROME_BINARY:-}" ] && export GATF_CHROME_BINARY="${GATF_E2E_CHROME_BINARY}"
  echo "GATF e2e: local chrome, driver=${GATF_E2E_CHROMEDRIVER} binary=${GATF_E2E_CHROME_BINARY:-<default>}"
fi
echo "GATF e2e server: jar=${JAR} proj=${PROJ} port=${PORT}"
# The GATF server uses SELENIUM_REMOTE_URL for selenium runs (T4c). Take it from GATF_SELENIUM_GRID so
# it reaches GATF *without* Playwright seeing it (Playwright reserves SELENIUM_REMOTE_URL to route its
# own browser through a Grid). JAVA_HOME is inherited as-is.
if [ -n "${GATF_SELENIUM_GRID:-}" ]; then
  export SELENIUM_REMOTE_URL="${GATF_SELENIUM_GRID}"
  echo "GATF e2e: Selenium Grid = ${SELENIUM_REMOTE_URL}"
fi
# main(): -configtool <port> <ip> <rootdir>  ('.' == cwd). Blocks until killed (Playwright teardown).
exec java ${GATF_E2E_CHROMEDRIVER:+-Dwebdriver.chrome.driver=${GATF_E2E_CHROMEDRIVER}} -cp "${JAR}" com.gatf.ui.GatfConfigToolUtil -configtool "${PORT}" 127.0.0.1 .
