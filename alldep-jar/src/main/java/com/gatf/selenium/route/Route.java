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
package com.gatf.selenium.route;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** M4 — one {@code route} rule: which requests (method + URL glob) and what to do with them. Also the decision type. */
public final class Route {

    public enum Action { FULFILL, ABORT, CONTINUE }

    public final String glob;
    final Pattern regex;
    /** upper-case HTTP method, or null for any */
    public final String method;
    public final Action action;
    public int status = 200;
    public byte[] body = new byte[0];
    /** FULFILL: response headers; CONTINUE: request headers to add / override */
    public final Map<String, String> headers = new LinkedHashMap<String, String>();
    /** ABORT: a Playwright / CDP error code, e.g. {@code failed}, {@code aborted}, {@code timedout} */
    public String reason = "failed";
    /** where a HAR-replayed decision came from (report / trace detail); null for plain routes */
    public String source;

    public Route(String method, String glob, Action action) {
        this.glob = glob;
        this.regex = Glob.toRegex(glob);
        this.method = method == null || method.isEmpty() ? null : method.toUpperCase(Locale.ROOT);
        this.action = action;
    }

    public boolean matches(String reqMethod, String url) {
        if (method != null && !method.equalsIgnoreCase(reqMethod)) return false;
        return url != null && regex.matcher(url).matches();
    }

    /** A FULFILL decision with the given response (HAR replay builds these per request). */
    static Route fulfilled(String glob, int status, Map<String, String> headers, byte[] body, String source) {
        Route r = new Route(null, glob, Action.FULFILL);
        r.status = status;
        r.headers.putAll(headers);
        r.body = body;
        r.source = source;
        return r;
    }

    public String header(String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        return null;
    }

    @Override
    public String toString() {
        return (method == null ? "" : method + " ") + glob + " " + action.name().toLowerCase(Locale.ROOT)
                + (action == Action.FULFILL ? " " + status : action == Action.ABORT ? " " + reason : "");
    }
}
