# seleasy for VS Code (GATF)

Language support for GATF's **seleasy** UI-test DSL (`.sel` files): syntax highlighting,
diagnostics, completion, hover, and a debugger that drives a running GATF server.

This is a sibling to the in-browser editor shipped with the GATF config-tool SPA. The two are kept
in step on purpose — the highlighting and the command catalog are **generated from the same
sources** the web editor and the transpiler use, so behaviour matches.

## Features

- **Syntax highlighting** — a TextMate grammar generated from `docs/seleasy/commands.json` (itself
  generated from `Command.java`) merged with the CM6 web editor's highlight map
  (`frontend/src/editor/seleasyLang.ts`). Keywords, atoms/browser targets, selectors (`id@`, `css@`,
  …), `@`-directives, providers (`#sql`, `#mongo`, …), inline Java (`#j`), embedded code blocks
  (`<<< … >>>`, `begin code in … end code`), strings, `${vars}`, numbers, and conditionals.
  A trailing `//` comment is highlighted, but a `//` inside a URL (`https://…`) or a string is not
  (Phase 7 parity with the transpiler).
- **Diagnostics** — an offline structural checker mirroring `tools/smoke/sel-lint.py`: unbalanced
  `{}` / `[]`, unterminated block comments and code blocks, and (opt-in) unrecognised leading
  keywords. Runs with no server attached. Enable keyword warnings with `seleasy.strictDiagnostics`.
- **Completion & hover** — the command catalog drives completion (keywords insert a trailing space
  when they require an argument) and hover shows the backing `Command` class and match kind.
- **Debugging (F5)** — an inline Debug Adapter that bridges VS Code's debugger to a **running GATF
  server** using its existing `PUT /reports?action=debug` protocol: breakpoints, step in/out/over,
  continue, pause, current-line and call-file tracking.

## Build

```
cd vscode-extension
npm install
npm run gen        # regenerate the grammar + bundled catalog from the repo sources
npm run compile    # esbuild -> dist/extension.js + dist/server.js
npm run typecheck  # tsc --noEmit
npm run test:grammar
```

`npm run gen` runs `tools/gen-tmgrammar/gen_tmgrammar.py` (stdlib Python 3). Do not hand-edit
`syntaxes/seleasy.tmLanguage.json` or `src/commands.json` — they are generated. Package with
`vsce package` once `dist/` is built (`vscode:prepublish` runs gen + compile).

## Debugging against a GATF server

The debugger talks to a live GATF config-tool server; start GATF first, then launch. Settings:

- `seleasy.gatfUrl` — base URL of the server (default `http://localhost:9080`).
- `seleasy.gatfToken` — appended as `?token=` when the server has auth enabled.

A minimal `launch.json`:

```json
{
  "type": "seleasy",
  "request": "launch",
  "name": "Debug seleasy (current file)",
  "testcaseFile": "${relativeFile}",
  "stopOnEntry": true
}
```

**Path assumption.** `testcaseFile` must be the file name **as GATF knows it** — i.e. relative to
the configured test-case directory. When you open that test-case directory as the workspace root,
`${relativeFile}` is exactly right (and is the default when no `launch.json` is present). If your
workspace root sits above the test-case dir, set `testcaseFile` to the GATF-relative path yourself.
Stack frames resolve files back to disk best-effort (absolute → workspace root → launched file's
directory).

**Transport.** Requests use the same HTTP debug protocol as the SPA online debugger, over Node
`http`/`https` (no extra runtime dependency, so it works on the Node the VS Code engine ships). The
WebSocket debug channel is a latency optimisation on the server side and is **not required** for the
extension — commands ride the HTTP protocol either way. Variables are limited to execution state
(current file/line/session) because that is what this protocol exposes.

## Layout

```
vscode-extension/
  package.json               manifest (language, grammar, debugger, settings)
  language-configuration.json comments / brackets / auto-close
  syntaxes/seleasy.tmLanguage.json   generated grammar
  src/
    extension.ts             activate: start LSP client + register debug adapter
    server.ts                LSP: diagnostics / completion / hover
    seleasyDebug.ts          inline DAP bridging /reports?action=debug
    commands.json            generated catalog (bundled for the LSP)
  test/grammar.test.mjs      tokenises sample lines under real Oniguruma
  esbuild.mjs                bundles extension.js + server.js
```
