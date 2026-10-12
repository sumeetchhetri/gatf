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
gen_commands.py  --  Generate a machine-readable catalog of the seleasy DSL.

Source of truth: the statement-dispatch chain in
    alldep-jar/src/main/java/com/gatf/selenium/Command.java

seleasy is NOT parsed by a generated parser. Every statement keyword/prefix is
recognised by the big if / else-if chain in Command.parse(...) (and the verb
sub-dispatch in Command.handleActions(...)). This script reads those two methods
and extracts, for every branch, the literal keyword(s) it matches and the
Command subclass it constructs (the `comd = new XxxCommand(...)` assignment).

Output: docs/seleasy/commands.json  --  the authoritative keyword surface, used
by tools/smoke/sel-lint.py and (later) by the editor / VSCode extension for
autocomplete and syntax checking.

This is intentionally dependency-free (stdlib only) and runnable without a JDK
or a Maven build, so it works as an offline guardrail in CI.
"""
import json
import re
import sys
from pathlib import Path

# --- locate Command.java relative to repo root (this file lives in tools/gen-commands/) ---
REPO_ROOT = Path(__file__).resolve().parents[2]
COMMAND_JAVA = REPO_ROOT / "alldep-jar/src/main/java/com/gatf/selenium/Command.java"
OUT = REPO_ROOT / "docs/seleasy/commands.json"

# Regex-only prefixes that live in Command.java as compiled Pattern constants
# (typeExStr / clickExStr) rather than string literals, so the literal scanner
# below cannot see them. They are added explicitly and kept in sync by hand.
#   typeExStr = ^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?
#   clickExStr = ^click(fo|nf)?
REGEX_KEYWORDS = [
    {"keyword": "type", "match": "regex", "takesArg": True, "class": "TypeCommand",
     "qualifiers": ["bl", "ch", "bk", "cl", "fo"]},
    {"keyword": "sendkeys", "match": "regex", "takesArg": True, "class": "TypeCommand",
     "qualifiers": ["bl", "ch", "bk", "cl", "fo"]},
    {"keyword": "chord", "match": "regex", "takesArg": True, "class": "TypeCommand",
     "qualifiers": ["bl", "ch", "bk", "cl", "fo"]},
    {"keyword": "randomize", "match": "regex", "takesArg": True, "class": "TypeCommand",
     "qualifiers": ["bl", "ch", "bk", "cl", "fo"]},
    {"keyword": "click", "match": "regex", "takesArg": True, "class": "ClickCommand",
     "qualifiers": ["fo", "nf"]},
]

# M6: statements matched by Command's HOOK_BLOCK (whole line) and SUBTEST_MARKER (`only|skip|fixme subtest ...`)
# Pattern constants; kept in sync by hand like the list above.
PATTERN_KEYWORDS = [
    {"keyword": k, "match": "exact", "requiresArg": False, "canBeBare": True, "class": "SubTestCommand"}
    for k in ("beforeAll", "beforeEach", "afterEach", "afterAll")
] + [
    {"keyword": k, "match": "prefix", "requiresArg": True, "canBeBare": False, "class": "SubTestCommand"}
    for k in ("only", "skip", "fixme")
]

# M1: `nowait <verb> ...` / `nowait ?? ...` opt-out prefix, stripped by Command.stripNoWait before dispatch.
NOWAIT_KEYWORD = {"keyword": "nowait", "match": "prefix", "requiresArg": True, "canBeBare": False, "class": "Command"}
# M1: `<verb>! ...` is the same opt-out; the verbs are read from Command.NOWAIT_VERBS and flagged bang=true.
NOWAIT_VERBS_RE = re.compile(r'NOWAIT_VERBS\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);')

# Verbs whose authoritative class comes from handleActions(): when the same
# keyword is also seen in the main chain (routed via the global-timeout wrapper
# to WaitTill...), the handleActions class wins.
ACTION_METHOD = "handleActions"


def extract_method_body(src: str, signature_repattern: str) -> str:
    """Return the brace-balanced body of the first method whose declaration
    matches signature_repattern. Brace counting ignores braces inside strings,
    chars and // or /* */ comments so DSL literals like "{" don't skew it."""
    m = re.search(signature_repattern, src)
    if not m:
        raise SystemExit(f"could not locate method: {signature_repattern}")
    i = src.index("{", m.end())
    depth = 0
    j = i
    in_str = in_chr = in_line = in_block = False
    esc = False
    while j < len(src):
        c = src[j]
        nxt = src[j + 1] if j + 1 < len(src) else ""
        if in_line:
            if c == "\n":
                in_line = False
        elif in_block:
            if c == "*" and nxt == "/":
                in_block = False
                j += 1
        elif in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        elif in_chr:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == "'":
                in_chr = False
        else:
            if c == "/" and nxt == "/":
                in_line = True
                j += 1
            elif c == "/" and nxt == "*":
                in_block = True
                j += 1
            elif c == '"':
                in_str = True
            elif c == "'":
                in_chr = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return src[i:j + 1]
        j += 1
    raise SystemExit("unbalanced braces while scanning method body")


# match-call literals in branch conditions: cmd.startsWith("x") / equals("x") /
# equalsIgnoreCase("x") / toLowerCase().equals("x") etc. We only care about the
# call name + the string literal argument.
LITERAL_RE = re.compile(r'\.(startsWith|equals|equalsIgnoreCase)\(\s*"((?:[^"\\]|\\.)*)"')
# the statement's own command object is always assigned to `comd` (any class name: WaitForReady has no
# "Command" suffix, and requiring one attributed `waitready` to the next branch's MaximizeCommand)
COMD_NEW_RE = re.compile(r'comd\s*=\s*new\s+([A-Za-z_]\w*)\b')


def blank_comments(body: str) -> str:
    """Replace // line and /* */ block comments with equal-length runs of
    spaces (newlines preserved) so commented-out dispatch branches contribute
    no keywords while source offsets used for pairing stay intact. String and
    char literals are left untouched."""
    out = []
    j = 0
    n = len(body)
    in_str = in_chr = esc = False
    while j < n:
        c = body[j]
        nxt = body[j + 1] if j + 1 < n else ""
        if in_str:
            out.append(c)
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
            j += 1
        elif in_chr:
            out.append(c)
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == "'":
                in_chr = False
            j += 1
        elif c == "/" and nxt == "/":
            while j < n and body[j] != "\n":
                out.append(" ")
                j += 1
        elif c == "/" and nxt == "*":
            while j < n and not (body[j] == "*" and j + 1 < n and body[j + 1] == "/"):
                out.append("\n" if body[j] == "\n" else " ")
                j += 1
            if j < n:
                out.append("  ")
                j += 2
        else:
            if c == '"':
                in_str = True
            elif c == "'":
                in_chr = True
            out.append(c)
            j += 1
    return "".join(out)


def unescape_java(s: str) -> str:
    return (s.replace('\\"', '"').replace("\\\\", "\\")
             .replace("\\t", "\t").replace("\\n", "\n").replace("\\r", "\r"))


def scan(body: str):
    """Yield (pos, kind, payload) tokens across a method body, in source order.
    kind is 'lit' (payload=(call, literal)) or 'new' (payload=class)."""
    toks = []
    for m in LITERAL_RE.finditer(body):
        toks.append((m.start(), "lit", (m.group(1), unescape_java(m.group(2)))))
    for m in COMD_NEW_RE.finditer(body):
        toks.append((m.start(), "new", m.group(1)))
    toks.sort(key=lambda t: t[0])
    return toks


def pair_keywords(body: str):
    """For each keyword literal, attach it to the NEAREST following
    `comd = new XxxCommand`. When a keyword appears more than once, keep the
    occurrence closest to its constructor (handles the nested mobile branch,
    where outer-condition literals repeat right before their inner comd=new)."""
    toks = scan(body)
    news = [(p, cls) for (p, k, cls) in toks if k == "new"]
    # keyword -> aggregated forms across every branch that matches it
    agg = {}
    for pos, kind, payload in toks:
        if kind != "lit":
            continue
        call, literal = payload
        if not literal:
            continue
        follow = next(((np, ncls) for (np, ncls) in news if np > pos), None)
        if follow is None:
            continue
        dist = follow[0] - pos
        kw = literal.strip()
        key = kw.lower()
        req_space = call == "startsWith" and literal.endswith(" ")
        pfx_nospace = call == "startsWith" and not literal.endswith(" ")
        bare = call in ("equals", "equalsIgnoreCase")
        a = agg.setdefault(key, {"keyword": kw, "cls": None, "best": 1 << 62,
                                 "req_space": False, "pfx_nospace": False,
                                 "bare": False})
        a["req_space"] |= req_space
        a["pfx_nospace"] |= pfx_nospace
        a["bare"] |= bare
        # class from the branch whose constructor is nearest this literal
        if dist < a["best"]:
            a["best"] = dist
            a["cls"] = follow[1]
    out = {}
    for key, a in agg.items():
        is_prefix = a["req_space"] or a["pfx_nospace"]
        out[key] = {
            "keyword": a["keyword"],
            "match": "prefix" if is_prefix else "exact",
            # requiresArg: the prefix must be followed by a space + argument and
            # cannot stand alone (e.g. `open `); false when a bare form exists
            # (e.g. `clear` / `clear <locator>`) or the prefix takes no space.
            "requiresArg": bool(a["req_space"] and not a["bare"] and not a["pfx_nospace"]),
            "canBeBare": bool(a["bare"] or a["pfx_nospace"] or not is_prefix),
            "class": a["cls"],
        }
    return out


def main():
    src = COMMAND_JAVA.read_text(encoding="utf-8", errors="replace")

    parse_body = blank_comments(extract_method_body(
        src, r'static\s+Command\s+parse\s*\('))
    actions_body = blank_comments(extract_method_body(
        src, r'static\s+Command\s+handleActions\s*\('))

    main_map = pair_keywords(parse_body)
    action_map = pair_keywords(actions_body)

    # handleActions is authoritative for the verbs it owns
    merged = dict(main_map)
    merged.update(action_map)

    # add the regex-only prefixes (type/click family)
    for e in REGEX_KEYWORDS:
        merged[e["keyword"].lower()] = {
            "keyword": e["keyword"],
            "match": e["match"],
            "requiresArg": False,   # bare `click`/`type` are valid
            "canBeBare": True,
            "class": e["class"],
            "qualifiers": e["qualifiers"],
        }

    # M6: hook blocks + subtest markers are matched by Pattern constants (HOOK_BLOCK / SUBTEST_MARKER)
    for e in PATTERN_KEYWORDS:
        merged[e["keyword"].lower()] = dict(e)

    # M1: auto-wait opt-out -- the `nowait` prefix, and `bang` on every verb that accepts `<verb>!`
    merged[NOWAIT_KEYWORD["keyword"]] = dict(NOWAIT_KEYWORD)
    vm = NOWAIT_VERBS_RE.search(src)
    if not vm:
        raise SystemExit("could not locate Command.NOWAIT_VERBS")
    verbs = re.compile("".join(unescape_java(x) for x in re.findall(r'"((?:[^"\\]|\\.)*)"', vm.group(1))))
    for e in merged.values():
        if verbs.fullmatch(e["keyword"].lower()):
            e["bang"] = True

    entries = sorted(merged.values(),
                     key=lambda e: (e["match"] != "regex", e["keyword"]))

    doc = {
        "_generator": "tools/gen-commands/gen_commands.py",
        "_source": "alldep-jar/src/main/java/com/gatf/selenium/Command.java",
        "_note": ("seleasy statement keywords extracted from the parse() and "
                  "handleActions() dispatch. match: 'prefix' = keyword begins "
                  "the line (startsWith); 'exact' = whole-line keyword only; "
                  "'regex' = matched via a compiled Pattern (see qualifiers). "
                  "requiresArg=true means the keyword must be followed by a "
                  "space+argument and cannot stand alone (e.g. `open `). "
                  "canBeBare=true means the keyword may appear as a whole line "
                  "(e.g. `clear`, `submit`). bang=true means `<keyword>! ...` is "
                  "accepted (auto-wait opt-out, like the `nowait` prefix). Regenerate with the generator "
                  "above; do not hand-edit."),
        "count": len(entries),
        "commands": entries,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO_ROOT)}  ({len(entries)} keywords)")
    # tiny summary to stderr for eyeballing
    kinds = {}
    for e in entries:
        kinds[e["match"]] = kinds.get(e["match"], 0) + 1
    print("  by match-kind:", kinds, file=sys.stderr)


if __name__ == "__main__":
    main()
