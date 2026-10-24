---
title: Migration
sidebar_position: 0
---

# Migrating to GATF

`gatf import` converts tests from other tools into GATF files, and prints what it could not carry over so you know what to review. It never overwrites a file without `--force`.

| From | Command | You get |
|---|---|---|
| [Postman](from-postman.md) | `gatf import postman collection.json` | API test cases (XML), plus the collection variables to put in `globalVariables` |
| [Playwright](from-playwright.md) | `gatf import playwright login.spec.ts` | A seleasy script, one subtest per `test(...)` |
| [JMeter](from-jmeter.md) | `gatf import jmeter plan.jmx` | API test cases, plus the thread-group settings for a load run |

The same command also reads cURL commands (`curl`), browser recordings (`har`, with `--filter <url-regex>` to keep only your API) and OpenAPI or Swagger documents (`openapi`, with `--negative` for negative cases). The web console's **Import** view does the same in the browser. Every flag is in the [CLI reference](../../reference/cli.md#gatf-import).

Going the other way, `gatf export playwright` and `gatf export selenium-java` turn seleasy scripts into Playwright Test or Selenium + JUnit 5 code; see [Export seleasy scripts](../../how-to/export-scripts.md).
