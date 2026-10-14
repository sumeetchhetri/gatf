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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.plan.Planner;

/** Δ152 test helpers: the canned plan, an adversarial plan, and a fake gate that checks the emitted statement forms. */
public final class GenFixtures {

    static final ObjectMapper OM = new ObjectMapper();

    private GenFixtures() {}

    /** Δ151's shop crawl through the Planner with AI off, as the plan JSON text `gatf plan` writes. */
    public static String shopPlanJson() throws Exception {
        Path tmp = Files.createTempFile("shop-coverage", ".json");
        try {
            try (InputStream in = GenFixtures.class.getResourceAsStream("/plan/shop-coverage.json")) {
                Files.write(tmp, in.readAllBytes());
            }
            Planner.Options o = new Planner.Options();
            o.ai = false;
            Planner.Result r = Planner.plan("shop-coverage.json", Planner.readMap(tmp, OM), null, o, OM);
            return OM.writerWithDefaultPrettyPrinter().writeValueAsString(r.json);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** The adversarial plan (test resource gen/adversarial-plan.json). */
    public static String adversarialPlanJson() throws Exception {
        try (InputStream in = GenFixtures.class.getResourceAsStream("/gen/adversarial-plan.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static GenPlan read(String json) throws Exception {
        return GenPlan.read(OM.readTree(json));
    }

    /**
     * A gate that accepts exactly the statement forms seleasy's corpus uses for these steps (open, maximize, goto,
     * click, clear, type "…", select index@N, ?? title/currentUrl/visible/text) with a locator of GATF's grammar, and
     * fails anything else, a '$' outside a comment, or a missing open. {@link #fail} can make a script fail on purpose.
     */
    public static final class FakeGate implements SelGate {
        static final String LOC = "(?:testid|label|role|id|name|css|xpath)@\\S.*";
        static final Pattern[] FORMS = {
                Pattern.compile("open chrome"), Pattern.compile("maximize"),
                Pattern.compile("goto https?://[^\\s\"'$]+"),
                Pattern.compile("click " + LOC), Pattern.compile("clear " + LOC),
                Pattern.compile("type \"[^\"\\\\$]*\" " + LOC),
                Pattern.compile("select index@\\d+ " + LOC),
                Pattern.compile("\\?\\? title =\"(?:[^\"\\\\]|\\\\.)*\""),
                Pattern.compile("\\?\\? currentUrl =\"https?://[^\"\\s$]+\""),
                Pattern.compile("\\?\\? " + LOC + " visible =true"),
                Pattern.compile("\\?\\? " + LOC + " text =\"(?:[^\"\\\\]|\\\\.)*\""),
        };
        public final List<String> linted = new ArrayList<String>();
        public final List<Map<String, String>> dryRuns = new ArrayList<Map<String, String>>();
        /** (file, text) → extra errors (null/empty: none). */
        public BiFunction<String, String, List<Diag>> fail = (f, t) -> null;
        /** suite → errors. */
        public java.util.function.Function<Map<String, String>, List<Diag>> dryFail = m -> null;

        @Override
        public List<Diag> lint(String name, String text) {
            linted.add(name);
            List<Diag> out = new ArrayList<Diag>();
            String[] lines = text.split("\n", -1);
            boolean open = false;
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                if (l.isEmpty() || l.startsWith("// ")) continue;
                if (l.equals("open chrome")) open = true;
                if (l.indexOf('$') >= 0) { out.add(new Diag(name, i + 1, null, "error", "dollar outside a comment")); continue; }
                boolean ok = false;
                for (Pattern p : FORMS) ok |= p.matcher(l).matches();
                if (!ok) out.add(new Diag(name, i + 1, null, "error", "Error parsing command at line " + (i + 1)));
            }
            if (!open) out.add(new Diag(name, null, null, "error", "No open command found..."));
            List<Diag> extra = fail.apply(name, text);
            if (extra != null) out.addAll(extra);
            return out;
        }

        @Override
        public List<Diag> dryRun(Map<String, String> files) {
            dryRuns.add(new LinkedHashMap<String, String>(files));
            List<Diag> out = new ArrayList<Diag>();
            java.util.Set<String> lower = new java.util.HashSet<String>();
            for (String f : files.keySet()) if (!lower.add(f.toLowerCase(java.util.Locale.ROOT))) out.add(new Diag(f, null, null, "error", "duplicate"));
            for (Map.Entry<String, String> e : files.entrySet()) if (!lint(e.getKey(), e.getValue()).isEmpty()) out.add(new Diag(e.getKey(), null, null, "error", "compile"));
            List<Diag> extra = dryFail.apply(files);
            if (extra != null) out.addAll(extra);
            return out;
        }
    }
}
