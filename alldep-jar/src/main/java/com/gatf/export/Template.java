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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Δ160 — a seleasy value split into literal text and references, never resolved: {@code $v{name}} is a script
 * variable, {@code ${name}} / {@code $!{name}} a config / context value (exported as an environment variable). A bare
 * Velocity {@code $name} is kept as literal text and flagged ({@link #bareReferences()}), because Velocity leaves an
 * undefined reference as written. Pure.
 */
public final class Template {

    public enum PartKind { LITERAL, VAR, ENV }

    public static final class Part {
        public final PartKind kind;
        /** Literal text, or the referenced name. */
        public final String text;

        Part(PartKind kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private static final Pattern REF = Pattern.compile("\\$v\\{([^{}]*)\\}|\\$!?\\{([A-Za-z_][A-Za-z0-9_.\\-]*)\\}");
    private static final Pattern BARE = Pattern.compile("\\$!?[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern VAR_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]*");

    private final List<Part> parts;
    private final List<String> bare;

    private Template(List<Part> parts, List<String> bare) {
        this.parts = Collections.unmodifiableList(parts);
        this.bare = Collections.unmodifiableList(bare);
    }

    public static Template parse(String raw) {
        List<Part> parts = new ArrayList<Part>();
        List<String> bare = new ArrayList<String>();
        String s = raw == null ? "" : raw;
        Matcher m = REF.matcher(s);
        int at = 0;
        while (m.find()) {
            String varName = m.group(1);
            if (varName != null && !VAR_NAME.matcher(varName.trim()).matches()) continue;   // not a plain name: literal
            literal(parts, bare, s.substring(at, m.start()));
            if (varName != null) parts.add(new Part(PartKind.VAR, varName.trim()));
            else parts.add(new Part(PartKind.ENV, m.group(2)));
            at = m.end();
        }
        literal(parts, bare, s.substring(at));
        return new Template(parts, bare);
    }

    private static void literal(List<Part> parts, List<String> bare, String text) {
        if (text.isEmpty()) return;
        Matcher b = BARE.matcher(text);
        while (b.find()) bare.add(b.group());
        if (!parts.isEmpty() && parts.get(parts.size() - 1).kind == PartKind.LITERAL) {
            Part last = parts.remove(parts.size() - 1);
            parts.add(new Part(PartKind.LITERAL, last.text + text));
        } else {
            parts.add(new Part(PartKind.LITERAL, text));
        }
    }

    public List<Part> parts() {
        return parts;
    }

    /** True when the value holds no reference. */
    public boolean isLiteral() {
        for (Part p : parts) if (p.kind != PartKind.LITERAL) return false;
        return true;
    }

    /** The literal text (only meaningful when {@link #isLiteral()}). */
    public String literalText() {
        StringBuilder b = new StringBuilder();
        for (Part p : parts) if (p.kind == PartKind.LITERAL) b.append(p.text);
        return b.toString();
    }

    /** Bare Velocity references ({@code $name}) left as literal text. */
    public List<String> bareReferences() {
        return bare;
    }

    /** The environment-variable name a {@code ${name}} reference is exported as: GATF_ + upper case, non [A-Z0-9] → _. */
    public static String envName(String name) {
        String u = name.toUpperCase(java.util.Locale.ROOT);
        StringBuilder b = new StringBuilder("GATF_");
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            b.append((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return b.toString();
    }
}
