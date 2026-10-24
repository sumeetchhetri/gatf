---
title: Secrets
sidebar_position: 7
---

# Secrets

Keep passwords, tokens and API keys out of config and test files, and know where GATF 5.0.0 writes what it sends.

## Where secrets come from

| Secret | How to pass it |
|---|---|
| Credentials and tokens used by tests | Global variables backed by the environment: `"password": "env.GATF_DEMO_PASSWORD"` (see [Environments](environments.md#global-variables)) |
| Login rows for automatic authentication | A data provider (`authDataProvider`), for example a CSV file kept out of version control |
| AI provider key | `ai.apiKeyEnv` (an environment variable name, default `ANTHROPIC_API_KEY` or `OPENAI_API_KEY`) or `ai.apiKeyFile`; the key is never in the config |
| Device clouds (BrowserStack, Sauce Labs, LambdaTest) | Environment variables only: `BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY`, `SAUCE_USERNAME`/`SAUCE_ACCESS_KEY`, `LT_USERNAME`/`LT_ACCESS_KEY` |
| Jira (requirements for `gatf docs`) | `GATF_JIRA_URL`, `GATF_JIRA_TOKEN` (and `GATF_JIRA_USER` for basic auth) |
| Web console users | `auth.props` in the folder `gatf ui` runs in; with no entries the console runs in anonymous mode |

In CI, map the platform's secret store to these environment variables (for example `env:` entries from `secrets.*` in GitHub Actions).

## What 5.0.0 does not mask

GATF 5.0.0 has no general secret masking. A value you send in a request ends up wherever the request is recorded:

- **API reports**: the run folder's `index.data.json` holds each request with its headers and body. A token sent as `Authorization: Bearer ${apiToken}` appears there in clear text.
- **Logs**: with `debugEnabled`, every request is logged as a curl command in `logs/gatf.log`.
- **UI traces**: network headers and bodies are recorded when `traceNetwork` is on (the default with tracing).

So treat the `out/` folder, `logs/` and trace files as sensitive when tests use real credentials: don't publish them as public CI artifacts, or switch off what you don't need (`traceNetwork`, `reportResponseContent` on a case, `debugEnabled`).

The exceptions, which never write the secret: the AI key (never in reports, traces or logs), the device-cloud credentials (sent as HTTP basic auth, never in URLs, capabilities, logs or reports), and the export commands (`gatf export` turns `${name}` values into environment-variable lookups instead of copying configured values).

## Checklist

- Commit configs with `env.NAME` placeholders, never real values.
- An unset variable stays the literal `env.NAME` text: check your CI sets every variable the config names.
- Keep `auth.props` and credential CSV files out of version control.
- Keep run output private.
