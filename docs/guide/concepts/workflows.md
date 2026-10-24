---
title: Workflows
sidebar_position: 4
---

# Workflows

A workflow is a chain of API calls where later calls use values from earlier responses: log in, keep the token, create a user, read it back by its id.

## Capture and reuse

`workflowContextParameterMap` copies values out of a response into variables. The key is the variable name and the value says where to read it: a JSON path (or XPath for XML), or one of `#responseHeader[name]`, `#responseCookie[name]`, `#responseMappedValue[…]`, `#responseMappedCount[…]`. Every later case can use the variable as `${name}`.

```xml title="tests/users.xml"
<TestCases>
  <TestCase>
    <name>Login</name>
    <url>auth/login</url>
    <method>POST</method>
    <headers>
      <Content-Type>application/json</Content-Type>
    </headers>
    <content>{"username": "${username}", "password": "${password}"}</content>
    <expectedResCode>200</expectedResCode>
    <expectedNodes>
      <string>token</string>
    </expectedNodes>
    <workflowContextParameterMap>
      <authToken>token</authToken>
    </workflowContextParameterMap>
    <sequence>1</sequence>
  </TestCase>
  <TestCase>
    <name>Create user</name>
    <url>api/users</url>
    <method>POST</method>
    <headers>
      <Authorization>Bearer ${authToken}</Authorization>
      <Content-Type>application/json</Content-Type>
    </headers>
    <content>{"name": "alice", "role": "admin"}</content>
    <expectedResCode>201</expectedResCode>
    <workflowContextParameterMap>
      <userId>id</userId>
      <userUrl>#responseHeader[Location]</userUrl>
    </workflowContextParameterMap>
    <sequence>2</sequence>
  </TestCase>
  <TestCase>
    <name>Read user</name>
    <url>api/users/${userId}</url>
    <method>GET</method>
    <headers>
      <Authorization>Bearer ${authToken}</Authorization>
    </headers>
    <expectedResCode>200</expectedResCode>
    <expectedNodes>
      <string>name</string>
    </expectedNodes>
    <sequence>3</sequence>
  </TestCase>
</TestCases>
```

`${username}` and `${password}` here are [global variables](environments.md) from the config. The example runs as is against the sample project's local server (`node sample-project/load-server/server.js`, port 8880): see [API workflows](../../how-to/api-workflows.md). Variable names start with a letter or underscore and contain letters, digits and underscores.

## Order

Captures only help if the capturing case runs first:

- inside a file, cases run by `sequence`;
- across files, set `isOrderByFileName` (alphanumeric file names, e.g. `01-auth.xml`, `02-orders.xml`) or list files in `orderedFiles`.

## Dependencies and conditions

- **`relatedTestName`** names another case to run right after this one succeeds. `gatf test --grep` and `--tag` pull a selected case's related cases in automatically.
- **`executeOnCondition`** is a Velocity expression over the variables; when false the case is skipped and the reason recorded.
- **`preExecutionDataSourceHookName` / `postExecutionDataSourceHookName`** run a [data-source hook](data-providers.md#hooks) (for example SQL set-up and clean-up) around the call.

## Automatic authentication

For APIs where every call needs a session token, the config can do the login for you: `authEnabled`, `authUrl` (the login case's `url`), `authExtractAuth` (where the token is and how to send it) and `authParamsDetails` (where the credentials go). Cases marked `secure` then carry the token. The fields are described in the [configuration reference](../../reference/configuration.md#gatfexecutorconfig).

## In UI scripts

seleasy scripts call API cases with the `api` plugin (`api <test-case-file>@<test-name>`) and `curl`, which share the browser's cookies by default (`browserApiSession`). See the [language reference](../../seleasy/REFERENCE.md#api-plugin).
