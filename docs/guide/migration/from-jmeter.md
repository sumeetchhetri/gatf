---
title: From JMeter
sidebar_position: 3
---

# Migrating from JMeter

`gatf import jmeter` reads a JMeter test plan (`.jmx`) and writes its HTTP samplers as API test cases. The thread-group settings become load settings for the config.

```bash
gatf import jmeter sample-plan.jmx
```

```text
Imported 2 test case(s) → sample-plan.xml  {threadGroup=Users Load (threads=50, rampUp=10000ms, time=60000ms), requests=2, headers=3, assertions=2, captures=0, unsupported=4}
  load settings for gatf-config: {loadTestingEnabled=true, concurrentUserSimulationNum=50, concurrentUserRampUpTime=10000, loadTestingTime=60000}
  4 item(s) need review:
   ! JSR223Sampler x1 → no direct GATF equivalent → translate manually (logic/timers/data-sources are out of scope for a test-case import)
   ! CSVDataSet x1 → no direct GATF equivalent → translate manually (logic/timers/data-sources are out of scope for a test-case import)
   ! ConstantTimer x1 → no direct GATF equivalent → translate manually (logic/timers/data-sources are out of scope for a test-case import)
   ! ThreadGroup 'Users Load' → load settings go in gatf-config (see loadConfig): concurrentUserSimulationNum=50, concurrentUserRampUpTime=10000, loadTestingTime=60000
```

## What is converted

| JMeter | GATF |
|---|---|
| HTTP Request samplers | `TestCase` (`url`, `method`, `content`) |
| HTTP Header Manager | `headers` |
| Response assertions | `expectedResCode` and `expectedNodes` where they map |
| JSON and regex extractors | `workflowContextParameterMap` captures |
| Thread group (threads, ramp-up, duration) | Config fields `loadTestingEnabled`, `concurrentUserSimulationNum`, `concurrentUserRampUpTime`, `loadTestingTime` (ms) |

Scripted samplers, timers, CSV data sets and controller logic are listed for manual work. A CSV data set usually becomes a [file data provider](../concepts/data-providers.md).

## Run it as a load test

Put the printed load settings into a config for the imported file:

```json title="gatf-config-load.json"
{
  "baseUrl": "https://api.example.com",
  "testCaseDir": "load",
  "outFilesDir": "out-load",
  "loadTestingEnabled": true,
  "concurrentUserSimulationNum": 50,
  "concurrentUserRampUpTime": 10000,
  "loadTestingTime": 60000,
  "loadTestingReportSamples": 6
}
```

This replays the whole suite with 50 simulated users for 60 seconds (load testing only runs when `loadTestingTime` is above 10000 ms). For raw throughput on a single endpoint, a `perfConfig` case with the wperfk engine is usually closer to what a JMeter plan measured: see [First load test](../getting-started/first-load-test.md) and [Load testing](../../how-to/load-testing.md).
