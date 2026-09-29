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
package com.gatf.docs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Q11 — one documented test case (a row of the Test Cases sheet), derived from an API case or a seleasy script. */
public final class TestDoc {

    public String id;              // TC-0001 … assigned in document order
    public String type;            // API | UI
    public String module;          // folder of the source file ("" at the root)
    public String source;          // test file (relative), as the run reports it
    public String name;            // API case name, or UI subtest name (null = the whole script)
    public String title;
    public String description = "";
    public String priority = "";
    public final Set<String> tags = new LinkedHashSet<String>();
    public final Set<String> requirements = new LinkedHashSet<String>();
    public final List<String> preconditions = new ArrayList<String>();
    public final List<String> steps = new ArrayList<String>();
    public final List<String> expected = new ArrayList<String>();
    /** passed | failed | flaky | skipped | not run */
    public String status = "not run";
    public String error = "";

    /** Default requirement tags: Jira-style keys ({@code @REQ-12}, {@code @PROJ-401}) or {@code @req:<anything>}. */
    public static final Pattern DEFAULT_REQ = Pattern.compile("@(?:req:(.+)|([A-Z][A-Z0-9_]*-\\d+))");
    private static final Pattern PRIORITY = Pattern.compile("@(?i:p([0-4])|priority[:=]?(\\w+))");

    /** Adds the @tags found in {@code text}; requirement and priority tags are split out. */
    public void addTags(String text, Pattern reqPattern) {
        for (String t : tagsIn(text)) {
            java.util.regex.Matcher r = reqPattern.matcher(t);
            if (r.matches()) {
                String id = null;
                for (int g = 1; g <= r.groupCount() && id == null; g++) id = r.group(g);
                requirements.add(id == null ? t.substring(1) : id);
                continue;
            }
            java.util.regex.Matcher p = PRIORITY.matcher(t);
            if (p.matches()) {
                priority = p.group(1) != null ? "P" + p.group(1) : p.group(2).toUpperCase(Locale.ROOT);
                continue;
            }
            tags.add(t);
        }
    }

    /** @tags followed by whitespace or the end (so "@call," in prose is not a tag). */
    private static final Pattern TAG = Pattern.compile("(?<!\\S)@[A-Za-z0-9_][A-Za-z0-9_.:-]*(?=\\s|$)");

    static List<String> tagsIn(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        java.util.regex.Matcher m = TAG.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    static String numbered(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append('\n');
            b.append(i + 1).append(". ").append(items.get(i));
        }
        return b.toString();
    }

    /** The Test Cases sheet header. */
    public static final String[] COLUMNS = { "Test Case ID", "Module", "Source", "Title", "Type", "Priority", "Tags",
            "Requirements", "Description", "Preconditions", "Test Steps", "Expected Result", "Last Status", "Last Error" };

    public String[] row() {
        return new String[] { id, module, source, title, type, priority, String.join(" ", tags), String.join(", ", requirements),
                description, numbered(preconditions), numbered(steps), numbered(expected), status, error };
    }
}
