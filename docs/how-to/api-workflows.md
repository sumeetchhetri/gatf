---
title: Chain API calls
sidebar_position: 2
---

# Chain API calls into a workflow

Log in, keep the token, create a resource and read it back. This example runs against the small server in the GATF repository (Node 16 or newer, no dependencies):

```bash
node sample-project/load-server/server.js        # http://localhost:8880, Ctrl-C to stop
```

Config, with the password taken from the environment:

```json title="gatf-config.json"
{
  "baseUrl": "http://localhost:8880",
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "gatfTestDataConfig": {
    "globalVariables": { "username": "alice", "password": "env.GATF_DEMO_PASSWORD" }
  }
}
```

Copy `tests/users.xml` from [Workflows](../guide/concepts/workflows.md#capture-and-reuse), then:

```bash
GATF_DEMO_PASSWORD=demo gatf test
```

```text
  ✓  1 users.xml › Login (62ms)
  ✓  2 users.xml › Create user (4ms)
  ✓  3 users.xml › Read user (4ms)

  3 passed (687ms)
```

What makes it work:

- `Login` captures the `token` field into `${authToken}` (`workflowContextParameterMap`).
- `Create user` sends it in `Authorization` and captures `id` into `${userId}` and the `Location` header into `${userUrl}`.
- `Read user` calls `api/users/${userId}`.
- `sequence` 1, 2, 3 keeps that order.

**Selecting part of a chain.** `gatf test --tag smoke` with only `Read user` tagged fails, because the login and create steps that set `${authToken}` and `${userId}` don't run. Tag every case of the chain, or select the file: `gatf test --grep users.xml`.

See also: [data-driven tests](../guide/concepts/data-providers.md) (one call per data row) and the [test case model](../reference/test-case-model.md).
