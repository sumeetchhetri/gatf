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
import java.util.List;
import java.util.Map;

/**
 * H3 (Δ153): {@link ContextOps} for CDP and WebDriver BiDi as raw commands over a {@link ContextOps.Sender}. Pure:
 * no Selenium types, so the call shapes and result parsing are unit-tested against a fake sender.
 * <pre>
 *            CDP                                          BiDi
 * create     Target.createBrowserContext → browserContextId    browser.createUserContext → userContext
 * openPage   Target.createTarget{url, browserContextId} → targetId   browsingContext.create{type:tab, userContext} → context
 * remove     Target.disposeBrowserContext{browserContextId}    browser.removeUserContext{userContext}
 * list       Target.getBrowserContexts → browserContextIds     browser.getUserContexts → userContexts[].userContext (minus "default")
 * </pre>
 */
public final class ContextCalls {

    private ContextCalls() {}

    public static ContextOps cdp(ContextOps.Sender s) { return new Ops("cdp", s, null); }

    /** CDP over a browser-level connection: Target.*Context is refused on a page target ("Not allowed"). */
    public static ContextOps cdp(ContextOps.Sender s, Runnable close) { return new Ops("cdp", s, close); }

    public static ContextOps bidi(ContextOps.Sender s) { return new Ops("bidi", s, null); }

    /**
     * A raw BiDi command made to act on one per-test user context: the storage commands get
     * {@code partition: {type: storageKey, userContext}} and {@code script.addPreloadScript} gets
     * {@code userContexts: [userContext]} (without them BiDi uses the default user context / every context).
     * Other commands, and a null {@code userContext}, pass through unchanged. Returns a copy.
     */
    public static Map<String, Object> inUserContext(String method, Map<String, Object> params, String userContext) {
        Map<String, Object> p = params == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(params);
        if (userContext == null) return p;
        if (method.startsWith("storage.")) {
            Map<String, Object> part = new LinkedHashMap<String, Object>();
            part.put("type", "storageKey");
            part.put("userContext", userContext);
            p.put("partition", part);
        } else if ("script.addPreloadScript".equals(method)) {
            p.remove("contexts");   // exclusive with userContexts
            List<String> ucs = new ArrayList<String>();
            ucs.add(userContext);
            p.put("userContexts", ucs);
        }
        return p;
    }

    private static final class Ops implements ContextOps {
        private final String protocol;
        private final ContextOps.Sender s;
        private final Runnable closer;

        Ops(String protocol, ContextOps.Sender s, Runnable closer) {
            this.protocol = protocol;
            this.s = s;
            this.closer = closer;
        }

        @Override
        public void close() {
            if (closer != null) closer.run();
        }

        private boolean cdp() { return "cdp".equals(protocol); }

        @Override public String protocol() { return protocol; }

        @Override
        public String create() {
            Map<String, Object> r = s.send(cdp() ? "Target.createBrowserContext" : "browser.createUserContext", new LinkedHashMap<String, Object>());
            return id(r, cdp() ? "browserContextId" : "userContext", "create");
        }

        @Override
        public String openPage(String context) {
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            if (cdp()) {
                p.put("url", "about:blank");
                p.put("browserContextId", context);
                return id(s.send("Target.createTarget", p), "targetId", "openPage");
            }
            p.put("type", "tab");
            p.put("userContext", context);
            return id(s.send("browsingContext.create", p), "context", "openPage");
        }

        @Override
        public void remove(String context) {
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put(cdp() ? "browserContextId" : "userContext", context);
            s.send(cdp() ? "Target.disposeBrowserContext" : "browser.removeUserContext", p);
        }

        @Override
        public List<String> list() {
            Map<String, Object> r = s.send(cdp() ? "Target.getBrowserContexts" : "browser.getUserContexts", new LinkedHashMap<String, Object>());
            List<String> out = new ArrayList<String>();
            Object v = r == null ? null : r.get(cdp() ? "browserContextIds" : "userContexts");
            if (!(v instanceof List)) return out;
            for (Object o : (List<?>) v) {
                Object id = o instanceof Map ? ((Map<?, ?>) o).get("userContext") : o;
                if (id != null && !"default".equals(String.valueOf(id))) out.add(String.valueOf(id));
            }
            return out;
        }

        private String id(Map<String, Object> r, String key, String op) {
            Object v = r == null ? null : r.get(key);
            if (v == null || String.valueOf(v).isEmpty()) throw new IllegalStateException(protocol + " " + op + ": no " + key + " in the reply");
            return String.valueOf(v);
        }
    }
}
