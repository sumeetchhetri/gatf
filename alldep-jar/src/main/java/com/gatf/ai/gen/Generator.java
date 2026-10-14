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
package com.gatf.ai.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiException;
import com.gatf.ai.AiReply;

/**
 * H1 (Δ152) Generator: test plan ({@code gatf-test-plan} v1) → seleasy suite, gated.
 * <ol>
 * <li>Every journey of every route is mapped by {@link SelEmitter}. Journeys with side effects (the plan's flag, or a
 * click GATF itself recognises as one) are skipped unless {@link Options#includeSideEffects}, and listed.</li>
 * <li>Each script must pass {@link SelGate#lint} ({@code Command.validateSel}). A failing script goes to the bounded
 * repair loop when AI is on and configured: the model returns closed-set plan edits ({@link RepairPrompt}), GATF
 * regenerates and re-gates; at most {@link Options#maxRounds} rounds per file, {@link Options#maxCalls} calls per run
 * on top of the client's per-run caps, and the loop stops early when the same normalised error comes back or the
 * edits change nothing.</li>
 * <li>The scripts that passed are compiled together ({@link SelGate#dryRun}); one the dry run blames is dropped and the
 * rest are compiled again (bounded).</li>
 * </ol>
 * The {@link Result} lists every script with its status and errors; only {@code pass} scripts are meant to be written.
 * Pure apart from the gate and the client.
 */
public final class Generator {

    public static final class Options {
        public boolean ai = true;
        public boolean includeSideEffects;
        public int maxRounds = 3;
        public int maxCalls = 20;
        public int maxDryRuns = 5;
        /** Scripts gated per run (each is a transpile + compile); journeys past it are listed as skipped. */
        public int maxScripts = 500;
    }

    public static final class FileResult {
        public SelEmitter.Script script;
        public String status;   // pass | fail
        public final List<SelGate.Diag> lint = new ArrayList<SelGate.Diag>();
        public final List<SelGate.Diag> dryRun = new ArrayList<SelGate.Diag>();
        public int rounds;
        public List<String> edits = new ArrayList<String>();
        public String stoppedBecause;
    }

    public static final class Result {
        public final List<FileResult> files = new ArrayList<FileResult>();
        public final List<Map<String, Object>> skipped = new ArrayList<Map<String, Object>>();
        public final List<String> sideEffectJourneys = new ArrayList<String>();
        public final List<String> notes = new ArrayList<String>();
        public final Map<String, Object> ai = new LinkedHashMap<String, Object>();
        public boolean dryRunOk;
        public boolean dryRunRan;

        public List<FileResult> passing() {
            List<FileResult> l = new ArrayList<FileResult>();
            for (FileResult f : files) if ("pass".equals(f.status)) l.add(f);
            return l;
        }

        /** The summary (no script text unless {@code withText}). Holds ids, counts, fixed notes and gate messages; never a key. */
        public Map<String, Object> json(boolean withText) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            int pass = passing().size();
            Map<String, Object> c = new LinkedHashMap<String, Object>();
            c.put("scripts", files.size());
            c.put("passed", pass);
            c.put("failed", files.size() - pass);
            c.put("skippedJourneys", skipped.size());
            c.put("sideEffectJourneysSkipped", sideEffectJourneys.size());
            m.put("counts", c);
            m.put("dryRun", dryRunOk ? "ok" : dryRunRan ? "failed" : "nothing to compile");
            m.put("ai", ai);
            List<Map<String, Object>> fs = new ArrayList<Map<String, Object>>();
            for (FileResult f : files) {
                Map<String, Object> x = new LinkedHashMap<String, Object>();
                x.put("file", f.script.file);
                x.put("route", GenPlan.safeId(f.script.routeId));
                x.put("journey", GenPlan.safeId(f.script.journeyId));
                x.put("goal", f.script.goal);
                x.put("sideEffects", f.script.sideEffects);
                x.put("status", f.status);
                x.put("steps", f.script.steps);
                x.put("stepsSkipped", f.script.stepsSkipped);
                x.put("lint", diags(f.lint));
                x.put("dryRun", diags(f.dryRun));
                if (f.rounds > 0) x.put("repairRounds", f.rounds);
                if (!f.edits.isEmpty()) x.put("edits", f.edits);
                if (f.stoppedBecause != null) x.put("repairStopped", f.stoppedBecause);
                x.put("notes", f.script.notes);
                if (withText) x.put("text", f.script.text);
                fs.add(x);
            }
            m.put("files", fs);
            m.put("skipped", skipped);
            m.put("sideEffectJourneys", sideEffectJourneys);
            m.put("notes", notes);
            return m;
        }

        private static List<Map<String, Object>> diags(List<SelGate.Diag> d) {
            List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
            for (SelGate.Diag x : d) l.add(x.json());
            return l;
        }
    }

    private Generator() {}

    public static Result generate(GenPlan plan, String planName, SelGate gate, AiClient ai, Options o, ObjectMapper om) throws Exception {
        if (o == null) o = new Options();
        Result res = new Result();
        res.notes.addAll(plan.notes);
        boolean aiOn = o.ai && ai != null && ai.configured();
        res.ai.put("used", false);
        if (!o.ai) res.ai.put("status", "off: deterministic output only");
        else if (ai == null || !ai.configured()) res.ai.put("status", ai == null ? "AI not configured: deterministic output only" : ai.status() + " (deterministic output only)");
        else res.ai.put("status", ai.status());
        int[] calls = { 0 };
        int[] failsInRow = { 0 };
        String[] aiStop = { null };
        Set<String> names = new HashSet<String>();
        int capped = 0;
        for (GenPlan.Route r : plan.routes) {
            for (GenPlan.Journey j : r.journeys) {
                SelEmitter.Script s = SelEmitter.emit(plan, planName, r, j, null);
                if (s.text == null) {
                    Map<String, Object> k = new LinkedHashMap<String, Object>();
                    k.put("route", GenPlan.safeId(r.id));
                    k.put("journey", GenPlan.safeId(j.id));
                    k.put("why", s.skipReason);
                    res.skipped.add(k);
                    continue;
                }
                if (s.sideEffects && !o.includeSideEffects) {
                    res.sideEffectJourneys.add(GenPlan.safeId(j.id));
                    continue;
                }
                if (res.files.size() >= Math.max(1, o.maxScripts)) {
                    capped++;
                    continue;
                }
                s.file = fileName(r.id, j.id, names);
                FileResult f = new FileResult();
                f.script = s;
                f.lint.addAll(gate.lint(s.file, s.text));
                if (!f.lint.isEmpty() && aiOn) repair(plan, planName, r, j, f, gate, ai, o, om, calls, failsInRow, aiStop, res);
                f.status = f.lint.isEmpty() ? "pass" : "fail";
                res.files.add(f);
            }
        }
        if (!res.sideEffectJourneys.isEmpty() && !o.includeSideEffects) {
            res.notes.add(res.sideEffectJourneys.size() + " journey(s) with side effects were not generated (they click a button or submit a non-GET form); use --include-side-effects / includeSideEffects:true");
        }
        if (capped > 0) res.notes.add("cap: " + capped + " journey(s) over the cap of " + o.maxScripts + " scripts per run were not generated");
        if (aiStop[0] != null) {
            res.notes.add("AI repair stopped: " + aiStop[0] + "; the remaining failures are reported as they are");
            res.ai.put("stopped", aiStop[0]);
        }
        if (aiOn) {
            res.ai.put("used", calls[0] > 0);
            res.ai.put("calls", calls[0]);
            res.ai.put("usage", ai.usage());
        }
        dryRun(res, gate, o);
        return res;
    }

    private static void repair(GenPlan plan, String planName, GenPlan.Route r, GenPlan.Journey j, FileResult f, SelGate gate, AiClient ai,
            Options o, ObjectMapper om, int[] calls, int[] failsInRow, String[] aiStop, Result res) throws Exception {
        SelEmitter.Edits edits = new SelEmitter.Edits();
        String lastErr = normalise(f.lint);
        String file = f.script.file;
        for (int round = 1; round <= Math.max(0, Math.min(3, o.maxRounds)); round++) {
            if (aiStop[0] != null) { f.stoppedBecause = "AI stopped for this run"; return; }
            if (calls[0] >= Math.max(1, o.maxCalls)) { aiStop[0] = "the generator's cap of " + o.maxCalls + " AI calls"; f.stoppedBecause = aiStop[0]; return; }
            AiReply reply;
            calls[0]++;
            f.rounds = round;
            try {
                reply = ai.ask(RepairPrompt.build(plan, r, j, f.lint, om));
                failsInRow[0] = 0;
            } catch (AiException e) {
                if (e.code == AiException.Code.BUDGET || e.code == AiException.Code.NOT_CONFIGURED || e.code == AiException.Code.INTERRUPTED) {
                    aiStop[0] = "AI " + e.word();
                } else if (++failsInRow[0] >= 2) {
                    aiStop[0] = "two AI failures in a row";
                }
                f.stoppedBecause = "AI " + e.word();
                return;
            }
            SelEmitter.Edits got = RepairPrompt.parse(reply.text, om);
            if (got == null || got.isEmpty()) { f.stoppedBecause = "the reply held no usable edits"; return; }
            SelEmitter.Edits next = edits.copy();
            next.drop.addAll(got.drop);
            next.alternative.putAll(got.alternative);
            next.data.putAll(got.data);
            SelEmitter.Script s = SelEmitter.emit(plan, planName, r, j, next);
            if (s.text == null) { f.stoppedBecause = "the edits left nothing to generate"; return; }
            if (s.sideEffects && !o.includeSideEffects) { f.stoppedBecause = "the edited journey has side effects"; return; }
            if (s.text.equals(f.script.text)) { f.stoppedBecause = "the edits changed nothing"; return; }
            edits = next;
            s.file = file;
            f.script = s;
            f.edits = edits.words();
            f.lint.clear();
            f.lint.addAll(gate.lint(file, s.text));
            if (f.lint.isEmpty()) { f.stoppedBecause = null; return; }
            String err = normalise(f.lint);
            if (err.equals(lastErr)) { f.stoppedBecause = "the same error repeated"; return; }
            lastErr = err;
        }
        f.stoppedBecause = "the round cap (" + Math.min(3, o.maxRounds) + ")";
    }

    /** The dry run over the passing scripts; a script it blames is failed and the rest compiled again (bounded). */
    private static void dryRun(Result res, SelGate gate, Options o) throws Exception {
        for (int k = 0; k < Math.max(1, o.maxDryRuns); k++) {
            List<FileResult> pass = res.passing();
            if (pass.isEmpty()) { res.dryRunOk = false; return; }
            Map<String, String> files = new LinkedHashMap<String, String>();
            Set<String> lower = new HashSet<String>();
            for (FileResult f : pass) {
                if (!lower.add(f.script.file.toLowerCase(Locale.ROOT))) {   // never reached: fileName() keeps names unique
                    f.status = "fail";
                    f.dryRun.add(new SelGate.Diag(f.script.file, null, null, "error", "duplicate file name in the suite"));
                    continue;
                }
                files.put(f.script.file, f.script.text);
            }
            res.dryRunRan = true;
            List<SelGate.Diag> ds = gate.dryRun(files);
            if (ds.isEmpty()) { res.dryRunOk = true; return; }
            boolean blamed = false;
            for (SelGate.Diag d : ds) {
                for (FileResult f : pass) {
                    if (f.script.file.equals(d.file)) { f.dryRun.add(d); f.status = "fail"; blamed = true; }
                }
            }
            if (!blamed) {   // the suite failed as a whole: nothing can be written
                for (FileResult f : pass) { f.dryRun.addAll(ds); f.status = "fail"; }
                res.dryRunOk = false;
                return;
            }
        }
        for (FileResult f : res.passing()) {
            f.status = "fail";
            f.dryRun.add(new SelGate.Diag(f.script.file, null, null, "error", "the dry run did not settle within " + o.maxDryRuns + " rounds"));
        }
        res.dryRunOk = false;
    }

    /** Gate messages with numbers, generated class names and spacing normalised, for the repeated-error check. */
    static String normalise(List<SelGate.Diag> ds) {
        StringBuilder b = new StringBuilder();
        for (SelGate.Diag d : ds) b.append(d.message).append('|');
        return b.toString().toLowerCase(Locale.ROOT).replaceAll("stc_\\d+", "stc").replaceAll("\\d+", "#").replaceAll("\\s+", " ").trim();
    }

    /**
     * {@code <journey>.sel} from the plan's ids, sanitised ([A-Za-z0-9_-], no dots, so never {@code ..} or a path),
     * prefixed with the route id when the journey id does not start with it, unique case-insensitively
     * ({@code -2}, {@code -3}, …), and never a Windows device name.
     */
    public static String fileName(String routeId, String journeyId, Set<String> taken) {
        String r = GenPlan.safeId(routeId), j = GenPlan.safeId(journeyId);
        String stem = j.startsWith(r + "-") ? j : r + "-" + j;
        if (stem.length() > 60) stem = stem.substring(0, 60);
        if (stem.matches("(?i)(con|prn|aux|nul|com\\d|lpt\\d)(-.*)?")) stem = "t-" + stem;
        String name = stem;
        for (int i = 2; !taken.add(name.toLowerCase(Locale.ROOT)); i++) name = stem + "-" + i;
        return name + ".sel";
    }
}
