---
title: Load testing
sidebar_position: 5
---

# Load testing

GATF has two ways to put load on a system:

| | Load-engine test | Concurrent users |
|---|---|---|
| What | One API case driven by a load tool | The whole API suite replayed by N simulated users |
| Set in | the case: `perfConfig` | the config: `loadTestingEnabled`, `concurrentUserSimulationNum`, `loadTestingTime` |
| Good for | Throughput and latency of an endpoint | Realistic journeys (login, captures, several calls) under load |
| Engine | wperfk (default), vegeta, autocannon, wrk, wrk2, or your own | GATF's own HTTP client, on virtual threads |

## Load-engine test

See [First load test](../guide/getting-started/first-load-test.md) for a complete example. The fields of `perfConfig` (`type`, `connections`, `threads`, `durationSeconds`, `rate`, `timeout`, `latency`, `filePath` for a wrk-style Lua script, `extras`) are in the [test case model](../reference/test-case-model.md#performanceconfig). How each engine is found, downloaded and verified, and what each one cannot do, is in [Load tools](../load-tools.md).

## Concurrent users

```json title="gatf-config-users.json"
{
  "baseUrl": "http://localhost:8880",
  "testCaseDir": "tests",
  "outFilesDir": "out-users",
  "loadTestingEnabled": true,
  "loadTestingTime": 15000,
  "concurrentUserSimulationNum": 5,
  "concurrentUserRampUpTime": 2000,
  "loadTestingReportSamples": 3
}
```

```bash
gatf test gatf-config-users.json
```

Five users start 400 ms apart and replay the suite until 15 seconds are up; the console prints only the summary (for example `15480 passed (15.6s)`), and the report has latency percentiles per sample. Load testing runs only when `loadTestingTime` is above `10000` (ms). Each user can log in with its own credentials from a data provider (`authDataProvider`).

The sample project has a ready-made suite for its local server: `sample-project/gatf-config-load.xml` with `tests/load/load-suite.xml` (login, reads, writes and a slow endpoint).

## From a UI flow

A UI trace knows every request the page made. In the Trace Viewer, **⚡ Load test this flow** writes `<script>-load.xml`, one `perfConfig` case per API or document request (wperfk, 10 users, 30 seconds), and starts it. From the command line:

```bash
gatf import flow network.har --users 10 --duration 30 --engine wperfk
```

## Distributed load

Start `gatf -listener` on each load machine, list them in `distributedNodes` (`host` or `host@port`, default port `9567`) and set `distributedLoadTests`. The coordinator splits the simulated users across the nodes and the console aggregates the results per node.
