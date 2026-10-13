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

import java.util.Locale;

/**
 * H3 (Δ153): which protocol creates per-test browser contexts for a session, and what each context isolates.
 * The protocol choice is static (channels the session has); the browser's answer is decided at run time — a
 * refused command makes {@link ContextScope} fall back to the shared default context and say so in the run log.
 *
 * <p>Protocol: {@code -Dgatf.contextProtocol=cdp|bidi} forces one; otherwise WebDriver BiDi when the session has a
 * socket (GATF asks for one when contexts are on), else CDP over a browser-level connection (Chromium), else
 * unsupported. {@link Decision#alternate} is tried when the first is refused at run time.
 *
 * <p>The isolation table ({@link #ISOLATION}) is the contract the leakage tests and docs/browser-modes.md follow.
 */
public final class ContextMatrix {

    private ContextMatrix() {}

    /** The decision for one session: {@code protocol} null means unsupported, {@code reason} says why. */
    public static final class Decision {
        public final String protocol;
        /** The other channel the session has, tried if {@link #protocol} is refused at run time; null if none. */
        public final String alternate;
        public final String reason;

        Decision(String protocol, String reason) {
            this(protocol, null, reason);
        }

        Decision(String protocol, String alternate, String reason) {
            this.protocol = protocol;
            this.alternate = alternate;
            this.reason = reason;
        }

        public boolean supported() { return protocol != null; }

        @Override public String toString() { return supported() ? protocol + " (" + reason + ")" : "unsupported: " + reason; }
    }

    /**
     * @param forced  {@code gatf.contextProtocol} (cdp / bidi), or null / blank
     * @param hasCdp  the driver can send raw CDP (HasCdp: local Chromium)
     * @param hasBidi the session has a live WebDriver BiDi socket (webSocketUrl)
     */
    public static Decision decide(String forced, boolean hasCdp, boolean hasBidi) {
        String f = forced == null ? "" : forced.trim().toLowerCase(Locale.ROOT);
        if ("cdp".equals(f)) return hasCdp ? new Decision("cdp", "forced") : new Decision(null, "gatf.contextProtocol=cdp but this session has no CDP (local Chromium only)");
        if ("bidi".equals(f)) return hasBidi ? new Decision("bidi", "forced") : new Decision(null, "gatf.contextProtocol=bidi but this session has no WebDriver BiDi socket (webSocketUrl)");
        if (hasBidi) return new Decision("bidi", hasCdp ? "cdp" : null, "browser.createUserContext");
        if (hasCdp) return new Decision("cdp", "Target.createBrowserContext");
        return new Decision(null, "needs CDP (local Chromium) or a WebDriver BiDi session (webSocketUrl); e.g. Safari / IE / a Grid session without webSocketUrl");
    }

    /** What a context isolates, per feature: {feature, CDP, BiDi}. Kept in step with docs/browser-modes.md. */
    public static final String[][] ISOLATION = {
        { "cookies / localStorage / sessionStorage / cache", "per context (browser)", "per context (browser)" },
        { "state save|load, storageState (Δ129)", "per context: loaded into every new context", "per context: loaded into every new context" },
        { "route (Δ127/128)", "per context: inside a context route goes over BiDi, scoped to the context's tab (needs webSocketUrl, set automatically); fails if the session has no BiDi", "per context: intercept scoped to the context's tab" },
        { "emulate (Δ137)", "per context: tab-level overrides; permission grants carry browserContextId", "per context: overrides scoped to the tab; permission grants carry userContext" },
        { "clock (Δ138)", "per context: page script + preload on the context's tab", "per context: page script + preload scoped to the tab" },
        { "trace network / console / video, netapix", "default context only (bound to the script's DevTools session)", "all contexts (session-wide subscriptions)" },
        { "tab / window / close others", "the context's tabs only", "the context's tabs only" },
    };
}
