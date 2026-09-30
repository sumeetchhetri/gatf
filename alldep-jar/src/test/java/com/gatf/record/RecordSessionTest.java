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
package com.gatf.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** G7: recorder pacing — a gap > 100 ms between captured actions emits {@code sleep <ms>} before the action. */
class RecordSessionTest {

    private static Map<String, Object> act(String type, String s, String v, long t) {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("type", type);
        Map<String, Object> sel = new HashMap<String, Object>();
        sel.put("s", s);
        sel.put("v", v);
        a.put("sel", sel);
        a.put("t", Long.valueOf(t));
        return a;
    }

    @Test
    void emitsRoundedCappedSleepsOnlyAboveThreshold() {
        RecordSession r = new RecordSession("t", null);
        r.ingest(act("click", "id", "a", 1000));      // first action: no sleep
        r.ingest(act("click", "id", "b", 1080));      // 80 ms: below threshold
        r.ingest(act("click", "id", "c", 1330));      // 250 ms → sleep 300 (nearest 100)
        r.ingest(act("click", "id", "c", 1900));      // duplicate: dropped, no dangling sleep
        r.ingest(act("click", "id", "d", 60000));     // huge idle → capped
        List<String> l = r.currentLines();
        assertThat(l).containsExactly("click id@a", "click id@b", "sleep 300", "click id@c",
                "sleep " + RecordSession.SLEEP_CAP_MS, "click id@d");
    }

    @Test
    void untimedActionsEmitNoSleep() {
        RecordSession r = new RecordSession("t", null);
        Map<String, Object> a = act("click", "id", "a", 0);
        a.remove("t");
        r.ingest(a);
        r.ingest(act("click", "id", "b", 5000));
        assertThat(r.currentLines()).containsExactly("click id@a", "click id@b");
    }

    @Test
    void prefersAUniqueSemanticLocator() {
        RecordSession r = new RecordSession("t", null);
        Map<String, Object> a = act("click", "xpath", "/html/body[1]/div[1]/button[2]", 0);
        a.remove("t");
        Map<String, Object> role = new HashMap<String, Object>();
        role.put("s", "role"); role.put("role", "button"); role.put("name", "Save draft"); role.put("n", 1L);
        Map<String, Object> label = new HashMap<String, Object>();
        label.put("s", "label"); label.put("v", "Draft"); label.put("n", 2L);
        a.put("cand", java.util.Arrays.asList(label, role));
        r.ingest(a);
        Map<String, Object> ty = act("type", "id", "email", 0);
        ty.remove("t");
        ty.put("value", "me@x.test");
        Map<String, Object> tid = new HashMap<String, Object>();
        tid.put("s", "testid"); tid.put("v", "email-input"); tid.put("n", 1L);
        ty.put("cand", java.util.Arrays.asList(tid));
        r.ingest(ty);
        Map<String, Object> amb = act("click", "id", "go", 0);
        amb.remove("t");
        role.put("n", 3L);
        amb.put("cand", java.util.Arrays.asList(role));
        r.ingest(amb);
        assertThat(r.currentLines()).containsExactly("click role@button[name='Save draft']", "type \"me@x.test\" testid@email-input", "click id@go");
    }

    @Test
    void recorderUsesTheProjectTestIdAttribute() {
        assertThat(RecordSession.RECORDER_JS).contains(RecordSession.CAND_CALL);
        assertThat(new RecordSession("t", null, "chrome").recorderJs()).isEqualTo(RecordSession.RECORDER_JS);
        assertThat(new RecordSession("t", null, "chrome", " ").recorderJs()).isEqualTo(RecordSession.RECORDER_JS);
        String js = new RecordSession("t", null, "chrome", "data-qa").recorderJs();
        assertThat(js).contains("__gatfCandidates(el,\"data-qa\")").doesNotContain(RecordSession.CAND_CALL);
        assertThat(RecordSession.jsString("a\"b\\c</")).isEqualTo("\"a\\\"b\\\\c\\u003c/\"");
    }
}
