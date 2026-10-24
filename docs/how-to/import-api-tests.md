---
title: Import API tests
sidebar_position: 3
---

# Import API tests from OpenAPI, cURL or HAR

`gatf import <type> <file>` writes a GATF test file and lists what it could not convert. Postman, Playwright and JMeter imports have their own pages under [Migration](../guide/migration/index.md).

## OpenAPI and Swagger

OpenAPI 3.0/3.1 and Swagger 2.0, JSON or YAML:

```bash
gatf import openapi petstore-v3.yaml --negative
```

```text
Imported 13 test case(s) → petstore-v3.xml  {format=openapi 3.0.3, title=Swagger Petstore - OpenAPI 3.0, baseUrl=https://petstore3.swagger.io, operations=10, testCases=13}
  variables for gatf-config globalVariables: {authToken=, api_key=}
  3 item(s) need review:
   ! POST /pet callback onPetAdded — callbacks are requests made by the API — not converted
   ! GET /pet/{petId} links — response links are not converted — chain with workflowContextParameterMap
   ! POST /pet/{petId}/uploadImage body application/octet-stream — binary request body — set contentFile on the test case
```

- One case per operation, with parameters and bodies from the spec's examples and defaults (or built from the schema), and the first 2xx status as `expectedResCode`. Operation tags become `@tags`.
- Security schemes become `${authToken}` and API-key placeholders: fill them in `globalVariables`.
- Each case keeps a `contract` pointer to its operation, and the spec is saved beside the file. A run then also fails a response whose status or `Content-Type` the operation does not declare, or whose JSON body breaks the response schema, naming the JSON pointer (for example `/photoUrls: required property missing`).
- `--negative` adds cases that drop a required parameter or send a wrong-typed property and expect `400` (or `422` when only that is declared).
- Importing again over the same file lists the operations added, removed or changed since the last import.

## cURL

A `curl` command from a terminal, the browser's "Copy as cURL", or a GATF report:

```bash
echo "curl 'https://api.example.com/users' -H 'Accept: application/json'" | gatf import curl -
```

## HAR recordings

A browser's network export (or a GATF trace's `network.har`):

```bash
gatf import har recording.har --filter 'api\.example\.com'
```

`--filter` keeps only requests whose URL matches the regular expression. `gatf import flow network.har` turns the same recording into a load test instead; see [Load testing](load-testing.md#from-a-ui-flow).

All flags: [CLI reference](../reference/cli.md#gatf-import). The console's **Import** view does the same, including multi-file OpenAPI specs with `$ref`s to other files.
