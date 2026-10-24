---
title: Compare environments
sidebar_position: 4
---

# Compare two environments

A comparison run sends every API case to `baseUrl` and to each URL in `compareEnvBaseUrls`, and compares the responses, for example staging against production.

```json title="gatf-config-compare.json"
{
  "baseUrl": "http://localhost:8880",
  "testCaseDir": "tests",
  "outFilesDir": "out-compare",
  "compareEnabled": true,
  "gatfTestDataConfig": {
    "compareEnvBaseUrls": ["http://localhost:8881"]
  }
}
```

```bash
gatf test gatf-config-compare.json
```

At least one compare URL must differ from `baseUrl`. Load testing is off in this mode. The console's report view and its **Compare** file view show the responses side by side.

To try it locally, start two copies of the sample server: `node sample-project/load-server/server.js` and `node sample-project/load-server/server.js --port 8881`.
