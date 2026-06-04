# seleasy language assets

Generated, single-source-of-truth artifacts for the seleasy DSL. Used by the
Phase-0 smoke guardrail today and by the editor / VSCode extension later.

- `commands.json` — machine-readable catalog of every statement keyword, its
  match kind (`prefix` / `exact` / `regex`), whether it takes/requires an
  argument, and the backing `Command` subclass. **Generated — do not hand-edit.**
- `../../alldep-jar/seleasy.ebnf` — human-readable reference grammar for the same
  dispatch. Keep it in step with `commands.json`.

## Regenerate the catalog

    python3 tools/gen-commands/gen_commands.py

It parses the `parse()` / `handleActions()` dispatch in
`alldep-jar/src/main/java/com/gatf/selenium/Command.java` (no JDK/Maven needed).

## Lint .sel scripts against it

    python3 tools/smoke/sel-lint.py [--regen] [--strict] sample/*.sel

Structural checks (brace/value-list balance, unterminated comment/code blocks)
fail the run; unrecognised leading keywords warn (or fail under `--strict`).
This is the offline half of `tools/smoke/run-smoke.sh` and runs in CI.
