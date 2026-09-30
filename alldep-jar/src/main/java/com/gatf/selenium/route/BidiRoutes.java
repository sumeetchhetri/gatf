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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M4 — a route decision as the raw WebDriver BiDi command for a request blocked by {@code network.addIntercept}
 * ({@code network.provideResponse} / {@code failRequest} / {@code continueRequest}). Pure; {@code BiDiTransport}
 * sends it through BidiRaw. BiDi has no abort reason: every abort is a plain network error.
 */
public final class BidiRoutes {

    private BidiRoutes() {}

    /** {@code network.addIntercept} params: every request, before it is sent. */
    public static Map<String, Object> interceptParams() {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        List<String> phases = new ArrayList<String>();
        phases.add("beforeRequestSent");
        p.put("phases", phases);
        return p;
    }

    /** {@code [method, params]} for the blocked request; {@code d == null} continues it unchanged. */
    public static Object[] command(Route d, String requestId, Map<String, String> requestHeaders) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("request", requestId);
        if (d == null) return new Object[] { "network.continueRequest", p };
        switch (d.action) {
            case FULFILL: {
                p.put("statusCode", d.status);
                List<Map<String, Object>> hs = headers(d.headers);
                boolean hasLength = false;
                for (Map<String, Object> h : hs) hasLength |= "content-length".equalsIgnoreCase(String.valueOf(h.get("name")));
                if (!hasLength) hs.add(header("Content-Length", String.valueOf(d.body.length)));
                p.put("headers", hs);
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("type", "base64");
                body.put("value", Base64.getEncoder().encodeToString(d.body));
                p.put("body", body);
                return new Object[] { "network.provideResponse", p };
            }
            case ABORT:
                return new Object[] { "network.failRequest", p };
            default:
                if (!d.headers.isEmpty()) p.put("headers", headers(CdpRoutes.merge(requestHeaders, d.headers)));
                return new Object[] { "network.continueRequest", p };
        }
    }

    private static List<Map<String, Object>> headers(Map<String, String> hs) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, String> e : hs.entrySet()) out.add(header(e.getKey(), e.getValue()));
        return out;
    }

    /** BiDi network.Header: {name, value: {type: "string", value}}. */
    private static Map<String, Object> header(String name, String value) {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("type", "string");
        v.put("value", value);
        Map<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("name", name);
        h.put("value", v);
        return h;
    }
}
