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
# run.sh -- run the dogfood seleasy suite: GATF drives its own /app console via a Selenium Grid.
# Mirrors tools/smoke/trace-e2e.sh: generates a config and invokes GATF's `-selenium` executor.
#
# Prereqs:
#   1. a running GATF /app server (the target under test), e.g.:  bash e2e/start-gatf-server.sh 9223 &
#   2. a Selenium Grid, with SELENIUM_REMOTE_URL pointing at it (GATF's native Grid var — NOT the
#      GATF_SELENIUM_GRID used by the Playwright e2e).
#   3. the shaded jar built (mvn -pl alldep-jar -am package -DskipTests) or GATF_JAR set.
#   4. tests/app.props pointing at the /app server (default http://localhost:9223/app/).
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${HERE}/.." && pwd)"

if [ -z "${SELENIUM_REMOTE_URL:-}" ]; then
  echo "SELENIUM_REMOTE_URL is required (e.g. http://localhost:4444)." >&2
  exit 1
fi
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(command -v java)")")}"
GATF_JAR="${GATF_JAR:-$(ls -t "${REPO_ROOT}"/alldep-jar/target/gatf-alldep-jar-*.jar 2>/dev/null | grep -Ev '\-(sources|javadoc)\.jar$' | head -1 || true)}"
if [ -z "${GATF_JAR:-}" ]; then
  echo "No shaded jar. Build:  mvn -q -pl alldep-jar -am package -DskipTests  (or set GATF_JAR)." >&2
  exit 1
fi
GATF_JAR="$(cd "$(dirname "${GATF_JAR}")" && pwd)/$(basename "${GATF_JAR}")"

# Generate a runnable config with resolved absolute paths (the -selenium executor resolves
# testCases/out + the config relative to the current dir, so we run from HERE and pass the basename).
CONFIG="${HERE}/gatf-config.gen.xml"
cat > "${CONFIG}" <<XML
<gatf-execute-config>
  <testCasesBasePath>${HERE}</testCasesBasePath>
  <testCaseDir>tests</testCaseDir>
  <outFilesBasePath>${HERE}</outFilesBasePath>
  <outFilesDir>out</outFilesDir>
  <authEnabled>false</authEnabled>
  <numConcurrentExecutions>1</numConcurrentExecutions>
  <concurrentUserSimulationNum>0</concurrentUserSimulationNum>
  <enabled>true</enabled>
  <loadTestingEnabled>false</loadTestingEnabled>
  <isSeleniumExecutor>true</isSeleniumExecutor>
  <enableTrace>true</enableTrace>
  <traceProtocol>cdp</traceProtocol>
  <traceNetwork>true</traceNetwork>
  <traceConsole>true</traceConsole>
  <traceSnapshots>true</traceSnapshots>
  <autoWait>true</autoWait>
  <webFirstAssertions>true</webFirstAssertions>
  <seleniumScripts>
    <string>app-console.sel</string>
  </seleniumScripts>
  <seleniumDriverConfigs>
    <seleniumDriverConfig>
      <name>chrome</name>
      <arguments>--headless=new --no-sandbox --disable-dev-shm-usage</arguments>
    </seleniumDriverConfig>
  </seleniumDriverConfigs>
  <javaHome>${JAVA_HOME}/</javaHome>
  <gatfJarPath>${GATF_JAR}</gatfJarPath>
</gatf-execute-config>
XML

echo "dogfood: jar=${GATF_JAR}"
echo "dogfood: grid=${SELENIUM_REMOTE_URL}  appUrl=$(grep -E '^appUrl=' "${HERE}/tests/app.props" | cut -d= -f2-)"
( cd "${HERE}" && "${JAVA_HOME}/bin/java" -jar "${GATF_JAR}" -selenium "$(basename "${CONFIG}")" )
echo "dogfood done -> open ${HERE}/out/<run>/*-trace.zip in the /app Trace Viewer (GATF tracing GATF)."
