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
package com.gatf.generator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** M15: the pure decisions behind `gatf test --watch`. */
class WatchPlannerTest {

    private static Set<String> set(String... s) { return new TreeSet<String>(Arrays.asList(s)); }

    @Test
    void watchesTestInputsButNotOutputsOrBuildDirs() {
        assertTrue(WatchPlanner.watched("tests/ui/login.sel"));
        assertTrue(WatchPlanner.watched("tests/api/users.xml"));
        assertTrue(WatchPlanner.watched("gatf-config.json"));
        assertTrue(WatchPlanner.watched("tests/ui/app.properties"));
        assertFalse(WatchPlanner.watched("out/run1/report.json"));
        assertFalse(WatchPlanner.watched("tests/out/x.sel"));
        assertFalse(WatchPlanner.watched("gatf-results.json"));
        assertFalse(WatchPlanner.watched("node_modules/a/b.js"));
        assertFalse(WatchPlanner.watched(".git/config.xml"));
        assertFalse(WatchPlanner.watched("tests/.login.sel.swp"));
        assertFalse(WatchPlanner.watched("tests/ui/shot.png"));
        assertFalse(WatchPlanner.watched("tests/ui/__screenshots__/chrome/a.json"));
    }

    @Test
    void diffFindsAddedChangedAndRemoved() {
        Map<String, Long> a = new HashMap<String, Long>();
        a.put("a.sel", 1L); a.put("b.sel", 1L); a.put("c.sel", 1L);
        Map<String, Long> b = new HashMap<String, Long>();
        b.put("a.sel", 1L); b.put("b.sel", 2L); b.put("d.sel", 1L);
        assertEquals(set("b.sel", "c.sel", "d.sel"), WatchPlanner.diff(a, b));
        assertTrue(WatchPlanner.diff(b, b).isEmpty());
    }

    @Test
    void debounceWaitsForTheBurstToSettle() {
        WatchPlanner.Debouncer d = new WatchPlanner.Debouncer(400);
        assertTrue(d.poll(0).isEmpty());
        d.changed(set("a.sel"), 1000);
        assertTrue(d.poll(1200).isEmpty());          // still inside the quiet window
        d.changed(set("b.sel"), 1300);               // another save extends it
        assertTrue(d.poll(1650).isEmpty());
        assertEquals(set("a.sel", "b.sel"), d.poll(1700));
        assertFalse(d.hasPending());
        assertTrue(d.poll(5000).isEmpty());          // released once
        d.changed(Collections.<String>emptySet(), 6000);
        assertTrue(d.poll(9000).isEmpty());          // an empty scan diff is not a change
    }

    @Test
    void aSaveRunsTheChangedScriptsAndTheLastFailures() {
        List<String> failed = Arrays.asList(WatchPlanner.fileGrep("cart.sel"));
        List<WatchPlanner.Run> p = WatchPlanner.afterChange(set("tests/ui/login.sel"), failed, set("login.sel", "cart.sel", "users.xml"));
        assertEquals(1, p.size());
        assertEquals("changed + failed", p.get(0).label);
        assertEquals(Arrays.asList(WatchPlanner.fileGrep("login.sel"), WatchPlanner.fileGrep("cart.sel")), p.get(0).grep);
        // the pattern selects the script by file name, as a whole path segment
        Pattern g = Pattern.compile(WatchPlanner.fileGrep("login.sel"));
        assertTrue(g.matcher("tests/ui/login.sel @smoke").find());
        assertTrue(g.matcher("login.sel").find());
        assertFalse(g.matcher("tests/ui/relogin.sel").find());
        assertFalse(g.matcher("tests/ui/login.sel.bak").find());
    }

    @Test
    void anythingElseRunsEverythingFailedFirst() {
        List<String> failed = Arrays.asList(WatchPlanner.fileGrep("cart.sel"));
        Set<String> known = set("login.sel", "cart.sel");
        for (Set<String> changed : Arrays.asList(set("gatf-config.json"), set("tests/ui/lib/auth.sel"), set("tests/ui/login.sel", "tests/ui/app.properties"))) {
            List<WatchPlanner.Run> p = WatchPlanner.afterChange(changed, failed, known);
            assertEquals(2, p.size(), changed.toString());
            assertEquals("failed", p.get(0).label);
            assertEquals(failed, p.get(0).grep);
            assertEquals("rest", p.get(1).label);
            assertEquals(failed, p.get(1).grepInvert);
        }
        // no previous run: nothing is known yet, so everything runs
        assertTrue(WatchPlanner.afterChange(set("tests/ui/login.sel"), Collections.<String>emptyList(), Collections.<String>emptySet()).get(0).isFull());
        assertTrue(WatchPlanner.full(Collections.<String>emptyList()).get(0).isFull());
        assertTrue(WatchPlanner.failedOnly(Collections.<String>emptyList()).isEmpty());
        assertEquals(1, WatchPlanner.failedOnly(failed).size());
    }

    @Test
    void failuresAreReselectedByScriptOrApiTitle() throws Exception {
        String json = "{\"tests\":["
                + "{\"suite\":\"tests/ui/login.sel\",\"name\":\"doLogin\",\"title\":\"tests/ui/login.sel › doLogin\",\"status\":\"failed\"},"
                + "{\"suite\":\"tests/ui/login.sel\",\"name\":\"other\",\"title\":\"tests/ui/login.sel › other\",\"status\":\"flaky\"},"
                + "{\"suite\":\"users.xml\",\"name\":\"get (1)\",\"title\":\"users.xml › get (1)\",\"status\":\"failed\"},"
                + "{\"suite\":\"ok.sel\",\"name\":\"x\",\"title\":\"ok.sel › x\",\"status\":\"passed\"}]}";
        com.fasterxml.jackson.databind.JsonNode r = new ObjectMapper().readTree(json);
        List<String> g = WatchPlanner.failedGreps(r);
        assertEquals(2, g.size());
        assertEquals(WatchPlanner.fileGrep("login.sel"), g.get(0));
        assertTrue(Pattern.compile(g.get(1)).matcher("users.xml › get (1)").find());
        assertFalse(Pattern.compile(g.get(1)).matcher("users.xml › get (2)").find());
        // the whole API title: a longer name or another file with the same suffix is not re-selected
        String one = WatchPlanner.failedGreps(new ObjectMapper().readTree(
                "{\"tests\":[{\"suite\":\"users.xml\",\"name\":\"Get user\",\"title\":\"users.xml › Get user\",\"status\":\"failed\"}]}")).get(0);
        assertTrue(Pattern.compile(one).matcher("users.xml › Get user").find());
        assertTrue(Pattern.compile(one).matcher("users.xml › Get user @smoke").find());
        assertTrue(Pattern.compile(one).matcher("api/users.xml › Get user").find());
        assertFalse(Pattern.compile(one).matcher("users.xml › Get users").find());
        assertFalse(Pattern.compile(one).matcher("admin-users.xml › Get user").find());
        assertEquals(set("login.sel", "ok.sel", "users.xml"), WatchPlanner.knownSuites(r));
    }

    @Test
    void watchFlagParsesAndIsKeptOutOfChildRuns() {
        TestCommandArgs a = TestCommandArgs.parse(new String[] { "cfg.json", "--watch", "--grep", "x" }, new File("."));
        assertTrue(a.watch);
        assertEquals("cfg.json", a.config);
        assertFalse(TestCommandArgs.parse(new String[] { "cfg.json" }, new File(".")).watch);
        assertTrue(WatchMode.hasJsonReporter(Arrays.asList("--reporter", "junit,json")));
        assertTrue(WatchMode.hasJsonReporter(Arrays.asList("--reporter=json")));
        assertFalse(WatchMode.hasJsonReporter(Arrays.asList("--reporter", "junit")));
        assertFalse(WatchMode.hasJsonReporter(Arrays.asList("cfg.json")));
        // a user filter ORs with any focus --grep, so watch then re-runs the filtered suite only
        assertTrue(WatchMode.hasUserFilter(Arrays.asList("cfg.json", "--tag", "smoke")));
        assertTrue(WatchMode.hasUserFilter(Arrays.asList("--grep=login")));
        assertTrue(WatchMode.hasUserFilter(Arrays.asList("-t", "x")));
        assertTrue(WatchMode.hasUserFilter(Arrays.asList("--grep-invert", "x")));
        assertFalse(WatchMode.hasUserFilter(Arrays.asList("cfg.json", "--workers", "2", "--reporter", "json")));
    }
}
