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
package com.gatf.selenium.route;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M4 — a browser session's routes, in registration order; the FIRST rule that matches decides (a HAR source is one
 * rule). Thread-safe: the transport's event thread reads while the script adds or removes rules. Pure.
 */
public final class RouteTable {

    /** Something that can answer for a request: a {@link Route} or a HAR source. */
    public interface Rule {
        /** The decision for this request, or null when the rule does not apply. */
        Route decide(String method, String url);
        /** As above, with the request body when the transport has it (null otherwise); HAR replay uses it. */
        default Route decide(String method, String url, String body) { return decide(method, url); }
        /** the URL glob the rule was registered with ({@code unroute <glob>} removes by it) */
        String glob();
        String describe();
    }

    private final List<Rule> rules = new CopyOnWriteArrayList<Rule>();
    private final List<String> log = new CopyOnWriteArrayList<String>();

    public void add(final Route r) {
        rules.add(new Rule() {
            public Route decide(String method, String url) { return r.matches(method, url) ? r : null; }
            public String glob() { return r.glob; }
            public String describe() { return r.toString(); }
        });
    }

    public void add(Rule rule) {
        rules.add(rule);
    }

    /** Removes every rule registered for {@code glob} ({@code null} or "all" = everything); returns how many. */
    public int remove(String glob) {
        int before = rules.size();
        if (glob == null || glob.equals("all")) rules.clear();
        else rules.removeIf(r -> glob.equals(r.glob()));
        return before - rules.size();
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }

    /** The first matching decision, or null (let the request through untouched). */
    public Route match(String method, String url) {
        return match(method, url, null);
    }

    /** As {@link #match(String, String)}, with the request body when known (picks between HAR entries). */
    public Route match(String method, String url, String body) {
        for (Rule r : rules) {
            Route d = r.decide(method, url, body);
            if (d != null) {
                if (log.size() < 1000) log.add(method + " " + url + " -> " + d);
                return d;
            }
        }
        return null;
    }

    /** What matched so far ({@code METHOD url -> decision}), newest last; capped at 1000 entries. */
    public List<String> hits() {
        return new ArrayList<String>(log);
    }

    public List<String> describe() {
        List<String> out = new ArrayList<String>();
        for (Rule r : rules) out.add(r.describe());
        return out;
    }
}
