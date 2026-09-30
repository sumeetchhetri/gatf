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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.core.CookieRules;

/** M5 — the storage-state codec and save / load against a fake Chromium driver. */
class StorageStateTest {

    private static final String PLAYWRIGHT = "{\"cookies\":[{\"name\":\"sid\",\"value\":\"abc\",\"domain\":\".shop.test\",\"path\":\"/\","
            + "\"expires\":-1,\"httpOnly\":true,\"secure\":true,\"sameSite\":\"Lax\"},{\"name\":\"old\",\"value\":\"x\",\"domain\":\"shop.test\","
            + "\"path\":\"/\",\"expires\":1000,\"httpOnly\":false,\"secure\":false,\"sameSite\":\"None\"}],"
            + "\"origins\":[{\"origin\":\"https://shop.test\",\"localStorage\":[{\"name\":\"token\",\"value\":\"t1\"}]}]}";

    @Test
    void readsAndWritesPlaywrightStorageState() throws Exception {
        StorageState s = StorageState.fromJson(PLAYWRIGHT);
        assertThat(s.cookies).hasSize(2);
        CookieRules.C sid = s.cookies.get(0);
        assertThat(sid.domain).isEqualTo("shop.test");
        assertThat(sid.hostOnly).isFalse();
        assertThat(sid.httpOnly).isTrue();
        assertThat(sid.expiry).isNull();
        assertThat(s.liveCookies(2000)).extracting(c -> c.name).containsExactly("sid");   // "old" expired
        assertThat(s.origin("https://shop.test", "local")).containsEntry("token", "t1");
        JsonNode back = new ObjectMapper().readTree(s.toJson());
        assertThat(back.get("cookies").get(0).get("domain").asText()).isEqualTo(".shop.test");
        assertThat(back.get("cookies").get(0).get("expires").asInt()).isEqualTo(-1);
        assertThat(back.get("origins").get(0).get("localStorage").get(0).get("value").asText()).isEqualTo("t1");
        assertThat(back.get("origins").get(0).get("sessionStorage").size()).isEqualTo(0);
        assertThatThrownBy(() -> StorageState.fromJson("[]")).hasMessageContaining("not a storage-state file");
    }

    @Test
    void mergeReplacesSameCookiesAndOrigins() {
        StorageState a = StorageState.fromJson(PLAYWRIGHT);
        StorageState b = new StorageState();
        CookieRules.C sid = new CookieRules.C("sid", "new", "shop.test", "/");
        b.cookies.add(sid);
        b.origin("https://shop.test", "session").put("k", "v");
        b.origin("https://admin.test", "local").put("a", "1");
        a.merge(b);
        assertThat(a.cookies).extracting(c -> c.name + "=" + c.value).containsExactly("old=x", "sid=new");
        assertThat(a.origin("https://shop.test", "local")).isEmpty();          // whole origin replaced
        assertThat(a.origins.keySet()).containsExactly("https://shop.test", "https://admin.test");
    }

    @Test
    void cdpAndBidiCookieShapes() {
        Map<String, Object> cdp = new HashMap<String, Object>();
        cdp.put("name", "sid"); cdp.put("value", "v"); cdp.put("domain", "shop.test"); cdp.put("path", "/app");
        cdp.put("secure", true); cdp.put("httpOnly", true); cdp.put("expires", 99999999999.0); cdp.put("session", false); cdp.put("sameSite", "Strict");
        CookieRules.C c = StorageState.fromCdp(cdp);
        assertThat(c.hostOnly).isTrue();
        assertThat(c.expiry).isEqualTo(99999999999L);
        Map<String, Object> param = StorageState.toCdp(c);
        assertThat(param).containsEntry("url", "https://shop.test/app").containsEntry("sameSite", "Strict").doesNotContainKey("domain");
        Map<String, Object> v = new HashMap<String, Object>();
        v.put("type", "string"); v.put("value", "b");
        Map<String, Object> bidi = new HashMap<String, Object>();
        bidi.put("name", "sid"); bidi.put("value", v); bidi.put("domain", ".shop.test"); bidi.put("path", "/"); bidi.put("expiry", 5L);
        CookieRules.C bc = StorageState.fromBidi(bidi);
        assertThat(bc.value).isEqualTo("b");
        assertThat(bc.hostOnly).isFalse();
        @SuppressWarnings("unchecked") Map<String, Object> k = (Map<String, Object>) StorageState.toBidi(bc).get("cookie");
        assertThat(k).containsEntry("domain", ".shop.test").containsEntry("expiry", 5L);
        assertThat(k.get("value").toString()).isEqualTo("{type=string, value=b}");
    }

    @Test
    void scriptsCarryStateAndTokenSpec() {
        StorageState s = StorageState.fromJson(PLAYWRIGHT);
        String js = s.applyScript();
        assertThat(js).contains("\"https://shop.test\":{\"l\":{\"token\":\"t1\"},\"s\":{}}").contains("__gatf_state__");
        assertThat(StorageState.tokenScript(null)).isNull();
        assertThat(StorageState.tokenScript("localStorage:auth#access_token")).contains("localStorage.getItem(\"auth\")").contains("JSON.parse(v)")
                .contains("o[\"access_token\"]").doesNotContain("sessionStorage.getItem");
        assertThat(StorageState.tokenScript("jwt")).contains("(localStorage.getItem(\"jwt\")||sessionStorage.getItem(\"jwt\"))");
    }

    /** A Chromium-like driver: CDP cookies + a page with storage, recorded. */
    static WebDriver fakeChromium(List<Object[]> calls, Object pageStorage) {
        return (WebDriver) Proxy.newProxyInstance(StorageStateTest.class.getClassLoader(),
                new Class<?>[] { WebDriver.class, HasCdp.class, JavascriptExecutor.class }, (p, m, a) -> {
                    calls.add(new Object[] { m.getName(), a == null ? null : a[0], a == null || a.length < 2 ? null : a[1] });
                    if (m.getName().equals("executeCdpCommand") && "Network.getAllCookies".equals(a[0])) {
                        Map<String, Object> c = new LinkedHashMap<String, Object>();
                        c.put("name", "sid"); c.put("value", "s1"); c.put("domain", ".shop.test"); c.put("path", "/"); c.put("httpOnly", true);
                        c.put("session", true);
                        Map<String, Object> r = new HashMap<String, Object>();
                        r.put("cookies", List.of(c));
                        return r;
                    }
                    if (m.getName().equals("executeCdpCommand")) return new HashMap<String, Object>();
                    if (m.getName().equals("executeScript")) return String.valueOf(a[0]).startsWith("try{if(!/^https?:$/") ? pageStorage : null;
                    if (m.getName().equals("hashCode")) return 1;
                    if (m.getName().equals("equals")) return p == a[0];
                    return null;
                });
    }

    @Test
    void saveThenLoadThroughCdp() throws Exception {
        File dir = Files.createTempDirectory("gatf-state").toFile();
        File f = new File(dir, ".auth/admin.json");
        Map<String, Object> storage = new LinkedHashMap<String, Object>();
        storage.put("origin", "https://shop.test");
        storage.put("local", Map.of("token", "t1"));
        storage.put("session", Map.of());
        List<Object[]> calls = new ArrayList<Object[]>();
        assertThat(BrowserState.save(fakeChromium(calls, storage), f)).isEqualTo("state save " + f.getPath() + ": 1 cookie(s), 1 origin(s) with storage");
        StorageState saved = StorageState.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertThat(saved.cookies).extracting(c -> c.name).containsExactly("sid");
        assertThat(saved.origin("https://shop.test", "local")).containsEntry("token", "t1");

        calls.clear();
        assertThat(BrowserState.load(fakeChromium(calls, null), f)).isEqualTo("state load " + f.getPath() + ": 1/1 cookie(s), 1 origin(s) with storage");
        assertThat(calls).extracting(c -> c[0] + " " + (c[0].equals("executeCdpCommand") ? c[1] : "js"))
                .containsExactly("executeCdpCommand Network.setCookies", "executeCdpCommand Page.addScriptToEvaluateOnNewDocument", "executeScript js");
        assertThatThrownBy(() -> BrowserState.load(fakeChromium(calls, null), new File(dir, "missing.json"))).hasMessageContaining("file not found");
    }

    @Test
    void oneRejectedCookieDoesNotDropTheOthers() throws Exception {
        // E3: Chrome fails the whole Network.setCookies batch for one bad cookie: retried one by one, the bad one skipped
        StorageState s = new StorageState();
        s.cookies.add(new CookieRules.C("sid", "1", "shop.test", "/"));
        s.cookies.add(new CookieRules.C("bad", "2", "shop.test", "/"));
        s.cookies.add(new CookieRules.C("lang", "en", "shop.test", "/"));
        File f = File.createTempFile("gatf-state", ".json");
        Files.write(f.toPath(), s.toJson().getBytes(StandardCharsets.UTF_8));
        List<String> set = new ArrayList<String>();
        WebDriver d = (WebDriver) Proxy.newProxyInstance(StorageStateTest.class.getClassLoader(),
                new Class<?>[] { WebDriver.class, HasCdp.class, JavascriptExecutor.class }, (p, m, a) -> {
                    if (m.getName().equals("executeCdpCommand") && "Network.setCookies".equals(a[0])) throw new RuntimeException("Invalid cookie fields");
                    if (m.getName().equals("executeCdpCommand") && "Network.setCookie".equals(a[0])) {
                        Object name = ((Map<?, ?>) a[1]).get("name");
                        if ("bad".equals(name)) throw new RuntimeException("Invalid cookie fields");
                        set.add(String.valueOf(name));
                        return new HashMap<String, Object>();
                    }
                    if (m.getName().equals("hashCode")) return 1;
                    return null;
                });
        assertThat(BrowserState.load(d, f)).isEqualTo("state load " + f.getPath() + ": 2/3 cookie(s), 0 origin(s) with storage");
        assertThat(set).containsExactly("sid", "lang");
        // no CDP at all (every call fails): not counted as set, so the other transports are tried
        WebDriver none = (WebDriver) Proxy.newProxyInstance(StorageStateTest.class.getClassLoader(),
                new Class<?>[] { WebDriver.class, HasCdp.class }, (p, m, a) -> {
                    if (m.getName().equals("executeCdpCommand")) throw new RuntimeException("no CDP");
                    if (m.getName().equals("getCurrentUrl")) return "https://other.test/";
                    if (m.getName().equals("hashCode")) return 1;
                    return null;
                });
        assertThat(BrowserState.setCookies(none, s.cookies)).isEqualTo(0);
    }

    @Test
    void tokenScriptUnwrapsAJsonQuotedToken() {
        // E8: JSON.stringify'd tokens ("\"eyJ…\"") are unwrapped; the page-side behaviour is run on node in the delta check
        assertThat(StorageState.tokenScript("jwt")).contains("JSON.parse(v)").contains("typeof q==='string'");
    }

    @Test
    void unknownSameSiteIsWrittenAsLax() throws Exception {
        // Playwright only accepts Strict / Lax / None
        StorageState s = new StorageState();
        s.cookies.add(new com.gatf.executor.core.CookieRules.C("sid", "1", "app.test", "/"));
        assertThat(s.toJson().replace(" ", "")).contains("\"sameSite\":\"Lax\"");
    }
}
