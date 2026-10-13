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
package com.gatf.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiConfig;
import com.gatf.ai.AiFakes;
import com.gatf.coverage.CoverageUrls;
import com.gatf.crawler.SpaCrawler;

/**
 * H1 (Δ151) Planner, R1 with the fake provider on canned crawls ({@code plan/shop-coverage.json}, an SPA map with
 * Δ151 elements; {@code plan/legacy-crawl-coverage.json}, a portal map). The accept line: the plan covers every
 * crawled route, with AI off, not configured, failing, over budget or adversarial.
 */
public class PlannerTest {

    static final ObjectMapper OM = new ObjectMapper();
    static final String KEY = "fake-key-0123456789abcdef";

    static byte[] resource(String name) throws Exception {
        try (InputStream in = PlannerTest.class.getResourceAsStream("/plan/" + name)) {
            assertNotNull(in, "fixture " + name);
            return in.readAllBytes();
        }
    }

    static CrawlMap map(String name) throws Exception {
        return CrawlMap.read(new java.io.ByteArrayInputStream(resource(name)), OM);
    }

    /** Every crawled route, computed independently from the raw file (the coverage index's own key function). */
    static Set<String> crawledRoutes(String name) throws Exception {
        JsonNode root = OM.readTree(resource(name));
        JsonNode pages = root.isArray() ? root : root.path("pages");
        Set<String> out = new LinkedHashSet<String>();
        for (JsonNode p : pages) {
            String r = CoverageUrls.route(p.path("url").asText(null));
            if (r != null) out.add(r);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Set<String> planRoutes(Map<String, Object> plan) {
        Set<String> out = new LinkedHashSet<String>();
        for (Map<String, Object> r : (List<Map<String, Object>>) plan.get("routes")) out.add((String) r.get("route"));
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> route(Map<String, Object> plan, String route) {
        for (Map<String, Object> r : (List<Map<String, Object>>) plan.get("routes")) if (route.equals(r.get("route"))) return r;
        return null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> journeys(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("journeys");
    }

    static Planner.Options noAi() {
        Planner.Options o = new Planner.Options();
        o.ai = false;
        return o;
    }

    @Test
    void everyCrawledRouteHasAnEntryWithAiOff() throws Exception {
        for (String f : new String[] { "shop-coverage.json", "legacy-crawl-coverage.json" }) {
            Set<String> crawled = crawledRoutes(f);
            assertTrue(crawled.size() >= 3, f);
            Map<String, Object> off = Planner.plan(f, map(f), null, noAi(), OM).json;
            assertEquals(crawled, planRoutes(off), f + " (AI off)");
            Map<String, Object> none = Planner.plan(f, map(f), AiClient.notConfigured("test"), new Planner.Options(), OM).json;
            assertEquals(crawled, planRoutes(none), f + " (AI not configured)");
            assertEquals("gatf-test-plan", off.get("format"));
            assertEquals(1, off.get("version"));
            assertEquals(TestPlan.INTENTS, off.get("intents"));
            // every navigable route has at least the smoke journey, built without any model
            for (Object o : (List<?>) off.get("routes")) {
                @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) o;
                if (Boolean.TRUE.equals(r.get("navigable"))) assertEquals("smoke", journeys(r).get(0).get("name"), f + " " + r.get("id"));
                else assertTrue(journeys(r).isEmpty() && !((List<?>) r.get("notes")).isEmpty(), "non-navigable: listed, explained, no journeys");
            }
        }
    }

    @Test
    void shopMapIsReadSafely() throws Exception {
        CrawlMap m = map("shop-coverage.json");
        assertEquals("spa", m.format);
        assertEquals("https://shop.test", m.origin);
        assertEquals(1, m.pagesWithoutRoute, "mailto: counted, not silently dropped");
        Map<String, Object> plan = Planner.plan("shop-coverage.json", m, null, noAi(), OM).json;
        assertTrue(String.valueOf(plan.get("notes")).contains("1 crawled page(s) have no http(s) route"), String.valueOf(plan.get("notes")));

        Map<String, Object> login = route(plan, "https://shop.test/login");
        assertEquals("high", login.get("priority"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> els = (List<Map<String, Object>>) login.get("elements");
        assertEquals("testid@login-email", els.get(0).get("locator"), "semantic first, as the recorder ranks");
        assertTrue(((List<?>) els.get(0).get("alternatives")).contains("id@email"));
        assertEquals("label@Password", els.get(1).get("locator"));
        assertEquals("role@button[name='Log in']", els.get(2).get("locator"));
        // fill-and-submit (POST: may change server state) + submit-empty validation, both deterministic
        Map<String, Object> form = journeys(login).get(1), req = journeys(login).get(2);
        assertEquals("form-1", form.get("name"));
        assertEquals(Boolean.TRUE, form.get("sideEffects"));
        assertEquals("form-1-required", req.get("name"));
        assertEquals("validation", req.get("goal"));

        // the cart: two crawled URLs, one route; the query is never kept; the '$' button has no safe locator
        Map<String, Object> cart = route(plan, "https://shop.test/cart");
        assertEquals(2, cart.get("pages"));
        assertTrue(String.valueOf(cart.get("notes")).contains("query string"));
        assertTrue(String.valueOf(cart.get("notes")).contains("1 element(s) left out"), String.valueOf(cart.get("notes")));
        assertFalse(OM.writeValueAsString(plan).contains("abc123"), "a query (maybe a token) never reaches the plan");
        assertFalse(OM.writeValueAsString(plan).contains("${"), "no '$' locator or text in the plan");
        // the checkbox outside a form is not checked unless required: no 'inputs' journey with only a navigate
        for (Map<String, Object> j : journeys(cart)) assertTrue(((List<?>) j.get("steps")).size() >= 2);

        // hash route kept; select gets option 1 of 3
        Map<String, Object> products = route(plan, "https://shop.test/#/products");
        assertNotNull(products);
        assertTrue(OM.writeValueAsString(journeys(products)).contains("\"intent\":\"select\",\"ref\":\"e1\",\"option\":1"), OM.writeValueAsString(journeys(products)));

        // off-origin and error routes are in the plan
        Map<String, Object> off = route(plan, "https://evil.test/off");
        assertEquals(Boolean.FALSE, off.get("navigable"));
        assertNull(off.get("url"));
        Map<String, Object> broken = route(plan, "https://shop.test/broken");
        assertEquals("error", broken.get("priority"));
        assertEquals(Boolean.TRUE, broken.get("navigable"));
    }

    @Test
    void portalMapIsPlannedToo() throws Exception {
        Map<String, Object> plan = Planner.plan("legacy-crawl-coverage.json", map("legacy-crawl-coverage.json"), null, noAi(), OM).json;
        Map<String, Object> home = route(plan, "http://legacy.test/");
        String j = OM.writeValueAsString(home);
        assertTrue(j.contains("name@term") && j.contains("id@go"), j);
        assertEquals(Boolean.FALSE, journeys(home).get(1).get("sideEffects"), "a GET form submit does not change state");
        Map<String, Object> contact = route(plan, "http://legacy.test/contact");
        assertFalse(OM.writeValueAsString(contact).contains("csrf"), "hidden inputs are not planned");
        assertEquals("error", route(plan, "http://legacy.test/gone").get("priority"));
    }

    @Test
    void crawlerElementsRoundTripThroughTheSavedMap() throws Exception {
        SpaCrawler.PageInfo p = new SpaCrawler.PageInfo();
        p.url = "https://app.test/signup";
        p.title = "Sign up";
        Map<String, Object> form = new LinkedHashMap<String, Object>();
        form.put("method", "post");
        List<Object> fields = new ArrayList<Object>();
        fields.add(field("input", "email", "email", true, sel("id", "em"), cand("testid", "signup-email", 1)));
        fields.add(field("input", "text", "nick", false, sel("name", "nick"), cand("label", "Nick", 2)));   // label not unique → name@
        fields.add(field("input", "text", "evil", false, sel("id", "x$y"), cand("label", "Evil ${secret}", 1)));   // unsafe → dropped
        form.put("fields", fields);
        form.put("submit", sel("css", "form button"));
        form.put("submitCand", List.of(cand("role", null, 1)));
        p.forms.add(form);
        List<Map<String, Object>> cov = SpaCrawler.coveragePlan(List.of(p));
        assertEquals(4, ((List<?>) cov.get(0).get("elements")).size(), "the crawl map keeps what the page reported (3 fields + submit)");
        byte[] saved = OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(cov);
        CrawlMap m = CrawlMap.read(new java.io.ByteArrayInputStream(saved), OM);
        CrawlMap.Route r = m.routes.get(0);
        assertEquals(3, r.elements.size());
        assertEquals("testid@signup-email", r.elements.get(0).locator);
        assertEquals("name@nick", r.elements.get(1).locator);
        assertEquals("submit", r.elements.get(2).kind);
        assertEquals("css@\"form button\"", r.elements.get(2).locator, "role candidate without a name falls back");
        assertEquals(1, r.elementsDropped);
    }

    /** The Δ155 coverage index still reads a crawl map with Δ151 elements: same routes, same priorities. */
    @Test
    void coverageIndexReadsTheExtendedMap(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path f = dir.resolve("shop-coverage.json");
        java.nio.file.Files.write(f, resource("shop-coverage.json"));
        Map<String, Object> idx = com.gatf.coverage.CoverageIndex.build(dir, f, null);
        @SuppressWarnings("unchecked") Map<String, Object> crawl = (Map<String, Object>) idx.get("crawl");
        assertEquals(crawledRoutes("shop-coverage.json").size(), ((Number) crawl.get("routes")).intValue(), String.valueOf(idx));
    }

    static Map<String, Object> field(String tag, String type, String name, boolean req, Map<String, Object> sel, Map<String, Object> cand) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sel", sel);
        m.put("cand", List.of(cand));
        m.put("tag", tag);
        m.put("type", type);
        m.put("name", name);
        m.put("required", req);
        m.put("label", name);
        m.put("options", 0);
        return m;
    }

    static Map<String, Object> sel(String s, String v) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("v", v);
        return m;
    }

    static Map<String, Object> cand(String s, String v, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        if ("role".equals(s)) { m.put("role", "button"); if (v != null) m.put("name", v); }
        else m.put("v", v);
        m.put("n", n);
        return m;
    }

    // ------------------------------------------------------------------ fake provider

    static AiConfig cfg() {
        AiConfig c = AiFakes.config();
        c.setRetries(0);
        return c;
    }

    static String reply(String json) {
        return AiFakes.reply(json, 100, 50);
    }

    @Test
    void fakeProviderEnrichesWithinTheClosedSetOnly() throws Exception {
        CrawlMap m = map("shop-coverage.json");
        // ids in crawl order: r1 home, r2 login, r3 cart, r4 products, r5 evil (off-origin), r6 broken
        String model = "Sure! {\"routes\":["
                + "{\"id\":\"r2\",\"priority\":\"medium\",\"journeys\":["
                + "  {\"goal\":\"negative\",\"steps\":[{\"intent\":\"fill\",\"ref\":\"e1\",\"data\":\"invalid\",\"url\":\"javascript:alert(1)\",\"code\":\"driver.quit()\"},"
                + "     {\"intent\":\"fill\",\"ref\":\"e2\",\"data\":\"sample\"},{\"intent\":\"click\",\"ref\":\"e3\"},{\"intent\":\"assert-url\",\"route\":\"r2\"}]},"
                + "  {\"goal\":\"navigation\",\"steps\":[{\"intent\":\"navigate\",\"route\":\"r5\"}]},"          // off-origin: rejected
                + "  {\"goal\":\"happy-path\",\"steps\":[{\"intent\":\"fill\",\"ref\":\"e3\"}]},"               // fill on a button: rejected
                + "  {\"goal\":\"happy-path\",\"steps\":[{\"intent\":\"click\",\"ref\":\"e9\"}]},"              // unseen element: rejected
                + "  {\"goal\":\"exploit\",\"steps\":[{\"intent\":\"click\",\"ref\":\"e3\"}]},"                 // unknown goal: rejected
                + "  {\"goal\":\"smoke\",\"steps\":[{\"intent\":\"eval\",\"js\":\"x\"}]}"                         // unknown intent: rejected
                + "]},"
                + "{\"id\":\"r1\",\"priority\":\"high\",\"journeys\":[{\"goal\":\"navigation\",\"steps\":["
                + "  {\"intent\":\"click\",\"ref\":\"e3\"},{\"intent\":\"assert-url\",\"route\":\"r2\"},{\"intent\":\"assert-visible\",\"ref\":\"e1\"},{\"intent\":\"assert-title\"}]}]},"
                + "{\"id\":\"r99\",\"journeys\":[{\"goal\":\"smoke\",\"steps\":[{\"intent\":\"assert-title\"}]}]},"   // not a route: ignored
                + "{\"id\":\"r6\",\"priority\":\"low\"}"                                                         // error route keeps 'error'
                + "]} trailing prose";
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok(reply(model)));
        Planner.Result res = Planner.plan("shop-coverage.json", m, AiFakes.client(cfg(), KEY, s), new Planner.Options(), OM);
        Map<String, Object> plan = res.json;
        assertEquals(crawledRoutes("shop-coverage.json"), planRoutes(plan), "the model neither adds nor drops a route");
        assertEquals(1, s.urls.size(), "one chunk");

        Map<String, Object> login = route(plan, "https://shop.test/login");
        assertEquals("medium", login.get("priority"));
        assertEquals("ai", login.get("priorityFrom"));
        List<Map<String, Object>> lj = journeys(login);
        Map<String, Object> neg = lj.get(lj.size() - 1);
        assertEquals("ai", neg.get("origin"));
        assertEquals("negative-ai1", neg.get("name"));
        String negJson = OM.writeValueAsString(neg);
        assertFalse(negJson.contains("javascript") || negJson.contains("quit"), "unknown fields are dropped: " + negJson);
        assertTrue(negJson.startsWith("{\"id\":\"r2-j4\""), negJson);
        assertEquals("{intent=navigate, route=r2}", String.valueOf(((List<?>) neg.get("steps")).get(0)), "a journey starts on its route");
        assertEquals(1, lj.stream().filter(j -> "ai".equals(j.get("origin"))).count(), "5 bad journeys rejected");

        // multi-route: after assert-url r2, e1 resolves on r2 (login email), assert-title on r2
        Map<String, Object> home = route(plan, "https://shop.test/");
        Map<String, Object> nav = journeys(home).get(journeys(home).size() - 1);
        String navJson = OM.writeValueAsString(nav.get("steps"));
        assertTrue(navJson.contains("{\"intent\":\"assert-visible\",\"ref\":\"e1\",\"on\":\"r2\"}"), navJson);
        assertEquals(Boolean.TRUE, nav.get("sideEffects"), "clicking a button may change state");
        // login's title is an injection attempt but holds no '$', so it is plain data for assert-title, escaped by the generator
        assertTrue(navJson.contains("{\"intent\":\"assert-title\",\"on\":\"r2\"}"), navJson);

        assertEquals("error", route(plan, "https://shop.test/broken").get("priority"));
        @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) plan.get("ai");
        assertEquals(true, ai.get("used"));
        assertEquals(2, ai.get("journeysAdded"));
        assertEquals(5, ai.get("journeysRejected"));
        assertEquals(1, ai.get("suggestionsIgnored"));
        assertEquals("ready: fake / fake-model-1", ai.get("status"));

        // the prompt: page text only inside the delimited block, as JSON values; the delimiter defused; no locators sent
        JsonNode body = OM.readTree(s.bodies.get(0));
        String system = body.path("system").asText(), user = body.path("user").asText();
        assertFalse(system.contains("IGNORE PREVIOUS") || system.contains("shop.test"), "instructions carry no page text");
        int open = user.indexOf("<<<ROUTE_DATA\n"), close = user.lastIndexOf("\nROUTE_DATA>>>");
        assertTrue(open >= 0 && close > open, user);
        String block = user.substring(open + 14, close);
        assertFalse(block.contains("ROUTE_DATA"), "the page cannot close the block");
        assertTrue(block.contains("IGNORE PREVIOUS INSTRUCTIONS"), "the title is still there, as data");
        JsonNode data = OM.readTree(block);
        assertEquals(5, data.path("routes").size(), "navigable routes only (the off-origin one is not sent)");
        assertFalse(block.contains("testid@") || block.contains("abc123"), "no locators, no query strings to the model");
    }

    @Test
    void chunksAndBudgetNeverDropARoute() throws Exception {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) b.append(',');
            b.append("{\"url\":\"https://big.test/p").append(i).append("\",\"title\":\"P").append(i).append("\",\"depth\":").append(i == 0 ? 0 : 1)
             .append(",\"priority\":\"low\",\"elements\":[{\"kind\":\"button\",\"text\":\"Go\",\"sel\":{\"s\":\"id\",\"v\":\"go").append(i).append("\"},\"cand\":[]}]}");
        }
        b.append(']');
        CrawlMap m = CrawlMap.read(new java.io.ByteArrayInputStream(b.toString().getBytes(StandardCharsets.UTF_8)), OM);
        AiConfig c = cfg();
        c.setMaxCallsPerRun(2);
        String ok = reply("{\"routes\":[{\"id\":\"r1\",\"journeys\":[{\"goal\":\"happy-path\",\"steps\":[{\"intent\":\"click\",\"ref\":\"e1\"}]}]}]}");
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok(ok)).then(AiFakes.ok(reply("no json here")));
        Planner.Options o = new Planner.Options();
        o.routesPerCall = 15;
        Map<String, Object> plan = Planner.plan("big", m, AiFakes.client(c, KEY, s), o, OM).json;
        assertEquals(40, planRoutes(plan).size());
        assertEquals(2, s.urls.size(), "the run's call cap holds");
        @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) plan.get("ai");
        assertEquals(3, ai.get("chunks"));
        assertEquals(1, ai.get("chunksAnswered"));
        String notes = String.valueOf(plan.get("notes"));
        assertTrue(notes.contains("chunk 2 of 3: the reply was not a usable JSON plan"), notes);
        assertTrue(notes.contains("AI stopped at AI budget: 10 route(s) in 1 chunk(s) keep the deterministic plan"), notes);

        // the planner's own call cap, and a prompt-size bound that splits chunks
        o.maxCalls = 1;
        o.maxPromptChars = 4_000;
        AiFakes.Sender s2 = new AiFakes.Sender().then(AiFakes.ok(ok));
        plan = Planner.plan("big", m, AiFakes.client(cfg(), KEY, s2), o, OM).json;
        assertEquals(1, s2.urls.size());
        assertTrue(String.valueOf(plan.get("notes")).contains("the planner's cap of 1 AI calls"), String.valueOf(plan.get("notes")));
        assertTrue(s2.bodies.get(0).length() < 4_000 + 4_000, "the first chunk respects the size bound");

        // two failures in a row stop asking; everything is still planned
        AiFakes.Sender s3 = new AiFakes.Sender().then(AiFakes.io("down")).then(AiFakes.status(500, "{}"));
        plan = Planner.plan("big", m, AiFakes.client(cfg(), KEY, s3), new Planner.Options(), OM).json;
        assertEquals(40, planRoutes(plan).size());
        assertEquals(2, s3.urls.size());
        notes = String.valueOf(plan.get("notes"));
        assertTrue(notes.contains("AI chunk 1 of 3: io") && notes.contains("AI chunk 2 of 3: http") && notes.contains("two AI failures in a row"), notes);
    }

    @Test
    void routeCapIsANoteAndAListNeverASilentDrop() throws Exception {
        Planner.Options o = noAi();
        o.maxRoutes = 2;
        Map<String, Object> plan = Planner.plan("shop", map("shop-coverage.json"), null, o, OM).json;
        @SuppressWarnings("unchecked") Map<String, Object> un = (Map<String, Object>) plan.get("unplanned");
        Set<String> all = new LinkedHashSet<String>(planRoutes(plan));
        for (Object u : (List<?>) un.get("routes")) all.add((String) u);
        assertEquals(crawledRoutes("shop-coverage.json"), all, "planned + unplanned = every crawled route");
        assertEquals(4, un.get("count"));
        assertTrue(String.valueOf(plan.get("notes")).contains("6 routes; 2 planned, 4 not planned"), String.valueOf(plan.get("notes")));
        assertTrue(Planner.plan("shop", map("shop-coverage.json"), null, o, OM).markdown.contains("## Not planned (cap)"));
    }

    @Test
    void visitedRoutesMarkTested() throws Exception {
        Planner.Options o = noAi();
        o.visited = Set.of("https://shop.test/login");
        Map<String, Object> plan = Planner.plan("shop", map("shop-coverage.json"), null, o, OM).json;
        assertEquals(Boolean.TRUE, route(plan, "https://shop.test/login").get("tested"));
        assertEquals(Boolean.FALSE, route(plan, "https://shop.test/").get("tested"));
        assertNull(route(Planner.plan("shop", map("shop-coverage.json"), null, noAi(), OM).json, "https://shop.test/").get("tested"), "unknown without a run");
    }

    @Test
    void unsafeUrlsAreListedButNeverNavigated() throws Exception {
        String crawl = "[{\"url\":\"https://a.test/\",\"depth\":0,\"title\":\"Home\",\"elements\":[]},"
                + "{\"url\":\"https://a.test/x$%7Bpw%7D\",\"depth\":1,\"title\":\"T\",\"elements\":[]},"
                + "{\"url\":\"https://a.test/login/\",\"depth\":1,\"title\":\"L\",\"elements\":[]},"
                + "{\"url\":\"https://a.test/login\",\"depth\":2,\"title\":\"L2\",\"elements\":[]},"
                + "{\"url\":\"file:///etc/passwd\",\"depth\":1,\"elements\":[]},"
                + "{\"url\":\"javascript:alert(1)\",\"depth\":1,\"elements\":[]}]";
        CrawlMap m = CrawlMap.read(new java.io.ByteArrayInputStream(crawl.getBytes(StandardCharsets.UTF_8)), OM);
        assertEquals(3, m.routes.size(), "trailing slash merged; file: and javascript: have no route");
        assertEquals(2, m.pagesWithoutRoute);
        String reply = "{\"routes\":[{\"id\":\"r1\",\"journeys\":[{\"goal\":\"navigation\",\"steps\":[{\"intent\":\"navigate\",\"route\":\"r2\"}]}]}]}";
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok(reply(reply)));
        Map<String, Object> plan = Planner.plan("a", m, AiFakes.client(cfg(), KEY, s), new Planner.Options(), OM).json;
        Map<String, Object> dollar = route(plan, "https://a.test/x$%7Bpw%7D");
        assertEquals(Boolean.FALSE, dollar.get("navigable"));
        assertTrue(String.valueOf(dollar.get("notes")).contains("cannot be written safely"), String.valueOf(dollar.get("notes")));
        @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) plan.get("ai");
        assertEquals(1, ai.get("journeysRejected"), "a navigate to it is rejected");
        assertFalse(OM.readTree(s.bodies.get(0)).path("user").asText().contains("x$"), "a non-navigable route is not sent");
    }

    @Test
    void markdownEscapesPageText() throws Exception {
        String crawl = "[{\"url\":\"https://x.test/\",\"depth\":0,\"title\":\"<img src=x onerror=alert(1)> [click](javascript:alert(1)) `code` | pipe\",\"elements\":[]}]";
        CrawlMap m = CrawlMap.read(new java.io.ByteArrayInputStream(crawl.getBytes(StandardCharsets.UTF_8)), OM);
        String md = Planner.plan("x", m, null, noAi(), OM).markdown;
        assertFalse(md.contains("<img") || md.contains("](javascript") || md.contains("`code`") || md.contains(" | pipe"), md);
        assertTrue(md.contains("&lt;img src=x onerror=alert\\(1\\)&gt; \\[click\\]\\(javascript:alert\\(1\\)\\)"), md);
    }

    @Test
    void replyParsingIsBoundedAndTyped() throws Exception {
        assertNull(PlanPrompt.parse(null, OM, 10));
        assertNull(PlanPrompt.parse("no object", OM, 10));
        assertNull(PlanPrompt.parse("{\"routes\":{}}", OM, 10));
        StringBuilder big = new StringBuilder("{\"routes\":[]}");
        while (big.length() <= PlanPrompt.MAX_REPLY) big.append(' ');
        assertNull(PlanPrompt.parse(big.toString(), OM, 10), "over the reply cap");
        List<PlanPrompt.Suggestion> s = PlanPrompt.parse("{\"routes\":[{\"id\":\"r1; DROP\"},{\"id\":\"r2\",\"priority\":\"urgent\",\"journeys\":"
                + "[{\"goal\":\"smoke\",\"steps\":[{\"intent\":\"fill\",\"ref\":\"e1\",\"data\":\"'; rm -rf /\"},{\"intent\":\"select\",\"ref\":\"e2\",\"option\":1.5}]}]}]}", OM, 10);
        assertEquals(1, s.size(), "an id outside r<digits> is skipped");
        assertNull(s.get(0).priority);
        assertEquals("?", s.get(0).journeys.get(0).get(0).get("data"), "data outside the closed set is marked, never defaulted");
        assertEquals("?", s.get(0).journeys.get(0).get(1).get("option"));
    }
}
