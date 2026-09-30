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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * M4 — the {@code route} / {@code unroute} statements:
 * <pre>
 *   route [METHOD] &lt;glob&gt; fulfill &lt;status&gt; ['body' | file] [type &lt;content-type&gt;] [header 'Name: value']...
 *   route [METHOD] &lt;glob&gt; abort [reason]
 *   route [METHOD] &lt;glob&gt; continue [header 'Name: value']...
 *   route from har &lt;file&gt; [url &lt;glob&gt;] [notfound abort|fallback]
 *   unroute &lt;glob&gt; | unroute all
 * </pre>
 * A quoted fulfill body is literal text; an unquoted one is a file (resolved by the caller, relative to the script).
 * Parsed at compile time (to reject bad statements early) and again at run time, after variables are substituted.
 * Pure.
 */
public final class RouteSpec {

    private RouteSpec() {}

    public static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    /** Playwright's route.abort() error codes; each maps onto a CDP Network.ErrorReason / BiDi failRequest. */
    public static final List<String> ABORT_REASONS = List.of("aborted", "accessdenied", "addressunreachable", "blockedbyclient",
            "blockedbyresponse", "connectionaborted", "connectionclosed", "connectionfailed", "connectionrefused", "connectionreset",
            "internetdisconnected", "namenotresolved", "timedout", "failed");

    /** One parsed statement. */
    public static final class Parsed {
        public String kind;          // route | har | unroute
        public Route route;          // kind == route
        public String bodyFile;      // fulfill: unquoted body = file to read
        public String harFile;       // kind == har
        public String harGlob = "**";
        public boolean harAbortMissing;
        public String glob;          // kind == unroute (null = all)
    }

    /** @param statement the whole line, starting with {@code route} or {@code unroute} */
    public static Parsed parse(String statement) {
        List<String[]> t = tokens(statement == null ? "" : statement.trim());
        if (t.isEmpty()) throw new IllegalArgumentException("empty route statement");
        String kw = t.get(0)[0].toLowerCase(Locale.ROOT);
        Parsed p = new Parsed();
        if (kw.equals("unroute")) {
            p.kind = "unroute";
            if (t.size() > 2) throw new IllegalArgumentException("unroute takes one URL pattern (or 'all')");
            p.glob = t.size() == 1 || t.get(1)[0].equalsIgnoreCase("all") ? null : t.get(1)[0];
            return p;
        }
        if (!kw.equals("route")) throw new IllegalArgumentException("not a route statement: " + statement);
        int i = 1;
        if (t.size() > 3 && t.get(1)[0].equalsIgnoreCase("from") && t.get(2)[0].equalsIgnoreCase("har")) {
            p.kind = "har";
            p.harFile = t.get(3)[0];
            for (i = 4; i < t.size(); i++) {
                String w = t.get(i)[0].toLowerCase(Locale.ROOT);
                if (w.equals("url") && i + 1 < t.size()) { p.harGlob = t.get(++i)[0]; Glob.toRegex(p.harGlob); }
                else if (w.equals("notfound") && i + 1 < t.size()) {
                    String v = t.get(++i)[0].toLowerCase(Locale.ROOT);
                    if (!v.equals("abort") && !v.equals("fallback")) throw new IllegalArgumentException("route from har: notfound abort|fallback");
                    p.harAbortMissing = v.equals("abort");
                } else throw new IllegalArgumentException("route from har <file> [url <glob>] [notfound abort|fallback]: unexpected '" + t.get(i)[0] + "'");
            }
            return p;
        }
        p.kind = "route";
        String method = null;
        if (i < t.size() && METHODS.contains(t.get(i)[0].toUpperCase(Locale.ROOT)) && t.get(i)[1] == null && t.size() > i + 2
                && !List.of("fulfill", "abort", "continue").contains(t.get(i + 1)[0].toLowerCase(Locale.ROOT))) {
            method = t.get(i++)[0];
        }
        if (i >= t.size()) throw new IllegalArgumentException("route needs a URL pattern");
        String glob = t.get(i++)[0];
        if (i >= t.size()) throw new IllegalArgumentException("route " + glob + ": fulfill | abort | continue expected");
        String action = t.get(i++)[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "fulfill": {
                Route r = new Route(method, glob, Route.Action.FULFILL);
                if (i >= t.size()) throw new IllegalArgumentException("route " + glob + " fulfill: status expected");
                try {
                    r.status = Integer.parseInt(t.get(i++)[0]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("route " + glob + " fulfill: status must be a number");
                }
                if (r.status < 100 || r.status > 599) throw new IllegalArgumentException("route " + glob + " fulfill: status " + r.status + " out of range");
                if (i < t.size() && !isKeyword(t.get(i))) {
                    String[] b = t.get(i++);
                    if (b[1] != null) r.body = b[0].getBytes(StandardCharsets.UTF_8);
                    else p.bodyFile = b[0];
                }
                i = options(t, i, r, true);
                p.route = r;
                break;
            }
            case "abort": {
                Route r = new Route(method, glob, Route.Action.ABORT);
                if (i < t.size()) {
                    String reason = t.get(i++)[0].toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                    if (!ABORT_REASONS.contains(reason)) throw new IllegalArgumentException("route " + glob + " abort: unknown reason '" + reason + "' (" + String.join(", ", ABORT_REASONS) + ")");
                    r.reason = reason;
                }
                if (i < t.size()) throw new IllegalArgumentException("route " + glob + " abort: unexpected '" + t.get(i)[0] + "'");
                p.route = r;
                break;
            }
            case "continue": {
                Route r = new Route(method, glob, Route.Action.CONTINUE);
                i = options(t, i, r, false);
                p.route = r;
                break;
            }
            default:
                throw new IllegalArgumentException("route " + glob + ": unknown action '" + action + "' (fulfill | abort | continue)");
        }
        return p;
    }

    private static boolean isKeyword(String[] tok) {
        return tok[1] == null && (tok[0].equalsIgnoreCase("header") || tok[0].equalsIgnoreCase("type"));
    }

    // [type <ct>] [header 'Name: value' | header Name: value]...
    private static int options(List<String[]> t, int i, Route r, boolean fulfill) {
        while (i < t.size()) {
            String w = t.get(i)[0].toLowerCase(Locale.ROOT);
            if (w.equals("type") && fulfill && t.get(i)[1] == null && i + 1 < t.size()) {
                r.headers.put("Content-Type", t.get(i + 1)[0]);
                i += 2;
            } else if (w.equals("header") && t.get(i)[1] == null && i + 1 < t.size()) {
                String h = t.get(i + 1)[0];
                String name, value;
                if (t.get(i + 1)[1] == null && h.endsWith(":") && i + 2 < t.size()) {
                    name = h.substring(0, h.length() - 1);
                    value = t.get(i + 2)[0];
                    i += 3;
                } else {
                    int c = h.indexOf(':');
                    if (c <= 0) throw new IllegalArgumentException("route " + r.glob + ": header needs 'Name: value'");
                    name = h.substring(0, c);
                    value = h.substring(c + 1);
                    i += 2;
                }
                if (name.trim().isEmpty()) throw new IllegalArgumentException("route " + r.glob + ": empty header name");
                r.headers.put(name.trim(), value.trim());
            } else {
                throw new IllegalArgumentException("route " + r.glob + ": unexpected '" + t.get(i)[0] + "'"
                        + (fulfill ? " (type <content-type> | header 'Name: value')" : " (header 'Name: value')"));
            }
        }
        return i;
    }

    /**
     * Applies a statement to the session's table. {@code files} reads a file named in the statement (fulfill body,
     * HAR) relative to the script, or throws.
     */
    public static String apply(Parsed p, RouteTable table, Function<String, byte[]> files) {
        switch (p.kind) {
            case "unroute":
                return "unroute " + (p.glob == null ? "all" : p.glob) + ": " + table.remove(p.glob) + " rule(s) removed";
            case "har": {
                HarRoutes har = HarRoutes.parse(new String(files.apply(p.harFile), StandardCharsets.UTF_8), p.harFile, p.harGlob, p.harAbortMissing);
                table.add(har);
                return "route from har " + p.harFile + ": " + har.size() + " response(s)";
            }
            default: {
                Route r = p.route;
                if (p.bodyFile != null) r.body = files.apply(p.bodyFile);
                if (r.action == Route.Action.FULFILL && r.header("Content-Type") == null) {
                    r.headers.put("Content-Type", contentType(p.bodyFile, r.body));
                }
                table.add(r);
                return "route " + r;
            }
        }
    }

    /** Content-Type from the body file's extension, else sniffed (JSON-looking text → application/json). */
    static String contentType(String file, byte[] body) {
        String f = file == null ? "" : file.toLowerCase(Locale.ROOT);
        if (f.endsWith(".json")) return "application/json";
        if (f.endsWith(".html") || f.endsWith(".htm")) return "text/html; charset=utf-8";
        if (f.endsWith(".xml")) return "application/xml";
        if (f.endsWith(".js") || f.endsWith(".mjs")) return "text/javascript";
        if (f.endsWith(".css")) return "text/css";
        if (f.endsWith(".svg")) return "image/svg+xml";
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".gif")) return "image/gif";
        if (f.endsWith(".txt") || f.endsWith(".csv")) return "text/plain; charset=utf-8";
        String s = body == null ? "" : new String(body, 0, Math.min(body.length, 64), StandardCharsets.UTF_8).trim();
        if (s.startsWith("{") || s.startsWith("[")) return "application/json";
        if (s.startsWith("<")) return "text/html; charset=utf-8";
        return f.isEmpty() ? "text/plain; charset=utf-8" : "application/octet-stream";
    }

    /** Whitespace-separated tokens; '…' / "…" group (quotes removed, {@code [1]} = the quote char), \ escapes the quote. */
    static List<String[]> tokens(String s) {
        List<String[]> out = new ArrayList<String[]>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '\'' || c == '"') {
                StringBuilder b = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                for (; j < n; j++) {
                    char d = s.charAt(j);
                    if (d == '\\' && j + 1 < n && (s.charAt(j + 1) == c || s.charAt(j + 1) == '\\')) { b.append(s.charAt(++j)); continue; }
                    if (d == c) { closed = true; break; }
                    b.append(d);
                }
                if (!closed) throw new IllegalArgumentException("unterminated quote in: " + s);
                out.add(new String[] { b.toString(), String.valueOf(c) });
                i = j + 1;
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j))) j++;
                out.add(new String[] { s.substring(i, j), null });
                i = j;
            }
        }
        return out;
    }
}
