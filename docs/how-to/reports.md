---
title: Read reports
sidebar_position: 25
---

# Read reports

Every run writes a folder under `outFilesDir` (named by a run id) plus `gatf-results.json` and `gatf-junit.xml` for the latest run. The web console (`gatf ui`) lists runs in **Reports**.

- **HTML report per run**: pass/fail summary charts and, for API tests, the full request and response of each case with its `curl` equivalent (copy it, run it, or import it back). **Play** re-runs a case; **Replay** re-sends a captured request.
- **UI runs** also get PDF and CSV run reports beside the HTML one (`…-RP-<timestamp>.pdf` and `.csv`): one results table per script, plus an error page per failure with the stack trace and screenshot.
- **Load reports**: latency percentiles and samples over time; distributed runs per node.
- **Run insights** (Reports › Latest CLI run › Insights, and every merged report): a timeline per worker, the slowest tests and files, flaky tests, filters, an attachments gallery (screenshots, visual diffs, traces, checks) and **Raise defect** on failed tests.
- **Trends**: every CLI run's results are kept in `<out>/history/` (the newest 50; `-Dgatf.history.keep=N`). Reports › Trends charts the last 10 runs and ranks the least stable tests; `GET /api/history?limit=N` returns the same data.
- **Coverage**: a run's **Coverage** button merges its traces' JS coverage and visited pages with the newest crawl map and lists untested routes and scripts.

For CI, `gatf-results.json` is the easiest to process. Each test has `suite`, `name`, `title`, `status`, `attempts`, `durationMs`, `worker`, `error`, `tags` and `attachments`:

```json title="out/gatf-results.json"
{
  "dir": "/home/you/hello-gatf/out",
  "summary": {"total": 1, "passed": 0, "failed": 1, "flaky": 0, "skipped": 0, "softFailures": 0, "durationMs": 696, "workers": 1, "shard": null},
  "tests": [
    {"suite": "health.xml", "name": "Health check", "title": "health.xml › Health check", "status": "failed", "attempts": 1, "durationMs": 76, "startMs": 531, "worker": 0, "error": "Expected Node value for version is null", "softFailures": 0, "tags": [], "attachments": []}
  ]
}
```

Sharded runs are joined with `gatf merge-reports` ([shards](filter-and-shard.md#shard-across-machines)). Test-case documents and traceability: [Test documents](test-documents.md).
