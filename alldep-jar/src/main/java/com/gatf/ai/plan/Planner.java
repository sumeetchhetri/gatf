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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiException;
import com.gatf.ai.AiReply;

/**
 * H1 (Δ151) Planner: crawl map + (optionally) the configured model → test plan (JSON + Markdown).
 * <ol>
 * <li>{@link TestPlan#build}: every crawled route gets an entry and deterministic journeys, AI or not.</li>
 * <li>With a configured {@link AiClient} and AI not turned off, the navigable entries go to the model in chunks
 * (by route count and prompt size); each reply is parsed to the closed set ({@link PlanPrompt#parse}) and every
 * journey validated against the plan ({@link TestPlan#addJourney}). Suggestions for routes outside the chunk are
 * ignored, so the model can neither add nor drop a route.</li>
 * <li>Bounded: the client's per-run call/token/cost caps, plus {@link Options#maxCalls}. Chunks the budget does not
 * reach keep their deterministic plan, with a note naming how many.</li>
 * </ol>
 * The AI summary records only the client's status (never a key), fixed error words, and counts. Pure apart from the
 * client's network use.
 */
public final class Planner {

    public static final class Options {
        public int maxRoutes = TestPlan.DEFAULT_MAX_ROUTES;
        /** False: deterministic plan only, no call. */
        public boolean ai = true;
        public int routesPerCall = 15;
        public int maxPromptChars = 48_000;
        public int maxCalls = 20;
        /** Routes some test already visits (from a coverage index), or null. */
        public Set<String> visited;
        /** Notes from the caller (e.g. from {@link Planner#visited}), copied into the plan. */
        public final List<String> notes = new ArrayList<String>();
    }

    public static final class Result {
        public final TestPlan plan;
        public final Map<String, Object> json;
        public final String markdown;

        Result(TestPlan plan) {
            this.plan = plan;
            this.json = plan.toJson();
            this.markdown = PlanMarkdown.render(json);
        }
    }

    private Planner() {}

    public static Result plan(String source, CrawlMap map, AiClient ai, Options o, ObjectMapper om) {
        if (o == null) o = new Options();
        TestPlan p = TestPlan.build(source, map, o.visited, o.maxRoutes);
        p.notes.addAll(o.notes);
        Map<String, Object> s = p.ai;
        s.put("used", false);
        if (!o.ai) {
            s.put("status", "off: deterministic plan only");
        } else if (ai == null || !ai.configured()) {
            s.put("status", ai == null ? "AI not configured" : ai.status());
            p.notes.add("AI not configured: deterministic plan only (every route still has an entry)");
        } else {
            s.put("status", ai.status());
            enrich(p, ai, o, om);
            s.put("usage", ai.usage());
        }
        if (o.visited == null && o.notes.isEmpty()) p.notes.add("no coverage run given: 'tested' is unknown (pass a run folder with trace coverage to mark untested routes)");
        return new Result(p);
    }

    /** Reads a crawl map file, refusing one over {@link CrawlMap#MAX_BYTES}. The message names no page content. */
    public static CrawlMap readMap(Path crawl, ObjectMapper om) throws IOException {
        if (!Files.isRegularFile(crawl)) throw new IOException("crawl map not found");
        if (Files.size(crawl) > CrawlMap.MAX_BYTES) throw new IOException("crawl map is larger than " + CrawlMap.MAX_BYTES + " bytes");
        try (InputStream in = Files.newInputStream(crawl)) {
            return CrawlMap.read(in, om);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("crawl map is not valid JSON");
        }
    }

    /**
     * The routes some test visited in a run folder (Δ155 coverage index, the same {@code CoverageUrls.route} keys), or
     * null when the run has no route data; {@code notes} says why.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> visited(Path runDir, List<String> notes) {
        if (runDir == null || !Files.isDirectory(runDir)) {
            notes.add("coverage run folder not found: 'tested' is unknown");
            return null;
        }
        Map<String, Object> idx = com.gatf.coverage.CoverageIndex.build(runDir);
        Object routes = idx.get("routes");
        if ("no-data".equals(idx.get("status")) || !(routes instanceof Map)) {
            notes.add("the coverage run has no visited-page data (trace coverage off?): 'tested' is unknown");
            return null;
        }
        if (!"ok".equals(idx.get("status"))) notes.add("the coverage run's index is " + idx.get("status") + ": 'tested: no' may be a route a test without coverage visited");
        return new LinkedHashSet<String>(((Map<String, Object>) routes).keySet());
    }

    private static void enrich(TestPlan p, AiClient ai, Options o, ObjectMapper om) {
        List<TestPlan.Entry> nav = new ArrayList<TestPlan.Entry>();
        for (TestPlan.Entry e : p.entries) if (e.navigable()) nav.add(e);
        List<List<TestPlan.Entry>> chunks = new ArrayList<List<TestPlan.Entry>>();
        List<TestPlan.Entry> cur = new ArrayList<TestPlan.Entry>();
        int size = 0;
        int per = Math.max(1, Math.min(100, o.routesPerCall));
        // the navigation list and the instructions go with every chunk: the routes get what is left of the budget
        int fixed = PlanPrompt.build(new ArrayList<TestPlan.Entry>(), nav, om).user.length() + PlanPrompt.SYSTEM.length();
        int budget = Math.max(4_000, o.maxPromptChars - fixed);
        for (TestPlan.Entry e : nav) {
            int sz = PlanPrompt.size(e, om);
            if (!cur.isEmpty() && (cur.size() >= per || size + sz > budget)) {
                chunks.add(cur);
                cur = new ArrayList<TestPlan.Entry>();
                size = 0;
            }
            cur.add(e);
            size += sz;
        }
        if (!cur.isEmpty()) chunks.add(cur);
        Map<String, Object> s = p.ai;
        s.put("chunks", chunks.size());
        int answered = 0, enriched = 0, added = 0, rejected = 0, ignored = 0, failedInRow = 0;
        Map<String, Integer> why = new LinkedHashMap<String, Integer>();
        int i = 0;
        String stop = null;
        for (; i < chunks.size(); i++) {
            if (i >= Math.max(1, o.maxCalls)) { stop = "the planner's cap of " + o.maxCalls + " AI calls"; break; }
            List<TestPlan.Entry> chunk = chunks.get(i);
            AiReply r;
            try {
                r = ai.ask(PlanPrompt.build(chunk, nav, om));
            } catch (AiException e) {
                if (e.code == AiException.Code.BUDGET || e.code == AiException.Code.NOT_CONFIGURED || e.code == AiException.Code.INTERRUPTED) {
                    stop = "AI " + e.word();
                    break;
                }
                p.notes.add("AI chunk " + (i + 1) + " of " + chunks.size() + ": " + e.word() + "; its " + chunk.size() + " route(s) keep the deterministic plan");
                if (++failedInRow >= 2) { stop = "two AI failures in a row"; i++; break; }
                continue;
            }
            failedInRow = 0;
            s.put("used", true);
            List<PlanPrompt.Suggestion> sug = PlanPrompt.parse(r.text, om, chunk.size() * 4);
            if (sug == null) {
                p.notes.add("AI chunk " + (i + 1) + " of " + chunks.size() + ": the reply was not a usable JSON plan; its routes keep the deterministic plan");
                continue;
            }
            answered++;
            for (PlanPrompt.Suggestion g : sug) {
                TestPlan.Entry e = p.entry(g.id);
                if (e == null || !chunk.contains(e)) { ignored++; continue; }
                String before = e.priority;
                p.setPriority(e, g.priority);
                boolean touched = !before.equals(e.priority);
                for (int j = 0; j < g.journeys.size(); j++) {
                    String no = p.addJourney(e, g.goals.get(j), g.journeys.get(j));
                    if (no == null) { added++; touched = true; }
                    else { rejected++; why.merge(no, 1, Integer::sum); }
                }
                if (touched) enriched++;
            }
        }
        if (stop != null && i < chunks.size()) {
            int left = 0;
            for (int k = i; k < chunks.size(); k++) left += chunks.get(k).size();
            p.notes.add("AI stopped at " + stop + ": " + left + " route(s) in " + (chunks.size() - i) + " chunk(s) keep the deterministic plan");
        }
        s.put("chunksAnswered", answered);
        s.put("routesEnriched", enriched);
        s.put("journeysAdded", added);
        s.put("journeysRejected", rejected);
        if (!why.isEmpty()) s.put("rejectedBecause", why);
        if (ignored > 0) s.put("suggestionsIgnored", ignored);
    }
}
