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
 * M4 — a route decision as the raw CDP Fetch command that carries it out ({@code Fetch.fulfillRequest} /
 * {@code failRequest} / {@code continueRequest}). Raw (method + JSON params) rather than the devtools.vNNN
 * classes, so interception does not pin a CDP version. Pure; {@code CdpTransport} sends the result.
 */
public final class CdpRoutes {

    private CdpRoutes() {}

    /** CDP Network.ErrorReason names, matched case-insensitively from the abort reason. */
    static final List<String> ERROR_REASONS = List.of("Failed", "Aborted", "TimedOut", "AccessDenied", "ConnectionClosed",
            "ConnectionReset", "ConnectionRefused", "ConnectionAborted", "ConnectionFailed", "NameNotResolved",
            "InternetDisconnected", "AddressUnreachable", "BlockedByClient", "BlockedByResponse");

    /** {@code [method, params]} for the paused request; {@code d == null} continues it unchanged. */
    public static Object[] command(Route d, String requestId, Map<String, String> requestHeaders) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("requestId", requestId);
        if (d == null) return new Object[] { "Fetch.continueRequest", p };
        switch (d.action) {
            case FULFILL: {
                p.put("responseCode", d.status);
                List<Map<String, Object>> hs = entries(d.headers);
                for (String[] x : d.extraHeaders) hs.add(entry(x[0], x[1]));   // CDP takes repeated names
                boolean hasLength = false;
                for (Map<String, Object> h : hs) hasLength |= "content-length".equalsIgnoreCase(String.valueOf(h.get("name")));
                if (!hasLength) hs.add(entry("Content-Length", String.valueOf(d.body.length)));
                p.put("responseHeaders", hs);
                p.put("body", Base64.getEncoder().encodeToString(d.body));
                return new Object[] { "Fetch.fulfillRequest", p };
            }
            case ABORT:
                p.put("errorReason", errorReason(d.reason));
                return new Object[] { "Fetch.failRequest", p };
            default: {
                if (!d.headers.isEmpty()) {
                    List<Map<String, Object>> hs = entries(merge(requestHeaders, d.headers));
                    for (String[] x : d.extraHeaders) hs.add(entry(x[0], x[1]));
                    p.put("headers", hs);
                }
                return new Object[] { "Fetch.continueRequest", p };
            }
        }
    }

    /** The request's headers with the route's added / overridden (header names compare case-insensitively). */
    static Map<String, String> merge(Map<String, String> original, Map<String, String> overrides) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (original != null) {
            for (Map.Entry<String, String> e : original.entrySet()) {
                boolean replaced = false;
                for (String k : overrides.keySet()) replaced |= k.equalsIgnoreCase(e.getKey());
                if (!replaced) out.put(e.getKey(), e.getValue());
            }
        }
        out.putAll(overrides);
        return out;
    }

    static String errorReason(String reason) {
        String r = reason == null ? "" : reason.replace("_", "").replace("-", "");
        for (String e : ERROR_REASONS) if (e.equalsIgnoreCase(r)) return e;
        return "Failed";
    }

    private static List<Map<String, Object>> entries(Map<String, String> headers) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, String> e : headers.entrySet()) out.add(entry(e.getKey(), e.getValue()));
        return out;
    }

    private static Map<String, Object> entry(String name, String value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }
}
