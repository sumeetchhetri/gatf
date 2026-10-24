---
title: From Postman
sidebar_position: 1
---

# Migrating from Postman

Export the collection from Postman (**Export → Collection v2.1**), then:

```bash
gatf import postman demo.postman_collection.json
```

```text
Imported 6 test case(s) → demo.postman_collection.xml  {format=postman v2, requests=7, testCases=6}
  variables for gatf-config globalVariables: {baseUrl=https://api.demo.test, token=t0k}
  4 item(s) need review:
   ! collection prerequest script → collection/folder scripts are not translated
   ! #1 Auth / Login test script → only status-code assertions are translated → add expectedNodes for the rest
   ! #3 Upload form field t → GATF multipart parts cannot contain ':' → add this part by hand
   ! #6 HEAD /h → GATF API tests support GET/POST/PUT/DELETE/PATCH only → skipped
```

(On a terminal without a UTF-8 locale the arrows print as `?`.)

## What is converted

| Postman | GATF |
|---|---|
| Each request (folders flattened, `Folder / Request` names) | A `TestCase` with `url`, `method`, `headers`, `content`, in collection order (`sequence`) |
| `{{variable}}` | `${variable}`; collection variables are printed so you can add them to `gatfTestDataConfig.globalVariables` |
| Bearer and other auth on the collection or a folder | The matching `Authorization` header on each case |
| `pm.response.to.have.status(...)` in test scripts | `expectedResCode` |
| Form-data and file uploads | `multipartContent` |

Not converted, and listed under "need review": pre-request and test scripts beyond status checks (rewrite body checks as `expectedNodes`, captures as `workflowContextParameterMap`), `HEAD` and `OPTIONS` requests, and multipart fields with `:` in them.

## After the import

1. Move the printed variables into the config:

   ```json title="gatf-config.json"
   {
     "baseUrl": "https://api.demo.test",
     "testCaseDir": "tests",
     "outFilesDir": "out",
     "gatfTestDataConfig": {
       "globalVariables": { "token": "env.DEMO_API_TOKEN" }
     }
   }
   ```

   Replace secrets with `env.NAME` references (see [Secrets](../concepts/secrets.md)).
2. Move the `.xml` file into `testCaseDir` and run `gatf test`.
3. Add `expectedNodes` and captures where the Postman tests checked or saved body values. [Workflows](../concepts/workflows.md) shows the patterns.

`--name <suite>` sets the output name, `-o <file>` the output path, and `-` reads the collection from stdin.
