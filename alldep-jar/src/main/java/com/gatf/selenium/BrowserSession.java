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

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import com.gatf.executor.core.CookieRules;
import com.gatf.executor.core.SessionBridge;

/**
 * Q8 — the browser side of {@link SessionBridge}: reads the cookies the browser would send to an API URL and stores
 * API Set-Cookies back into the browser.
 *
 * <p>Chromium (local, or any driver exposing CDP): {@code Network.getCookies(urls)} / {@code Network.setCookie}, so
 * cookies of any domain work, HttpOnly included. Other drivers (Firefox, remote without CDP): WebDriver's cookie API,
 * which only sees and sets cookies for the domain of the page currently open; cookies for other domains are skipped
 * with a log line. M5: Firefox and other BiDi sessions read / write cookies of any domain through {@code storage.*}; with
 * {@code browserApiStorageToken} set, a bearer token kept in the page's web storage is sent as Authorization.
 */
final class BrowserSession implements SessionBridge.Bridge {

    private static final Logger LOG = LogManager.getLogger(BrowserSession.class);

    private final WebDriver driver;
    private final boolean sync;
    private final String storageToken;   // M5: browserApiStorageToken, null = none
    private final String[] tokenHosts;   // E9: browserApiStorageTokenHosts, extra hosts the token may go to
    private static final java.util.Set<String> REFUSED_HOSTS = java.util.concurrent.ConcurrentHashMap.newKeySet();   // log once per host

    BrowserSession(WebDriver driver, String mode) {
        this(driver, mode, null);
    }

    BrowserSession(WebDriver driver, String mode, String storageToken) {
        this(driver, mode, storageToken, null);
    }

    BrowserSession(WebDriver driver, String mode, String storageToken, String[] tokenHosts) {
        this.driver = driver;
        this.sync = "sync".equals(mode);
        this.storageToken = storageToken == null || storageToken.trim().isEmpty() ? null : storageToken.trim();
        this.tokenHosts = tokenHosts;
    }

    /** Installs a bridge for {@code driver} unless there is no browser or the mode is off; returns the previous one. */
    static SessionBridge.Bridge enter(WebDriver driver, String configuredMode) {
        return enter(driver, configuredMode, null);
    }

    static SessionBridge.Bridge enter(WebDriver driver, String configuredMode, String storageToken) {
        return enter(driver, configuredMode, storageToken, null);
    }

    static SessionBridge.Bridge enter(WebDriver driver, String configuredMode, String storageToken, String[] tokenHosts) {
        String mode = SessionBridge.effectiveMode(configuredMode);
        if (driver == null || "off".equals(mode)) return SessionBridge.enter(null);
        return SessionBridge.enter(new BrowserSession(driver, mode, storageToken, tokenHosts));
    }

    @Override
    public String authorization(URI uri) {
        if (storageToken == null) return null;
        // E9: the page's token goes only to its own host (or browserApiStorageTokenHosts), never https -> http
        URI page = null;
        try {
            String u = driver.getCurrentUrl();
            if (u != null) page = URI.create(u);
        } catch (Exception e) {
            // no page open
        }
        if (!SessionBridge.tokenAllowed(uri, page, tokenHosts)) {
            String h = uri == null ? null : uri.getHost();
            if (h != null && REFUSED_HOSTS.add(h)) {
                LOG.info("browserApiStorageToken not sent to {}: not the open page's host (add it to browserApiStorageTokenHosts)", h);
            }
            return null;
        }
        return BrowserState.storageToken(driver, storageToken);
    }

    @Override
    public String cookieHeader(URI uri) {
        long now = System.currentTimeMillis() / 1000;
        try {
            List<CookieRules.C> cs = cdpCookies(uri);
            if (cs == null) cs = bidiCookies();
            if (cs == null) cs = webDriverCookies();
            String h = CookieRules.header(cs, uri, now);
            return h.isEmpty() ? null : h;
        } catch (Exception e) {
            LOG.warn("browser session: could not read cookies for {}: {}", uri, e.toString());
            return null;
        }
    }

    @Override
    public void onSetCookies(URI uri, List<String> setCookies) {
        if (!sync || setCookies == null || setCookies.isEmpty()) return;
        long now = System.currentTimeMillis() / 1000;
        for (CookieRules.C c : CookieRules.fromSetCookie(setCookies, uri, now)) {
            try {
                if (!cdpSet(c, uri) && !bidiSet(c) && !webDriverSet(c)) {
                    LOG.warn("browser session: cookie '{}' for {} not stored in the browser - it can only set cookies for "
                            + "the open page's domain; open a page on {} first", c.name, c.domain, c.domain);
                }
            } catch (Exception e) {
                LOG.warn("browser session: could not store cookie '{}': {}", c.name, e.toString());
            }
        }
    }

    // ---------------------------------------------------------------- CDP

    @SuppressWarnings("unchecked")
    private List<CookieRules.C> cdpCookies(URI uri) {
        if (!(driver instanceof HasCdp)) return null;
        try {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("urls", List.of(uri.toString()));
            Map<String, Object> r = ((HasCdp) driver).executeCdpCommand("Network.getCookies", args);
            List<CookieRules.C> out = new ArrayList<CookieRules.C>();
            Object list = r == null ? null : r.get("cookies");
            if (!(list instanceof List)) return out;
            for (Object o : (List<Object>) list) {
                Map<String, Object> m = (Map<String, Object>) o;
                String domain = String.valueOf(m.get("domain"));
                CookieRules.C c = new CookieRules.C(String.valueOf(m.get("name")), String.valueOf(m.get("value")),
                        domain.startsWith(".") ? domain.substring(1) : domain, String.valueOf(m.get("path")));
                c.hostOnly = !domain.startsWith(".");
                c.secure = Boolean.TRUE.equals(m.get("secure"));
                c.httpOnly = Boolean.TRUE.equals(m.get("httpOnly"));
                Object exp = m.get("expires");
                if (!Boolean.TRUE.equals(m.get("session")) && exp instanceof Number && ((Number) exp).doubleValue() > 0) {
                    c.expiry = ((Number) exp).longValue();
                }
                out.add(c);
            }
            return out;
        } catch (Exception e) {
            LOG.debug("browser session: CDP Network.getCookies unavailable ({}), using WebDriver cookies", e.toString());
            return null;
        }
    }

    private boolean cdpSet(CookieRules.C c, URI uri) {
        if (!(driver instanceof HasCdp)) return false;
        try {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("name", c.name);
            if (c.deleted) {
                if (c.hostOnly) args.put("url", uri.getScheme() + "://" + c.domain + c.path);
                else args.put("domain", "." + c.domain);
                args.put("path", c.path);
                ((HasCdp) driver).executeCdpCommand("Network.deleteCookies", args);
                return true;
            }
            args.put("value", c.value == null ? "" : c.value);
            if (c.hostOnly) args.put("url", uri.getScheme() + "://" + c.domain + c.path);   // host-only: no domain attribute
            else args.put("domain", "." + c.domain);
            args.put("path", c.path);
            args.put("secure", c.secure);
            args.put("httpOnly", c.httpOnly);
            if (c.sameSite != null) {
                String s = c.sameSite.substring(0, 1).toUpperCase() + c.sameSite.substring(1).toLowerCase();
                if (s.equals("Strict") || s.equals("Lax") || s.equals("None")) args.put("sameSite", s);
            }
            if (c.expiry != null) args.put("expires", c.expiry);
            Map<String, Object> r = ((HasCdp) driver).executeCdpCommand("Network.setCookie", args);
            return r == null || !Boolean.FALSE.equals(r.get("success"));
        } catch (Exception e) {
            LOG.debug("browser session: CDP Network.setCookie unavailable ({}), using WebDriver cookies", e.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- M5: WebDriver BiDi (Firefox), any domain

    /** Every cookie in the browser (CookieRules.header then picks the ones for the URL); null without a BiDi socket. */
    @SuppressWarnings("unchecked")
    private List<CookieRules.C> bidiCookies() {
        if (driver instanceof HasCdp || !com.gatf.selenium.trace.BidiRaw.available(driver)) return null;
        try {
            Map<String, Object> r = com.gatf.selenium.trace.BidiRaw.send(driver, "storage.getCookies", new HashMap<String, Object>());
            List<CookieRules.C> out = new ArrayList<CookieRules.C>();
            if (r != null && r.get("cookies") instanceof List) {
                for (Object o : (List<Object>) r.get("cookies")) if (o instanceof Map) out.add(StorageState.fromBidi((Map<?, ?>) o));
            }
            return out;
        } catch (Exception e) {
            LOG.debug("browser session: BiDi storage.getCookies unavailable ({}), using WebDriver cookies", e.toString());
            return null;
        }
    }

    private boolean bidiSet(CookieRules.C c) {
        if (driver instanceof HasCdp || !com.gatf.selenium.trace.BidiRaw.available(driver)) return false;
        try {
            if (c.deleted) {
                Map<String, Object> filter = new HashMap<String, Object>();
                filter.put("name", c.name);
                filter.put("domain", (c.hostOnly ? "" : ".") + c.domain);
                com.gatf.selenium.trace.BidiRaw.send(driver, "storage.deleteCookies", java.util.Collections.<String, Object>singletonMap("filter", filter));
                return true;
            }
            com.gatf.selenium.trace.BidiRaw.send(driver, "storage.setCookie", StorageState.toBidi(c));
            return true;
        } catch (Exception e) {
            LOG.debug("browser session: BiDi storage.setCookie unavailable ({}), using WebDriver cookies", e.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- WebDriver fallback (open page's domain only)

    private List<CookieRules.C> webDriverCookies() {
        List<CookieRules.C> out = new ArrayList<CookieRules.C>();
        for (Cookie k : driver.manage().getCookies()) {
            String domain = k.getDomain() == null ? currentHost() : k.getDomain();
            if (domain == null) continue;
            CookieRules.C c = new CookieRules.C(k.getName(), k.getValue(), domain.startsWith(".") ? domain.substring(1) : domain, k.getPath());
            c.hostOnly = !domain.startsWith(".");
            c.secure = k.isSecure();
            c.httpOnly = k.isHttpOnly();
            if (k.getExpiry() != null) c.expiry = k.getExpiry().getTime() / 1000;
            out.add(c);
        }
        return out;
    }

    private boolean webDriverSet(CookieRules.C c) {
        String host = currentHost();
        if (host == null || !CookieRules.domainMatch(host, c.domain, c.hostOnly)) return false;
        if (c.deleted) {
            driver.manage().deleteCookieNamed(c.name);
            return true;
        }
        Cookie.Builder b = new Cookie.Builder(c.name, c.value == null ? "" : c.value).path(c.path)
                .isSecure(c.secure).isHttpOnly(c.httpOnly);
        if (!c.hostOnly) b.domain("." + c.domain);
        if (c.expiry != null) b.expiresOn(new Date(c.expiry * 1000));
        if (c.sameSite != null) b.sameSite(c.sameSite);
        driver.manage().addCookie(b.build());
        return true;
    }

    private String currentHost() {
        try {
            String u = driver.getCurrentUrl();
            return u == null ? null : URI.create(u).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
