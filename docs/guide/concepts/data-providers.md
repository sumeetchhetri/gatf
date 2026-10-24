---
title: Data providers
sidebar_position: 5
---

# Data providers

A data provider is a named list of rows. Each row is a set of variables (the provider's `providerProperties`). An API case that names a provider runs once per row; a seleasy `#provider` loop runs its block once per row. Providers are defined in the config, under `gatfTestDataConfig.providerTestDataList`.

## Inline and file providers

```json title="gatf-config.json"
{
  "baseUrl": "http://localhost:8880",
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "gatfTestDataConfig": {
    "providerTestDataList": [
      {
        "providerName": "newUsers",
        "enabled": true,
        "providerClass": "com.gatf.executor.dataprovider.InlineValueTestDataProvider",
        "providerProperties": "name,role",
        "args": ["alice,admin", "bob,viewer"]
      },
      {
        "providerName": "csvUsers",
        "enabled": true,
        "providerClass": "com.gatf.executor.dataprovider.FileTestDataProvider",
        "providerProperties": "name,role",
        "args": ["data/users.csv", "csv"]
      }
    ]
  }
}
```

Set `"enabled": true` on every provider: in GATF 5.0.0 a provider without it is disabled and the log says `Provider newUsers is Disabled...`.

`data/users.csv` has one row per line, columns in `providerProperties` order:

```text
carol,editor
dave,viewer
eve,admin
```

A case uses a provider with `repeatScenarioProviderName`, and the row's values as `${name}` placeholders:

```xml title="tests/create.xml"
<TestCases>
  <TestCase>
    <name>Create user per row</name>
    <url>api/users</url>
    <method>POST</method>
    <headers>
      <Content-Type>application/json</Content-Type>
    </headers>
    <content>{"name": "${name}", "role": "${role}"}</content>
    <expectedResCode>201</expectedResCode>
    <repeatScenarioProviderName>newUsers</repeatScenarioProviderName>
  </TestCase>
</TestCases>
```

The case is sent twice, once for `alice` and once for `bob`. On the console each extra row is listed as `retry #1`, `retry #2`, …. File providers read `csv`, `xls`, `xlsx`, `xml` and `json`; the path is looked up under `testCaseDir` first, then the project folder.

## Other providers

| `providerClass` (package `com.gatf.executor.dataprovider`) | Rows from |
|---|---|
| `InlineValueTestDataProvider` | `args`, one comma-separated row each |
| `FileTestDataProvider` | a file: `args` = path, type |
| `StateFulFileTestDataProvider` | a file, split so that each simulated user (`concurrentUserSimulationNum`) gets its own data file |
| `RandomValueTestDataProvider` | generated values: `args` = value types (`alpha`, `alphanum`, `number`, `boolean`, `date(format)`, …) and a row count |
| your own class implementing `TestDataProvider` | anything |

Instead of a `providerClass`, a provider can read rows from a **data source** (`dataSourceName` + `queryStr`): an SQL database through JDBC (`SQLDatabaseTestDataSource`) or MongoDB (`MongoDBTestDataSource`), defined in `dataSourceList`. A `live` provider re-runs its query every time it is used. The commented sample in [Configuration and test cases](../../configuration.md#executor-configuration) shows each kind.

## Hooks

`dataSourceHooks` are named lists of queries run against a data source at start-up (`executeOnStart`), at shutdown (`executeOnShutdown`), or around a test case that names them in `preExecutionDataSourceHookName` / `postExecutionDataSourceHookName`: typical for inserting fixtures and cleaning up.

## In seleasy

```sel title="users.sel"
open chrome
goto https://shop.example.com/admin/users
#provider newUsers
{
	click role@button[name='Add user']
	type "${name}" label@Name
	type "${role}" label@Role
	click role@button[name=Save]
}
```

seleasy also has `#sql`, `#mongo` and `#file` loops, and providers built from page elements (`#transient-provider`): see the [language reference](../../seleasy/REFERENCE.md#variables-data--files).

## Reference

All provider, data-source and hook fields: [configuration reference](../../reference/configuration.md#gatftestdataconfig).
