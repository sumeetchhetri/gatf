---
title: GATF documentation
sidebar_position: 1
---

# GATF documentation

GATF (Generic Automated Test Framework) tests APIs, web and mobile UIs, and performance from one tool. This is the documentation for **GATF 5.0.0**.

## Start here

- [Getting started](getting-started/index.md): install, then a first API, UI and load test in about fifteen minutes.
- [Concepts](concepts/index.md): configuration, test cases, seleasy, workflows, data providers, environments, secrets and traces.
- [How-to guides](../how-to/index.md): one short page per feature, each with an example.
- [Troubleshooting and FAQ](troubleshooting.md).
- [Migration](migration/index.md) from Postman, Playwright and JMeter.
- [Release notes](release-notes.md).

## Reference

Generated from the source, so they list everything GATF 5.0.0 accepts:

- [Configuration](../reference/configuration.md), [test case model](../reference/test-case-model.md), [CLI](../reference/cli.md), [server REST API](../reference/rest-api.md), [seleasy keywords](../reference/seleasy-keywords.md).
- [seleasy language reference](../seleasy/REFERENCE.md): every statement with syntax and examples.

## In-depth pages

| Page | Covers |
|---|---|
| [Configuration and test cases](../configuration.md) | A fully commented executor config, test-case formats, data sources and providers, the Maven plugin, the test generator |
| [Browser modes](../browser-modes.md) | Local Chrome, chrome-headless-shell, Selenium Grid, cross-browser support, per-test browser contexts |
| [Load tools](../load-tools.md) | wperfk, vegeta, autocannon, wrk and wrk2: how each is found or installed, offline setup, your own engine |
| [Selenium overrides](../selenium-overrides.md) | Pinned browser-infrastructure versions and the Selenium classes GATF overrides |
| [Windows support](../WINDOWS-SUPPORT.md) | Running GATF on Windows |
| [seleasy language assets](../seleasy/README.md) | The generated command catalog and the linter |
| [Tier 3 release notes](../release-notes/tier-3.md) | The features added in 5.0.0, behaviour changes and upgrade notes |
