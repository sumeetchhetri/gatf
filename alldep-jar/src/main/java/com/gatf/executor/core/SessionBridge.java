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

import java.net.URI;
import java.util.List;

/**
 * Q8 — lets HTTP calls made from a UI script (the {@code api} and {@code curl} plugins) share the browser's session,
 * like Playwright's {@code page.request}. The UI runtime installs a {@link Bridge} on the script thread for the
 * duration of a plugin call ({@code SeleniumTest.pluginize}); the API executor and the curl plugin consult
 * {@link #current()}, so outside a UI script nothing changes.
 *
 * <p>Modes ({@code browserApiSession} in gatf-config): {@code sync} (default) sends the browser's cookies and stores
 * response Set-Cookies back into the browser; {@code send} only sends; {@code off} disables the bridge.
 */
public final class SessionBridge {

    public static final String[] MODES = { "sync", "send", "off" };

    /** The browser side of the bridge. */
    public interface Bridge {
        /** Cookie header value the browser would send to {@code uri} (null or empty for none). */
        String cookieHeader(URI uri);

        /** Store response Set-Cookie headers from {@code uri} in the browser (no-op in {@code send} mode). */
        void onSetCookies(URI uri, List<String> setCookies);

        /** M5: an Authorization value taken from the page's web storage ({@code browserApiStorageToken}), or null. */
        default String authorization(URI uri) { return null; }
    }

    private static final ThreadLocal<Bridge> CURRENT = new ThreadLocal<Bridge>();

    private SessionBridge() {}

    public static Bridge current() {
        return CURRENT.get();
    }

    /** Installs {@code b} on this thread and returns the previous bridge, for {@link #restore}. */
    public static Bridge enter(Bridge b) {
        Bridge prev = CURRENT.get();
        CURRENT.set(b);
        return prev;
    }

    public static void restore(Bridge prev) {
        if (prev == null) CURRENT.remove();
        else CURRENT.set(prev);
    }

    /** Validates/normalises a mode; blank → null (= default {@code sync}). */
    public static String normalizeMode(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        String t = v.trim().toLowerCase(java.util.Locale.ROOT);
        for (String m : MODES) {
            if (m.equals(t)) return t;
        }
        throw new IllegalArgumentException("Invalid browserApiSession '" + v + "' - expected one of: " + String.join(", ", MODES));
    }

    /** The effective mode for a configured value (lenient: an unknown value falls back to the default). */
    public static String effectiveMode(String configured) {
        try {
            String m = normalizeMode(configured);
            return m == null ? "sync" : m;
        } catch (IllegalArgumentException e) {
            return "sync";
        }
    }
}
