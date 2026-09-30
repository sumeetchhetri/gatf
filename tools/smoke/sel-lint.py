#!/usr/bin/env python3
#    Copyright 2013-2026, Sumeet Chhetri
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
"""
sel-lint.py  --  Offline structural linter for seleasy (.sel) scripts.

This is the fast, dependency-free half of the Phase-0 smoke guardrail: it needs
no JDK, no Maven build, no browser and no Selenium endpoint, so it runs in CI on
every push. It does NOT replace the full parse->transpile->compile->run pipeline
(see run-smoke.sh) -- it catches the cheap, high-value regressions:

  FAIL (exit 1), always:
    - unbalanced block braces  {  }   (each must be on its own line, as the
      seleasy parser requires)
    - unbalanced value lists   [  ]
    - an unterminated /* ... */ block comment
    - an unterminated code block ( <<< / `begin code in` with no >>> / `end code`)
    - a broken/empty command catalog (missing core keywords like open/click)

  WARN (exit 0 by default, exit 1 under --strict):
    - a statement whose leading keyword is not in the generated catalog. seleasy
      legitimately falls through to plugin / NLP / subtest calls (e.g. `api ...`,
      `jsonwrite ...`), so unknown leading tokens are reported but not fatal
      unless you opt into --strict for a pure-core suite.

The keyword surface comes from docs/seleasy/commands.json, generated from the
real Command.java dispatch by tools/gen-commands/gen_commands.py. Pass --regen to
regenerate it first so the lint always reflects the current parser.

Usage:
    python3 tools/smoke/sel-lint.py [--regen] [--strict] [PATH ...]
Default PATH is sample/*.sel.
"""
import argparse
import glob
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG = REPO_ROOT / "docs/seleasy/commands.json"
GENERATOR = REPO_ROOT / "tools/gen-commands/gen_commands.py"
CORE_KEYWORDS = ("open", "goto", "click", "type", "if", "sleep")
ALPHA_TOK = re.compile(r"[a-z0-9_\-]+")


def strip_eol_comment(s: str) -> str:
    """Drop a trailing end-of-line // comment (Phase 7), mirroring
    Command.stripTrailingLineComment: the // must be whitespace-preceded so a URL scheme
    (http://, https://, file:///) is preserved, and quoted text is masked first so a // inside
    a string is not seen as a comment. A leading full-line // is left for the caller to handle."""
    masked = re.sub(r'"(?:\\.|[^"\\])*"', lambda m: "\0" * len(m.group()), s)
    masked = re.sub(r"'(?:\\.|[^'\\])*'", lambda m: "\0" * len(m.group()), masked)
    for i in range(1, len(masked) - 1):
        if masked[i] == "/" and masked[i + 1] == "/" and masked[i - 1] in " \t":
            return s[:i].rstrip()
    return s


class Catalog:
    def __init__(self, doc):
        cmds = doc["commands"]
        self.alpha = set()      # single-word alpha keywords
        self.multiword = []     # keywords containing spaces
        self.symbolic = []      # keywords beginning with a non-alnum char
        self.regex_bases = []   # regex verb bases (type/click/...)
        for c in cmds:
            kw = c["keyword"].lower()
            if c["match"] == "regex":
                self.regex_bases.append(kw)
                continue
            if " " in kw:
                self.multiword.append(kw)
            elif not kw[:1].isalnum():
                self.symbolic.append(kw)
            else:
                self.alpha.add(kw)
        self.multiword.sort(key=len, reverse=True)
        self.symbolic.sort(key=len, reverse=True)

    def known_leading(self, line: str) -> bool:
        """True if the statement's leading keyword is recognised by the parser."""
        L = line.strip().lower()
        if not L:
            return True
        if L.startswith("nowait "):   # M1 auto-wait opt-out prefix (Command.stripNoWait), not a keyword itself
            L = L[7:].lstrip()
        for kw in self.multiword:
            if L == kw or L.startswith(kw + " "):
                return True
        if not L[:1].isalnum():
            for kw in self.symbolic:
                if L.startswith(kw):
                    return True
        m = ALPHA_TOK.match(L)
        if m:
            w = m.group(0)
            if w in self.alpha:
                return True
            for rb in self.regex_bases:
                if w == rb or w.startswith(rb):
                    return True
        return False


def load_catalog(regen: bool) -> Catalog:
    if regen:
        subprocess.run([sys.executable, str(GENERATOR)], check=True)
    if not CATALOG.exists():
        sys.exit(f"error: catalog not found: {CATALOG} (run with --regen)")
    doc = json.loads(CATALOG.read_text(encoding="utf-8"))
    missing = [k for k in CORE_KEYWORDS
               if k not in {c["keyword"].lower() for c in doc["commands"]}]
    if missing or doc.get("count", 0) == 0:
        sys.exit(f"error: command catalog looks broken "
                 f"(count={doc.get('count')}, missing core keywords={missing})")
    return Catalog(doc)


def lint_file(path: Path, cat: Catalog):
    errors, warnings = [], []
    in_block_comment = in_code = False
    block_depth = 0
    vlist_depth = 0
    block_open_line = code_open_line = comment_open_line = 0

    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    for n, raw in enumerate(lines, 1):
        s = raw.strip()

        if in_block_comment:
            if "*/" in s:
                in_block_comment = False
            continue
        if in_code:
            if s == ">>>" or s == "end code":
                in_code = False
            continue
        if vlist_depth > 0:
            if s.startswith("]"):
                vlist_depth -= 1
            elif s.startswith("["):
                vlist_depth += 1
            # everything else here is a value literal, not a statement
            continue

        if not s:
            continue
        if s.startswith("/*"):
            if "*/" not in s:
                in_block_comment = True
                comment_open_line = n
            continue
        if s.startswith("//"):
            continue
        # strip a trailing // comment so brace/vlist/keyword classification sees a clean statement
        s = strip_eol_comment(s)
        if not s:
            continue
        if s == "<<<" or s.startswith("<<<(") or s.startswith("begin code in"):
            in_code = True
            code_open_line = n
            continue
        if s.startswith("["):
            vlist_depth += 1
            continue
        if s.startswith("]"):
            vlist_depth -= 1
            if vlist_depth < 0:
                errors.append((n, "value-list close `]` without a matching `[`"))
                vlist_depth = 0
            continue
        if s == "{":
            block_depth += 1
            block_open_line = n
            continue
        if s == "}":
            block_depth -= 1
            if block_depth < 0:
                errors.append((n, "block close `}` without a matching `{`"))
                block_depth = 0
            continue

        # a normal statement line
        if not cat.known_leading(s):
            tok = s.split()[0] if s.split() else s
            warnings.append((n, f"unrecognised leading keyword `{tok}` "
                                f"(plugin/NLP/subtest call, or a removed command?)"))

    if in_block_comment:
        errors.append((comment_open_line, "unterminated block comment `/*` (no `*/`)"))
    if in_code:
        errors.append((code_open_line, "unterminated code block (no `>>>` / `end code`)"))
    if block_depth > 0:
        errors.append((block_open_line, f"{block_depth} unclosed block brace(s) `{{`"))
    if vlist_depth > 0:
        errors.append((0, f"{vlist_depth} unclosed value list(s) `[`"))
    return errors, warnings


def main():
    ap = argparse.ArgumentParser(description="Offline structural linter for seleasy .sel scripts")
    ap.add_argument("paths", nargs="*", help="files or globs (default: sample/*.sel)")
    ap.add_argument("--regen", action="store_true", help="regenerate commands.json from Command.java first")
    ap.add_argument("--strict", action="store_true", help="treat unrecognised keywords as failures")
    args = ap.parse_args()

    cat = load_catalog(args.regen)

    targets = []
    for p in (args.paths or [str(REPO_ROOT / "sample" / "*.sel")]):
        targets.extend(sorted(glob.glob(p)))
    if not targets:
        sys.exit("error: no .sel files matched")

    total_err = total_warn = 0
    for t in targets:
        path = Path(t)
        errors, warnings = lint_file(path, cat)
        rp = path.resolve()
        rel = rp.relative_to(REPO_ROOT) if REPO_ROOT in rp.parents else path
        status = "FAIL" if errors or (args.strict and warnings) else "ok"
        print(f"[{status}] {rel}  ({len(errors)} error, {len(warnings)} warn)")
        for ln, msg in errors:
            print(f"    ERROR {rel}:{ln}: {msg}")
        for ln, msg in warnings:
            print(f"    warn  {rel}:{ln}: {msg}")
        total_err += len(errors)
        total_warn += len(warnings)

    print(f"\n{len(targets)} file(s): {total_err} error(s), {total_warn} warning(s)")
    if total_err or (args.strict and total_warn):
        sys.exit(1)


if __name__ == "__main__":
    main()
