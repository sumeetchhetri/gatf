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
package com.gatf.selenium.trace;

import java.util.Locale;

/**
 * Δ163o: credentials never reach a trace. Header values that carry credentials (Authorization, cookies, API keys,
 * tokens, sessions, passwords) are written to the HAR as {@link #REDACTED}; the header name is kept so the request
 * shape stays readable. Pure.
 */
public final class TraceRedact {

    public static final String REDACTED = "[redacted]";

    private static final String[] EXACT = { "authorization", "proxy-authorization", "cookie", "set-cookie", "set-cookie2" };
    private static final String[] PARTS = { "token", "secret", "password", "passwd", "api-key", "apikey", "api_key", "session", "auth", "credential", "signature" };

    private TraceRedact() {
    }

    public static boolean sensitiveHeader(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String e : EXACT) if (n.equals(e)) return true;
        for (String p : PARTS) if (n.contains(p)) return true;
        return false;
    }

    public static String headerValue(String name, String value) {
        return value != null && sensitiveHeader(name) ? REDACTED : value;
    }
}
