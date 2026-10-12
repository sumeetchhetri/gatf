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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gatf.selenium.LocatorRanking;
import com.gatf.selenium.SemanticLocators;

/**
 * Δ148 pick locator: the ranked, strict locator for one picked element. The element's candidates come from
 * {@link SemanticLocators#CANDIDATES_FN} (testid / role+name / label, each with its match count) and its legacy
 * selector from {@link SemanticLocators#FALLBACK_FN} (id / name / css, only when it matches exactly one element);
 * {@link LocatorRanking#choose} picks testid → role → label → that fallback, the recorder's own order. Used for the live
 * browser ({@link RecordSession} picker) and for Trace Viewer snapshots (the frontend runs the same scripts on the
 * snapshot and posts the result to {@code /record?action=rank}). Pure.
 */
public final class PickLocator {

    private PickLocator() {}

    /** Largest {@code /record?action=rank} body accepted (bytes), and most candidates; over either → 413. */
    public static final int MAX_BODY = 64 * 1024;
    public static final int MAX_CANDIDATES = 16;

    /** Parses a request body (JSON → Map / List / String / Number / Boolean / null); throws on malformed input. */
    @FunctionalInterface
    public interface Json {
        Object read(byte[] body) throws Exception;
    }

    /** An HTTP status and its JSON body. */
    public static final class Reply {
        public final int status;
        public final Map<String, Object> body;
        Reply(int status, Map<String, Object> body) { this.status = status; this.body = body; }
    }

    /**
     * {@code POST /record?action=rank}: no session or browser involved, a pure call into the ranking. The body is
     * {@code {cand:[…], sel:{s, v, n}|null}} as the picker scripts return them. Bounded: more than {@link #MAX_BODY}
     * bytes (by Content-Length or as read) or more than {@link #MAX_CANDIDATES} candidates → 413; anything that is not
     * that shape → 400. Never throws.
     */
    public static Reply rankRequest(InputStream in, long contentLength, Json json) {
        try {
            if (contentLength > MAX_BODY) return error(413, "request body over " + MAX_BODY + " bytes");
            byte[] b = readCapped(in, MAX_BODY);
            if (b == null) return error(413, "request body over " + MAX_BODY + " bytes");
            if (b.length == 0) return error(400, "empty body: expected {cand, sel}");
            Object o;
            try {
                o = json.read(b);
            } catch (Exception | StackOverflowError e) {   // deep nesting is malformed input too, never a 500
                return error(400, "malformed JSON body");
            }
            if (!(o instanceof Map)) return error(400, "expected a JSON object {cand, sel}");
            Map<?, ?> m = (Map<?, ?>) o;
            Object cand = m.get("cand"), sel = m.get("sel");
            if (cand != null && !(cand instanceof List)) return error(400, "cand must be an array");
            if (sel != null && !(sel instanceof Map)) return error(400, "sel must be an object or null");
            List<?> cl = cand == null ? null : (List<?>) cand;
            if (cl != null && cl.size() > MAX_CANDIDATES) return error(413, "more than " + MAX_CANDIDATES + " candidates");
            if (cl != null) for (Object c : cl) if (!(c instanceof Map)) return error(400, "each candidate must be an object");
            return new Reply(200, rank(cl, (Map<?, ?>) sel));
        } catch (Exception e) {
            return error(400, "bad request");
        }
    }

    /** At most {@code cap} bytes of {@code in}, or null when it holds more. */
    static byte[] readCapped(InputStream in, int cap) throws IOException {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > cap) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Reply error(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", message);
        return new Reply(status, m);
    }

    /** The ready-to-paste line for a picked locator: the recorder's click step ({@link RecordSession} emits clicks with it). */
    public static String line(String locator) {
        return locator == null ? null : "click " + locator;
    }

    /**
     * {@code {locator, line, strict, source, candidates:[{kind, locator, n}], fallback:{locator, n}|null}}.
     * {@code locator} (and {@code line}, the ready-to-paste step) is null when nothing resolves strictly;
     * {@code candidates} then shows what each one matched. {@code source} names what was chosen: the candidate's index,
     * or -1 for the fallback (null when nothing was), so the caller can re-check that element's own count.
     */
    public static Map<String, Object> rank(List<?> candidates, Map<?, ?> fallback) {
        String fb = null;
        Map<String, Object> fbOut = null;
        if (fallback != null) {
            int n = count(fallback.get("n"));
            String loc = selector(fallback);
            if (loc != null) {
                fbOut = new LinkedHashMap<String, Object>();
                fbOut.put("locator", loc);
                fbOut.put("n", n);
                if (SemanticLocators.resolvesStrictly(n)) fb = loc;
            }
        }
        List<Map<String, Object>> cands = new ArrayList<Map<String, Object>>();
        if (candidates != null) {
            for (Object o : candidates) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> c = (Map<?, ?>) o;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("kind", String.valueOf(c.get("s")));
                m.put("locator", LocatorRanking.render(c));
                m.put("n", count(c.get("n")));
                cands.add(m);
            }
        }
        String chosen = LocatorRanking.choose(candidates, fb);
        Integer source = null;
        if (chosen != null) {
            for (int i = 0; i < cands.size() && source == null; i++) {
                Map<String, Object> c = cands.get(i);
                if (chosen.equals(c.get("locator")) && SemanticLocators.resolvesStrictly((Integer) c.get("n"))) source = i;
            }
            if (source == null) source = -1;   // LocatorRanking fell through to the (strict) fallback
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("locator", chosen);
        out.put("line", line(chosen));
        out.put("strict", chosen != null);
        out.put("source", source);
        out.put("candidates", cands);
        out.put("fallback", fbOut);
        return out;
    }

    /** {s:strategy, v:value} → seleasy selector, choosing a quote wrapper that avoids collisions (moved from RecordSession). */
    public static String selector(Map<?, ?> sel) {
        if (sel == null) return null;
        String s = str(sel.get("s"));
        String v = str(sel.get("v"));
        if (v.isEmpty()) return null;
        if (LocatorRanking.unsafe(v)) return null;   // Δ148a: '$' / Velocity directive / control or line-separator char
        if ("id".equals(s)) return "id@" + simpleOrQuoted(v);
        if ("name".equals(s)) return "name@" + simpleOrQuoted(v);
        if ("css".equals(s)) return "css@" + q(v);
        return "xpath@" + xp(v);
    }

    private static String simpleOrQuoted(String v) {
        return v.matches("[A-Za-z0-9_\\-:.]+") ? v : q(v);
    }

    private static String q(String v) {
        if (v.indexOf('"') >= 0 && v.indexOf('\'') < 0) return "'" + v + "'";
        return "\"" + v.replace("\"", "\\\"") + "\"";
    }

    private static String xp(String x) {
        String t = x.trim();
        if (t.indexOf('"') >= 0 && t.indexOf('\'') < 0) return "'" + t + "'";
        return "\"" + t.replace("\"", "\\\"") + "\"";
    }

    private static int count(Object n) {
        return SemanticLocators.matchCount(n);
    }

    /** Δ148a: whether {@code sel} names a value that {@link #selector} refuses as unsafe (not merely empty). */
    public static boolean unsafeSelector(Map<?, ?> sel) {
        if (sel == null) return false;
        String v = str(sel.get("v"));
        return !v.isEmpty() && LocatorRanking.unsafe(v);
    }

    /** Δ148a: longest fallback selector value (id / name / css path) a drained pick keeps. */
    public static final int MAX_SELECTOR = 1024;

    /**
     * Δ148a: a drained page pick reduced to what ranking reads, bounded: {@code type}, at most {@link #MAX_CANDIDATES}
     * candidates with only {s, v, role, name, n}, candidate strings cut to {@code MAX_TEXT + 1} chars and fallback strings
     * to {@code MAX_SELECTOR + 1}. An entry with a cut string gets {@code n = -1}: a cut value is a different selector, so
     * the page's count no longer applies and it can never be ranked as strict.
     */
    public static Map<String, Object> boundedPick(Map<?, ?> p) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Object type = p == null ? null : p.get("type");
        out.put("type", type instanceof String && ((String) type).length() <= 16 ? type : "");
        List<Map<String, Object>> cand = new ArrayList<Map<String, Object>>();
        Object c = p == null ? null : p.get("cand");
        if (c instanceof List) {
            for (Object o : (List<?>) c) {
                if (cand.size() >= MAX_CANDIDATES) break;
                if (o instanceof Map) cand.add(boundedEntry((Map<?, ?>) o, LocatorRanking.MAX_TEXT));
            }
        }
        out.put("cand", cand);
        Object sel = p == null ? null : p.get("sel");
        out.put("sel", sel instanceof Map ? boundedEntry((Map<?, ?>) sel, MAX_SELECTOR) : null);
        return out;
    }

    private static Map<String, Object> boundedEntry(Map<?, ?> m, int cap) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        boolean cut = false;
        for (String k : new String[] { "s", "v", "role", "name" }) {
            Object o = m.get(k);
            if (o == null) continue;
            String s = o instanceof String ? (String) o : (o instanceof Number || o instanceof Boolean ? String.valueOf(o) : "");
            if (s.length() > cap) { s = s.substring(0, cap + 1); cut = true; }
            e.put(k, s);
        }
        e.put("n", cut ? -1 : count(m.get("n")));
        return e;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * The live picker's page script; needs {@link SemanticLocators#RESOLVER_FN}, {@link SemanticLocators#CANDIDATES_FN}
     * and {@link SemanticLocators#FALLBACK_FN} defined first. {@code __gatfPickerInstall(tidAttr)} adds, on window in the
     * capture phase (before the recorder's document listeners): a hover outline (one fixed, pointer-events:none element),
     * and a click that is swallowed (not recorded, not acted on by the page) and pushes {@code {type:'pick', cand, sel}}
     * to {@code window.__gatfPicks}; Escape pushes {@code {type:'cancel'}}. Either one removes every listener and the
     * outline ({@code window.__gatfPicker.off()}), so nothing of the picker stays on the page. Keep backslashes doubled.
     */
    public static final String PICKER_FN = """
        var __gatfPickerInstall = function (tidAttr) {
          if (window.__gatfPicker) return;
          try { delete window.__gatfPicks; } catch (x) { window.__gatfPicks = undefined; }   // nothing queued before arming counts
          var doc = document, box = null, last = null;
          var BLOCK = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'dblclick', 'auxclick', 'contextmenu'];
          var deep = function (x, y) {
            var e = doc.elementFromPoint(x, y);
            for (var g = 0; e && e.shadowRoot && g < 32; g++) {
              var i = e.shadowRoot.elementFromPoint(x, y);
              if (!i || i === e) break;
              e = i;
            }
            return e;
          };
          var show = function (e) {
            if (!box) {
              box = doc.createElement('gatf-pick-outline');
              box.setAttribute('style', 'position:fixed;pointer-events:none;z-index:2147483647;box-sizing:border-box;' +
                'border:2px solid #2563eb;background:rgba(37,99,235,.12);display:none;left:0;top:0;width:0;height:0');
              (doc.documentElement || doc.body).appendChild(box);
            }
            if (!e || !e.getBoundingClientRect) { box.style.display = 'none'; return; }
            var r = e.getBoundingClientRect();
            box.style.left = r.left + 'px'; box.style.top = r.top + 'px';
            box.style.width = r.width + 'px'; box.style.height = r.height + 'px';
            box.style.display = 'block';
          };
          var swallow = function (ev) {
            ev.preventDefault(); ev.stopPropagation();
            if (ev.stopImmediatePropagation) ev.stopImmediatePropagation();
          };
          var push = function (p) { (window.__gatfPicks = window.__gatfPicks || []).push(p); };
          var off = function () {
            window.removeEventListener('mousemove', move, true);
            window.removeEventListener('click', click, true);
            window.removeEventListener('keydown', key, true);
            for (var b = 0; b < BLOCK.length; b++) window.removeEventListener(BLOCK[b], swallow, true);
            if (box && box.parentNode) box.parentNode.removeChild(box);
            box = null;
            try { delete window.__gatfPicker; } catch (x) { window.__gatfPicker = undefined; }
          };
          var move = function (ev) { last = deep(ev.clientX, ev.clientY); show(last); };
          var click = function (ev) {
            swallow(ev);
            var el = deep(ev.clientX, ev.clientY) || last;
            off();   // the outline goes first: it must not take part in the match counts
            var p = { type: 'pick', cand: [], sel: null };
            try { p.cand = __gatfCandidates(el, tidAttr); } catch (x) { }
            try { p.sel = __gatfFallback(el); } catch (x) { }
            push(p);
          };
          var key = function (ev) {
            if (ev.key !== 'Escape') return;
            swallow(ev);
            off();
            push({ type: 'cancel' });
          };
          window.addEventListener('mousemove', move, true);
          window.addEventListener('click', click, true);
          window.addEventListener('keydown', key, true);
          for (var b = 0; b < BLOCK.length; b++) window.addEventListener(BLOCK[b], swallow, true);
          window.__gatfPicker = { off: off };
        };
        """;

    /** Arms the picker in the current document (idempotent); {@code tidAttr} is a JS string literal. */
    public static String armJs(String tidAttrLiteral) {
        return "(function(){if(window.__gatfPicker)return;" + SemanticLocators.RESOLVER_FN + SemanticLocators.CANDIDATES_FN
                + SemanticLocators.FALLBACK_FN + PICKER_FN + "__gatfPickerInstall(" + tidAttrLiteral + ");})();";
    }

    /**
     * Takes the queued picks out of the page (leaves no global behind). Δ148a: only the last one, rebuilt in the page as
     * {type, cand (at most 16), sel} with each string cut one past its cap (candidates 60, fallback 1024), so a hostile
     * page cannot hand the poller an unbounded structure; {@link #boundedPick} bounds it again and voids cut entries.
     */
    public static final String DRAIN_JS =
            "var p=window.__gatfPicks;try{delete window.__gatfPicks;}catch(e){window.__gatfPicks=undefined;}"
            + "if(!p||typeof p.length!=='number'||p.length<1)return[];var x=p[p.length-1];if(!x||typeof x!=='object')return[];"
            + "var t=function(v,k){return typeof v==='string'?v.slice(0,k+1):null;};"
            + "var e=function(o,k){if(!o||typeof o!=='object')return null;return{s:t(o.s,k),v:t(o.v,k),role:t(o.role,k),name:t(o.name,k),n:typeof o.n==='number'?o.n:-1};};"
            + "var c=[];if(x.cand&&typeof x.cand.length==='number')for(var i=0;i<x.cand.length&&c.length<16;i++){var y=e(x.cand[i],60);if(y)c.push(y);}"
            + "return[{type:t(x.type,16),cand:c,sel:e(x.sel,1024)}];";

    /** Removes the picker (listeners, outline, globals) if it is there; a no-op otherwise. */
    public static final String OFF_JS =
            "try{if(window.__gatfPicker&&window.__gatfPicker.off)window.__gatfPicker.off();}catch(e){}"
            + "try{delete window.__gatfPicker;}catch(e){}try{delete window.__gatfPicks;}catch(e){}";
}
