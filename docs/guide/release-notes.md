---
title: Release notes
sidebar_position: 6
---

# Release notes

## GATF 5.0.0

The current release. Downloads (installers, distribution ZIP, jar and VS Code extension) are on the [5.0.0 release page](https://github.com/sumeetchhetri/gatf/releases/tag/5.0.0).

GATF 5.0.0 includes a single React web console (`gatf ui`) for configuration, editing, recording, crawling, execution, reports and traces; Playwright-style UI testing in seleasy (auto-wait, web-first assertions, semantic locators, network mocking, browser contexts, workers and sharding, visual and accessibility checks); streaming traces with DOM time travel; wperfk as the default load engine; importers and exporters; an AI healer and planner (opt-in); an MCP server; and installers for Linux, macOS and Windows.

What changed from earlier versions, how to switch each feature on, and the upgrade notes are in the [Tier 3 release notes](../release-notes/tier-3.md) (sections "Behaviour changes" and "Upgrade notes").

## Known issues in 5.0.0

Found while writing these docs; the workarounds are in [Troubleshooting](troubleshooting.md).

- A UI run whose browser fails to start prints `No tests were executed.` and exits `0`.
- For a local `open chrome`, the driver config's `arguments` reach Chrome as one switch, so only the first switch takes effect.
- `GATF_CHROME_BINARY` is not applied to a plain local `open chrome`.
- A data provider without `"enabled": true` is disabled.
- `logicalValidations` do not fail a plain test case.
- `#readfile` does not compile; write `readfile`.
- `-validate-sel` exits `0` on `FAILURE`.
