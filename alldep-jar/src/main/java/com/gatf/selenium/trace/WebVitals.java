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

/**
 * Q9 — web-vitals collection script shared by the CDP and BiDi transports (run with executeAsyncScript).
 *
 * <p>LCP and layout-shift entries are not part of the performance timeline ({@code getEntriesByType} returns
 * nothing for them), so the earlier synchronous script never produced {@code lcp}. They are read through buffered
 * {@code PerformanceObserver}s, whose callbacks run on the next task; the script answers after a short delay.
 * Values are milliseconds from navigation start, except {@code cls} (unitless). This script measures the page open
 * when the trace stops; {@link #PRELOAD_JS} covers the pages left before that (per-navigation vitals).
 */
public final class WebVitals {

    private WebVitals() {}

    public static final String ASYNC_JS =
        "var done=arguments[arguments.length-1];var o={};" +
        "try{o.url=location.href;if(window.__gatfVitalsId)o.id=window.__gatfVitalsId;}catch(e){}" +
        "try{var n=performance.getEntriesByType('navigation')[0];" +
        "if(n){o.ttfb=n.responseStart;o.domContentLoaded=n.domContentLoadedEventEnd;o.load=n.loadEventEnd;o.domInteractive=n.domInteractive;}" +
        "var p=performance.getEntriesByType('paint');" +
        "for(var i=0;i<p.length;i++){if(p[i].name==='first-contentful-paint')o.fcp=p[i].startTime;if(p[i].name==='first-paint')o.fp=p[i].startTime;}" +
        "}catch(e){o.error=String(e);}" +
        "var types=(window.PerformanceObserver&&PerformanceObserver.supportedEntryTypes)||[];" +
        "try{if(types.indexOf('largest-contentful-paint')>=0){new PerformanceObserver(function(l){var e=l.getEntries();" +
        "if(e.length)o.lcp=e[e.length-1].startTime;}).observe({type:'largest-contentful-paint',buffered:true});}}catch(e){}" +
        "try{if(types.indexOf('layout-shift')>=0){o.cls=0;new PerformanceObserver(function(l){l.getEntries().forEach(function(x){" +
        "if(!x.hadRecentInput)o.cls+=x.value;});}).observe({type:'layout-shift',buffered:true});}}catch(e){}" +
        "((window.__gatfClock&&window.__gatfClock.real&&window.__gatfClock.real.setTimeout)||setTimeout).call(window,function(){done(JSON.stringify(o));},100);";   // M11: real timer

    /**
     * Per-navigation observer, installed on every new document (CDP Page.addScriptToEvaluateOnNewDocument / BiDi
     * script.addPreloadScript) and once into the current one. It keeps the page's LCP/CLS/FCP/TTFB and, when the
     * page is left ({@code pagehide}, or hidden), hands one flat entry {@code {id,url,ts,lcp,cls,...}} to the CDP
     * binding {@code __gatfVitalsSink} when present (out-of-band, survives cross-origin navigation), else appends
     * it to {@code sessionStorage['__gatf_vitals']} (same-origin only) for {@link #DRAIN_JS}. Idempotent per document.
     */
    public static final String PRELOAD_JS =
        "(function(){if(window.__gatfVitalsId)return;" +
        "var id=Date.now().toString(36)+Math.random().toString(36).slice(2,8);window.__gatfVitalsId=id;var o={id:id};" +
        "var types=(window.PerformanceObserver&&PerformanceObserver.supportedEntryTypes)||[];" +
        "try{if(types.indexOf('largest-contentful-paint')>=0){new PerformanceObserver(function(l){var e=l.getEntries();" +
        "if(e.length)o.lcp=e[e.length-1].startTime;}).observe({type:'largest-contentful-paint',buffered:true});}}catch(e){}" +
        "try{if(types.indexOf('layout-shift')>=0){o.cls=0;new PerformanceObserver(function(l){l.getEntries().forEach(function(x){" +
        "if(!x.hadRecentInput)o.cls+=x.value;});}).observe({type:'layout-shift',buffered:true});}}catch(e){}" +
        "function flush(){try{var n=performance.getEntriesByType('navigation')[0];" +
        "if(n){o.ttfb=n.responseStart;o.domContentLoaded=n.domContentLoadedEventEnd;o.load=n.loadEventEnd;}" +
        "var p=performance.getEntriesByType('paint');for(var i=0;i<p.length;i++){if(p[i].name==='first-contentful-paint')o.fcp=p[i].startTime;}" +
        "}catch(e){}o.url=location.href;o.ts=Date.now();var s=JSON.stringify(o);" +
        "if(typeof window.__gatfVitalsSink==='function'){try{window.__gatfVitalsSink(s);return;}catch(e){}}" +
        "try{var k='__gatf_vitals',a=JSON.parse(sessionStorage.getItem(k)||'[]');" +
        "a=a.filter(function(x){return x&&x.id!==id;});a.push(JSON.parse(s));if(a.length>50)a=a.slice(-50);" +
        "sessionStorage.setItem(k,JSON.stringify(a));}catch(e){}}" +
        "addEventListener('pagehide',flush,true);" +
        "addEventListener('visibilitychange',function(){if(document.visibilityState==='hidden')flush();},true);" +
        "})();";

    /** Reads and clears the sessionStorage-buffered entries (JSON array string, or null). */
    public static final String DRAIN_JS =
        "try{var k='__gatf_vitals',v=sessionStorage.getItem(k);sessionStorage.removeItem(k);return v;}catch(e){return null;}";

    /** CDP binding name the preload script reports through. */
    public static final String SINK = "__gatfVitalsSink";
}
