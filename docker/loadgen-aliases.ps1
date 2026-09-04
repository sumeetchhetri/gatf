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
# Use the gatf-loadgen container's wrk / wrk2 / vegeta like local commands (PowerShell, Windows):
#   . .\docker\loadgen-aliases.ps1
#   wrk -t2 -c10 -d10s http://host.docker.internal:8080/
# Start the container once (GATF also does this automatically when Docker Desktop is running):
#   Get-Content alldep-jar\src\main\resources\gatf-loadgen\Dockerfile | docker build -t gatf/loadgen:1 -
#   docker run -d --name gatf-loadgen gatf/loadgen:1
if (-not $env:GATF_PERF_CONTAINER) { $env:GATF_PERF_CONTAINER = "gatf-loadgen" }
function wrk    { docker exec -i $env:GATF_PERF_CONTAINER wrk @args }
function wrk2   { docker exec -i $env:GATF_PERF_CONTAINER wrk2 @args }
function vegeta { docker exec -i $env:GATF_PERF_CONTAINER vegeta @args }
