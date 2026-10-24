---
title: Run in CI
sidebar_position: 7
---

# Run GATF in CI

`gatf test` exits `0` when every test passed or was flaky and `1` when a test failed, the run aborted or the command line was invalid, so any CI system can gate on it. Each run writes, in the out folder:

- `gatf-junit.xml`: JUnit XML (flaky tests carry `<flakyFailure>`; screenshots and traces of failures are attached with the `[[ATTACHMENT|path]]` convention Jenkins and GitLab show);
- `gatf-results.json`: every test with status, duration, worker, tags and attachments;
- on GitHub Actions, an `::error` annotation per failure and a `::warning` per flaky test.

`--reporter junit,json,github,docs` picks the reports (`none` disables them; `docs` also writes [test-case documents](test-documents.md)).

## GitHub Actions

```yaml
jobs:
  ui-tests:
    runs-on: ubuntu-latest
    services:
      selenium:
        image: selenium/standalone-chrome:latest
        ports: ['4444:4444']
        options: --shm-size=2g
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: curl -fsSL -o gatf.jar https://github.com/sumeetchhetri/gatf/releases/download/5.0.0/gatf-alldep-5.0.0.jar
      - run: java -jar gatf.jar test gatf-config.xml --reporter junit,json,github
        env:
          SELENIUM_REMOTE_URL: 'http://localhost:4444'
          GATF_DEMO_PASSWORD: ${{ secrets.GATF_DEMO_PASSWORD }}
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: gatf-results
          path: |
            out/
            logs/
```

The browser runs in the Selenium service container (`SELENIUM_REMOTE_URL`), which also avoids Chrome's sandbox problems when the job runs as root. Run output can contain request headers and bodies, so keep the artifact private (see [Secrets](../guide/concepts/secrets.md)).

## Sharded jobs

```yaml
jobs:
  ui-tests:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix: { shard: [1, 2, 3, 4] }
    services:
      selenium:
        image: selenium/standalone-chrome:latest
        ports: ['4444:4444']
        options: --shm-size=2g
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: curl -fsSL -o gatf.jar https://github.com/sumeetchhetri/gatf/releases/download/5.0.0/gatf-alldep-5.0.0.jar
      - run: java -jar gatf.jar test gatf-config.xml --shard ${{ matrix.shard }}/4 --workers 50% --reporter junit,json
        env: { SELENIUM_REMOTE_URL: 'http://localhost:4444' }
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: 'out-${{ matrix.shard }}', path: out/, if-no-files-found: ignore }
  merge:
    needs: ui-tests
    if: always()
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: curl -fsSL -o gatf.jar https://github.com/sumeetchhetri/gatf/releases/download/5.0.0/gatf-alldep-5.0.0.jar
      - uses: actions/download-artifact@v4          # one folder per artifact: out-1 … out-4
      - run: |
          dirs=""
          for d in out-*; do [ -f "$d/gatf-results.json" ] && dirs="$dirs $d"; done
          java -jar gatf.jar merge-reports $dirs -o merged-report
```

A shard that gets no files writes no results, and `merge-reports` refuses such a folder, so the merge step passes only the folders that have a `gatf-results.json`. `merge-reports` exits `1` when the merged results contain a failure.

## Console output and logs

The console shows one line per finished test and a summary; `logs/gatf.log` has everything (requests, step output, stack traces). Each run starts a new log and keeps the last ten as `gatf.<n>.log.gz`. Colour is automatic; `NO_COLOR` (or `-Dgatf.color=never`) turns it off and `FORCE_COLOR` (`-Dgatf.color=always`) forces it.

| JVM property, via `GATF_JAVA_OPTS="-D…"` | Default | Effect |
|---|---|---|
| `gatf.log.dir` | `logs` | Where `gatf.log` goes |
| `gatf.log.level` | `info` | Detail in the log file (`debug` for more) |
| `gatf.console.level` | `warn` for CLI runs | Log events shown on the console (in 5.0.0 some `[INFO ]` start-up lines still reach it) |
| `gatf.capture.stdout` | `true` | `false` also shows the engine's raw output on the console |
| `gatf.reporters` | `junit,json,github` | Same as `--reporter` |

With the plain jar, pass them as `java -Dgatf.log.level=debug -jar gatf.jar …`.

## Safari and WebKit

The repository's `.github/workflows/ci-webkit.yml` runs a CDP-free subset on Safari (macOS runner, `safaridriver --enable`) and WebKitGTK, separately from the main build. WebKit has no CDP, so tracing, `route`, `emulate` and `clock` need a WebDriver BiDi socket there; see [Safari and WebKit](safari-and-webkit.md).
