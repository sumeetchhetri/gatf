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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** H3 (Δ153), R1: the raw CDP / BiDi commands behind a per-test context, and ContextMatrix's protocol choice. */
class ContextCallsTest {

    static final class Rec implements ContextOps.Sender {
        final List<String> methods = new ArrayList<String>();
        final List<Map<String, Object>> params = new ArrayList<Map<String, Object>>();
        final Map<String, Map<String, Object>> replies = new LinkedHashMap<String, Map<String, Object>>();

        public Map<String, Object> send(String m, Map<String, Object> p) {
            methods.add(m);
            params.add(p);
            return replies.getOrDefault(m, new LinkedHashMap<String, Object>());
        }

        Rec reply(String m, Object... kv) {
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
            replies.put(m, r);
            return this;
        }
    }

    @Test
    void cdpCommands() {
        Rec r = new Rec().reply("Target.createBrowserContext", "browserContextId", "BC1")
                .reply("Target.createTarget", "targetId", "T9")
                .reply("Target.getBrowserContexts", "browserContextIds", Arrays.asList("BC1", "BC2"));
        ContextOps o = ContextCalls.cdp(r);
        assertEquals("cdp", o.protocol());
        assertEquals("BC1", o.create());
        assertEquals("T9", o.openPage("BC1"));
        assertEquals("about:blank", r.params.get(1).get("url"));
        assertEquals("BC1", r.params.get(1).get("browserContextId"));
        o.remove("BC1");
        assertEquals("Target.disposeBrowserContext", r.methods.get(2));
        assertEquals("BC1", r.params.get(2).get("browserContextId"));
        assertEquals(Arrays.asList("BC1", "BC2"), o.list());
    }

    @Test
    void bidiCommands() {
        Map<String, Object> def = new LinkedHashMap<String, Object>();
        def.put("userContext", "default");
        Map<String, Object> u1 = new LinkedHashMap<String, Object>();
        u1.put("userContext", "U1");
        Rec r = new Rec().reply("browser.createUserContext", "userContext", "U1")
                .reply("browsingContext.create", "context", "C7")
                .reply("browser.getUserContexts", "userContexts", Arrays.asList(def, u1));
        ContextOps o = ContextCalls.bidi(r);
        assertEquals("U1", o.create());
        assertEquals("C7", o.openPage("U1"));
        assertEquals("tab", r.params.get(1).get("type"));
        assertEquals("U1", r.params.get(1).get("userContext"));
        o.remove("U1");
        assertEquals("browser.removeUserContext", r.methods.get(2));
        assertEquals("U1", r.params.get(2).get("userContext"));
        assertEquals(Arrays.asList("U1"), o.list(), "the default user context is not a per-test context");
    }

    @Test
    void missingIdIsAnError() {
        assertThrows(IllegalStateException.class, () -> ContextCalls.cdp(new Rec()).create());
        assertThrows(IllegalStateException.class, () -> ContextCalls.bidi(new Rec()).openPage("U1"));
        assertTrue(ContextCalls.bidi(new Rec()).list().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bidiCommandsScopedToTheUserContext() {
        Map<String, Object> cookie = new LinkedHashMap<String, Object>();
        cookie.put("cookie", "c");
        Map<String, Object> p = ContextCalls.inUserContext("storage.setCookie", cookie, "U1");
        assertEquals("storageKey", ((Map<String, Object>) p.get("partition")).get("type"));
        assertEquals("U1", ((Map<String, Object>) p.get("partition")).get("userContext"));
        assertEquals(null, cookie.get("partition"), "the caller's map was changed");
        assertTrue(ContextCalls.inUserContext("storage.getCookies", null, "U1").containsKey("partition"));
        Map<String, Object> pre = new LinkedHashMap<String, Object>();
        pre.put("contexts", Arrays.asList("T1"));
        Map<String, Object> q = ContextCalls.inUserContext("script.addPreloadScript", pre, "U1");
        assertEquals(Arrays.asList("U1"), q.get("userContexts"));
        assertTrue(!q.containsKey("contexts"), "contexts and userContexts are exclusive");
        assertEquals(cookie, ContextCalls.inUserContext("storage.setCookie", cookie, null), "off: unchanged");
        assertTrue(!ContextCalls.inUserContext("network.addIntercept", new LinkedHashMap<String, Object>(), "U1").containsKey("partition"));
    }

    @Test
    void cdpOpsReleaseTheirConnection() {
        int[] closed = { 0 };
        ContextCalls.cdp(new Rec(), () -> closed[0]++).close();
        ContextCalls.cdp(new Rec()).close();
        assertEquals(1, closed[0]);
    }

    @Test
    void matrixPicksTheChannel() {
        assertEquals("bidi", ContextMatrix.decide(null, true, true).protocol, "BiDi first: CDP needs a browser-level connection");
        assertEquals("cdp", ContextMatrix.decide(null, true, true).alternate);
        assertEquals("cdp", ContextMatrix.decide(null, true, false).protocol);
        assertEquals(null, ContextMatrix.decide(null, false, true).alternate);
        assertEquals("bidi", ContextMatrix.decide("", false, true).protocol);
        assertEquals("bidi", ContextMatrix.decide("BiDi", true, true).protocol);
        assertEquals(null, ContextMatrix.decide("cdp", false, true).protocol);
        assertEquals(null, ContextMatrix.decide(null, false, false).protocol);
        assertTrue(ContextMatrix.decide(null, false, false).reason.contains("webSocketUrl"));
        assertEquals(7, ContextMatrix.ISOLATION.length);
        for (String[] row : ContextMatrix.ISOLATION) assertEquals(3, row.length);
    }
}
