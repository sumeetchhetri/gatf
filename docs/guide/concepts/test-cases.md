---
title: Test cases
sidebar_position: 2
---

# Test cases

An API test case is one HTTP (or SOAP) request plus what you expect back. Test files live under `testCaseDir`; each file holds one or more cases.

## Anatomy

```xml title="tests/users.xml"
<TestCases>
  <TestCase>
    <name>Create user @smoke</name>
    <description>Creates a user and checks the echo</description>
    <url>api/users</url>
    <method>POST</method>
    <headers>
      <Content-Type>application/json</Content-Type>
    </headers>
    <content>{"name": "alice"}</content>
    <expectedResCode>201</expectedResCode>
    <expectedResContentType>application/json</expectedResContentType>
    <expectedNodes>
      <string>id</string>
      <string>name</string>
    </expectedNodes>
  </TestCase>
</TestCases>
```

| Part | Fields |
|---|---|
| The request | `url` (appended to the config's `baseUrl`, or the case's own `baseUrl`), `method`, `headers`, `content` or `contentFile`, `exQueryPart`, `multipartContent` |
| Status and type | `expectedResCode` (`0` = don't check), `expectedResContentType` (a prefix match) |
| Body assertions | `expectedNodes`: each entry must exist, as a JSON path for JSON responses and an XPath for XML. (`logicalValidations` holds Velocity expressions over variables; in 5.0.0 they do not fail a plain case, see the [field reference](../../reference/test-case-model.md#testcase).) |
| Captures | `workflowContextParameterMap`: response values saved as `${name}` for later cases (see [Workflows](workflows.md)) |
| Control | `skipTest`, `sequence`, `executeOnCondition`, `relatedTestName`, `preWaitMs`/`postWaitMs`, `abortOnInvalidStatusCode` |
| Load | `perfConfig`: hand the case to a load engine (see [First load test](../getting-started/first-load-test.md)) |

Values may contain `${name}` placeholders, resolved from global variables, captured workflow variables and data-provider rows.

**Tags.** A word such as `@smoke` in the name or description is a tag: `gatf test --tag smoke` runs only those cases. The title used by `--grep` is `<file> › <name>`.

## Formats

The same case in JSON (a file holds an array):

```json title="tests/users.json"
[
  {
    "name": "Create user @smoke",
    "url": "api/users",
    "method": "POST",
    "headers": { "Content-Type": "application/json" },
    "content": "{\"name\": \"alice\"}",
    "expectedResCode": 201,
    "expectedResContentType": "application/json",
    "expectedNodes": ["id", "name"]
  }
]
```

CSV (one case per row, for simple requests) and XLS/XLSX are also read; the column order is in [Configuration and test cases](../../configuration.md#formats).

## Order

Files run in the order the file system lists them, by file name when the config sets `isOrderByFileName`, and `orderedFiles` puts chosen files first. Inside a file, cases run by `sequence`. A case that logs in and captures a token should come first; see [Workflows](workflows.md).

## Reference

Every field: [test case model](../../reference/test-case-model.md).
