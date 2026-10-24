---
title: AI healer and planner
sidebar_position: 21
---

# AI healer, planner and generator

These features work without AI and use a model only when the config has an `ai` block. Without it nothing calls a model.

```json title="gatf-config.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "ai": {
    "provider": "anthropic",
    "model": "your-model-id",
    "apiKeyEnv": "ANTHROPIC_API_KEY",
    "maxCallsPerRun": 10,
    "maxTokensPerRun": 50000
  }
}
```

The key is read from the environment variable named by `apiKeyEnv` (or the first line of `apiKeyFile`) and never appears in reports, traces or logs. `provider` is `anthropic`, `openai` or `local` (an OpenAI-compatible `endpoint`). Budgets per session cap calls, tokens and, with prices set, cost. All fields: [AiConfig](../reference/configuration.md#aiconfig).

## Healer

When a step fails, **🩹 Heal failed step** in the Trace Viewer (or **Heal last failure** above a script in the editor) proposes fixes as diffs. It first tries deterministic fixes (a wait, or a strict locator for the element the old locator meant) using the trace's failure snapshot (`traceDomSnapshots` on), and asks the model only when those find nothing. Every proposal must match exactly one element in the snapshot. **Accept** writes the fix only if the line has not changed since and the script still validates; the healer never edits a script on its own. The MCP `heal` tool returns the same proposals without writing.

## Planner and generator

```bash
gatf plan tests/shop-coverage.json --no-ai      # → tests/shop-plan.json and tests/shop-plan.md
gatf generate tests/shop-plan.json --no-ai      # → tests/generated/shop/*.sel
```

- `gatf plan` turns a crawl's coverage map into a test plan. Every crawled route gets an entry and deterministic journeys (page load, fill and submit each form, submit empty when fields are required); with AI the model only adds journeys and priorities over the elements the crawl saw.
- `gatf generate` turns a plan into seleasy scripts by a fixed mapping (the model never writes seleasy). Each script must pass validation and the suite must compile; only passing scripts are written. Journeys with side effects need `--include-side-effects`. With AI, a failing script gets up to 3 repair rounds limited to dropping a step, picking another locator or changing fill data. It exits `2` when some scripts failed the gate.

Neither overwrites files without `--force`. Flags: [plan](../reference/cli.md#gatf-plan), [generate](../reference/cli.md#gatf-generate).
