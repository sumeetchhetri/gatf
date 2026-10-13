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
package com.gatf.selenium.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * H3 (Δ153), R2: a fake browser that answers both CDP and WebDriver BiDi context commands and keeps the state a
 * real browser keeps per context (cookies, web storage, cache, permission grants) or per tab (emulation overrides,
 * preload scripts), plus request intercepts with their {@code contexts} scope. One session, many tabs, like a driver.
 */
final class FakeBrowser implements ContextOps.Sender, ContextScope.Window {

    static final String DEFAULT = "default";

    static final class Ctx {
        final Map<String, String> cookies = new LinkedHashMap<String, String>();
        final Map<String, String> storage = new LinkedHashMap<String, String>();
        final Set<String> cache = new LinkedHashSet<String>();
        final Set<String> permissions = new LinkedHashSet<String>();
    }

    final Map<String, Ctx> contexts = new LinkedHashMap<String, Ctx>();
    final Map<String, String> tabs = new LinkedHashMap<String, String>();          // handle -> context
    final Map<String, Map<String, Object>> overrides = new LinkedHashMap<String, Map<String, Object>>();   // handle -> emulation
    final Map<String, List<String>> preloads = new LinkedHashMap<String, List<String>>();                 // handle -> preload ids
    final Map<String, List<String>> intercepts = new LinkedHashMap<String, List<String>>();               // id -> contexts (null = all)
    final List<String> calls = new ArrayList<String>();
    String current = "T0";
    int seq;
    /** Method names to fail (a browser that refuses them). */
    final Set<String> refuse = new LinkedHashSet<String>();
    /** Handles switchTo refuses (a driver that cannot see a new tab). */
    final Set<String> hidden = new LinkedHashSet<String>();
    /** Per-target preload counters, as Chromium numbers Page.addScriptToEvaluateOnNewDocument ids ("1", "2", …). */
    final Map<String, Integer> preloadSeq = new LinkedHashMap<String, Integer>();
    /** BiDi preload scripts: id -> user contexts (null = every context, present and future). */
    final Map<String, List<String>> bidiPreloads = new LinkedHashMap<String, List<String>>();

    /** The page channel (executeCdpCommand on the current tab): Chromium refuses browser-level Target.*Context there. */
    ContextOps.Sender pageCdp() {
        return (m, p) -> {
            if (m.startsWith("Target.") && m.contains("BrowserContext")) throw new IllegalStateException("Not allowed");
            return send(m, p);
        };
    }

    FakeBrowser() {
        contexts.put(DEFAULT, new Ctx());
        tabs.put("T0", DEFAULT);
    }

    // --- Window port ---
    public String current() {
        if (!tabs.containsKey(current)) throw new IllegalStateException("no such window");
        return current;
    }
    public void switchTo(String h) {
        if (!tabs.containsKey(h) || hidden.contains(h)) throw new IllegalStateException("no such window: " + h);
        current = h;
    }
    public Set<String> handles() { return new LinkedHashSet<String>(tabs.keySet()); }

    // --- what the page under test does ---
    Ctx here() { return contexts.get(tabs.get(current())); }
    void closeTab() { tabs.remove(current()); }
    boolean intercepted() {
        for (List<String> scope : intercepts.values()) if (scope == null || scope.contains(current())) return true;
        return false;
    }

    // --- protocol ---
    @SuppressWarnings("unchecked")
    public Map<String, Object> send(String m, Map<String, Object> p) {
        calls.add(m);
        if (refuse.contains(m)) throw new IllegalStateException("unknown command " + m);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        switch (m) {
            case "Target.createBrowserContext":
            case "browser.createUserContext": {
                String id = "BC" + (++seq);
                contexts.put(id, new Ctx());
                r.put(m.startsWith("Target") ? "browserContextId" : "userContext", id);
                return r;
            }
            case "Target.createTarget":
            case "browsingContext.create": {
                String ctx = (String) p.get(m.startsWith("Target") ? "browserContextId" : "userContext");
                if (!contexts.containsKey(ctx)) throw new IllegalStateException("no such context " + ctx);
                String h = "T" + (++seq);
                tabs.put(h, ctx);
                r.put(m.startsWith("Target") ? "targetId" : "context", h);
                return r;
            }
            case "Target.disposeBrowserContext":
            case "browser.removeUserContext": {
                String ctx = (String) p.get(m.startsWith("Target") ? "browserContextId" : "userContext");
                if (DEFAULT.equals(ctx) || contexts.remove(ctx) == null) throw new IllegalStateException("no such context " + ctx);
                tabs.values().removeIf(c -> c.equals(ctx));
                for (List<String> ucs : bidiPreloads.values()) if (ucs != null) ucs.remove(ctx);
                overrides.keySet().retainAll(tabs.keySet());
                preloads.keySet().retainAll(tabs.keySet());
                for (List<String> scope : intercepts.values()) if (scope != null) scope.retainAll(tabs.keySet());
                return r;
            }
            case "Target.getBrowserContexts": {
                List<String> ids = new ArrayList<String>(contexts.keySet());
                ids.remove(DEFAULT);
                r.put("browserContextIds", ids);
                return r;
            }
            case "browser.getUserContexts": {
                List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
                for (String id : contexts.keySet()) {
                    Map<String, Object> e = new LinkedHashMap<String, Object>();
                    e.put("userContext", id);
                    l.add(e);
                }
                r.put("userContexts", l);
                return r;
            }
            case "Browser.grantPermissions": {
                Object bc = p.get("browserContextId");
                contexts.get(bc == null ? DEFAULT : (String) bc).permissions.addAll((List<String>) p.get("permissions"));
                return r;
            }
            case "permissions.setPermission": {
                Object uc = p.get("userContext");
                contexts.get(uc == null ? DEFAULT : (String) uc).permissions.add((String) ((Map<String, Object>) p.get("descriptor")).get("name"));
                return r;
            }
            case "Emulation.setGeolocationOverride":
                overrides.computeIfAbsent(current(), k -> new LinkedHashMap<String, Object>()).put("geo", p);
                return r;
            case "emulation.setGeolocationOverride":
                for (String h : (List<String>) p.get("contexts")) overrides.computeIfAbsent(h, k -> new LinkedHashMap<String, Object>()).put("geo", p);
                return r;
            case "Page.addScriptToEvaluateOnNewDocument": {
                int n = preloadSeq.merge(current(), 1, Integer::sum);
                String id = String.valueOf(n);
                preloads.computeIfAbsent(current(), k -> new ArrayList<String>()).add(id);
                r.put("identifier", id);
                return r;
            }
            case "Page.removeScriptToEvaluateOnNewDocument":
                if (preloads.containsKey(current())) preloads.get(current()).remove(p.get("identifier"));
                return r;
            case "storage.setCookie": {
                Map<String, Object> part = (Map<String, Object>) p.get("partition");
                String uc = part == null ? DEFAULT : (String) part.get("userContext");
                Map<String, Object> c = (Map<String, Object>) p.get("cookie");
                contexts.get(uc).cookies.put((String) c.get("name"), String.valueOf(c.get("value")));
                return r;
            }
            case "storage.getCookies": {
                Map<String, Object> part = (Map<String, Object>) p.get("partition");
                String uc = part == null ? DEFAULT : (String) part.get("userContext");
                r.put("cookies", new ArrayList<String>(contexts.get(uc).cookies.keySet()));
                return r;
            }
            case "script.addPreloadScript": {
                String id = "P" + (++seq);
                Object ucs = p.get("userContexts");
                if (ucs != null && p.containsKey("contexts")) throw new IllegalStateException("invalid argument: contexts and userContexts");
                bidiPreloads.put(id, ucs == null ? null : new ArrayList<String>((List<String>) ucs));
                r.put("script", id);
                return r;
            }
            case "network.addIntercept": {
                String id = "I" + (++seq);
                Object scope = p.get("contexts");
                intercepts.put(id, scope == null ? null : new ArrayList<String>((List<String>) scope));
                r.put("intercept", id);
                return r;
            }
            case "network.removeIntercept":
                intercepts.remove(p.get("intercept"));
                return r;
            default:
                throw new IllegalStateException("unknown command " + m);
        }
    }
}
