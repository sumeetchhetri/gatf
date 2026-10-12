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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Δ160 — a backend-neutral model of one parsed .sel script: what each statement means (navigate, act on a locator,
 * assert, define or call a subtest, ...), not the Java the transpiler would emit. Built once by {@link SelParser}
 * and walked by every {@link Exporter} backend. Values keep seleasy's template text ({@code ${x}}, {@code $v{x}});
 * {@link Template} splits them, and no backend resolves them. Pure: no Selenium types.
 */
public final class SelScript {

    /** The script name: the file name without {@code .sel}. */
    public final String name;
    /** The script's source lines, as split by the parser (CR, LF or CRLF), index 0 = line 1. */
    public final List<String> lines;
    /** Top-level statements in source order; subtest / func / hook definitions stay in place. */
    public final List<Step> steps = new ArrayList<Step>();

    SelScript(String name, List<String> lines) {
        this.name = name;
        this.lines = Collections.unmodifiableList(lines);
    }

    /** The original text of a 1-based line, without its line terminator. */
    public String line(int lineNo) {
        return lineNo >= 1 && lineNo <= lines.size() ? lines.get(lineNo - 1) : "";
    }

    /** What a statement means. Backends switch on this; anything they cannot map is rendered from source lines. */
    public enum Kind {
        COMMENT, OPEN, GOTO, BACK, FORWARD, REFRESH, MAXIMIZE, WAITREADY, SLEEP, TIMEOUT, CLOSE,
        /** An element action; {@link Step#verb} = click, dblclick, hover, clear, submit, type, select, upload, hoverclick, drag. */
        ACTION,
        /** {@link Step#verb} = present ({@code ??}, {@code ??-}, {@code wait for}) or gone ({@code ??+}, {@code wait till visible}). */
        WAIT,
        ASSERT, SCROLL,
        VAR, ELEMENT_VAR, JS_VAR, EXEC_JS,
        IF, ELSE_IF, ELSE,
        SUBTEST, FUNC, HOOK, CALL,
        PASS, FAIL, WARN,
        SCREENSHOT, ELE_SCREENSHOT, SCREENSHOT_COMPARE,
        ROUTE, ROUTE_HAR, UNROUTE, STATE_SAVE, EMULATE, CLOCK,
        /** Not mappable: rendered as TODO comments from its source lines ({@link Step#line}..{@link Step#endLine}). */
        UNSUPPORTED
    }

    /** One statement (with its block, when it opens one). */
    public static final class Step {
        public Kind kind;
        /** 1-based line of the statement; {@link #endLine} is its last line (the closing brace of its block, if any). */
        public final int line;
        public int endLine;
        /** Block nesting depth (0 = top level), counted in {@code { }} blocks as the parser does. */
        public int depth;
        /** The catalog keyword the statement starts with ({@link ExportCatalog}), or null for plugin / unknown words. */
        public String keyword;
        /** Why the statement is UNSUPPORTED, or null. */
        public String reason;
        /** A trailing {@code // comment} stripped from the line, or null. */
        public String trailingComment;

        public String verb;
        public String qualifier = "";
        /** {@code nowait} / {@code <verb>!}: skip the actionability wait for this line. */
        public boolean noWait;
        /** {@code ??~}: a failure is recorded and the script continues. */
        public boolean soft;
        /** {@code ??N}: seconds to wait for the element, or null for the global timeout. */
        public Integer timeoutSec;
        /** {@code ?!}, {@code ifnot}, ...: the condition is negated. */
        public boolean negated;

        public Locator target;
        public Locator target2;
        /** The statement's value as written (outer quotes stripped): typed text, URL, message, path, ... */
        public String text;
        public Assertion assertion;
        /** Named arguments (route status, select mode, emulate values, subtest marker, ...). */
        public final Map<String, String> args = new LinkedHashMap<String, String>();
        /** Ordered extras (route headers, screenshot masks as raw locators, call/subtest parameters). */
        public final List<String> list = new ArrayList<String>();
        public final List<Locator> locators = new ArrayList<Locator>();

        /** True when a {@code { }} block follows the statement. */
        public boolean hasBlock;
        /** True when the block holds data (plugin body, value lists), not statements. */
        public boolean rawBlock;
        public final List<Step> children = new ArrayList<Step>();

        Step(Kind kind, int line) {
            this.kind = kind;
            this.line = line;
            this.endLine = line;
        }

        Step unsupported(String why) {
            this.kind = Kind.UNSUPPORTED;
            if (this.reason == null) this.reason = why;
            return this;
        }

        @Override
        public String toString() {
            return line + ":" + kind + (keyword != null ? "(" + keyword + ")" : "") + (reason != null ? " - " + reason : "");
        }
    }

    /** A find expression: {@code <by>@<value>}, or {@code active} / {@code this} / {@code current}. */
    public static final class Locator {
        /** The token as written, e.g. {@code role@button[name=Save]}. */
        public final String raw;
        /** The strategy as written (case kept), e.g. {@code role}, {@code id}, {@code xpath}; {@code active} for active. */
        public final String by;
        /** The value with seleasy's outer-quote stripping applied, as the parser does it. */
        public final String value;
        /** {@code SemanticLocators.parse(by, value)} for semantic locators (role, label, ..., text@=x / text@/re/), else null. */
        public final Map<String, Object> semantic;
        /** Why this locator cannot be exported, or null. */
        public final String unsupported;

        Locator(String raw, String by, String value, Map<String, Object> semantic, String unsupported) {
            this.raw = raw;
            this.by = by;
            this.value = value;
            this.semantic = semantic;
            this.unsupported = unsupported;
        }

        /** The strategy in lower case. */
        public String kind() {
            return by == null ? "" : by.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /** {@code ?? <locator> <property> <op><value>}; property may be page-level (title, currentUrl, ...). */
    public static final class Assertion {
        /** Lower-case property: text, tagname, attr, cssvalue, selected, enabled, visible, title, currenturl, ... */
        public String property;
        /** The attribute / CSS property name for attr@x / cssvalue@x. */
        public String name;
        /** = != < > <= >= contains (%x%) startsWith (%x) endsWith (x%). */
        public String op;
        /** The expected value (outer quotes stripped), or null when {@link #values} holds a list. */
        public String value;
        /** {@code =[a, b]}: the value must be one of these. */
        public List<String> values;
        /** True when the property is read from the page, not from the element ({@code ?? title ="x"}). */
        public boolean pageLevel;
    }
}
