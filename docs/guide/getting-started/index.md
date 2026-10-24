---
title: Getting started
sidebar_position: 1
---

# Getting started

GATF tests APIs, web UIs and performance from one tool. These pages take you from nothing to three green runs in about fifteen minutes:

1. [Install GATF](install.md) on Linux, macOS or Windows.
2. [Write your first API test](first-api-test.md): one XML test case, run with `gatf test`.
3. [Write your first UI test](first-ui-test.md): a seleasy script that drives Chrome and records a trace.
4. [Run your first load test](first-load-test.md): the same kind of API test, driven by the wperfk load engine.

Every step uses the GATF web console (`gatf ui`) as the system under test, so you need nothing but GATF itself (and Chrome for the UI test). When the examples work, point `baseUrl` and the URLs at your own application.

## What you need

- **Java 21 or newer.** A JRE is enough to run tests; the online debugger needs a JDK. The installer can download a private Java for you.
- **Chrome**, for UI tests only. If it is missing, Selenium Manager downloads Chrome for Testing on first use.
- **About 300 MB** of disk for GATF and its load tools, plus a few hundred MB if Chrome for Testing is downloaded.

## Where to go next

- [Concepts](../concepts/index.md): how configuration, test cases, seleasy, workflows and traces fit together.
- [How-to guides](../../how-to/index.md): one page per feature.
- [Reference](../../reference/index.md): every config field, CLI flag, REST endpoint and seleasy keyword.
