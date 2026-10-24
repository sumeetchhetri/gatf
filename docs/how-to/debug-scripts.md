---
title: Debug scripts
sidebar_position: 20
---

# Debug seleasy scripts

## Check without running

```bash
gatf -validate-sel /full/path/to/tests/login.sel gatf-config.xml /full/path/to/project
```

It parses and compiles the script and prints `SUCCESS` or `FAILURE` with the error. Use full paths (a bare file name is looked up under `/workdir/`), and read the output: it exits `0` either way in 5.0.0. The web editor does the same check on every save.

## Step debugger

In the console's editor (and in VS Code with the seleasy extension), set breakpoints in the gutter and **Debug** the script: step over, step into subtests, continue, with the current line highlighted. The debugger needs a JDK (it uses `jdk.jdi`). `autoPauseOnElNotFound` in the config pauses a run at a step whose element was not found, so you can fix the page state and resume.

## Watch mode

- In the editor, **Watch** runs the script beside it and re-runs it on every save, with a live screencast next to the step list. The frames come from the trace video, so set `traceVideo: true`.
- On the command line, `gatf test --watch` re-runs the changed scripts or API files plus the last failures on every save; Enter re-runs everything (failures first), `f` + Enter only the failures, `q` + Enter quits.

## After a failure

The failed line in the console output is followed by the screenshot and trace paths. Open the trace in **◫ Trace Viewer**: the step list shows the failing step, and with `traceDomSnapshots: full` the **DOM** view shows the page as it was, with **⌖ Pick locator** to get a strict locator for any element of that snapshot. The healer can propose a fix: see [AI healer and planner](ai-healer-and-planner.md).
