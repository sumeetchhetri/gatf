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
package com.gatf.selenium;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import com.gatf.executor.core.CookieRules;
import com.gatf.selenium.trace.BidiRaw;

/**
 * M5 — {@code state save|load <file>}: the browser side of {@link StorageState}.
 * <ul>
 *  <li>Cookies, every domain, HttpOnly included: CDP {@code Network.getAllCookies} / {@code setCookies} on Chromium,
 *      WebDriver BiDi {@code storage.getCookies} / {@code setCookie} elsewhere (Firefox), else WebDriver's cookie API
 *      (the open page's domain only).</li>
 *  <li>localStorage / sessionStorage: read from the open page's origin (merged into the file, so a login-once script
 *      can save several origins by saving on each); applied now when the page is on a stored origin, and on every
 *      later navigation through a new-document script (CDP {@code Page.addScriptToEvaluateOnNewDocument} / BiDi
 *      {@code script.addPreloadScript}), once per tab.</li>
 * </ul>
 */
final class BrowserState {

    private static final Logger LOG = LogManager.getLogger(BrowserState.class);

    private BrowserState() {}

    /** Saves (merging into an existing file); returns a one-line summary. */
    static String save(WebDriver d, File f) throws Exception {
        StorageState s = new StorageState();
        s.cookies.addAll(cookies(d));
        if (d instanceof JavascriptExecutor) {
            Object r = ((JavascriptExecutor) d).executeScript(StorageState.READ_JS);
            if (r instanceof Map) s.addOrigin((Map<?, ?>) r);
        }
        StorageState out = f.isFile() ? StorageState.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).merge(s) : s;
        if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
        Files.write(f.toPath(), out.toJson().getBytes(StandardCharsets.UTF_8));
        return "state save " + f.getPath() + ": " + s.cookies.size() + " cookie(s), " + s.origins.size() + " origin(s) with storage";
    }

    /** Loads a state file into the session; returns a one-line summary. */
    static String load(WebDriver d, File f) throws Exception {
        if (!f.isFile()) throw new IllegalArgumentException("state load: file not found: " + f.getPath());
        StorageState s = StorageState.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        List<CookieRules.C> live = s.liveCookies(System.currentTimeMillis() / 1000);
        int set = setCookies(d, live);
        if (!s.origins.isEmpty()) {
            String js = s.applyScript();
            preload(d, js);
            if (d instanceof JavascriptExecutor) {
                try {
                    ((JavascriptExecutor) d).executeScript(js);
                } catch (Exception e) {
                    LOG.debug("state load: storage not applied to the open page ({})", e.toString());
                }
            }
        }
        return "state load " + f.getPath() + ": " + set + "/" + live.size() + " cookie(s), " + s.origins.size() + " origin(s) with storage";
    }

    // ---------------------------------------------------------------- cookies

    @SuppressWarnings("unchecked")
    static List<CookieRules.C> cookies(WebDriver d) {
        if (d instanceof HasCdp) {
            try {
                Map<String, Object> r = ((HasCdp) d).executeCdpCommand("Network.getAllCookies", new HashMap<String, Object>());
                List<CookieRules.C> out = new ArrayList<CookieRules.C>();
                if (r != null && r.get("cookies") instanceof List) for (Object o : (List<Object>) r.get("cookies")) if (o instanceof Map) out.add(StorageState.fromCdp((Map<?, ?>) o));
                return out;
            } catch (Exception e) {
                LOG.debug("state: CDP Network.getAllCookies unavailable ({})", e.toString());
            }
        }
        if (BidiRaw.available(d)) {
            try {
                Map<String, Object> r = BidiRaw.send(d, "storage.getCookies", new HashMap<String, Object>());
                List<CookieRules.C> out = new ArrayList<CookieRules.C>();
                if (r != null && r.get("cookies") instanceof List) for (Object o : (List<Object>) r.get("cookies")) if (o instanceof Map) out.add(StorageState.fromBidi((Map<?, ?>) o));
                return out;
            } catch (Exception e) {
                LOG.debug("state: BiDi storage.getCookies unavailable ({})", e.toString());
            }
        }
        List<CookieRules.C> out = new ArrayList<CookieRules.C>();
        String host = host(d);
        for (Cookie k : d.manage().getCookies()) {
            String domain = k.getDomain() == null ? host : k.getDomain();
            if (domain == null) continue;
            CookieRules.C c = new CookieRules.C(k.getName(), k.getValue(), domain.startsWith(".") ? domain.substring(1) : domain, k.getPath());
            c.hostOnly = !domain.startsWith(".");
            c.secure = k.isSecure();
            c.httpOnly = k.isHttpOnly();
            if (k.getExpiry() != null) c.expiry = k.getExpiry().getTime() / 1000;
            c.sameSite = k.getSameSite();
            out.add(c);
        }
        return out;
    }

    static int setCookies(WebDriver d, List<CookieRules.C> cs) {
        if (cs.isEmpty()) return 0;
        if (d instanceof HasCdp) {
            try {
                List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>();
                for (CookieRules.C c : cs) ps.add(StorageState.toCdp(c));
                ((HasCdp) d).executeCdpCommand("Network.setCookies", Collections.<String, Object>singletonMap("cookies", ps));
                return cs.size();
            } catch (Exception e) {
                // E3: one cookie Chrome rejects fails the whole batch: set them one by one, skipping the rejected ones
                int n = cdpEach((HasCdp) d, cs);
                if (n >= 0) return n;
                LOG.debug("state: CDP Network.setCookies unavailable ({})", e.toString());
            }
        }
        if (BidiRaw.available(d)) {
            int n = 0;
            for (CookieRules.C c : cs) {
                try {
                    BidiRaw.send(d, "storage.setCookie", StorageState.toBidi(c));
                    n++;
                } catch (Exception e) {
                    LOG.warn("state load: cookie '{}' ({}) not set over BiDi: {}", c.name, c.domain, e.toString());
                }
            }
            return n;
        }
        int n = 0;
        String host = host(d);
        for (CookieRules.C c : cs) {
            if (host == null || !CookieRules.domainMatch(host, c.domain, c.hostOnly)) {
                LOG.warn("state load: cookie '{}' for {} skipped - this driver can only set cookies for the open page's domain", c.name, c.domain);
                continue;
            }
            Cookie.Builder b = new Cookie.Builder(c.name, c.value == null ? "" : c.value).path(c.path).isSecure(c.secure).isHttpOnly(c.httpOnly);
            if (!c.hostOnly) b.domain("." + c.domain);
            if (c.expiry != null) b.expiresOn(new Date(c.expiry * 1000));
            if (c.sameSite != null && !StorageState.sameSite(c.sameSite).isEmpty()) b.sameSite(StorageState.sameSite(c.sameSite));
            d.manage().addCookie(b.build());
            n++;
        }
        return n;
    }

    /** E3: Network.setCookie per cookie; the number set, or -1 when none could be sent (no CDP after all). */
    private static int cdpEach(HasCdp d, List<CookieRules.C> cs) {
        int n = 0, failed = 0;
        for (CookieRules.C c : cs) {
            try {
                Map<String, Object> r = d.executeCdpCommand("Network.setCookie", StorageState.toCdp(c));
                if (r != null && Boolean.FALSE.equals(r.get("success"))) {
                    LOG.warn("state load: cookie '{}' ({}) rejected by the browser, skipped", c.name, c.domain);
                } else {
                    n++;
                }
            } catch (Exception e) {
                failed++;
                LOG.warn("state load: cookie '{}' ({}) rejected by the browser, skipped: {}", c.name, c.domain, e.toString());
            }
        }
        if (failed == cs.size()) return -1;
        if (n < cs.size()) LOG.warn("state load: {} of {} cookie(s) skipped", cs.size() - n, cs.size());
        return n;
    }

    // ---------------------------------------------------------------- storage preload

    private static void preload(WebDriver d, String js) {
        try {
            if (d instanceof HasCdp) {
                ((HasCdp) d).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Collections.<String, Object>singletonMap("source", js));
                return;
            }
            if (BidiRaw.available(d)) {
                Map<String, Object> p = new HashMap<String, Object>();
                p.put("functionDeclaration", "() => {" + js + "}");
                BidiRaw.send(d, "script.addPreloadScript", p);
            }
        } catch (Exception e) {
            LOG.warn("state load: storage will only apply to the open page ({})", e.toString());
        }
    }

    // ---------------------------------------------------------------- Q8: bearer token in web storage

    /** The Authorization value from the open page's web storage per {@code spec}, or null. */
    static String storageToken(WebDriver d, String spec) {
        String js = StorageState.tokenScript(spec);
        if (js == null || !(d instanceof JavascriptExecutor)) return null;
        try {
            Object r = ((JavascriptExecutor) d).executeScript(js);
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) {
            return null;
        }
    }

    private static String host(WebDriver d) {
        try {
            String u = d.getCurrentUrl();
            return u == null ? null : java.net.URI.create(u).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
