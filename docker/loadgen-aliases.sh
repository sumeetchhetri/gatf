#!/bin/sh
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
# Use the gatf-loadgen container's wrk / wrk2 / vegeta like local commands (bash, zsh, sh):
#   . docker/loadgen-aliases.sh
#   wrk -t2 -c10 -d10s http://host.docker.internal:8080/     (bridge network: macOS / Windows)
#   wrk -t2 -c10 -d10s http://localhost:8080/                (Linux container started with --network host)
# Start the container once (GATF also does this automatically when Docker is running):
#   docker build -t gatf/loadgen:1 - < alldep-jar/src/main/resources/gatf-loadgen/Dockerfile
#   docker run -d --name gatf-loadgen --network host gatf/loadgen:1        # Linux
#   docker run -d --name gatf-loadgen gatf/loadgen:1                       # macOS / Windows
# Lua scripts must exist inside the container: docker cp script.lua gatf-loadgen:/tmp/gatf/
GATF_PERF_CONTAINER="${GATF_PERF_CONTAINER:-gatf-loadgen}"
wrk()    { docker exec -i "$GATF_PERF_CONTAINER" wrk "$@"; }
wrk2()   { docker exec -i "$GATF_PERF_CONTAINER" wrk2 "$@"; }
vegeta() { docker exec -i "$GATF_PERF_CONTAINER" vegeta "$@"; }
