---
title: Environments
sidebar_position: 6
---

# Environments

GATF 5.0.0 has no separate profile mechanism: an environment is a config file plus the variables it reads. Three tools cover the usual needs.

## Global variables

`gatfTestDataConfig.globalVariables` holds project-wide values, used as `${name}` in API cases and seleasy scripts. A value can come from the environment or a JVM system property:

| Value written | Resolves to |
|---|---|
| `env.NAME` | the environment variable `NAME` |
| `env.var` | the environment variable with the same name as the global variable |
| `property.NAME` | the JVM system property `NAME` (set with `GATF_JAVA_OPTS="-DNAME=…"`) |
| `property.var` | the system property with the same name as the global variable |
| anything else | itself |

```json title="gatf-config.json"
{
  "baseUrl": "http://localhost:8880",
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "gatfTestDataConfig": {
    "globalVariables": {
      "username": "alice",
      "password": "env.GATF_DEMO_PASSWORD",
      "appUrl": "env.var"
    }
  }
}
```

With `export GATF_DEMO_PASSWORD=…` and `export appUrl=https://staging.example.com`, `${password}` and `${appUrl}` take those values. Watch out: when the variable is **not set**, the value stays the literal text (`env.GATF_DEMO_PASSWORD`), and the test runs with it instead of failing early.

## One config per environment

The simplest way to switch environments is one small config per environment, picked on the command line:

```bash
gatf test gatf-config-staging.json
gatf test gatf-config-prod.json --tag smoke
```

Test cases use relative `url`s, so the same test files run against any of them. seleasy scripts can read the target from a global variable (`goto ${appUrl}`) or from a properties file loaded with `config`.

## Comparing environments

Comparison runs replay every API case against several environments and compare the responses: set `compareEnabled` and list the other base URLs in `gatfTestDataConfig.compareEnvBaseUrls`. `baseUrl` is the first environment. Load testing is switched off in this mode.

## Related

- [Secrets](secrets.md): keeping credentials out of these files.
- [Configuration reference](../../reference/configuration.md#gatftestdataconfig): `globalVariables`, `compareEnvBaseUrls`.
