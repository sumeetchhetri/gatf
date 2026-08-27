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
}
