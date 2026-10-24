# GATF — Generic Automated Test Framework

[![Build gatf](https://github.com/sumeetchhetri/gatf/actions/workflows/ci-build.yml/badge.svg)](https://github.com/sumeetchhetri/gatf/actions/workflows/ci-build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**One tool for API, workflow, UI, load and performance testing.** GATF is a single Java 21 jar with a web console, a trace viewer, a step debugger and a VS Code extension.

- **API tests are data**: XML, JSON or CSV test cases, chained into workflows and fed by files, databases or inline data.
- **UI tests are written in [seleasy](docs/seleasy/REFERENCE.md)**, a compact automation language compiled to Selenium Java in-process: auto-wait, web-first assertions, semantic locators, network mocking, traces with DOM time travel.
- **The same suites scale** from one run to concurrent users, load engines (wperfk by default) and distributed agents.

## Install

macOS and Linux:

```bash
curl -fsSL https://github.com/sumeetchhetri/gatf/releases/latest/download/install.sh | bash
```

Windows (PowerShell):

```powershell
irm https://github.com/sumeetchhetri/gatf/releases/latest/download/install.ps1 | iex
```

Needs Java 21 or newer (the installer can download one). Offline installs, options and building from source: [Install](docs/guide/getting-started/install.md).

## Quick start

```bash
gatf version               # check the install
gatf ui                    # web console on http://127.0.0.1:9080/, project = the current folder
gatf test                  # run the suite in ./gatf-config.json (or gatf-config.xml)
```

[Getting started](docs/guide/getting-started/index.md) walks through a first API, UI and load test in about fifteen minutes.

## Documentation

| | |
|---|---|
| [Introduction](docs/intro.md) | What GATF is and where to start |
| [Getting started](docs/guide/getting-started/index.md) | Install, then a first API, UI and load test |
| [Concepts](docs/guide/concepts/index.md) | Configuration, test cases, seleasy, workflows, data providers, environments, secrets, traces |
| [How-to guides](docs/how-to/index.md) | One page per feature: mocking, auth state, visual and accessibility checks, load, CI, reports, export, MCP, plugins, … |
| [Reference](docs/reference/index.md) | Every config field, CLI command and flag, REST endpoint and seleasy keyword, generated from the source |
| [seleasy language reference](docs/seleasy/REFERENCE.md) | Every statement with syntax and examples |
| [Migration](docs/guide/migration/index.md) | From Postman, Playwright and JMeter |
| [Troubleshooting and FAQ](docs/guide/troubleshooting.md) | Common problems and the known issues of 5.0.0 |
| [Release notes](docs/guide/release-notes.md) | GATF 5.0.0 |

The full map, with the in-depth pages (configuration sample, browser modes, load tools, Windows), is on the [documentation home](docs/guide/index.md). The [VS Code extension](vscode-extension/README.md) and [end-to-end tests](e2e/README.md) have their own READMEs.

## Developing GATF

```bash
mvn -B install                                       # build + unit tests + integration tests + codegen goldens + JaCoCo report
cd frontend && npm ci && npx vitest run --coverage   # web console unit tests (coverage floors enforced)
cd e2e && npm ci && npx playwright test              # console e2e against a live server (see e2e/README.md)
java tools/gen-docs/GenDocs.java generate            # regenerate docs/reference from the sources
```

- **Docs gate:** `DocsDriftTest` (part of `mvn install`, or `java tools/gen-docs/GenDocs.java check` on its own) fails when a config field, CLI flag, REST endpoint or seleasy keyword is undocumented, when `docs/reference` is out of date, when a snippet does not parse, or when an internal link is broken. See [tools/gen-docs](tools/gen-docs/README.md).
- **Codegen goldens:** refresh with `mvn -pl alldep-jar -am test -Dtest=CodegenGoldenTest -Dsurefire.failIfNoSpecifiedTests=false -Dgatf.golden.update=true`, then commit the files under `alldep-jar/src/test/resources/codegen-golden/`. CI fails on a missing golden.
- **Coverage:** the JaCoCo report is at `alldep-jar/target/site/jacoco/index.html`, with its floor set by `gatf.coverage.line.min` in `pom.xml`.
- **Snapshot of the source tree** (for reviews): `tools/package-source.sh`.

Related: [test-html-generator-plugin](https://github.com/sumeetchhetri/test-html-generator-plugin) generates HTML pages for your REST endpoints.

## License

Apache License 2.0. See [LICENSE](LICENSE).
