---
title: Select, parallelise and shard tests
sidebar_position: 6
---

# Select, parallelise and shard tests

## Select

| Flag | Runs |
|---|---|
| `--tag smoke` (`-t`) | tests tagged `@smoke`: in an API case's name or description, or in the `//` comment lines at the top of a `.sel` file |
| `--grep RE` (`-g`) | tests whose title matches: `<file> › <name>` for API cases, the script path for UI scripts |
| `--grep-invert RE` | everything except matching titles (applied after `--grep` and `--tag`) |

`--grep` and `--tag` can be repeated and are combined with OR. A selected API case brings its `relatedTestName` dependencies with it; for chains that capture variables, tag every case of the chain. When nothing matches, the run stops with `No API tests match …` or `No UI scripts match …` and exit code `1`.

```bash
gatf test --tag smoke
gatf test --grep 'users\.xml' --grep-invert 'slow'
```

## Run in parallel

`--workers N` (`-j N`, or `N%` of the CPU cores) overrides `numConcurrentExecutions` for one run. UI scripts run file-parallel on that many workers; with `browserContexts: worker` each worker reuses one browser ([browser contexts](browser-contexts.md)).

## Shard across machines

`--shard i/n` runs slice `i` of `n` (1-based). Files are split by a hash of their path, so every machine computes the same split. Merge the slices' output folders afterwards:

```bash
gatf test --shard 1/2 && cp -r out out-1     # machine 1
gatf test --shard 2/2 && cp -r out out-2     # machine 2
gatf merge-reports out-1 out-2 -o out/merged  # after collecting both folders on one machine
```

```text
Merged 2 report dir(s) into /home/you/project/out/merged
  4 tests: 4 passed, 0 failed, 0 flaky, 0 skipped
```

The merged folder has `gatf-results.json`, `gatf-junit.xml`, an `index.html` the console opens, and each shard's reports under `shard-<i>-of-<n>/`. A shard that gets no files runs nothing and exits `0`, but writes no results, so `merge-reports` refuses its folder: leave such folders out of the merge.

## Only what a change touches

`gatf test --changed main...HEAD` runs only the UI scripts a git change can reach, using the JS coverage recorded by an earlier traced run (`traceCoverage` on). When it cannot decide safely it runs everything and says why; `--explain` prints the decision per script and runs nothing. Details: [CLI reference](../reference/cli.md#gatf-test).
