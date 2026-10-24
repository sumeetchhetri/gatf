---
title: Introduction
sidebar_position: 1
---

# GATF 5.0.0

GATF (Generic Automated Test Framework) is one tool for API, workflow, UI, load and performance testing. It is a single Java 21 jar with a web console, a trace viewer, a step debugger and a VS Code extension.

- **API tests are data**: XML, JSON or CSV test cases, chained into workflows by capturing response values, and fed by files, databases or inline data.
- **UI tests are written in seleasy**, a compact automation language compiled to Selenium Java in-process, with auto-wait, web-first assertions, semantic locators, network mocking and streaming traces.
- **The same suites scale** from one run to concurrent users, load engines (wperfk by default) and distributed agents.

```sel title="checkout.sel"
// Checkout happy path @smoke
open chrome
goto https://shop.example.com
click role@button[name='Add to cart']
?? testid@cart-count text ="1"
```

## Where to start

| If you want to | Read |
|---|---|
| Install GATF and run a first API, UI and load test | [Getting started](guide/getting-started/index.md) |
| Understand how the pieces fit | [Concepts](guide/concepts/index.md) |
| Do one specific thing | [How-to guides](how-to/index.md) |
| Look up a field, flag, endpoint or statement | [Reference](reference/index.md) and the [seleasy language reference](seleasy/REFERENCE.md) |
| Move from Postman, Playwright or JMeter | [Migration](guide/migration/index.md) |
| Fix a problem | [Troubleshooting and FAQ](guide/troubleshooting.md) |

The whole map, including the in-depth pages, is on the [documentation home](guide/index.md).

GATF is open source under the Apache License 2.0: [github.com/sumeetchhetri/gatf](https://github.com/sumeetchhetri/gatf).
