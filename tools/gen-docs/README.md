# tools/gen-docs

The docs generator and docs gate. JDK only: it parses the GATF sources with the JDK compiler's tree API, so it needs no build and no classpath.

```bash
java tools/gen-docs/GenDocs.java generate   # rewrite docs/reference/*.md from the sources
java tools/gen-docs/GenDocs.java check      # what DocsDriftTest runs first; exit 1 with a list of problems
```

## What it generates

| Page | From |
|---|---|
| `docs/reference/configuration.md` | `GatfExecutorConfig` and every `com.gatf` type its fields reference (fields, types, initialisers as defaults) |
| `docs/reference/test-case-model.md` | `TestCase` and the types it references |
| `docs/reference/cli.md` | `dist/bin/gatf` (launcher commands), `GatfCli.main` (jar commands), each command's `USAGE` constant and every flag literal its parser accepts |
| `docs/reference/rest-api.md` | `GatfConfigToolUtil` routes; per handler: HTTP methods, `getParameter` names, `action`/`sub`/`configType`/`pluginType` values |
| `docs/reference/seleasy-keywords.md` | `docs/seleasy/commands.json` |
| `docs/reference/index.md` | fixed |

`docs/reference/` is generated only: never edit it by hand.

## Descriptions

Each field, command, flag, route and selector value needs a description:

1. javadoc on the field (config and case model), which wins; or
2. a line in `descriptions/*.properties`:
   - `config.properties`: `Class` (type intro) and `Class.field`;
   - `case-model.properties`: the same for the case model;
   - `cli.properties`: `launcher.<cmd>`, `command.<cmd>`, `flag.<cmd>.<flag>`, optional `notes.<cmd>`;
   - `rest.properties`: `/route` and `/route#value`.

A value starting with `@internal <reason>` lists the field under "not meant to be set in a config file". Descriptions are Markdown table cells: put `{`, `}`, `<`, `>` and `|` in code spans. A missing description, or a description for something that no longer exists, fails the check.

`descriptions/seleasy-exempt.properties` lists `commands.json` keywords that `docs/seleasy/REFERENCE.md` need not document, with the reason (keyword with spaces as `_`).

## What `check` verifies

- the generated pages match the sources;
- every description is present and none is stale;
- every keyword in `commands.json` appears in a code block or code span of `REFERENCE.md`;
- every relative link in `README.md` and `docs/**` resolves (file and heading anchor, GitHub slug rules); links from `docs/` stay inside `docs/` (the docs site cannot serve other files: link `https://github.com/sumeetchhetri/gatf/blob/master/<path>` instead), so images live under `docs/` too;
- pages under `docs/guide`, `docs/how-to`, `docs/reference` and `docs/intro.md` have front matter (`title`, `sidebar_position`), tag every code block with a language, and use portable Markdown: no HTML or JSX, no `{` `}` outside code, no `:::` admonitions, no MDX import/export;
- seleasy blocks are tagged `sel`.

`DocsDriftTest` (alldep-jar tests) then checks the pages against the runtime classes (Jackson's properties, the CLI usage strings, the importer and exporter ids) and parses every snippet: `sel` blocks through `Command` (titled blocks on a page, such as `title="lib/auth.sel"` or `title="app.props"`, are written into a temp project first, so `import`/`include`/`config` examples resolve), JSON and XML for well-formedness, and blocks titled `gatf-config*.json` / `tests/*.json` or rooted at `<gatf-execute-config>` / `<TestCases>` / `<TestCase>` bound strictly to the model (unknown fields fail). A block whose meta contains `nocheck` is skipped (not allowed in `docs/guide` or `docs/how-to`).

## Screenshots

`screenshots.mjs` regenerates the images under `docs/guide/img/` with Playwright against a running `gatf ui`; see the comment at its top.
