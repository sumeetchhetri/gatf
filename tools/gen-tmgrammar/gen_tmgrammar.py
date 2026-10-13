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
# Generate the VS Code TextMate grammar (vscode-extension/syntaxes/seleasy.tmLanguage.json) from
# the SAME authoritative command catalog the offline linter uses (docs/seleasy/commands.json),
# which is itself generated from Command.java's dispatch by tools/gen-commands/gen_commands.py.
# Keeping the grammar generated from that one source means the extension's highlighting stays in
# step with the transpiler. Also refreshes the extension's bundled copy of commands.json (used by
# the language server for completion/hover/diagnostics).
#
#   Run:  python3 tools/gen-tmgrammar/gen_tmgrammar.py
#
# stdlib-only; do not hand-edit the generated files.
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CATALOG = REPO / "docs/seleasy/commands.json"
CM_LANG = REPO / "frontend/src/editor/seleasyLang.ts"
EXT = REPO / "vscode-extension"
GRAMMAR = EXT / "syntaxes/seleasy.tmLanguage.json"
BUNDLED = EXT / "src/commands.json"

# tokens with a dedicated rule below — excluded from the generated keyword/atom alternations
STRUCTURAL = {
    "//", "/*", "*/", "<<<", "<<<(", ">>>", "{", "}", "[", "]", "#j", "@call",
    ":", ":?", ":?!", "?", "?!", "??", "??+", "??-", "??~", "^", "#", "##",
    "#c", "#counter", "#file", "#mongo", "#o", "#optional", "#p", "#p-sf",
    "#provider", "#provider-sf", "#sql", "#tp", "#transient-provider",
    "begin code in", "end code",
}


def parse_cm_map():
    """Extract the SELEASY highlight map from the CM6 editor (the web editor's source of truth) so
    the TextMate grammar mirrors it: code 1 -> keyword, code 3 -> atom/constant. Operators (4) and
    the rest are handled by dedicated rules. Returns (keyword_words, atom_words)."""
    if not CM_LANG.exists():
        return set(), set()
    txt = CM_LANG.read_text(encoding="utf-8")
    m = re.search(r"SELEASY:\s*Record<string,\s*Style>\s*=\s*\{(.*?)\n\};", txt, re.S)
    if not m:
        return set(), set()
    body = m.group(1)
    kw, atom = set(), set()
    for em in re.finditer(r"(?:'([^']*)'|\"([^\"]*)\"|([A-Za-z_@$][\w@$-]*))\s*:\s*(\[[^\]]*\]|\d+)", body):
        key = em.group(1) or em.group(2) or em.group(3)
        val = em.group(4)
        code = int(re.search(r"\d+", val).group(0))
        if key in STRUCTURAL or key.startswith("@"):
            continue
        if code == 1:
            kw.add(key)
        elif code == 3:
            atom.add(key)
    return kw, atom


def esc(k: str) -> str:
    return re.sub(r"([\\.*+?()\[\]{}|^$/])", r"\\\1", k)


def multi_pat(k: str) -> str:
    return re.sub(r"\s+", r"\\s+", esc(k))


def build_grammar(cmds):
    cm_kw, cm_atom = parse_cm_map()
    kws = [c["keyword"] for c in cmds]
    # command keywords + CM keyword-styled words; atoms come only from the CM map (browser targets,
    # booleans, get/post, …) minus anything already a command keyword.
    all_kw = set(kws) | cm_kw
    multi = sorted([k for k in all_kw if " " in k and k not in STRUCTURAL], key=len, reverse=True)
    wordy = sorted(
        [k for k in all_kw if re.match(r"^[A-Za-z0-9_]+$", k) and k not in STRUCTURAL],
        key=len,
        reverse=True,
    )
    atoms = sorted(
        [k for k in (cm_atom - set(kws)) if re.match(r"^[A-Za-z0-9_-]+$", k) and k not in STRUCTURAL],
        key=len,
        reverse=True,
    )
    multi_alt = "|".join(multi_pat(k) for k in multi)
    wordy_alt = "|".join(esc(k) for k in wordy)
    atom_alt = "|".join(esc(k) for k in atoms)

    # selector prefixes (id@ css@ …) and @-directives — kept explicit; they take precedence over
    # the bare-keyword rule by ordering.
    selectors = (
        "id|css|xpath|class|name|tag|text|cssselector|linktext|partiallinktext|role|label|placeholder|testid|alt|title|"
        "jq|jquery|cssvalue|attr|active|relative|near|above|below|leftof|rightof"
    )
    atdirs = (
        "call|printprovjson|printProv|print|driver|window|element|sc|index|line|cntxtparam"
    )

    patterns = [{"include": "#" + n} for n in (
        "comments", "codeblocks", "strings", "variables", "selectors", "atdirs",
        "providers", "inlinejava", "conditionals", "kwmulti", "keywords", "atoms", "operators", "numbers",
    )]

    repository = {
        "comments": {
            "patterns": [
                {
                    "name": "comment.line.double-slash.seleasy",
                    "match": r"(?:^\s*|(?<=\s))//.*$",
                },
                {
                    # Δ145c: as Command.parse since Δ145b — '/*' opens a comment only at the start of a statement
                    # (after optional whitespace), so `xpath@//div/*/a` is not one; a quoted '*/' does not close it;
                    # text after the closing '*/' on that line is ignored by the transpiler, so it stays comment too
                    "name": "comment.block.seleasy",
                    "begin": r"^\s*/\*",
                    "end": r"\*/.*$",
                    "patterns": [
                        # the quoted strings Command's sanitize() masks (same regexes): an unclosed quote masks nothing
                        {"match": r'"(?:\\"|[^"])*"'},
                        {"match": r"'(?:\\'|[^'])*'"},
                    ],
                },
            ]
        },
        "codeblocks": {
            "patterns": [
                {
                    "name": "meta.embedded.block.seleasy",
                    "begin": r"^\s*(<<<(\([^)]*\))?)\s*.*$",
                    "end": r"^\s*(>>>)\s*$",
                    "beginCaptures": {"1": {"name": "keyword.control.embedded.seleasy"}},
                    "endCaptures": {"1": {"name": "keyword.control.embedded.seleasy"}},
                },
                {
                    "name": "meta.embedded.block.seleasy",
                    "begin": r"^\s*(begin\s+code\s+in\b).*$",
                    "end": r"^\s*(end\s+code)\s*$",
                    "beginCaptures": {"1": {"name": "keyword.control.embedded.seleasy"}},
                    "endCaptures": {"1": {"name": "keyword.control.embedded.seleasy"}},
                },
            ]
        },
        "strings": {
            "patterns": [
                {
                    "name": "string.quoted.double.seleasy",
                    "begin": '"',
                    "end": '"',
                    "patterns": [{"name": "constant.character.escape.seleasy", "match": r"\\."}],
                },
                {
                    "name": "string.quoted.single.seleasy",
                    "begin": "'",
                    "end": "'",
                    "patterns": [{"name": "constant.character.escape.seleasy", "match": r"\\."}],
                },
            ]
        },
        "variables": {
            "patterns": [
                {"name": "variable.other.seleasy", "match": r"\$\{[A-Za-z0-9_]+\}"},
            ]
        },
        "selectors": {
            "patterns": [
                {
                    "match": r"(?<![\w-])(" + selectors + r")(@)",
                    "captures": {
                        "1": {"name": "entity.name.tag.selector.seleasy"},
                        "2": {"name": "punctuation.definition.selector.seleasy"},
                    },
                }
            ]
        },
        "atdirs": {
            "patterns": [
                {"name": "support.function.directive.seleasy", "match": r"@(" + atdirs + r")\b"},
            ]
        },
        "providers": {
            "patterns": [
                {
                    "name": "keyword.control.provider.seleasy",
                    "match": r"(?:^\s*|(?<=\s))#{1,2}(?:sql|mongo|file|provider-sf|provider|counter|optional|transient-provider|p-sf|p|c|o|tp)?(?![\w-])",
                }
            ]
        },
        "inlinejava": {
            "patterns": [
                {"name": "keyword.control.inline-java.seleasy", "match": r"(?:^\s*|(?<=\s))#j"},
            ]
        },
        "conditionals": {
            "patterns": [
                {"name": "keyword.control.conditional.seleasy", "match": r"(?<![\w-])(\?\?[+~-]?|:\?!?|\?!|\?|:)(?![\w-])"},
                {"name": "keyword.control.skip.seleasy", "match": r"^\s*\^"},
            ]
        },
        "kwmulti": {
            "patterns": (
                [{"name": "keyword.control.seleasy", "match": r"(?<![\w-])(" + multi_alt + r")(?![\w-])"}]
                if multi_alt
                else []
            )
        },
        "keywords": {
            "patterns": [
                {"name": "keyword.control.seleasy", "match": r"(?<![\w-])(" + wordy_alt + r")(?![\w-])"},
            ]
        },
        "atoms": {
            "patterns": (
                [{"name": "constant.language.seleasy", "match": r"(?<![\w-])(" + atom_alt + r")(?![\w-])"}]
                if atom_alt
                else []
            )
        },
        "operators": {
            "patterns": [
                {"name": "keyword.operator.seleasy", "match": r"->|=~|!~|==|!=|>=|<=|&&|\|\||[+\-*/%<>=&|^~]"},
            ]
        },
        "numbers": {
            "patterns": [
                {"name": "constant.numeric.seleasy", "match": r"(?<![\w.])-?\d+(?:\.\d+)?(?![\w.])"},
            ]
        },
    }

    return {
        "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
        "name": "seleasy",
        "scopeName": "source.seleasy",
        "_generator": "tools/gen-tmgrammar/gen_tmgrammar.py",
        "_source": "docs/seleasy/commands.json",
        "_note": "Generated to mirror Command.java's dispatch. Do not hand-edit; regenerate with the generator above.",
        "patterns": patterns,
        "repository": repository,
    }


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    grammar = build_grammar(catalog["commands"])
    GRAMMAR.parent.mkdir(parents=True, exist_ok=True)
    BUNDLED.parent.mkdir(parents=True, exist_ok=True)
    GRAMMAR.write_text(json.dumps(grammar, indent=2) + "\n", encoding="utf-8")
    BUNDLED.write_text(json.dumps(catalog, indent=1) + "\n", encoding="utf-8")
    kw = len(catalog["commands"])
    print(f"wrote {GRAMMAR.relative_to(REPO)}  ({kw} keywords in catalog)")
    print(f"wrote {BUNDLED.relative_to(REPO)}")


if __name__ == "__main__":
    main()
