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
package com.gatf.ai.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Δ152 (carried from Δ141): {@code Command.validateSel}'s {@code {status, error}} as a {@code diagnostics} list
 * ({@code {file, line, column?, severity, message}}). A seleasy parse error ("Error parsing command at line N in file
 * F (…)" / "Error at line N …") gives the script line; a Java compile error of the transpiled code has no script line
 * (its line is in the generated Java), so {@code line} is null and the message keeps the compiler's text. Messages are
 * control-free and capped; scratch paths are shortened to the file name. Pure.
 */
public final class LintDiagnostics {

    public static final int MAX_MESSAGE = 500;
    public static final int MAX_DIAGNOSTICS = 50;
    static final Pattern SEL = Pattern.compile("Error(?: parsing command)? at line (\\d{1,7}) in file (.+?) \\((.*?)\\)(?=Error(?: parsing command)? at line |$)", Pattern.DOTALL);

    private LintDiagnostics() {}

    public static List<SelGate.Diag> from(String file, String status, String error) {
        List<SelGate.Diag> out = new ArrayList<SelGate.Diag>();
        if ("SUCCESS".equals(status)) return out;
        String e = error == null ? "" : error;
        Matcher m = SEL.matcher(e);
        while (m.find() && out.size() < MAX_DIAGNOSTICS) {
            out.add(new SelGate.Diag(file, Integer.valueOf(m.group(1)), null, "error", clean("Error parsing command at line " + m.group(1) + " (" + m.group(3) + ")")));
        }
        if (out.isEmpty()) {
            String msg = clean(e);
            out.add(new SelGate.Diag(file, null, null, "error", msg.isEmpty() ? "the script did not parse or compile (no detail reported)" : msg));
        }
        return out;
    }

    /**
     * The MCP {@code lint} result ({@code {status, error, file}}) → its {@code diagnostics} list of
     * {@code {file, line, column?, severity, message}}; empty on SUCCESS. The result's own keys are not changed.
     */
    public static List<java.util.Map<String, Object>> json(java.util.Map<String, Object> lintResult) {
        List<java.util.Map<String, Object>> out = new ArrayList<java.util.Map<String, Object>>();
        Object f = lintResult.get("file"), st = lintResult.get("status"), er = lintResult.get("error");
        for (SelGate.Diag d : from(f == null ? null : String.valueOf(f), st == null ? null : String.valueOf(st), er == null ? null : String.valueOf(er))) {
            out.add(d.json());
        }
        return out;
    }

    /** Control / line-separator characters to spaces, whitespace collapsed, scratch-dir paths (gatf-gen-*, gatf-code) cut to the file name, capped. */
    public static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\x00-\\x1f\\x7f\\u0085\\u2028\\u2029]", " ")
                .replaceAll("[^\\s()'\"]*gatf-gen-[^/\\\\\\s]*[/\\\\]", "")
                .replaceAll("[^\\s()'\"]*gatf-code[/\\\\]com[/\\\\]gatf[/\\\\]selenium[/\\\\]", "")
                .replaceAll("\\s+", " ").trim();
        return t.length() > MAX_MESSAGE ? t.substring(0, MAX_MESSAGE) + "…" : t;
    }
}
