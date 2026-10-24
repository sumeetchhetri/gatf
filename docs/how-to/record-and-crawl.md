---
title: Record and crawl
sidebar_position: 19
---

# Record scripts and crawl an application

Both start from the web console (`gatf ui`).

## Recorder

**Record** opens a browser (Chrome, Firefox or Edge) at the URL you give and turns your clicks and typing into seleasy, keeping the pauses between steps. Each step gets the first locator that is unique on the page, in this order: `testid@`, `role@…[name=…]`, `label@`, then id, name, CSS and XPath (the attribute for `testid@` is `testIdAttribute`, default `data-testid`). Stop and **Save** writes the script into the test folder. **⌖ Pick** lets you point at one element in the recording window to get its locator.

## Crawler

**Crawl** logs into an application (including single-page apps), explores it in a real browser and proposes a seleasy suite plus a coverage map, `<name>-coverage.json`, saved in the test folder. Locators are ranked the same way as the recorder's.

The coverage map feeds the planner: `gatf plan <name>-coverage.json` turns it into a test plan and `gatf generate` into scripts; see [AI healer and planner](ai-healer-and-planner.md). In **Reports**, a run's **Coverage** button lists the crawled routes no test visited.
