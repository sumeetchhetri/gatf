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
package com.gatf.selenium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M9: {@code screenshot compare <name> [threshold] [maxdiff N|N%] [mask <locator>]...}: visual regression against a
 * baseline in {@code <testCaseDir>/__screenshots__/<browser>/<name>.png} (see
 * {@link com.gatf.selenium.visual.VisualSnapshots}). {@code threshold} is the per-pixel colour tolerance (0..1,
 * default 0.2); {@code maxdiff} the differing pixels allowed (default none); masked elements are ignored.
 */
public class ScreenshotCompareCommand extends Command {

    final String name;
    final double threshold;
    final String maxDiff;
    final List<FindCommand> masks = new ArrayList<FindCommand>();

    ScreenshotCompareCommand(String val, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        List<String> t = tokens(val);
        if (t.isEmpty()) throwParseError(cmdDetails, new RuntimeException("screenshot compare <name> [threshold] [maxdiff N|N%] [mask <locator>]..."));
        name = unSantizedUnQuoted(t.get(0), state);
        double th = 0.2;
        String md = null;
        for (int i = 1; i < t.size(); i++) {
            String k = t.get(i);
            String kl = k.toLowerCase(Locale.ROOT);
            if (kl.equals("mask") || kl.equals("maxdiff")) {
                if (i + 1 >= t.size()) throwParseError(cmdDetails, new RuntimeException("screenshot compare: " + kl + " needs a value"));
                String v = t.get(++i);
                if (kl.equals("mask")) {
                    masks.add(new FindCommand(v, fileLineDetails, state));
                } else {
                    md = unSantizedUnQuoted(v, state);
                    try {
                        new com.gatf.selenium.visual.ImageDiff.Options().maxDiff(md);
                    } catch (IllegalArgumentException e) {
                        throwParseError(cmdDetails, e);
                    }
                }
            } else if (i == 1 && k.matches("\\d*\\.?\\d+")) {
                th = Double.parseDouble(k);
                if (th > 1) throwParseError(cmdDetails, new RuntimeException("screenshot compare: threshold must be between 0 and 1"));
            } else {
                throwParseError(cmdDetails, new RuntimeException("screenshot compare: unexpected '" + k + "' (expected threshold, maxdiff or mask)"));
            }
        }
        threshold = th;
        maxDiff = md;
    }

    /** Whitespace-separated tokens; single or double quotes group (quotes kept, for the locator parser). */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) {
                b.append(c);
                if (c == q) q = 0;
            } else if (c == '"' || c == '\'') {
                q = c;
                b.append(c);
            } else if (Character.isWhitespace(c)) {
                if (b.length() > 0) { out.add(b.toString()); b.setLength(0); }
            } else {
                b.append(c);
            }
        }
        if (b.length() > 0) out.add(b.toString());
        return out;
    }

    String toCmd() {
        StringBuilder b = new StringBuilder("screenshot compare \"").append(name).append("\"");
        if (threshold != 0.2) b.append(' ').append(threshold);
        if (maxDiff != null) b.append(" maxdiff ").append(maxDiff);
        for (FindCommand m : masks) b.append(" mask ").append(m.toCmd());
        return b.toString();
    }

    String javacode() {
        StringBuilder b = new StringBuilder("{\nList<WebElement> ___vmask___ = new java.util.ArrayList<WebElement>();\n");
        for (FindCommand m : masks) {
            // a mask that is not on the page masks nothing (short if-timeout, no failure)
            b.append(m.javacodeonlyNoAssert(null, true, m.ifTimeoutNum, false));   // Δ145d: the mask's own if-timeout (where written)
            b.append("if(___ce___!=null) ___vmask___.addAll(___ce___);\n");
        }
        b.append("__screenshotCompare__(get___d___(), evaluate(\"").append(esc(name)).append("\"), ").append(threshold).append(", ")
                .append(maxDiff == null ? "null" : "\"" + esc(maxDiff) + "\"").append(", ___vmask___);\n}\n");
        return b.toString();
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Compare the page with its baseline screenshot (visual regression)",
            "\tscreenshot compare {name} {optional threshold 0..1} {optional maxdiff N|N%} {optional mask {locator}}...",
            "Examples :-",
            "\tscreenshot compare home",
            "\tscreenshot compare \"checkout step 2\" 0.1 maxdiff 0.5%",
            "\tscreenshot compare dashboard mask id@clock mask css@'.ad-banner'",
        };
    }
}
