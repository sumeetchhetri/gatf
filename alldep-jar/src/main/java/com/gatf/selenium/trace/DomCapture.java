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
package com.gatf.selenium.trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H2 (Δ146): the protocol-neutral half of a styled DOM snapshot. Pure: the transports hand in a script runner and,
 * on CDP, the raw {@code DOMSnapshot.captureSnapshot} map; everything else (the in-page script, stale-target retry,
 * parsing, serialization) is here and unit-testable without a browser.
 *
 * <p>{@link #META_JS} runs in the page with {@code (targets, marks, serialize, maxNodes, maxChars, maxCss)} and
 * returns {@code {url,title,vw,vh,sx,sy,styles[],targets[],html?,trunc}}:
 * <ul>
 *  <li>{@code styles}: linked sheets with their CSSOM text ({@code text:null} when cross-origin), adopted sheets,
 *      and {@code <style>} elements filled through {@code insertRule} (CSS-in-JS: empty text content);
 *      {@code @import}ed sheets are inlined;</li>
 *  <li>{@code targets}: per target, {@code {path, rect}}; the path is the element-child index chain from
 *      {@code documentElement} (null inside a shadow root or another document);</li>
 *  <li>{@code html} (only when {@code serialize}): the BiDi/any-driver serialization, same rules as
 *      {@link DomSnapshotSerializer}.</li>
 * </ul>
 * The script never mutates the page.
 */
public final class DomCapture {

    private DomCapture() {}

    /** Runs a script with arguments in the page (JavascriptExecutor.executeScript, BiDi script.callFunction …). */
    @FunctionalInterface
    public interface ScriptRunner { Object run(Object... args) throws Exception; }

    /** Size caps passed into the page. */
    public static final class Caps {
        public final int maxNodes, maxChars, maxCss;
        public Caps(int maxNodes, int maxChars, int maxCss) { this.maxNodes = maxNodes; this.maxChars = maxChars; this.maxCss = maxCss; }
        public static Caps of(TraceConfig c) {
            return c == null ? new Caps(DomSnapshotSerializer.DEFAULT_MAX_NODES, DomSnapshotSerializer.DEFAULT_MAX_CHARS, 2 * 1024 * 1024)
                    : new Caps(c.getSnapshotMaxNodes(), c.getSnapshotMaxChars(), c.getSnapshotMaxCssChars());
        }
    }

    public static final String META_JS =
        "return (function(targets, marks, serialize, maxNodes, maxChars, maxCss){\n"
      + "var d=document, out={url:location.href,title:d.title,vw:innerWidth,vh:innerHeight,sx:Math.round(scrollX||0),sy:Math.round(scrollY||0),styles:[],targets:[],trunc:false};\n"
      + "targets=targets||[]; marks=marks||[];\n"
      + "function rulesText(sheet,depth){var rules;try{rules=sheet.cssRules;}catch(e){return null;} if(!rules)return null; var parts=[],n=0;\n"
      + " for(var i=0;i<rules.length;i++){var r=rules[i];\n"
      + "  if(r.type===3&&depth<4){var t=null;try{t=r.styleSheet?rulesText(r.styleSheet,depth+1):null;}catch(e){} if(t!=null){var m=r.media&&r.media.mediaText;parts.push(m?'@media '+m+'{'+t+'}':t);n+=t.length;continue;}}\n"
      + "  var c=r.cssText; parts.push(c); n+=c.length; if(n>maxCss){out.trunc=true;break;}}\n"
      + " return parts.join('\\n');}\n"
      + "var styleEls=d.querySelectorAll('style');\n"
      + "for(var i=0;i<d.styleSheets.length;i++){var s=d.styleSheets[i],own=s.ownerNode; if(!own)continue; var tag=(own.localName||'').toLowerCase();\n"
      + " if(tag==='link'){out.styles.push({owner:'link',href:s.href,media:s.media?s.media.mediaText:'',text:rulesText(s,0),index:-1});}\n"
      + " else if(tag==='style'&&!(own.textContent||'').trim()){var t=rulesText(s,0); if(t)out.styles.push({owner:'style',href:location.href,media:s.media?s.media.mediaText:'',text:t,index:Array.prototype.indexOf.call(styleEls,own)});}}\n"
      + "var ad=d.adoptedStyleSheets||[]; for(var i=0;i<ad.length;i++){var t=rulesText(ad[i],0); if(t)out.styles.push({owner:'adopted',href:location.href,media:'',text:t,index:-1});}\n"
      + "function path(el){if(!el||el.nodeType!==1||!el.isConnected||el.ownerDocument!==d)return null; var p=[],root=d.documentElement;\n"
      + " while(el&&el!==root){var par=el.parentNode; if(!par||par.nodeType!==1)return null; p.unshift(Array.prototype.indexOf.call(par.children,el)); el=par;}\n"
      + " return el===root?p:null;}\n"
      + "function rect(el){try{if(!el||!el.isConnected)return null; var r=el.getBoundingClientRect(); return [r.x,r.y,r.width,r.height];}catch(e){return null;}}\n"
      + "for(var i=0;i<targets.length;i++){var t=targets[i]; out.targets.push(t?{path:path(t),rect:rect(t)}:null);}\n"
      + "if(!serialize)return out;\n"
      + "var buf=[],len=0,nodes=0,stop=false,HTML='http://www.w3.org/1999/xhtml';\n"
      + "var VOID={area:1,base:1,br:1,col:1,embed:1,hr:1,img:1,input:1,link:1,meta:1,param:1,source:1,track:1,wbr:1};\n"
      + "var URLA={href:1,src:1,action:1,formaction:1,'xlink:href':1,data:1,poster:1};\n"
      + "var mk=new Map(); for(var i=0;i<targets.length;i++){var t=targets[i]; if(t&&marks[i]!=null){var v=mk.get(t); var s=String(marks[i]); mk.set(t,v?(v.split(' ').indexOf(s)<0?v+' '+s:v):s);}}\n"
      + "function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}\n"
      + "function escA(s){return String(s).replace(/&/g,'&amp;').replace(/\"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}\n"
      + "function mask(v){return new Array(Math.min(32,v.length)+1).join('\\u2022');}\n"
      + "function put(b,s){b.push(s);len+=s.length;}\n"
      + "function ser(node,b,depth){ if(stop)return; if(++nodes>maxNodes||len>maxChars){stop=true;out.trunc=true;return;}\n"
      + " var t=node.nodeType;\n"
      + " if(t===10){put(b,'<!DOCTYPE '+(node.name||'html')+'>');return;}\n"
      + " if(t===3){var p=node.parentNode,pn=p&&p.localName; if(pn==='style')put(b,node.data.split('</').join('<\\\\/')); else if(pn==='textarea')return; else put(b,esc(node.data));return;}\n"
      + " if(t===8){put(b,'<!--'+node.data.split('--').join('- -')+'-->');return;}\n"
      + " if(t===9||t===11){for(var c=node.firstChild;c;c=c.nextSibling)ser(c,b,depth);return;}\n"
      + " if(t!==1)return;\n"
      + " var tag=node.namespaceURI===HTML?node.localName:node.nodeName; if(!/^[A-Za-z][A-Za-z0-9._:-]*$/.test(tag))tag='span';\n"
      + " if(tag==='script'||tag==='noscript'||tag==='base')return;\n"
      + " if(tag==='meta'){var he=(node.getAttribute('http-equiv')||'').trim().toLowerCase(); if(he==='refresh'||he.indexOf('content-security-policy')===0)return;}\n"
      + " var isIn=tag==='input',isOpt=tag==='option',isFr=tag==='iframe'||tag==='frame',cur=(tag==='img'||tag==='video')&&node.currentSrc;\n"
      + " var s='<'+tag;\n"
      + " for(var i=0;i<node.attributes.length;i++){var a=node.attributes[i],n=a.name,ln=n.toLowerCase();\n"
      + "  if(ln.indexOf('on')===0||ln==='data-gatf-target')continue; if(!/^[^\\s\"'>\\/=<]+$/.test(n))continue;\n"
      + "  if(isIn&&(ln==='value'||ln==='checked'))continue; if(isOpt&&ln==='selected')continue;\n"
      + "  if(cur&&(ln==='src'||ln==='srcset'||ln==='sizes'))continue; if(isFr&&(ln==='src'||ln==='srcdoc'))continue;\n"
      + "  if(URLA[ln]&&/^\\s*javascript:/i.test(a.value))continue;\n"
      + "  s+=' '+n+'=\"'+escA(a.value)+'\"';}\n"
      + " if(isIn){var ty=(node.type||'').toLowerCase(); if(ty!=='file'&&node.value!=null&&node.value!=='')s+=' value=\"'+escA(ty==='password'?mask(node.value):node.value)+'\"'; if((ty==='checkbox'||ty==='radio')&&node.checked)s+=' checked';}\n"
      + " if(isOpt&&node.selected)s+=' selected';\n"
      + " if(cur)s+=' src=\"'+escA(node.currentSrc)+'\"';\n"
      + " if(mk.has(node))s+=' data-gatf-target=\"'+escA(mk.get(node))+'\"';\n"
      + " if(isFr&&depth<3){var cd=null;try{cd=node.contentDocument;}catch(e){} if(cd&&cd.documentElement){var ib=[];ser(cd,ib,depth+1); s+=' srcdoc=\"'+escA(ib.join(''))+'\"';}}\n"
      + " put(b,s+'>'); if(VOID[tag])return;\n"
      + " if(tag==='textarea')put(b,esc(node.value||''));\n"
      + " var sr=node.shadowRoot; if(sr){put(b,'<template shadowrootmode=\"open\">'); for(var c=sr.firstChild;c;c=c.nextSibling)ser(c,b,depth); put(b,'</template>');}\n"
      + " var kids=(tag==='template'&&node.content)?node.content:node; for(var c=kids.firstChild;c;c=c.nextSibling)ser(c,b,depth);\n"
      + " put(b,'</'+tag+'>');}\n"
      + "ser(d,buf,0); out.html=buf.join(''); return out;\n"
      + "})(arguments[0],arguments[1],arguments[2],arguments[3],arguments[4],arguments[5]);";

    /** CDP {@code DOMSnapshot.captureSnapshot} parameters: no computed styles or layout (the HTML + CSS carry it). */
    public static Map<String, Object> cdpParams() {
        Map<String, Object> p = new HashMap<>();
        p.put("computedStyles", Collections.emptyList());
        p.put("includeDOMRects", Boolean.FALSE);
        p.put("includePaintOrder", Boolean.FALSE);
        return p;
    }

    /**
     * Run {@link #META_JS}. A stale target makes the driver reject the whole call, so retry without the earlier
     * targets (the "after" of the previous action), then without any. Returns null when the page refused the script.
     */
    public static Object runMeta(ScriptRunner runner, List<Object> targets, List<String> marks, boolean serialize, Caps caps) {
        List<Object> t = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
        for (int attempt = 0; attempt <= t.size(); attempt++) {
            try {
                return runner.run(t, marks == null ? Collections.emptyList() : marks, serialize, caps.maxNodes, caps.maxChars, caps.maxCss);
            } catch (Exception e) {
                // null out the earliest still-set target and try again; the last attempt has none
                int i = 0;
                while (i < t.size() && t.get(i) == null) i++;
                if (i >= t.size()) return null;
                t.set(i, null);
            }
        }
        return null;
    }

    /** BiDi / any script-capable driver: {@link #META_JS} with serialize=true. */
    public static RichSnapshot captureByScript(ScriptRunner runner, List<Object> targets, List<String> marks, Caps caps) {
        Object meta = runMeta(runner, targets, marks, true, caps);
        RichSnapshot r = fromMeta(meta, targets == null ? 0 : targets.size());
        if (r == null) return null;
        r.source = "script";
        Object html = ((Map<?, ?>) meta).get("html");
        if (!(html instanceof String)) return null;
        r.html = (String) html;
        return r;
    }

    /** CDP: the metadata/targets from {@link #META_JS} (serialize=false) + the HTML from the DOMSnapshot result. */
    @SuppressWarnings("unchecked")
    public static RichSnapshot fromCdp(Map<String, Object> domSnapshot, Object meta, List<String> marks, int targetCount, Caps caps) {
        RichSnapshot r = fromMeta(meta, targetCount);
        if (r == null) r = new RichSnapshot();
        List<DomSnapshotSerializer.Mark> mk = new ArrayList<>();
        if (meta instanceof Map) {
            Object ts = ((Map<String, Object>) meta).get("targets");
            if (ts instanceof List) {
                List<Object> tl = (List<Object>) ts;
                for (int i = 0; i < tl.size() && marks != null && i < marks.size(); i++) {
                    if (!(tl.get(i) instanceof Map) || marks.get(i) == null) continue;
                    int[] p = intArray(((Map<String, Object>) tl.get(i)).get("path"));
                    if (p != null) mk.add(new DomSnapshotSerializer.Mark(p, marks.get(i)));
                }
            }
        }
        DomSnapshotSerializer.Result s = DomSnapshotSerializer.serialize(domSnapshot, mk, caps.maxNodes, caps.maxChars);
        if (s == null || s.html == null) return null;
        r.source = "cdp";
        r.html = s.html;
        r.truncated |= s.truncated;
        if (r.url == null) r.url = s.url;
        if (r.title == null) r.title = s.title;
        if (meta == null) { r.scrollX = s.scrollX; r.scrollY = s.scrollY; }
        return r;
    }

    /** Parse {@link #META_JS}'s result (everything but the html). Null when the script did not run. */
    @SuppressWarnings("unchecked")
    static RichSnapshot fromMeta(Object meta, int targetCount) {
        if (!(meta instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) meta;
        RichSnapshot r = new RichSnapshot();
        r.url = str(m.get("url"));
        r.title = str(m.get("title"));
        r.viewportW = DomSnapshotSerializer.num(m.get("vw"));
        r.viewportH = DomSnapshotSerializer.num(m.get("vh"));
        r.scrollX = DomSnapshotSerializer.num(m.get("sx"));
        r.scrollY = DomSnapshotSerializer.num(m.get("sy"));
        r.truncated = Boolean.TRUE.equals(m.get("trunc"));
        if (m.get("styles") instanceof List) {
            for (Object o : (List<Object>) m.get("styles")) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> sm = (Map<String, Object>) o;
                RichSnapshot.Style st = new RichSnapshot.Style();
                st.owner = str(sm.get("owner"));
                st.href = str(sm.get("href"));
                st.media = str(sm.get("media"));
                st.text = str(sm.get("text"));
                st.index = sm.get("index") instanceof Number ? ((Number) sm.get("index")).intValue() : -1;
                if (st.owner != null) r.styles.add(st);
            }
        }
        List<Object> ts = m.get("targets") instanceof List ? (List<Object>) m.get("targets") : Collections.emptyList();
        for (int i = 0; i < targetCount; i++) {
            double[] rect = null;
            if (i < ts.size() && ts.get(i) instanceof Map) {
                Object rr = ((Map<String, Object>) ts.get(i)).get("rect");
                if (rr instanceof List && ((List<Object>) rr).size() == 4) {
                    rect = new double[4];
                    for (int k = 0; k < 4; k++) {
                        Object v = ((List<Object>) rr).get(k);
                        rect[k] = v instanceof Number ? ((Number) v).doubleValue() : 0;
                    }
                }
            }
            r.targetRects.add(rect);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    static int[] intArray(Object o) {
        if (!(o instanceof List)) return null;
        List<Object> l = (List<Object>) o;
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            if (!(l.get(i) instanceof Number)) return null;
            a[i] = ((Number) l.get(i)).intValue();
        }
        return a;
    }

    static String str(Object o) { return o == null ? null : String.valueOf(o); }

    /** The marks list for a capture: one action id per target, null where the target is absent. */
    public static List<String> marks(int... actionIds) {
        String[] s = new String[actionIds.length];
        for (int i = 0; i < s.length; i++) s[i] = String.valueOf(actionIds[i]);
        return Arrays.asList(s);
    }
}
