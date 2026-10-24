---
title: First load test
sidebar_position: 5
---

# Your first load test

A load test in GATF is an API test case with a `perfConfig` block: GATF hands the request to a load engine instead of sending it once. The default engine is **wperfk** (wrk/wrk2-compatible, prebuilt for every OS); GATF downloads it on first use and checks its SHA-256. Again you will use the `hello-gatf` folder, with `gatf ui` running as the target.

## 1. Write the load case

Create `load/health-load.xml`:

```xml title="load/health-load.xml"
<TestCases>
  <TestCase>
    <name>Health under load</name>
    <url>api/health</url>
    <method>GET</method>
    <expectedResCode>200</expectedResCode>
    <perfConfig>
      <type>wperfk</type>
      <connections>10</connections>
      <threads>2</threads>
      <durationSeconds>5</durationSeconds>
      <latency>true</latency>
    </perfConfig>
  </TestCase>
</TestCases>
```

This runs 10 connections on 2 threads for 5 seconds, as fast as the server answers, and records the latency distribution. Add `<rate>500</rate>` for a constant 500 requests per second instead. Every field is in the [test case model](../../reference/test-case-model.md#performanceconfig).

## 2. Add a config for it

Create `gatf-config-load.json`:

```json title="gatf-config-load.json"
{
  "baseUrl": "http://127.0.0.1:9080",
  "testCaseDir": "load",
  "outFilesDir": "out-load"
}
```

## 3. Run it

```bash
gatf test gatf-config-load.json
```

```text
Running GATF API tests

  ✓  1 health-load.xml › Health under load (5.0s)

  1 passed (5.7s)

  Report: /home/you/hello-gatf/out-load
```

If wperfk has not been set up yet, the first run downloads it (see [Install](install.md#set-up-the-load-tools)). When wperfk cannot be found or downloaded, GATF falls back to vegeta (if vegeta is installed or can be downloaded, also from GitHub) and says so with a `NOTICE:` in the result.

## 4. Read the numbers

The run folder's `index.data.json` (also shown in the report) holds the engine's result for the case, with the same keys for every engine: `total_requests`, `rps`, `lat_avg_ms`, `lat_p50_ms`, `lat_p90_ms`, `lat_p99_ms`, `lat_max_ms`, `transport_errors` and `non_2xx`, plus the engine's own statistics. A run on a laptop gives something like `"tool":"wperfk"`, `"total_requests":71854`, `"rps":14369.98`, `"lat_p99_ms":4.58`.

## What you learned

- `perfConfig` turns one API case into a load test; `type` picks the engine (`wperfk`, `vegeta`, `autocannon`, `wrk`, `wrk2`).
- `connections`, `threads` and `durationSeconds` shape the load; `rate` switches to a constant request rate.

GATF has a second kind of load test: replaying a whole API suite with many concurrent simulated users (`loadTestingEnabled`, `concurrentUserSimulationNum`). See [Load testing](../../how-to/load-testing.md), and [Load tools](../../load-tools.md) for how each engine is found and set up.
