#!/usr/bin/env python3
"""
pw-import.py  --  Best-effort Playwright (.spec.ts / .spec.js) -> seleasy  [Phase 11].

Parses a Playwright spec and emits:
  * <out>.sel                 an equivalent seleasy script (open chrome ... close)
  * <out>.unsupported.txt     an honest report of every line NOT converted

This is a *best-effort transpiler*, not a lossless converter -- the goal is a
running GATF starting point that then gains GATF's trace bundle. It recognises
the common surface (page.goto/click/fill/type/press/hover/check/selectOption,
locator chains, getByRole/Text/Label/Placeholder/TestId, and expect(...).toBe*/
toHaveText/toContainText/toBeVisible/toHaveValue). Control flow (loops, if,
custom helpers, fixtures beyond `page`), and anything unrecognised, is emitted as
a `// [unsupported]` comment and listed in the report.

seleasy note: the generated script runs each imported Playwright `test()` block
sequentially in ONE browser (open chrome ... close). Playwright's per-test
context isolation is not reproduced -- this is called out in the report.

Dependency-free (stdlib re only). Output is validated by tools/smoke/sel-lint.py.
Usage:
    python3 tools/import/pw-import.py SPEC.spec.ts [-o OUTBASE]
"""
import argparse
import re
import sys
from pathlib import Path

# --------------------------------------------------------------------------
# selector -> seleasy locator
# --------------------------------------------------------------------------

def loc(sel):
    """Map a Playwright selector string to a seleasy locator (id@/css@/xpath@/text@)."""
    s = sel.strip()
    if s.startswith("xpath=") or s.startswith("//") or s.startswith("(//"):
        return 'xpath@"%s"' % s[len("xpath="):] if s.startswith("xpath=") else 'xpath@"%s"' % s
    if s.startswith("text="):
        return 'text@"%s"' % s[len("text="):]
    if s.startswith("#") and " " not in s and all(c not in s for c in ".>[]:"):
        return 'id@"%s"' % s[1:]
    if s.startswith("css="):
        s = s[len("css="):]
    return 'css@"%s"' % s


def by_role(role, name):
    """getByRole('button', {name:'Save'}) -> a text locator (name) when available."""
    if name:
        return 'text@"%s"' % name
    # role without a name -> approximate with an [role=] attribute selector
    return 'css@"[role=%s]"' % role


def by_helper(kind, arg):
    if kind == "getByText":
        return 'text@"%s"' % arg
    if kind == "getByLabel":
        # best-effort: an xpath that finds the control associated with a label text
        return 'xpath@"//label[normalize-space()=\'%s\']/following::*[self::input or self::select or self::textarea][1]"' % arg
    if kind == "getByPlaceholder":
        return 'css@"[placeholder=\'%s\']"' % arg
    if kind == "getByTestId":
        return 'css@"[data-testid=\'%s\']"' % arg
    if kind == "getByTitle":
        return 'css@"[title=\'%s\']"' % arg
    if kind == "getByAltText":
        return 'css@"[alt=\'%s\']"' % arg
    return None


# --------------------------------------------------------------------------
# regexes (best-effort; tolerant of await/single-double quotes)
# --------------------------------------------------------------------------

STR = r"""['"`]([^'"`]*)['"`]"""      # a single quoted arg, captured
TEST = re.compile(r"""\btest\s*(?:\.\w+)?\s*\(\s*""" + STR)
BEFORE_EACH = re.compile(r"""\btest\.beforeEach\s*\(""")
AFTER_EACH = re.compile(r"""\btest\.afterEach\s*\(""")

# page.<action>(...) forms
RE_GOTO = re.compile(r"""page\.goto\s*\(\s*""" + STR)
RE_PAGE_2 = re.compile(r"""page\.(click|fill|type|press|hover|check|uncheck|dblclick|selectOption|setInputFiles|waitForSelector)\s*\(\s*""" + STR + r"""\s*(?:,\s*""" + STR + r""")?""")
RE_PAGE_0 = re.compile(r"""page\.(goBack|goForward|reload)\s*\(""")

# locator/getBy chains ending in an action
GETBY = r"""(?:page|frameLocator\([^)]*\))\.(getByRole|getByText|getByLabel|getByPlaceholder|getByTestId|getByTitle|getByAltText|locator)\s*\(\s*""" + STR + r"""\s*(?:,\s*\{[^}]*\bname\s*:\s*""" + STR + r"""[^}]*\})?\s*\)"""
RE_CHAIN = re.compile(GETBY + r"""\s*\.\s*(click|fill|type|press|hover|check|dblclick|selectOption|setInputFiles)\s*\(\s*(?:""" + STR + r""")?""")

# expect(...).<matcher>(arg)
EXPECT_TARGET = r"""expect\s*\(\s*(?:page|frameLocator\([^)]*\))\.(getByRole|getByText|getByLabel|getByPlaceholder|getByTestId|getByTitle|getByAltText|locator)\s*\(\s*""" + STR + r"""\s*(?:,\s*\{[^}]*\bname\s*:\s*""" + STR + r"""[^}]*\})?\s*\)\s*\)"""
RE_EXPECT = re.compile(EXPECT_TARGET + r"""\s*\.\s*(?:not\.)?\s*(toBeVisible|toBeHidden|toHaveText|toContainText|toHaveValue|toBeEnabled|toBeDisabled|toBeChecked)\s*\(\s*(?:""" + STR + r""")?""")
RE_EXPECT_URL = re.compile(r"""expect\s*\(\s*page\s*\)\s*\.\s*(?:not\.)?\s*(toHaveURL|toHaveTitle)\s*\(\s*""" + STR)


def chain_locator(kind, arg1, arg2):
    if kind == "locator":
        return loc(arg1)
    if kind == "getByRole":
        return by_role(arg1, arg2)
    return by_helper(kind, arg1)


# --------------------------------------------------------------------------
# converter
# --------------------------------------------------------------------------

class Importer:
    def __init__(self):
        self.body = []        # seleasy lines (inside open chrome ... close)
        self.report = []
        self.nav_seen = 0

    def emit(self, line):
        self.body.append(line)

    def unsupported(self, lineno, text):
        t = text.strip()
        self.emit("// [unsupported] " + t)
        self.report.append("line %d: %s" % (lineno, t))

    def convert_action(self, act, locator, value, lineno):
        """Map a Playwright action+locator(+value) to seleasy."""
        if act in ("click", "dblclick", "check", "uncheck", "hover"):
            if locator:
                self.emit("??10 " + locator)
            verb = {"click": "click", "dblclick": "dblclick", "check": "click",
                    "uncheck": "click", "hover": "hover"}[act]
            self.emit("%s %s" % (verb, locator))
            if act in ("check", "uncheck"):
                self.report.append("line %d: %s() mapped to click (checkbox state not verified)" % (lineno, act))
        elif act in ("fill", "type"):
            if locator:
                self.emit("??10 " + locator)
            self.emit('type "%s" %s' % (value or "", locator))
        elif act == "press":
            self.emit('sendkeys "%s" %s' % (value or "", locator))
            self.report.append("line %d: press('%s') mapped to sendkeys (key names may differ)" % (lineno, value or ""))
        elif act == "selectOption":
            self.emit("select %s" % locator)
            self.report.append("line %d: selectOption('%s') -> `select %s` (verify option syntax)" % (lineno, value or "", locator))
        elif act == "setInputFiles":
            self.emit('upload %s "%s"' % (locator, value or ""))
            self.report.append("line %d: setInputFiles -> upload (verify file path)" % lineno)
        elif act == "waitForSelector":
            self.emit("??10 " + locator)
        else:
            self.report.append("line %d: action '%s' not mapped" % (lineno, act))

    def convert_expect(self, matcher, locator, value, lineno):
        if matcher in ("toBeVisible", "toBeEnabled", "toBeChecked"):
            self.emit("?? %s visible" % locator)
            if matcher != "toBeVisible":
                self.report.append("line %d: %s approximated as `visible`" % (lineno, matcher))
        elif matcher in ("toBeHidden", "toBeDisabled"):
            self.report.append("line %d: %s (negative visibility) not directly expressible; left as note" % (lineno, matcher))
            self.emit("// [unsupported] expect %s -> %s" % (locator, matcher))
        elif matcher in ("toHaveText", "toContainText"):
            self.emit('?? %s text ="%s"' % (locator, value or ""))
            if matcher == "toContainText":
                self.report.append("line %d: toContainText mapped to text-equals (contains not exact)" % lineno)
        elif matcher == "toHaveValue":
            self.emit('?? %s value ="%s"' % (locator, value or ""))
        else:
            self.report.append("line %d: matcher '%s' not mapped" % (lineno, matcher))

    def convert_line(self, raw, lineno):
        s = raw.strip()
        if not s or s in ("{", "}", "});", ");"):
            return
        # comments pass through
        if s.startswith("//"):
            self.emit(s if s.startswith("// ") else "// " + s[2:])
            return
        if s.startswith("import ") or s.startswith("const ") and "require(" in s:
            return  # JS imports -> irrelevant to seleasy
        if s.startswith("await page.screenshot") or "page.screenshot" in s:
            self.emit("screenshot")
            return

        # 1) expect(page).toHaveURL / toHaveTitle
        m = RE_EXPECT_URL.search(s)
        if m:
            self.report.append("line %d: expect(page).%s('%s') -> GATF has no page-URL/title assertion; not converted"
                               % (lineno, m.group(1), m.group(2)))
            self.emit("// [unsupported] " + s)
            return

        # 2) expect(locator).matcher
        m = RE_EXPECT.search(s)
        if m:
            kind, a1, a2, matcher, val = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
            self.convert_expect(matcher, chain_locator(kind, a1, a2), val, lineno)
            return

        # 3) page.goto
        m = RE_GOTO.search(s)
        if m:
            self.emit("goto " + m.group(1))
            self.nav_seen += 1
            return

        # 4) page.goBack/goForward/reload
        m = RE_PAGE_0.search(s)
        if m:
            self.emit({"goBack": "back", "goForward": "forward", "reload": "refresh"}[m.group(1)])
            return

        # 5) locator/getBy chain action
        m = RE_CHAIN.search(s)
        if m:
            kind, a1, a2, act, val = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
            self.convert_action(act, chain_locator(kind, a1, a2), val, lineno)
            return

        # 6) page.<action>(selector, value)
        m = RE_PAGE_2.search(s)
        if m:
            act, sel, val = m.group(1), m.group(2), m.group(3)
            self.convert_action(act, loc(sel), val, lineno)
            return

        # anything else with an await/page/expect is a real construct we couldn't map
        if any(tok in s for tok in ("await ", "page.", "expect(", "for ", "if ", "while ")):
            self.unsupported(lineno, s)
        # plain JS (declarations, closing braces) -> silently ignored

    def convert(self, text):
        lines = text.splitlines()
        # split into test blocks; steps outside any test are ignored with a note
        i = 0
        n = len(lines)
        depth = 0
        cur = None  # current test name or 'beforeEach'
        while i < n:
            raw = lines[i]
            s = raw.strip()
            mt = TEST.search(s)
            mb = BEFORE_EACH.search(s)
            ma = AFTER_EACH.search(s)
            if mt and cur is None:
                cur = mt.group(1)
                self.emit("")
                self.emit("// ===== test: %s =====" % cur)
                depth = raw.count("{") - raw.count("}")
                i += 1
                continue
            if (mb or ma) and cur is None:
                cur = "beforeEach" if mb else "afterEach"
                self.emit("")
                self.emit("// ===== %s hook =====" % cur)
                depth = raw.count("{") - raw.count("}")
                i += 1
                continue
            if cur is not None:
                depth += raw.count("{") - raw.count("}")
                if depth <= 0:
                    cur = None
                    i += 1
                    continue
                self.convert_line(raw, i + 1)
            i += 1

    def render(self):
        out = ["// Imported from Playwright by tools/import/pw-import.py (best-effort).",
               "// Runs sequentially in one browser; Playwright per-test isolation is not reproduced.",
               "",
               "open chrome"]
        out += self.body
        out.append("")
        out.append("close")
        return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser(description="Best-effort Playwright spec -> seleasy")
    ap.add_argument("spec", help="input .spec.ts / .spec.js")
    ap.add_argument("-o", "--out", help="output base path (default: input without extension)")
    args = ap.parse_args()

    src = Path(args.spec)
    if not src.exists():
        sys.exit("error: no such file: %s" % src)
    outbase = Path(args.out) if args.out else Path(re.sub(r"\.(spec\.)?(ts|js|mjs)$", "", str(src)))

    imp = Importer()
    imp.convert(src.read_text(encoding="utf-8", errors="replace"))

    sel_path = Path(str(outbase) + ".sel")
    rep_path = Path(str(outbase) + ".unsupported.txt")
    sel_path.write_text(imp.render(), encoding="utf-8")

    rep = ["Playwright -> seleasy import report for %s" % src.name,
           "seleasy statements emitted: %d" % len([l for l in imp.body if l and not l.startswith("//")]),
           "",
           "UNSUPPORTED / APPROXIMATED (%d):" % len(imp.report)]
    rep += ["  - " + r for r in imp.report] if imp.report else ["  (none)"]
    rep_path.write_text("\n".join(rep) + "\n", encoding="utf-8")

    print("wrote %s" % sel_path)
    print("wrote %s (%d notes)" % (rep_path, len(imp.report)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
