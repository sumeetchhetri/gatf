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
 * Values are milliseconds from navigation start, except {@code cls} (unitless). Only the page open when the trace
 * stops is measured.
 */
public final class WebVitals {

    private WebVitals() {}

    public static final String ASYNC_JS =
        "var done=arguments[arguments.length-1];var o={};" +
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
        "setTimeout(function(){done(JSON.stringify(o));},100);";
}
