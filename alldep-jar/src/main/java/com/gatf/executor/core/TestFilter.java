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
package com.gatf.executor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Q5: Playwright-style test selection for CLI runs ({@code gatf test --grep / --grep-invert / --tag}).
 * <ul>
 *  <li>A test is <b>included</b> when no include pattern is given, or when ANY {@code --grep} regex or {@code --tag}
 *      matches its title (find semantics, case-sensitive like Playwright).</li>
 *  <li>It is then <b>excluded</b> when ANY {@code --grep-invert} regex matches.</li>
 *  <li>Tags are {@code @word} tokens. API tests: in the test's name or description. UI scripts: in the {@code //}
 *      comment lines at the top of the {@code .sel} file (e.g. {@code // @smoke @checkout}).</li>
 *  <li>Selection is per API test case and per UI script (a script's subtests run together).</li>
 *  <li>API dependencies ({@code relatedTestName}) of a selected test are pulled in automatically, so filtering
 *      can't silently break a chain.</li>
 * </ul>
 * Only active when the CLI activates it, so the UI server and Maven mojo never filter.
 */
public final class TestFilter {

    private static volatile TestFilter active;

    /** {@code @tag} tokens: an '@' at the start or after whitespace (so e-mail addresses don't count). */
    private static final Pattern TAG = Pattern.compile("(?<!\\S)@[A-Za-z0-9_][A-Za-z0-9_.:-]*");

    private final List<Pattern> include = new ArrayList<Pattern>();
    private final List<Pattern> exclude = new ArrayList<Pattern>();
    private final String description;

    /**
     * @param grep        include regexes (OR)
     * @param grepInvert  exclude regexes (OR)
     * @param tags        include tags, with or without the leading '@' (OR with grep)
     * @throws IllegalArgumentException for an invalid regex (reported to the user before the run)
     */
    public TestFilter(List<String> grep, List<String> grepInvert, List<String> tags) {
        List<String> d = new ArrayList<String>();
        for (String g : nz(grep)) {
            include.add(compile(g, "--grep"));
            d.add("--grep " + g);
        }
        for (String t : nz(tags)) {
            String tag = t.startsWith("@") ? t : "@" + t;
            include.add(Pattern.compile("(?<!\\S)" + Pattern.quote(tag) + "(?![A-Za-z0-9_.:-])"));
            d.add("--tag " + tag);
        }
        for (String g : nz(grepInvert)) {
            exclude.add(compile(g, "--grep-invert"));
            d.add("--grep-invert " + g);
        }
        this.description = String.join(" ", d);
    }

    private static Pattern compile(String re, String opt) {
        try {
            return Pattern.compile(re);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid " + opt + " regex '" + re + "': " + e.getDescription());
        }
    }

    public static void activate(TestFilter f) { active = f; }
    public static TestFilter active() { return active; }
    /** True when a CLI filter with at least one pattern is in force. */
    public static boolean isActive() { TestFilter f = active; return f != null && !f.isEmpty(); }

    public boolean isEmpty() { return include.isEmpty() && exclude.isEmpty(); }
    public String describe() { return description; }

    public boolean matches(String title) {
        String t = title == null ? "" : title;
        if (!include.isEmpty()) {
            boolean any = false;
            for (Pattern p : include) {
                if (p.matcher(t).find()) { any = true; break; }
            }
            if (!any) return false;
        }
        for (Pattern p : exclude) {
            if (p.matcher(t).find()) return false;
        }
        return true;
    }

    /**
     * Filters {@code items} (order preserved), then adds back every transitive dependency of a selected item.
     * {@code dependsOn} returns the name of the item it depends on, or null; items are matched by {@code name}.
     */
    public <T> List<T> select(List<T> items, Function<T, String> title, Function<T, String> name, Function<T, String> dependsOn) {
        Set<T> keep = Collections.newSetFromMap(new java.util.IdentityHashMap<T, Boolean>());
        for (T it : items) {
            if (matches(title.apply(it))) keep.add(it);
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (T it : new ArrayList<T>(keep)) {
                String dep = dependsOn == null ? null : dependsOn.apply(it);
                if (dep == null || dep.isEmpty()) continue;
                for (T cand : items) {
                    if (dep.equals(name.apply(cand)) && keep.add(cand)) grew = true;
                }
            }
        }
        List<T> out = new ArrayList<T>();
        for (T it : items) {
            if (keep.contains(it)) out.add(it);
        }
        return out;
    }

    /** The {@code @tag} tokens in {@code text}, in order, without duplicates. */
    public static List<String> tagsIn(String text) {
        Set<String> out = new LinkedHashSet<String>();
        if (text != null) {
            Matcher m = TAG.matcher(text);
            while (m.find()) out.add(m.group());
        }
        return new ArrayList<String>(out);
    }

    /** Title used to match an API test case: {@code <file> › <name>} plus any tags in its description. */
    public static String apiTitle(String file, String name, String description) {
        StringBuilder b = new StringBuilder(file == null ? "" : file).append(" › ").append(name == null ? "" : name);
        for (String t : tagsIn(description)) {
            if (!b.toString().contains(t)) b.append(' ').append(t);
        }
        return b.toString();
    }

    /** Tags declared in a .sel file's leading {@code //} comment lines (blank lines allowed; stops at the first statement). */
    public static List<String> selHeaderTags(List<String> lines) {
        Set<String> out = new LinkedHashSet<String>();
        for (String raw : nz(lines)) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (!l.startsWith("//")) break;
            out.addAll(tagsIn(l.substring(2)));
        }
        return new ArrayList<String>(out);
    }

    /** Title used to match a UI script: its path (as configured) plus its header tags. */
    public static String selTitle(String path, List<String> headerTags) {
        StringBuilder b = new StringBuilder(path == null ? "" : path.replace('\\', '/'));
        for (String t : nz(headerTags)) b.append(' ').append(t);
        return b.toString();
    }

    private static List<String> nz(List<String> l) { return l == null ? Collections.<String>emptyList() : l; }

    @Override
    public String toString() { return description.isEmpty() ? "(no filter)" : description; }
}
