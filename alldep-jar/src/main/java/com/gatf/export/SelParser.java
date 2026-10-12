/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gatf.export.SelScript.Assertion;
import com.gatf.export.SelScript.Kind;
import com.gatf.export.SelScript.Locator;
import com.gatf.export.SelScript.Step;
import com.gatf.selenium.SemanticLocators;
import com.gatf.selenium.emulation.FakeClock;

/**
 * Δ160 — parses a .sel script into the backend-neutral {@link SelScript} model, once, for every exporter backend.
 *
 * <p>It mirrors the real parser ({@code Command.parse} / {@code Command.get}) where the meaning depends on it, and
 * {@code ExportParserParityTest} pins the two against each other on every corpus / dogfood / sample script:
 * <ul>
 * <li>lines split on CR, LF or CRLF (commons-io readLines);</li>
 * <li>quoting as {@code Command.sanitize}: double-quoted spans, then single-quoted spans, are atomic; tokens split on
 * spaces / tabs outside them; a value's outer quotes are stripped and nothing inside is unescaped;</li>
 * <li>a trailing {@code //} comment needs whitespace before it and is never inside quotes;</li>
 * <li>{@code nowait <stmt>} / {@code <verb>!} opt-outs ({@code Command.NOWAIT_PREFIX} / {@code NOWAIT_BANG});</li>
 * <li>the keyword dispatch order of {@code Command.parse};</li>
 * <li>a block must open with {@code {} on the very next line.</li>
 * </ul>
 * Anything it cannot give a meaning to becomes {@link Kind#UNSUPPORTED} with a reason; nothing is dropped. Locator
 * semantics come from {@link SemanticLocators#parse}, clock specs from {@link FakeClock}. Pure: no Selenium types.
 */
public final class SelParser {

    private static final Pattern DQ = Pattern.compile("\"(?:\\\\\"|[^\\\\\"])*\"");
    private static final Pattern SQ = Pattern.compile("'(?:\\\\'|[^'])*'");
    private static final String MASK_OPEN = "\uE000", MASK_CLOSE = "\uE001";   // private-use: String.trim() keeps them
    private static final Pattern MASK = Pattern.compile("\uE000(\\d+)\uE001");

    static final String NOWAIT_VERBS = "(?:click(?:fo|nf)?|(?:type|sendkeys|chord|randomize)(?:bl|ch|bk|cl|fo)?|select|hover|hoverclick"
            + "|clear|submit|upload|doubleclick|dblclick)";
    static final Pattern NOWAIT_PREFIX = Pattern.compile("^nowait[\\t ]+(?=(?:" + NOWAIT_VERBS + "|actions)(?:[\\t ]|$)|\\?\\?)", Pattern.CASE_INSENSITIVE);
    static final Pattern NOWAIT_BANG = Pattern.compile("^(" + NOWAIT_VERBS + ")!(?=[\\t ]|$)", Pattern.CASE_INSENSITIVE);

    private static final String TYPE_EX = "^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?";
    private static final Pattern TYPE_SW = Pattern.compile("^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?\\s+(.*)", Pattern.DOTALL);
    private static final Pattern TYPE_SW_CI = Pattern.compile("^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?\\s+(.*)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CLICK_ANY = Pattern.compile("^click(fo|nf)?(?:\\s+(.*))?$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final String CLICK_EX = "^click(fo|nf)?";
    private static final Pattern CLICK_SW = Pattern.compile("^click(fo|nf)?\\s+(.*)", Pattern.DOTALL);
    private static final Pattern WAIT = Pattern.compile("^\\?\\?([0-9]*)");
    private static final Pattern WAIT_IF = Pattern.compile("^\\?\\?(\\+|\\-)([0-9]*)");
    private static final Pattern WAIT_SOFT = Pattern.compile("^\\?\\?~([0-9]*)");
    private static final Pattern HOOK_BLOCK = Pattern.compile("(?i)^(beforeall|beforeeach|aftereach|afterall)$");
    private static final Pattern SUBTEST_MARKER = Pattern.compile("(?i)^(only|skip|fixme)\\s+subtest\\s+");
    private static final Pattern TAG = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern TEMPLATE_REF = Pattern.compile("\\$v\\{[^{}]*\\}|\\$!?\\{[^{}]*\\}");
    private static final List<String> BY = Arrays.asList("id", "name", "class", "classname", "tag", "tagname", "xpath", "text",
            "partiallinktext", "linktext", "jquery", "jq", "$", "css", "cssselector", "role", "label", "placeholder", "testid", "alt", "title");

    private List<String> lines;
    private int pos;

    /** Parses {@code source} (a .sel file's text) into a script named {@code name}. */
    public SelScript parse(String name, String source) {
        lines = splitLines(source);
        pos = 0;
        SelScript script = new SelScript(name, lines);
        script.steps.addAll(sequence(0, false, null));
        return script;
    }

    /** CR, LF or CRLF; no trailing empty line for a final terminator (BufferedReader.readLine semantics). */
    static List<String> splitLines(String src) {
        List<String> out = new ArrayList<String>();
        if (src == null || src.isEmpty()) return out;
        int start = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\n' || c == '\r') {
                out.add(src.substring(start, i));
                if (c == '\r' && i + 1 < src.length() && src.charAt(i + 1) == '\n') i++;
                start = i + 1;
            }
        }
        if (start < src.length()) out.add(src.substring(start));
        return out;
    }

    /** Trim as Command.utf8Trim + trim: whitespace plus a leading BOM / no-break space. */
    static String trim(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.charAt(0) == '\uFEFF' || t.charAt(0) == '\u00A0' || t.charAt(0) == '\u200B')) t = t.substring(1).trim();
        return t;
    }

    // ---- block structure -------------------------------------------------------------------------------------------

    /** Statements until the end of input, or (inBlock) the closing brace of the current block. */
    private List<Step> sequence(int depth, boolean inBlock, Step owner) {
        List<Step> out = new ArrayList<Step>();
        while (pos < lines.size()) {
            int lineNo = pos + 1;
            String t = trim(lines.get(pos));
            if (t.isEmpty()) { pos++; continue; }
            if (t.equals("}")) {
                pos++;
                if (inBlock) {
                    if (owner != null) owner.endLine = lineNo;
                    return out;
                }
                Step s = new Step(Kind.UNSUPPORTED, lineNo);
                s.keyword = "}";
                s.depth = depth;
                out.add(s.unsupported("closing brace without an open block"));
                continue;
            }
            if (t.startsWith("/*")) { out.add(blockComment(depth)); continue; }
            if (t.startsWith("//")) {
                Step s = new Step(Kind.COMMENT, lineNo);
                s.keyword = "//";
                s.depth = depth;
                s.text = t.substring(2);
                out.add(s);
                pos++;
                continue;
            }
            if (t.equals("<<<") || t.startsWith("<<<(") || t.startsWith("begin code in ")) { out.add(codeBlock(t, depth)); continue; }
            if (t.startsWith("[")) { out.add(valueList(depth)); continue; }
            if (t.startsWith("{")) {
                Step s = new Step(Kind.UNSUPPORTED, lineNo);
                s.keyword = "{";
                s.depth = depth;
                s.unsupported(t.equals("{") ? "a block with no statement before it" : "'{' must stand alone on its line");
                pos++;
                if (t.equals("{")) {
                    s.hasBlock = true;
                    s.children.addAll(sequence(depth + 1, true, s));
                }
                out.add(s);
                continue;
            }
            Step s = statement(lines.get(pos), lineNo, depth);
            pos++;
            attachBlock(s, depth);
            out.add(s);
        }
        if (inBlock && owner != null) {
            owner.endLine = lines.size();
            if (owner.reason == null && owner.kind != Kind.UNSUPPORTED) owner.unsupported("block is not closed");
            else if (owner.kind != Kind.UNSUPPORTED) owner.unsupported("block is not closed");
        }
        return out;
    }

    private void attachBlock(Step s, int depth) {
        BlockRule rule = blockRule(s);
        if (rule == BlockRule.NONE) return;
        boolean next = pos < lines.size() && trim(lines.get(pos)).equals("{");
        if (!next) {
            if (rule == BlockRule.REQUIRED) s.unsupported("no '{' block on the next line");
            return;
        }
        pos++;
        s.hasBlock = true;
        if (rule == BlockRule.RAW || s.rawBlock) {
            s.rawBlock = true;
            rawUntilClose(s);
        } else {
            s.children.addAll(sequence(depth + 1, true, s));
        }
    }

    private enum BlockRule { NONE, OPTIONAL, REQUIRED, RAW }

    private BlockRule blockRule(Step s) {
        String k = s.keyword;
        if (k == null) return BlockRule.RAW;   // plugin statements: optional data block
        switch (k) {
            case "?": case "?!": case "if": case "ifnot": case ":": case ":?": case ":?!": case "else": case "else if": case "else ifnot":
            case "subtest": case "func": case "only": case "skip": case "fixme":
            case "beforeAll": case "beforeEach": case "afterEach": case "afterAll":
            case "#": case "##": case "loop over": case "readfile":
            case "#sql": case "#mongo": case "#file": case "#provider": case "#p": case "#provider-sf": case "#p-sf":
            case "#counter": case "#c": case "#optional": case "#o":
                return BlockRule.REQUIRED;
            case "#tp": case "#transient-provider": case "frame": case "tab": case "window":
                return BlockRule.OPTIONAL;
            case "var":
                return BlockRule.RAW;
            default:
                return BlockRule.NONE;
        }
    }

    /** A data block (plugin body, var plugin body): skip to the matching close brace, counting lone braces. */
    private void rawUntilClose(Step s) {
        int depth = 1;
        while (pos < lines.size()) {
            String t = trim(lines.get(pos));
            pos++;
            if (t.equals("{")) depth++;
            else if (t.equals("}") && --depth == 0) { s.endLine = pos; return; }
        }
        s.endLine = lines.size();
        s.unsupported("block is not closed");
    }

    private Step blockComment(int depth) {
        Step s = new Step(Kind.COMMENT, pos + 1);
        s.keyword = "/*";
        s.depth = depth;
        StringBuilder b = new StringBuilder();
        // As Command.parse: the '/*' line opens the comment even when it also holds '*/'; the comment ends at the next
        // line that contains '*/'.
        if (trim(lines.get(pos)).substring(2).contains("*/")) s.args.put("sameLineEnd", "true");
        b.append(lines.get(pos)).append('\n');
        pos++;
        while (pos < lines.size()) {
            String raw = lines.get(pos);
            b.append(raw).append('\n');
            pos++;
            if (mask(trim(raw)).text.contains("*/")) break;
        }
        s.endLine = pos;
        s.text = b.toString();
        return s;
    }

    private Step codeBlock(String t, int depth) {
        Step s = new Step(Kind.UNSUPPORTED, pos + 1);
        s.keyword = t.startsWith("begin code in ") ? "begin code in" : t.equals("<<<") ? "<<<" : "<<<(";
        s.depth = depth;
        s.unsupported("embedded " + (t.startsWith("begin") ? t.substring(14).trim() : t.equals("<<<") ? "java" : t.substring(4).replaceAll("\\).*", ""))
                + " code has no Playwright equivalent");
        pos++;
        while (pos < lines.size()) {
            String x = trim(lines.get(pos));
            pos++;
            if (x.equals(">>>") || x.equals("end code")) { s.endLine = pos; return s; }
        }
        s.endLine = lines.size();
        s.reason += " (block is not closed)";
        return s;
    }

    private Step valueList(int depth) {
        Step s = new Step(Kind.UNSUPPORTED, pos + 1);
        s.keyword = "[";
        s.depth = depth;
        s.unsupported("value list outside a plugin or if statement");
        pos++;
        while (pos < lines.size()) {
            String x = trim(lines.get(pos));
            pos++;
            if (x.equals("]")) { s.endLine = pos; return s; }
        }
        s.endLine = lines.size();
        return s;
    }

    // ---- quoting (Command.sanitize) --------------------------------------------------------------------------------

    /** A line with quoted spans masked (no spaces or quotes inside a mask), so splitting and prefix checks are safe. */
    static final class Masked {
        final String text;
        final List<String> spans;

        Masked(String text, List<String> spans) {
            this.text = text;
            this.spans = spans;
        }

        /** Masks restored; a single-quoted span can hold a double-quoted one, so this repeats (as Command.unsanitize). */
        String unmask(String s) {
            for (int round = 0; round <= spans.size() && s.indexOf(MASK_OPEN) >= 0; round++) {
                Matcher m = MASK.matcher(s);
                StringBuffer b = new StringBuffer();
                while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(spans.get(Integer.parseInt(m.group(1)))));
                m.appendTail(b);
                s = b.toString();
            }
            return s;
        }

        /** Tokens split on spaces / tabs outside quotes, each unmasked. */
        List<String> tokens(String part) {
            List<String> out = new ArrayList<String>();
            for (String p : part.trim().split("[\\t ]+")) if (!p.isEmpty()) out.add(unmask(p));
            return out;
        }
    }

    static Masked mask(String cmd) {
        List<String> spans = new ArrayList<String>();
        String s = maskWith(DQ, cmd, spans);
        s = maskWith(SQ, s, spans);
        return new Masked(s, spans);
    }

    private static String maskWith(Pattern p, String cmd, List<String> spans) {
        Matcher m = p.matcher(cmd);
        String out = cmd;
        while (m.find()) {
            String g = m.group();
            int idx = spans.indexOf(g);
            if (idx < 0) {
                spans.add(g);
                idx = spans.size() - 1;
            }
            out = out.replace(g, MASK_OPEN + idx + MASK_CLOSE);
        }
        return out;
    }

    /** Outer quotes stripped (same quote at both ends), nothing unescaped — as the transpiler does. */
    static String unquote(String s) {
        if (s != null && s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'') && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Command.stripTrailingLineComment on a masked line: index of a whitespace-preceded //, or -1. */
    static int trailingComment(String masked) {
        if (masked.length() < 3) return -1;
        for (int i = 1; i < masked.length() - 1; i++) {
            if (masked.charAt(i) == '/' && masked.charAt(i + 1) == '/') {
                char prev = masked.charAt(i - 1);
                if (prev == ' ' || prev == '\t') return i;
            }
        }
        return -1;
    }

    // ---- statements ------------------------------------------------------------------------------------------------

    private Step statement(String rawLine, int lineNo, int depth) {
        if (rawLine.indexOf(MASK_OPEN) >= 0 || rawLine.indexOf(MASK_CLOSE) >= 0) {
            Step s = unsupported(lineNo, null, "contains U+E000 / U+E001, which the exporter reserves for quote masking");
            s.depth = depth;
            return s;
        }
        Masked mk = mask(trim(rawLine));
        String cmd = mk.text;
        String trailing = null;
        int tc = trailingComment(cmd);
        if (tc >= 0) {
            trailing = mk.unmask(cmd.substring(tc + 2));
            cmd = cmd.substring(0, tc).replaceAll("[ \\t]+$", "");
        }
        Step s = dispatch(mk, cmd, lineNo);
        s.depth = depth;
        s.trailingComment = trailing;
        return s;
    }

    private static Step step(Kind kind, int line, String keyword) {
        Step s = new Step(kind, line);
        s.keyword = keyword;
        return s;
    }

    private static Step unsupported(int line, String keyword, String reason) {
        return step(Kind.UNSUPPORTED, line, keyword).unsupported(reason);
    }

    private Step dispatch(Masked mk, String cmd, int line) {
        if (cmd.startsWith("^")) return unsupported(line, "^", "dynprop-conditional statement (^<dynprop>): decided by config at parse time");
        boolean noWait = false;
        Matcher nw = NOWAIT_PREFIX.matcher(cmd);
        if (nw.find()) {
            cmd = cmd.substring(nw.end());
            noWait = true;
        } else {
            nw = NOWAIT_BANG.matcher(cmd);
            if (nw.find()) {
                cmd = nw.group(1) + cmd.substring(nw.end());
                noWait = true;
            }
        }
        Step s = dispatch2(mk, cmd, line);
        if (noWait) {
            s.noWait = true;
            if (s.kind == Kind.WAIT || s.kind == Kind.ASSERT) s.args.put("nowaitNote", "true");
        }
        return s;
    }

    private Step dispatch2(Masked mk, String cmd, int line) {
        String lc = cmd.toLowerCase(Locale.ROOT);
        Matcher m;
        if (cmd.startsWith("??+")) {
            m = WAIT_IF.matcher(cmd);
            int start = 3;
            String time = null;
            if (m.find()) { time = m.group(2); start = m.end(2) + 1; }
            return waitStatement(mk, safeSub(cmd, start), line, "??+", time, true, false);
        }
        if (cmd.startsWith("wait till visible ")) return waitStatement(mk, cmd.substring(18), line, "wait till visible", null, true, false);
        if (cmd.startsWith("??-")) {
            m = WAIT_IF.matcher(cmd);
            int start = 3;
            String time = null;
            if (m.find()) { time = m.group(2); start = m.end(2) + 1; }
            return waitStatement(mk, safeSub(cmd, start), line, "??-", time, false, false);
        }
        if (cmd.startsWith("wait for ")) return waitStatement(mk, cmd.substring(9), line, "wait for", null, false, false);
        if (cmd.startsWith("??~")) {
            m = WAIT_SOFT.matcher(cmd);
            int start = 3;
            String time = null;
            if (m.find() && !m.group(1).isEmpty()) { time = m.group(1); start = m.end(1); }
            return waitStatement(mk, safeSub(cmd, start), line, "??~", time, false, true);
        }
        if (cmd.startsWith("??")) {
            m = WAIT.matcher(cmd);
            int start = 2;
            String time = null;
            if (m.find()) { time = m.group(1); start = m.end(1) + 1; }
            return waitStatement(mk, safeSub(cmd, start), line, "??", time, false, false);
        }
        if (cmd.contains("*/")) {
            // Command.parse reads any line holding '*/' (outside quotes) as the end of a block comment and generates
            // nothing for it, e.g. the '*/' inside an unquoted route glob '**/api/...'.
            if (cmd.trim().startsWith("*/") || cmd.trim().endsWith("*/") && !cmd.matches("(?s).*\\*\\*/.*")) return step(Kind.COMMENT, line, "*/");
            return unsupported(line, "*/", "seleasy reads a line containing '*/' as the end of a block comment and never runs it"
                    + " (here the '*/' of '**/'); quote the value ('**/...') so it runs, then export again");
        }
        if (cmd.equals(">>>")) return unsupported(line, ">>>", "code block end without a start");
        if (cmd.equals("end code")) return unsupported(line, "end code", "code block end without a start");
        if (cmd.startsWith("?")) {
            boolean not = cmd.startsWith("?!");
            return ifStatement(mk, cmd.substring(not ? 2 : 1), line, not ? "?!" : "?", Kind.IF, not);
        }
        if (cmd.startsWith("if ") || cmd.startsWith("ifnot ")) {
            boolean not = cmd.startsWith("ifnot ");
            return ifStatement(mk, cmd.substring(not ? 6 : 3), line, not ? "ifnot" : "if", Kind.IF, not);
        }
        if (cmd.startsWith(":?")) {
            boolean not = cmd.startsWith(":?!");
            return ifStatement(mk, cmd.substring(not ? 3 : 2), line, not ? ":?!" : ":?", Kind.ELSE_IF, not);
        }
        if (cmd.startsWith("else if ") || cmd.startsWith("else ifnot ")) {
            if (cmd.startsWith("else ifnot ")) {
                Step s = step(Kind.ELSE_IF, line, "else ifnot");
                return s.unsupported("the transpiler reads `else ifnot x` as `else if` on \"not x\" (it checks for 'else ifnot' with two spaces); "
                        + "not exported — write `:?! x` instead");
            }
            return ifStatement(mk, cmd.substring(8), line, "else if", Kind.ELSE_IF, false);
        }
        if (cmd.startsWith(":")) return step(Kind.ELSE, line, ":");
        if (cmd.equals("else")) return step(Kind.ELSE, line, "else");
        if (cmd.startsWith("#j")) return unsupported(line, "#j", "inline Java control statement");
        for (String p : new String[] { "#sql", "#mongo", "#file", "#provider", "#p", "#provider-sf", "#p-sf", "#counter", "#c", "#optional", "#o" }) {
            if (cmd.startsWith(p + " ")) return unsupported(line, p, "data-provider loop (" + p + ")");
        }
        if (cmd.startsWith("#transient-provider ")) return unsupported(line, "#transient-provider", "transient provider loop");
        if (cmd.startsWith("#transient-variable ")) return elementVar(mk, cmd.substring(20), line, "#transient-variable");
        if (cmd.startsWith("#tv ")) return elementVar(mk, cmd.substring(4), line, "#tv");
        if (cmd.startsWith("#tp ")) return unsupported(line, "#tp", "transient provider loop");
        if (cmd.startsWith("## ")) return unsupported(line, "##", "element loop (##)");
        if (cmd.startsWith("# ")) return unsupported(line, "#", "element loop (#)");
        if (cmd.startsWith("loop over ")) return unsupported(line, "loop over", "element loop (loop over)");
        if (cmd.startsWith("readfile ")) return unsupported(line, "readfile", "file line loop");
        if (cmd.startsWith("filevar ")) return unsupported(line, "filevar", "file variable");
        if (cmd.startsWith("dsq ")) return unsupported(line, "dsq", "data-source query");
        if (cmd.startsWith("ds query ")) return unsupported(line, "ds query", "data-source query");
        if (cmd.startsWith("]")) return unsupported(line, "]", "value list end without a start");
        if (cmd.startsWith("pass ") || cmd.equalsIgnoreCase("pass")) return message(mk, cmd, 4, line, "pass", Kind.PASS);
        if (cmd.startsWith("fail ") || cmd.equalsIgnoreCase("fail")) return message(mk, cmd, 4, line, "fail", Kind.FAIL);
        if (cmd.startsWith("warn ") || cmd.equalsIgnoreCase("warn")) return message(mk, cmd, 4, line, "warn", Kind.WARN);
        if (lc.startsWith("open ")) {
            Step s = step(Kind.OPEN, line, "open");
            s.text = mk.unmask(cmd.substring(5).trim());
            return s;
        }
        if (lc.startsWith("mode ")) return unsupported(line, "mode", "execution mode setting");
        if (lc.startsWith("goto ")) {
            Step s = step(Kind.GOTO, line, "goto");
            s.text = unquote(mk.unmask(cmd.substring(5).trim()));
            return s;
        }
        if (lc.startsWith("timeout ")) {
            List<String> t = mk.tokens(cmd.substring(8));
            Step s = step(Kind.TIMEOUT, line, "timeout");
            if (t.isEmpty() || !t.get(0).matches("\\d{1,9}")) return s.unsupported("timeout needs a number of seconds");
            s.text = t.get(0);
            if (t.size() > 1) s.args.put("extra", String.join(" ", t.subList(1, t.size())));
            return s;
        }
        if (lc.startsWith("layer ")) return unsupported(line, "layer", "no-activity layer setting");
        if (lc.equals("break")) return unsupported(line, "break", "loop control (loops are not exported)");
        if (lc.equals("continue")) return unsupported(line, "continue", "loop control (loops are not exported)");
        if (lc.equals("back")) return step(Kind.BACK, line, "back");
        if (lc.equals("forward")) return step(Kind.FORWARD, line, "forward");
        if (lc.equals("refresh")) return step(Kind.REFRESH, line, "refresh");
        if (lc.startsWith("close")) {
            Step s = step(Kind.CLOSE, line, "close");
            String rest = cmd.substring(5).trim();
            if (!rest.isEmpty()) return s.unsupported("close " + mk.unmask(rest) + ": multi-window close");
            return s;
        }
        if (lc.startsWith("scroll ")) return scroll(mk, cmd.substring(7), line);
        if (lc.equals("waitready")) return step(Kind.WAITREADY, line, "waitready");
        if (lc.equals("maximize")) return step(Kind.MAXIMIZE, line, "maximize");
        if (lc.startsWith("setting ")) return unsupported(line, "setting", "browser session setting");
        if (lc.startsWith("frame ")) return unsupported(line, "frame", "frame switching (use frameLocator in Playwright)");
        if (lc.startsWith("tab ")) return unsupported(line, "tab", "tab switching (multi-page)");
        if (lc.startsWith("window ") || lc.equals("window")) return unsupported(line, "window", "window switching (multi-page)");
        if (lc.startsWith("printpdf ")) return unsupported(line, "printpdf", "PDF print with text extraction");
        if (lc.startsWith("mail ")) return unsupported(line, "mail", "mail simulator check");
        if (lc.startsWith("http")) return unsupported(line, "http", "HTTP simulator check");
        if (lc.startsWith("wopensave ")) return unsupported(line, "wopensave", "window.open interception");
        if (lc.startsWith("netapix ")) return unsupported(line, "netapix", "network API capture (use page.waitForResponse)");
        if (lc.startsWith("a11y ")) return unsupported(line, "a11y", "accessibility check (needs @axe-core/playwright)");
        if (lc.startsWith("clock ")) return clock(mk, cmd.substring(6), line);
        if (lc.startsWith("emulate ")) return emulate(mk, cmd.substring(8), line);
        if (lc.startsWith("state ")) return state(mk, cmd.substring(6), line);
        if (lc.startsWith("route ") || lc.equals("unroute") || lc.startsWith("unroute ")) return route(mk, cmd, line);
        if (lc.startsWith("sleep ")) {
            Step s = step(Kind.SLEEP, line, "sleep");
            String v = mk.unmask(cmd.substring(6).trim());
            if (!v.matches("\\d{1,12}")) return s.unsupported("sleep needs a number of milliseconds");
            s.text = v;
            return s;
        }
        if (lc.matches(TYPE_SW.pattern()) || lc.matches(TYPE_EX) || lc.startsWith("select ")
                || lc.matches(CLICK_SW.pattern()) || lc.matches(CLICK_EX)
                || lc.startsWith("doubleclick ") || lc.equals("doubleclick") || lc.startsWith("dblclick ") || lc.equals("dblclick")
                || lc.startsWith("hover ") || lc.equals("hover") || lc.startsWith("actions ") || lc.startsWith("hoverclick ")
                || lc.startsWith("clear ") || lc.equals("clear") || lc.startsWith("submit ") || lc.equals("submit") || lc.startsWith("upload ")) {
            return action(mk, cmd, line);
        }
        if (lc.startsWith("robot ")) return unsupported(line, "robot", "OS-level keyboard (java.awt.Robot)");
        if (lc.equals("scrollup") || lc.equals("scrolldown") || lc.equals("scrollpageup") || lc.equals("scrollpagedown")) {
            return unsupported(line, lc, "OS-level scroll (java.awt.Robot)");
        }
        if (lc.startsWith("var ")) return var(mk, cmd.substring(4), line);
        if (lc.startsWith("jsvar ")) return jsvar(mk, cmd.substring(6), line);
        if (lc.startsWith("exec ")) return unsupported(line, "exec", "Java code");
        if (lc.startsWith("execjs ")) {
            Step s = step(Kind.EXEC_JS, line, "execjs");
            s.text = unquote(mk.unmask(cmd.substring(7).trim()));
            return s;
        }
        if (lc.startsWith("execjsfile ")) return unsupported(line, "execjsfile", "JavaScript from a file (use page.addScriptTag / evaluate)");
        if (lc.startsWith("canvas ")) return unsupported(line, "canvas", "canvas drawing");
        if (HOOK_BLOCK.matcher(cmd.trim()).matches()) {
            String hk = hookName(cmd.trim());
            Step s = step(Kind.HOOK, line, hk);
            s.text = hk;
            return s;
        }
        m = SUBTEST_MARKER.matcher(cmd);
        if (m.find()) {
            String marker = m.group(1).toLowerCase(Locale.ROOT);
            Step s = subtest(mk, cmd.substring(m.end()), line, marker, Kind.SUBTEST);
            s.args.put("marker", marker);
            return s;
        }
        if (lc.startsWith("subtest ")) return subtest(mk, cmd.substring(8), line, "subtest", Kind.SUBTEST);
        if (lc.startsWith("func ")) return subtest(mk, cmd.substring(5), line, "func", Kind.FUNC);
        if (lc.startsWith("@call ")) return call(mk, cmd.substring(6), line);
        if (lc.startsWith("require ")) return unsupported(line, "require", "Java import");
        if (lc.startsWith("import ")) return unsupported(line, "import", "subtests from another script (export that script too)");
        if (lc.startsWith("include ")) return unsupported(line, "include", "inline include of another script");
        if (lc.startsWith("config ")) return unsupported(line, "config", "config properties file (values come from environment variables)");
        if (lc.startsWith("dynprops ")) return unsupported(line, "dynprops", "dynamic properties file");
        if (lc.startsWith("screenshot compare ") || lc.matches("screenshot\\s+compare(\\s.*)?")) {
            return screenshotCompare(mk, cmd.replaceFirst("(?i)^screenshot\\s+compare", "").trim(), line);
        }
        if (lc.startsWith("screenshot")) {
            Step s = step(Kind.SCREENSHOT, line, "screenshot");
            String rest = cmd.substring(10).trim();
            if (!rest.isEmpty()) {
                List<String> t = mk.tokens(rest);
                if (t.size() > 1) return s.unsupported("screenshot takes one file");
                s.text = unquote(t.get(0));
            }
            return s;
        }
        if (lc.startsWith("ele-screenshot ")) {
            Step s = step(Kind.ELE_SCREENSHOT, line, "ele-screenshot");
            List<String> t = mk.tokens(cmd.substring(15));
            s.target = locator(t.get(0));
            if (t.size() > 2) return s.unsupported("ele-screenshot takes a locator and an optional file");
            if (t.size() == 2) s.text = unquote(t.get(1));
            return checkLocators(s);
        }
        if (lc.startsWith("imgsave ")) return unsupported(line, "imgsave", "element image save");
        if (lc.startsWith("readimgtext ")) return unsupported(line, "readimgtext", "OCR");
        if (lc.startsWith("ocr ")) return unsupported(line, "ocr", "OCR");
        if (lc.startsWith("readqrbar ")) return unsupported(line, "readqrbar", "QR / barcode read");
        if (lc.startsWith("qrcode ")) return unsupported(line, "qrcode", "QR code read");
        if (lc.startsWith("barcode ")) return unsupported(line, "barcode", "barcode read");
        if (lc.startsWith("alert")) return unsupported(line, "alert", "dialog handling (register page.on('dialog') before the action that opens it)");
        if (lc.startsWith("confirm")) return unsupported(line, "confirm", "dialog handling (register page.on('dialog') before the action that opens it)");
        for (String mob : new String[] { "zoom ", "pinch ", "tap ", "touch ", "swipe " }) {
            if (lc.startsWith(mob)) return unsupported(line, mob.trim(), "mobile (Appium) gesture");
        }
        for (String mob : new String[] { "rotate", "hidekeypad", "shake" }) {
            if (lc.trim().equals(mob)) return unsupported(line, mob, "mobile (Appium) command");
        }
        // anything else: a plugin statement (api / json / xml / curl ...), an NLP subtest call, or an unknown word
        Step s = step(Kind.UNSUPPORTED, line, lc.startsWith("curl:") ? "curl:" : null);
        String word = mk.unmask(cmd.split("[\\t ]+")[0]);
        return s.unsupported("plugin or unrecognised statement '" + word + "'");
    }

    private static String safeSub(String s, int start) {
        return start >= s.length() ? "" : s.substring(start);
    }

    private static String hookName(String line) {
        for (String h : new String[] { "beforeAll", "beforeEach", "afterEach", "afterAll" }) if (h.equalsIgnoreCase(line)) return h;
        return line;
    }

    private static Step message(Masked mk, String cmd, int len, int line, String keyword, Kind kind) {
        Step s = step(kind, line, keyword);
        String rest = cmd.length() > len ? cmd.substring(len).trim() : "";
        s.text = unquote(mk.unmask(rest));
        return s;
    }

    // ---- locators --------------------------------------------------------------------------------------------------

    /** One find-expression token. Never throws: an unusable locator carries its reason. */
    static Locator locator(String token) {
        if (token == null || token.isEmpty()) return new Locator("", "", "", null, "missing locator");
        int at = token.indexOf('@');
        if (at < 0) {
            String lc = token.toLowerCase(Locale.ROOT);
            if (lc.equals("active")) return new Locator(token, "active", "", null, null);
            if (lc.equals("this") || lc.equals("current")) return new Locator(token, lc, "", null, "'" + token + "' refers to the element of an enclosing loop");
            return new Locator(token, token, "", null, "'" + token + "' is not a find expression (<by>@<value>)");
        }
        String by = token.substring(0, at).trim();
        String value = unquote(token.substring(at + 1).trim());
        String lb = by.toLowerCase(Locale.ROOT);
        if (!BY.contains(lb) || value.isEmpty()) return new Locator(token, by, value, null, "invalid find expression " + token);
        if (lb.equals("jq") || lb.equals("jquery") || lb.equals("$")) return new Locator(token, by, value, null, "jQuery selector");
        Map<String, Object> sem = null;
        if (SemanticLocators.isSemantic(by, value)) {
            // As FindCommand.checkSemantic: a value holding ${..} / $v{..} is not validated as written. The references
            // are swapped for plain-letter placeholders while parsing, then put back into the parsed value.
            List<String> refs = new ArrayList<String>();
            Matcher r = TEMPLATE_REF.matcher(value);
            StringBuffer masked = new StringBuffer();
            while (r.find()) {
                refs.add(r.group());
                r.appendReplacement(masked, "GATFREF" + (refs.size() - 1) + "X");
            }
            r.appendTail(masked);
            try {
                sem = SemanticLocators.parse(by, masked.toString());
            } catch (IllegalArgumentException e) {
                return new Locator(token, by, value, null, e.getMessage());
            }
            if (!refs.isEmpty() && sem.get("value") instanceof String) {
                String v = (String) sem.get("value");
                for (int i = refs.size() - 1; i >= 0; i--) v = v.replace("GATFREF" + i + "X", refs.get(i));
                sem.put("value", v);
            }
        }
        return new Locator(token, by, value, sem, null);
    }

    private static boolean isLocatorPrefix(String tok) {
        String t = tok.toLowerCase(Locale.ROOT);
        return t.equals("eval") || t.equals("browser-scope") || t.equals("session-scope") || t.equals("relative");
    }

    private static Step checkLocators(Step s) {
        for (Locator l : new Locator[] { s.target, s.target2 }) {
            if (l != null && l.unsupported != null && s.kind != Kind.UNSUPPORTED) s.unsupported("locator " + l.raw + ": " + l.unsupported);
        }
        for (Locator l : s.locators) {
            if (l.unsupported != null && s.kind != Kind.UNSUPPORTED) s.unsupported("locator " + l.raw + ": " + l.unsupported);
        }
        return s;
    }

    // ---- ?? waits and assertions -----------------------------------------------------------------------------------

    /** WaitTillElementVisibleOrInvisibleCommand(time, val, ..., isVisible, forced=false). */
    private Step waitStatement(Masked mk, String val, int line, String keyword, String time, boolean gone, boolean soft) {
        Step s = step(Kind.WAIT, line, keyword);
        s.soft = soft;
        if (time != null && !time.isEmpty()) {
            try {
                int n = Integer.parseInt(time);
                if (n > 0) s.timeoutSec = n;
            } catch (NumberFormatException e) {
                // seleasy ignores an unparsable wait time too
            }
        }
        List<String> p = mk.tokens(val);
        if (p.isEmpty()) return s.unsupported("no locator");
        if (isLocatorPrefix(p.get(0))) return s.unsupported(p.get(0) + " find expression (eval / scope / relative)");
        if (p.size() > 1) {
            String verb = p.get(1).toLowerCase(Locale.ROOT);
            boolean typeLike = verb.matches("(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?");
            boolean act = typeLike || verb.equals("select") || verb.matches("click(fo|nf)?") || verb.equals("hover") || verb.equals("hoverclick")
                    || verb.equals("clear") || verb.equals("submit") || verb.equals("upload") || verb.equals("doubleclick") || verb.equals("dblclick");
            if (act) {
                s.kind = Kind.ACTION;
                s.target = locator(p.get(0));
                s.args.put("waited", "true");
                if (verb.startsWith("randomize")) return s.unsupported("randomize (random test data)");
                if (verb.equals("hoverclick")) return s.unsupported("hoverclick after ?? (two locators in one find)");
                boolean withValue = (typeLike && !verb.startsWith("randomize")) || verb.equals("select") || verb.equals("upload");
                int extraFrom = withValue ? 3 : 2;
                if (withValue && p.size() < 3) return s.unsupported(verb + " needs a value");
                if (p.size() > extraFrom) return s.unsupported("extra find-expression parts after the action: " + String.join(" ", p.subList(extraFrom, p.size())));
                actionVerb(s, verb, withValue ? p.get(2) : null);
                return checkLocators(s);
            }
            if (p.get(0).equalsIgnoreCase("actions") || verb.equals("actions")) return s.unsupported("actions chain after ??");
            return assertion(s, p);
        }
        if (p.get(0).equalsIgnoreCase("actions")) return s.unsupported("actions chain after ??");
        s.verb = gone ? "gone" : "present";
        s.target = locator(p.get(0));
        return checkLocators(s);
    }

    /** FindCommand(val): {@code <locator> <subselector> <op><value>} or {@code <page-property> <op><value>}. */
    private Step assertion(Step s, List<String> p) {
        s.kind = Kind.ASSERT;
        Assertion a = new Assertion();
        s.assertion = a;
        int nx;
        String sub;
        if (p.get(0).indexOf('@') >= 0) {
            s.target = locator(p.get(0));
            sub = p.get(1);
            nx = 2;
        } else {
            sub = p.get(0);
            String lsub = sub.toLowerCase(Locale.ROOT);
            if (lsub.equals("this") || lsub.equals("current") || lsub.equals("active")) {
                s.target = locator(p.get(0));
                if (p.size() < 2) return s.unsupported("no property to assert");
                sub = p.get(1);
                nx = 2;
            } else {
                a.pageLevel = true;
                nx = 1;
            }
        }
        if (p.size() > nx + 2) return s.unsupported("assertion has more parts than <property> <op><value>");
        String lsub = sub.toLowerCase(Locale.ROOT);
        if (lsub.startsWith("attr@") || lsub.startsWith("cssvalue@")) {
            a.property = lsub.substring(0, lsub.indexOf('@'));
            a.name = unquote(sub.substring(sub.indexOf('@') + 1));
        } else {
            a.property = lsub;
        }
        if (p.size() <= nx) return s.unsupported("assertion without an operator and value");
        String oper = p.get(nx) + (p.size() > nx + 1 ? p.get(nx + 1) : "");
        String cval;
        if (oper.startsWith("<=")) { a.op = "<="; cval = oper.substring(2); }
        else if (oper.startsWith(">=")) { a.op = ">="; cval = oper.substring(2); }
        else if (oper.startsWith("=")) { a.op = "="; cval = oper.substring(oper.startsWith("==") ? 1 : 1); }
        else if (oper.startsWith("<")) { a.op = "<"; cval = oper.substring(1); }
        else if (oper.startsWith(">")) { a.op = ">"; cval = oper.substring(1); }
        else if (oper.startsWith("!=")) { a.op = "!="; cval = oper.substring(2); }
        else if (oper.startsWith("%") && oper.endsWith("%") && oper.length() > 1) { a.op = "contains"; cval = oper.substring(1, oper.length() - 1); }
        else if (oper.startsWith("%")) { a.op = "startsWith"; cval = oper.substring(1); }
        else if (oper.endsWith("%")) { a.op = "endsWith"; cval = oper.substring(0, oper.length() - 1); }
        else return s.unsupported("unknown assertion operator in '" + oper + "'");
        cval = cval.trim();
        if (cval.isEmpty()) return s.unsupported("assertion without a value");
        if (cval.startsWith("[") && cval.endsWith("]")) {
            a.values = new ArrayList<String>();
            for (String v : cval.substring(1, cval.length() - 1).split(",")) a.values.add(unquote(v.trim()));
        } else {
            a.value = unquote(cval);
        }
        return checkLocators(s);
    }

    // ---- element actions -------------------------------------------------------------------------------------------

    private Step action(Masked mk, String cmd, int line) {
        String lc = cmd.toLowerCase(Locale.ROOT);
        Step s = step(Kind.ACTION, line, null);
        Matcher m = TYPE_SW_CI.matcher(cmd);
        if (lc.matches(TYPE_SW.pattern()) && m.matches()) {
            String verb = m.group(1).toLowerCase(Locale.ROOT);
            s.keyword = verb;
            if (verb.equals("randomize")) return s.unsupported("randomize (random test data)");
            String rest = cmd.substring(m.start(3));
            List<String> t = mk.tokens(rest);
            if (t.size() < 2) return s.unsupported(verb + " acts on the current element (no locator)");
            if (t.size() > 2) return s.unsupported(verb + ": extra parts after the locator");
            if (isLocatorPrefix(t.get(1))) return s.unsupported(t.get(1) + " find expression (eval / scope / relative)");
            s.target = locator(t.get(1));
            actionVerb(s, verb + (m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT)), t.get(0));
            return checkLocators(s);
        }
        if (lc.matches(TYPE_EX)) {
            s.keyword = lc.replaceAll("(bl|ch|bk|cl|fo)$", "");
            return s.unsupported("acts on the current element (no value or locator)");
        }
        if (lc.startsWith("select ")) {
            s.keyword = "select";
            List<String> t = mk.tokens(cmd.substring(7));
            if (t.size() != 2) return s.unsupported("select needs <mode>@<value> and a locator");
            if (isLocatorPrefix(t.get(1))) return s.unsupported(t.get(1) + " find expression (eval / scope / relative)");
            s.target = locator(t.get(1));
            actionVerb(s, "select", t.get(0));
            return checkLocators(s);
        }
        if (lc.matches(CLICK_SW.pattern()) || lc.matches(CLICK_EX)) {
            s.keyword = "click";
            Matcher c = CLICK_ANY.matcher(cmd);
            if (!c.matches() || c.group(2) == null) return s.unsupported("click acts on the current element (no locator)");
            List<String> t = mk.tokens(c.group(2));
            return simpleAction(s, "click" + (c.group(1) == null ? "" : c.group(1).toLowerCase(Locale.ROOT)), t);
        }
        for (String v : new String[] { "doubleclick", "dblclick", "hover", "clear", "submit" }) {
            if (lc.equals(v)) {
                s.keyword = v;
                return s.unsupported(v + " acts on the current element (no locator)");
            }
            if (lc.startsWith(v + " ")) {
                s.keyword = v;
                return simpleAction(s, v.equals("doubleclick") ? "dblclick" : v, mk.tokens(cmd.substring(v.length())));
            }
        }
        if (lc.startsWith("hoverclick ")) {
            s.keyword = "hoverclick";
            List<String> t = mk.tokens(cmd.substring(11));
            if (t.size() != 2) return s.unsupported("hoverclick needs two locators");
            s.verb = "hoverclick";
            s.target = locator(t.get(0));
            s.target2 = locator(t.get(1));
            return checkLocators(s);
        }
        if (lc.startsWith("upload ")) {
            s.keyword = "upload";
            List<String> t = mk.tokens(cmd.substring(7));
            if (t.size() != 2) return s.unsupported("upload needs a file and a locator");
            s.target = locator(t.get(1));
            actionVerb(s, "upload", t.get(0));
            return checkLocators(s);
        }
        if (lc.startsWith("actions ")) {
            s.keyword = "actions";
            List<String> t = mk.tokens(cmd.substring(8));
            if (t.size() == 3 && t.get(0).toLowerCase(Locale.ROOT).matches("draganddrop1?|dragdrop1?")) {
                s.verb = "drag";
                s.args.put("gesture", t.get(0).toLowerCase(Locale.ROOT));
                s.target = locator(t.get(1));
                s.target2 = locator(t.get(2));
                return checkLocators(s);
            }
            return s.unsupported("action chain other than a single draganddrop");
        }
        return s.unsupported("unrecognised action");
    }

    private static Step simpleAction(Step s, String verb, List<String> t) {
        if (t.isEmpty()) return s.unsupported(verb + " acts on the current element (no locator)");
        if (isLocatorPrefix(t.get(0))) return s.unsupported(t.get(0) + " find expression (eval / scope / relative)");
        if (t.size() > 1) return s.unsupported(verb + ": extra parts after the locator");
        s.target = locator(t.get(0));
        actionVerb(s, verb, null);
        return checkLocators(s);
    }

    /** Fills verb / qualifier / value for an action; {@code value} is the raw token (type text, select mode, upload path). */
    private static void actionVerb(Step s, String verbWithQual, String value) {
        String v = verbWithQual.toLowerCase(Locale.ROOT);
        Matcher tq = Pattern.compile("^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?$").matcher(v);
        Matcher cq = Pattern.compile("^click(fo|nf)?$").matcher(v);
        if (tq.matches()) {
            s.verb = tq.group(1).equals("chord") ? "chord" : "type";
            s.keyword = s.keyword == null ? tq.group(1) : s.keyword;
            s.qualifier = tq.group(2) == null ? "" : tq.group(2);
            s.text = unquote(value);
        } else if (cq.matches()) {
            s.verb = "click";
            s.qualifier = cq.group(1) == null ? "" : cq.group(1);
        } else if (v.equals("select")) {
            s.verb = "select";
            String mode = value;
            int at = mode.indexOf('@');
            String lm = (at < 0 ? mode : mode.substring(0, at)).toLowerCase(Locale.ROOT);
            if (at < 0 && (lm.equals("first") || lm.equals("last"))) {
                s.args.put("mode", lm);
            } else if (at > 0 && (lm.equals("text") || lm.equals("index") || lm.equals("value"))) {
                s.args.put("mode", lm);
                s.text = unquote(mode.substring(at + 1));
                if (lm.equals("index") && !s.text.matches("\\d{1,6}")) s.unsupported("select index@ needs a number");
            } else {
                s.unsupported("select mode must be text@, index@, value@, first or last");
            }
        } else if (v.equals("upload")) {
            s.verb = "upload";
            s.text = unquote(value);
        } else {
            s.verb = v.equals("doubleclick") ? "dblclick" : v;
        }
        if (s.keyword == null) s.keyword = v.replaceAll("(fo|nf)$", "");
    }

    // ---- control flow ----------------------------------------------------------------------------------------------

    private Step ifStatement(Masked mk, String cond, int line, String keyword, Kind kind, boolean not) {
        Step s = step(kind, line, keyword);
        s.negated = not;
        Masked m = mask(mk.unmask(cond));
        if (m.text.indexOf('&') >= 0) return s.unsupported("compound condition (&)");
        List<String> t = m.tokens(m.text);
        if (t.isEmpty()) return s.unsupported("empty condition");
        if (isLocatorPrefix(t.get(0))) return s.unsupported(t.get(0) + " condition (eval / scope / relative)");
        if (t.size() > 1) return s.unsupported("condition with a property assertion");
        s.target = locator(t.get(0));
        return checkLocators(s);
    }

    private Step subtest(Masked mk, String val, int line, String keyword, Kind kind) {
        Step s = step(kind, line, keyword);
        List<String> parts = mk.tokens(val);
        if (parts.isEmpty()) return s.unsupported("subtest without a name");
        String name = TAG.matcher(unquote(parts.get(0))).replaceAll("").trim();
        if (name.isEmpty()) return s.unsupported("subtest without a name");
        s.text = name;
        int i = 1;
        StringBuilder session = new StringBuilder();
        while (i < parts.size() && !parts.get(i).startsWith("(")) session.append(session.length() > 0 ? " " : "").append(parts.get(i++));
        if (i < parts.size()) {
            String joined = String.join(" ", parts.subList(i, parts.size())).trim();
            if (!joined.endsWith(")")) return s.unsupported("unclosed parameter list");
            for (String p : joined.substring(1, joined.length() - 1).trim().split("[\\t ]+")) if (!p.isEmpty()) s.list.add(p);
        }
        if (session.length() > 0) return s.unsupported("bound to a browser session (" + session + "): multi-browser sessions");
        return s;
    }

    private Step call(Masked mk, String val, int line) {
        Step s = step(Kind.CALL, line, "@call");
        List<String> parts = mk.tokens(val);
        if (parts.isEmpty()) return s.unsupported("@call without a name");
        s.text = unquote(parts.get(0));
        int i = 1;
        StringBuilder session = new StringBuilder();
        while (i < parts.size() && !parts.get(i).startsWith("(")) session.append(session.length() > 0 ? " " : "").append(parts.get(i++));
        if (i < parts.size()) {
            String joined = String.join(" ", parts.subList(i, parts.size())).trim();
            if (!joined.endsWith(")")) return s.unsupported("unclosed argument list");
            for (String p : joined.substring(1, joined.length() - 1).trim().split("[\\t ]+")) {
                if (p.isEmpty()) continue;
                int c = p.indexOf(':');
                if (c <= 0) return s.unsupported("argument '" + p + "' is not name:value");
                s.args.put("arg." + p.substring(0, c), unquote(p.substring(c + 1)));
            }
        }
        if (session.length() > 0) return s.unsupported("bound to a browser session (" + session + "): multi-browser sessions");
        return s;
    }

    // ---- variables -------------------------------------------------------------------------------------------------

    private Step var(Masked mk, String val, int line) {
        Step s = step(Kind.VAR, line, "var");
        List<String> t = mk.tokens(val);
        if (t.size() < 2) return s.unsupported("var needs a name and a value");
        s.text = t.get(0);
        String v = String.join(" ", t.subList(1, t.size()));
        if (t.size() == 2) {
            String one = t.get(1);
            if ((one.startsWith("\"") || one.startsWith("'")) && one.length() >= 2 && one.endsWith(one.substring(0, 1))) {
                s.args.put("value", unquote(one));
                return s;
            }
            if (one.startsWith("@") && one.length() > 1 && one.substring(1).matches("[A-Za-z_][A-Za-z0-9_]*")) {
                s.args.put("ref", one.substring(1));
                return s;
            }
            if (one.matches("-?\\d+(\\.\\d+)?|true|false")) {
                s.args.put("value", one);
                return s;
            }
        }
        if (t.get(1).equalsIgnoreCase("plugin")) {
            s.rawBlock = true;
            return s.unsupported("plugin value (" + v + ")");
        }
        return s.unsupported("Java expression value (" + v + ")");
    }

    private Step jsvar(Masked mk, String val, int line) {
        Step s = step(Kind.JS_VAR, line, "jsvar");
        List<String> t = mk.tokens(val);
        if (t.size() != 2) return s.unsupported("jsvar needs a name and one quoted script");
        s.text = t.get(0);
        s.args.put("script", unquote(t.get(1)));
        return s;
    }

    private Step elementVar(Masked mk, String val, int line, String keyword) {
        Step s = step(Kind.ELEMENT_VAR, line, keyword);
        List<String> t = mk.tokens(val);
        if (t.size() != 3) return s.unsupported("#tv needs a name, a locator and text|html|attr@name");
        s.text = t.get(0);
        if (isLocatorPrefix(t.get(1))) return s.unsupported(t.get(1) + " find expression (eval / scope / relative)");
        s.target = locator(t.get(1));
        String sub = t.get(2);
        String ls = sub.toLowerCase(Locale.ROOT);
        if (ls.equals("text") || ls.equals("html")) s.args.put("read", ls);
        else if (ls.startsWith("attr@") && sub.length() > 5) { s.args.put("read", "attr"); s.args.put("name", unquote(sub.substring(5))); }
        else return s.unsupported("#tv sub-selector '" + sub + "'");
        return checkLocators(s);
    }

    // ---- page-level statements -------------------------------------------------------------------------------------

    private Step scroll(Masked mk, String val, int line) {
        Step s = step(Kind.SCROLL, line, "scroll");
        List<String> t = mk.tokens(val);
        if (t.size() != 1) return s.unsupported("scroll takes one argument");
        String a = t.get(0).toLowerCase(Locale.ROOT);
        if (a.matches("up|down|pageup|pagedown|top|bottom")) {
            s.text = a;
            return s;
        }
        if (isLocatorPrefix(t.get(0))) return s.unsupported(t.get(0) + " find expression");
        s.target = locator(t.get(0));
        if (s.target.unsupported != null) return s.unsupported("scroll " + t.get(0) + ": one of up|down|pageup|pagedown|top|bottom|<find-expr>");
        return s;
    }

    private Step clock(Masked mk, String spec, int line) {
        Step s = step(Kind.CLOCK, line, "clock");
        String v = mk.unmask(spec).trim();
        if (v.contains("$")) return s.unsupported("clock value with a variable");
        String op = v.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String arg = v.contains(" ") ? unquote(v.substring(v.indexOf(' ') + 1).trim()) : "";
        try {
            FakeClock.parse(v);
        } catch (IllegalArgumentException e) {
            return s.unsupported(e.getMessage());
        }
        switch (op) {
            case "install":
                s.verb = "install";
                if (!arg.isEmpty()) s.args.put("ms", Long.toString(FakeClock.time(arg)));
                break;
            case "set":
                s.verb = "set";
                s.args.put("ms", Long.toString(FakeClock.time(arg)));
                break;
            case "fastforward": case "ff": case "runfor":
                s.verb = "fastforward";
                s.args.put("ms", Long.toString(FakeClock.duration(arg)));
                break;
            case "resume":
                s.verb = "resume";
                break;
            default:
                return s.unsupported("clock " + op + " has no Playwright equivalent");
        }
        s.text = v;
        return s;
    }

    private Step emulate(Masked mk, String val, int line) {
        Step s = step(Kind.EMULATE, line, "emulate");
        List<String> t = mk.tokens(val);
        if (t.isEmpty()) return s.unsupported("emulate needs a setting");
        String what = t.get(0).toLowerCase(Locale.ROOT);
        s.verb = what;
        switch (what) {
            case "viewport":
                if (t.size() != 3 || !t.get(1).matches("\\d{1,5}") || !t.get(2).matches("\\d{1,5}")) return s.unsupported("emulate viewport <width> <height>");
                s.args.put("width", t.get(1));
                s.args.put("height", t.get(2));
                return s;
            case "geolocation":
                if (t.size() != 3 || !t.get(1).matches("-?\\d+(\\.\\d+)?") || !t.get(2).matches("-?\\d+(\\.\\d+)?")) return s.unsupported("emulate geolocation <lat> <lon> (accuracy not mapped)");
                s.args.put("latitude", t.get(1));
                s.args.put("longitude", t.get(2));
                return s;
            case "permissions":
                if (t.size() != 2) return s.unsupported("emulate permissions a,b,...");
                for (String p : unquote(t.get(1)).split(",")) if (!p.trim().isEmpty()) s.list.add(p.trim());
                return s;
            case "colorscheme":
                if (t.size() != 2 || !t.get(1).toLowerCase(Locale.ROOT).matches("dark|light|no-preference")) return s.unsupported("emulate colorscheme dark|light");
                s.args.put("scheme", t.get(1).toLowerCase(Locale.ROOT));
                return s;
            case "offline": case "online":
                if (t.size() != 1) return s.unsupported("emulate " + what + " takes no value");
                return s;
            default:
                return s.unsupported("emulate " + what + " is a browser-context option in Playwright (test.use / newContext)");
        }
    }

    private Step state(Masked mk, String val, int line) {
        Step s = step(Kind.STATE_SAVE, line, "state");
        List<String> t = mk.tokens(val);
        if (t.size() != 2) return s.unsupported("state save|load <file>");
        if (t.get(0).equalsIgnoreCase("load")) return s.unsupported("state load: storageState is a browser-context option in Playwright (test.use({ storageState }))");
        if (!t.get(0).equalsIgnoreCase("save")) return s.unsupported("state save|load <file>");
        s.text = unquote(t.get(1));
        return s;
    }

    private Step route(Masked mk, String cmd, int line) {
        String lc = cmd.toLowerCase(Locale.ROOT);
        if (lc.startsWith("unroute")) {
            Step s = step(Kind.UNROUTE, line, "unroute");
            List<String> t = mk.tokens(cmd.substring(7));
            if (t.size() != 1) return s.unsupported("unroute <url-glob|all>");
            s.text = unquote(t.get(0));
            if (t.get(0).equals("all")) s.args.put("all", "true");
            return s;
        }
        Step s = step(Kind.ROUTE, line, "route");
        List<String> t = mk.tokens(cmd.substring(6));
        if (t.size() >= 3 && t.get(0).equalsIgnoreCase("from") && t.get(1).equalsIgnoreCase("har")) {
            s.kind = Kind.ROUTE_HAR;
            s.text = unquote(t.get(2));
            for (int i = 3; i < t.size(); i++) {
                String w = t.get(i).toLowerCase(Locale.ROOT);
                if (w.equals("url") && i + 1 < t.size()) s.args.put("url", unquote(t.get(++i)));
                else if (w.equals("notfound") && i + 1 < t.size() && t.get(i + 1).toLowerCase(Locale.ROOT).matches("abort|fallback")) s.args.put("notFound", t.get(++i).toLowerCase(Locale.ROOT));
                else return s.unsupported("route from har: unknown option '" + t.get(i) + "'");
            }
            return s;
        }
        int i = 0;
        if (!t.isEmpty() && t.get(0).matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")) s.args.put("method", t.get(i++));
        if (i >= t.size()) return s.unsupported("route needs a url glob");
        s.text = unquote(t.get(i++));
        if (i >= t.size()) return s.unsupported("route needs fulfill, abort or continue");
        String action = t.get(i++).toLowerCase(Locale.ROOT);
        s.verb = action;
        switch (action) {
            case "fulfill":
                if (i >= t.size() || !t.get(i).matches("\\d{3}")) return s.unsupported("route fulfill needs a status");
                s.args.put("status", t.get(i++));
                if (i < t.size() && !t.get(i).equalsIgnoreCase("type") && !t.get(i).equalsIgnoreCase("header")) {
                    String b = t.get(i++);
                    if (b.startsWith("'") || b.startsWith("\"")) s.args.put("body", unquote(b));
                    else s.args.put("bodyFile", b);
                }
                break;
            case "abort":
                if (i < t.size() && !t.get(i).equalsIgnoreCase("header")) s.args.put("reason", t.get(i++).toLowerCase(Locale.ROOT));
                break;
            case "continue":
                break;
            default:
                return s.unsupported("route action must be fulfill, abort or continue");
        }
        while (i < t.size()) {
            String w = t.get(i).toLowerCase(Locale.ROOT);
            if (w.equals("type") && action.equals("fulfill") && i + 1 < t.size()) { s.args.put("type", unquote(t.get(i + 1))); i += 2; }
            else if (w.equals("header") && !action.equals("abort") && i + 1 < t.size()) {
                String h = unquote(t.get(i + 1));
                if (h.indexOf(':') <= 0) return s.unsupported("header must be 'Name: value'");
                s.list.add(h);
                i += 2;
            } else return s.unsupported("route: unknown option '" + t.get(i) + "'");
        }
        return s;
    }

    private Step screenshotCompare(Masked mk, String val, int line) {
        Step s = step(Kind.SCREENSHOT_COMPARE, line, "screenshot compare");
        Masked m = mask(mk.unmask(val));
        List<String> t = m.tokens(m.text);
        if (t.isEmpty()) return s.unsupported("screenshot compare needs a name");
        s.text = unquote(t.get(0));
        for (int i = 1; i < t.size(); i++) {
            String w = t.get(i).toLowerCase(Locale.ROOT);
            if (w.equals("mask") && i + 1 < t.size()) s.locators.add(locator(t.get(++i)));
            else if (w.equals("maxdiff") && i + 1 < t.size()) s.args.put("maxdiff", t.get(++i));
            else if (w.matches("\\d+(\\.\\d+)?") && !s.args.containsKey("threshold")) s.args.put("threshold", w);
            else return s.unsupported("screenshot compare: unknown option '" + t.get(i) + "'");
        }
        String md = s.args.get("maxdiff");
        if (md != null && !md.matches("\\d+(\\.\\d+)?%?")) return s.unsupported("maxdiff must be a number or a percentage");
        return checkLocators(s);
    }
}
