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

import com.gatf.selenium.checks.A11yReport;

/**
 * M10: {@code a11y check [include <css@…|id@…>]... [exclude <css@…|id@…>]... [impact>=minor|moderate|serious|critical]
 * [report]}: runs the bundled axe-core in the page. Violations at or above the impact level (default: all) fail the
 * step, listed with their selectors; {@code report} only records them.
 */
public class A11yCommand extends Command {

    final List<String> include = new ArrayList<String>(), exclude = new ArrayList<String>();
    final String impact;
    final boolean reportOnly;

    A11yCommand(String val, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        List<String> t = ScreenshotCompareCommand.tokens(val);
        if (t.isEmpty() || !t.get(0).equalsIgnoreCase("check")) {
            throwParseError(cmdDetails, new RuntimeException("a11y check [include css@…]... [exclude css@…]... [impact>=serious] [report]"));
        }
        String imp = null;
        boolean rep = false;
        for (int i = 1; i < t.size(); i++) {
            String k = t.get(i), kl = k.toLowerCase(Locale.ROOT);
            try {
                if (kl.equals("include") || kl.equals("exclude")) {
                    if (i + 1 >= t.size()) throw new IllegalArgumentException("a11y check: " + kl + " needs a selector");
                    (kl.equals("include") ? include : exclude).add(A11yReport.cssOf(t.get(++i)));
                } else if (kl.startsWith("impact>=")) {
                    imp = A11yReport.normalizeImpact(k.substring(8));
                } else if (kl.equals("report")) {
                    rep = true;
                } else {
                    throw new IllegalArgumentException("a11y check: unexpected '" + k + "' (expected include, exclude, impact>=… or report)");
                }
            } catch (IllegalArgumentException e) {
                throwParseError(cmdDetails, e);
            }
        }
        impact = imp == null ? "minor" : imp;
        reportOnly = rep;
    }

    String toCmd() {
        StringBuilder b = new StringBuilder("a11y check");
        for (String s : include) b.append(" include css@'").append(s).append("'");
        for (String s : exclude) b.append(" exclude css@'").append(s).append("'");
        if (!"minor".equals(impact)) b.append(" impact>=").append(impact);
        if (reportOnly) b.append(" report");
        return b.toString();
    }

    private static String list(List<String> l) {
        StringBuilder b = new StringBuilder("java.util.Arrays.asList(new String[] {");
        for (int i = 0; i < l.size(); i++) b.append(i == 0 ? "" : ", ").append('"').append(esc(l.get(i))).append('"');
        return b.append("})").toString();
    }

    String javacode() {
        return "__a11yCheck__(get___d___(), " + list(include) + ", " + list(exclude) + ", \"" + impact + "\", " + reportOnly + ");\n";
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Check the page for accessibility violations (axe-core)",
            "\ta11y check {optional include {css@…|id@…}}... {optional exclude {css@…|id@…}}... {optional impact>=minor|moderate|serious|critical} {optional report}",
            "Examples :-",
            "\ta11y check",
            "\ta11y check impact>=serious",
            "\ta11y check include id@main exclude css@'.ad-banner' impact>=critical",
            "\ta11y check report",
        };
    }
}
