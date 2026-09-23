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
package com.gatf.imports;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Q2 — cURL command lines ↔ {@link ApiRequest}. Pure (no GATF runtime dependencies).
 *
 * <p>{@link #parseAll} reads one or more {@code curl …} commands as a shell would tokenize them: bash quoting
 * ({@code '…'}, {@code "…"}, {@code $'…'}, backslash escapes and line continuations) as produced by browser dev tools
 * "Copy as cURL (bash)" and by GATF's own report, plus Windows cmd {@code ^} escaping ("Copy as cURL (cmd)") and
 * PowerShell back-tick continuations. Commands are separated by newlines, {@code ;}, {@code &&}, {@code ||} or
 * {@code |}; anything that is not a curl command is reported, not guessed at.
 *
 * <p>{@link #format} writes the inverse: a single-line bash command that {@link #parseAll} reads back to the same
 * request. The executor uses it for the report's cURL line, so "copy from report → import" round-trips.
 */
public final class CurlCommand {

    private CurlCommand() {}

    /** One parsed command: the request (null when unusable) and the constructs that could not be carried over. */
    public static final class Parsed {
        public ApiRequest request;
        public final List<ImportResult.Unsupported> issues = new ArrayList<ImportResult.Unsupported>();
        public boolean insecure;

        void issue(int line, String construct, String reason) {
            issues.add(new ImportResult.Unsupported(line, construct, reason));
        }
    }

    static final class Tok {
        final String text;
        final int line;

        Tok(String text, int line) {
            this.text = text;
            this.line = line;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    // ------------------------------------------------------------------ tokenizer

    private static final Pattern CMD_STYLE = Pattern.compile("(?s).*(\\^\"|[ \\t]\\^\\r?\\n).*");

    /** Splits {@code text} into commands, each a list of shell words. */
    static List<List<Tok>> tokenize(String text) {
        String s = text == null ? "" : text.replace("\r\n", "\n");
        if (CMD_STYLE.matcher(s).matches()) {
            // Windows cmd: '^' + newline continues the line, '^x' is a literal x (and ^^ a literal ^)
            s = s.replaceAll("\\^\\n", " ");
            StringBuilder b = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '^' && i + 1 < s.length()) {
                    b.append(s.charAt(++i));
                } else {
                    b.append(c);
                }
            }
            s = b.toString();
        }
        s = s.replaceAll("`[ \\t]*\\n", " ");    // PowerShell continuation

        List<List<Tok>> cmds = new ArrayList<List<Tok>>();
        List<Tok> cur = new ArrayList<Tok>();
        StringBuilder w = null;
        int line = 1, wordLine = 1;
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n' || c == ';' || c == '|' || (c == '&' && i + 1 < n && s.charAt(i + 1) == '&')) {
                if (w != null) { cur.add(new Tok(w.toString(), wordLine)); w = null; }
                if (!cur.isEmpty()) { cmds.add(cur); cur = new ArrayList<Tok>(); }
                if (c == '\n') line++;
                i += (c == '&' || (c == '|' && i + 1 < n && s.charAt(i + 1) == '|')) ? 2 : 1;
                continue;
            }
            if (c == ' ' || c == '\t') {
                if (w != null) { cur.add(new Tok(w.toString(), wordLine)); w = null; }
                i++;
                continue;
            }
            if (c == '#' && w == null) {                       // comment to end of line
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if (w == null) { w = new StringBuilder(); wordLine = line; }
            if (c == '\\') {
                if (i + 1 < n && s.charAt(i + 1) == '\n') {     // continuation
                    line++;
                    i += 2;
                    if (w.length() == 0) w = null;
                    continue;
                }
                if (i + 1 < n) w.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '\'') {
                int end = s.indexOf('\'', i + 1);
                if (end < 0) end = n;
                String lit = s.substring(i + 1, end);
                line += count(lit, '\n');
                w.append(lit);
                i = end + 1;
                continue;
            }
            if (c == '$' && i + 1 < n && s.charAt(i + 1) == '\'') {
                i = ansiC(s, i + 2, w);
                continue;
            }
            if (c == '"') {
                i++;
                while (i < n && s.charAt(i) != '"') {
                    char d = s.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        char e = s.charAt(i + 1);
                        if (e == '"' || e == '\\' || e == '$' || e == '`') { w.append(e); i += 2; continue; }
                        if (e == '\n') { line++; i += 2; continue; }
                    }
                    if (d == '\n') line++;
                    w.append(d);
                    i++;
                }
                i++;
                continue;
            }
            w.append(c);
            i++;
        }
        if (w != null) cur.add(new Tok(w.toString(), wordLine));
        if (!cur.isEmpty()) cmds.add(cur);
        return cmds;
    }

    private static int count(String s, char c) {
        int k = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) k++;
        return k;
    }

    /** bash ANSI-C quoting {@code $'…'}; returns the index after the closing quote. */
    private static int ansiC(String s, int i, StringBuilder w) {
        int n = s.length();
        while (i < n && s.charAt(i) != '\'') {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= n) { w.append(c); i++; continue; }
            char e = s.charAt(i + 1);
            i += 2;
            switch (e) {
                case 'n': w.append('\n'); break;
                case 'r': w.append('\r'); break;
                case 't': w.append('\t'); break;
                case 'a': w.append('\u0007'); break;
                case 'b': w.append('\b'); break;
                case 'e': case 'E': w.append('\u001b'); break;
                case 'f': w.append('\f'); break;
                case 'v': w.append('\u000b'); break;
                case '0': w.append('\0'); break;
                case 'x': i = hex(s, i, 2, w); break;
                case 'u': i = hex(s, i, 4, w); break;
                case 'U': i = hex(s, i, 8, w); break;
                default: w.append(e);                      // \\ \' \" \? and unknown escapes
            }
        }
        return i + 1;
    }

    private static int hex(String s, int i, int max, StringBuilder w) {
        int j = i;
        while (j < s.length() && j - i < max && Character.digit(s.charAt(j), 16) >= 0) j++;
        if (j == i) return i;
        w.appendCodePoint(Integer.parseInt(s.substring(i, j), 16));
        return j;
    }

    // ------------------------------------------------------------------ parser

    /** short options that take a value */
    private static final String SHORT_VALUE = "XHdFubAeowDcmxTErKyYzC";
    /** short boolean options */
    private static final String SHORT_FLAG = "sSLkviIGfONq46Z#jlnMRp0123";

    private static final Set<String> LONG_VALUE_IGNORED = Set.of("output", "write-out", "dump-header", "cookie-jar",
            "max-time", "connect-timeout", "retry", "retry-delay", "retry-max-time", "cacert", "capath", "cert", "key",
            "cert-type", "key-type", "pass", "resolve", "connect-to", "max-redirs", "limit-rate", "max-filesize",
            "interface", "dns-servers", "proxy-user", "trace", "trace-ascii", "stderr", "range", "speed-limit",
            "speed-time", "local-port", "unix-socket", "abstract-unix-socket", "ciphers", "tls-max", "expect100-timeout",
            "keepalive-time", "happy-eyeballs-timeout-ms", "config", "proxy", "noproxy", "preproxy", "time-cond",
            "upload-file", "request-target", "aws-sigv4", "delegation", "ftp-port", "quote", "mail-from", "mail-rcpt",
            "socks5", "socks5-hostname", "socks4", "socks4a", "hostpubmd5", "pinnedpublickey", "variable", "url-query",
            "service-name", "sasl-authzid", "login-options", "netrc-file", "output-dir", "etag-save", "etag-compare",
            "trace-config", "proto", "proto-redir", "proto-default", "doh-url", "ech", "ip-tos", "vlan-priority",
            "retry-all-errors-max", "parallel-max", "rate");

    /** options whose effect GATF does not reproduce per request — reported so the user knows */
    private static final Set<String> NOTED = Set.of("proxy", "x", "upload-file", "T", "config", "K", "range", "r",
            "cert", "E", "key", "cacert", "resolve", "connect-to", "aws-sigv4", "url-query", "variable", "time-cond", "z");

    /** Parses every curl command in {@code text}. Non-curl commands yield a {@link Parsed} with a null request. */
    public static List<Parsed> parseAll(String text) {
        List<Parsed> out = new ArrayList<Parsed>();
        for (List<Tok> cmd : tokenize(text)) {
            String first = cmd.get(0).text;
            if (first.equals("$") && cmd.size() > 1) {             // pasted shell prompt
                cmd = cmd.subList(1, cmd.size());
                first = cmd.get(0).text;
            }
            String prog = first.replace('\\', '/');
            prog = prog.substring(prog.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (!prog.equals("curl") && !prog.equals("curl.exe")) {
                Parsed p = new Parsed();
                p.issue(cmd.get(0).line, abbreviate(joinWords(cmd)), "not a curl command — skipped");
                out.add(p);
                continue;
            }
            out.add(parse(cmd));
        }
        return out;
    }

    /** Parses exactly one curl command; throws IllegalArgumentException when {@code text} holds none. */
    public static Parsed parseOne(String text) {
        for (Parsed p : parseAll(text)) {
            if (p.request != null) return p;
        }
        throw new IllegalArgumentException("no curl command with a URL found");
    }

    static Parsed parse(List<Tok> cmd) {
        Parsed p = new Parsed();
        ApiRequest r = new ApiRequest();
        r.line = cmd.get(0).line;
        String method = null, url = null;
        List<String> data = new ArrayList<String>();
        String json = null;
        boolean get = false, head = false, anyForm = false;

        for (int i = 1; i < cmd.size(); i++) {
            Tok t = cmd.get(i);
            String a = t.text;
            String opt, val = null;
            if (a.startsWith("--") && a.length() > 2) {
                int eq = a.indexOf('=');
                opt = eq > 0 ? a.substring(2, eq) : a.substring(2);
                if (eq > 0) val = a.substring(eq + 1);
                if (val == null && takesLongValue(opt)) {
                    if (i + 1 >= cmd.size()) { p.issue(t.line, a, "missing value"); break; }
                    val = cmd.get(++i).text;
                }
            } else if (a.startsWith("-") && a.length() > 1) {
                // short options, possibly bundled: -sSL, -XPOST, -HAccept:x
                opt = null;
                for (int k = 1; k < a.length(); k++) {
                    char c = a.charAt(k);
                    if (SHORT_VALUE.indexOf(c) >= 0) {
                        opt = String.valueOf(c);
                        if (k + 1 < a.length()) val = a.substring(k + 1);
                        else if (i + 1 < cmd.size()) val = cmd.get(++i).text;
                        else { p.issue(t.line, a, "missing value"); opt = ""; }
                        break;
                    }
                    if (c == 'G') get = true;
                    else if (c == 'I') head = true;
                    else if (c == 'k') p.insecure = true;
                    else if (SHORT_FLAG.indexOf(c) < 0) p.issue(t.line, "-" + c, "unknown curl option — ignored");
                }
                if (opt == null || opt.isEmpty()) continue;
            } else {
                if (url == null) url = a;
                else p.issue(t.line, a, "extra URL — only the first URL of a command is imported");
                continue;
            }

            switch (opt) {
                case "X": case "request": method = val; break;
                case "url": if (url == null) url = val; else p.issue(t.line, "--url " + val, "extra URL — ignored"); break;
                case "H": case "header": header(r, val, p, t.line); break;
                case "d": case "data": case "data-ascii":
                    if (val.startsWith("@")) fileBody(r, val.substring(1), p, t.line, "--data");
                    else data.add(val);
                    break;
                case "data-raw": data.add(val); break;
                case "data-binary":
                    if (val.startsWith("@")) fileBody(r, val.substring(1), p, t.line, "--data-binary");
                    else data.add(val);
                    break;
                case "data-urlencode": {
                    String enc = urlencodeArg(val);
                    if (enc == null) p.issue(t.line, "--data-urlencode " + val, "file form (@file) is not supported — add the value to the body");
                    else data.add(enc);
                    break;
                }
                case "json":
                    if (val.startsWith("@")) { fileBody(r, val.substring(1), p, t.line, "--json"); json = json == null ? "" : json; }
                    else json = json == null ? val : json + val;
                    break;
                case "F": case "form": anyForm |= form(r, val, false, p, t.line); break;
                case "form-string": anyForm |= form(r, val, true, p, t.line); break;
                case "u": case "user":
                    if (val.indexOf(':') < 0) p.issue(t.line, "--user " + val, "no password given (curl would prompt) — add an Authorization header");
                    else r.putHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8)));
                    break;
                case "oauth2-bearer": r.putHeader("Authorization", "Bearer " + val); break;
                case "b": case "cookie":
                    if (val.indexOf('=') < 0) p.issue(t.line, "--cookie " + val, "cookie file is not read — add a Cookie header");
                    else r.appendHeader("Cookie", val, "; ");
                    break;
                case "A": case "user-agent": r.putHeader("User-Agent", val); break;
                case "e": case "referer": r.putHeader("Referer", val.replaceAll(";auto$", "")); break;
                case "G": case "get": get = true; break;
                case "I": case "head": head = true; break;
                case "k": case "insecure": p.insecure = true; break;
                default:
                    if (NOTED.contains(opt)) {
                        p.issue(t.line, (opt.length() == 1 ? "-" : "--") + opt + (val == null ? "" : " " + val),
                                "not reproduced per request by GATF — configure it in gatf-config if needed");
                    } else if (!LONG_VALUE_IGNORED.contains(opt) && !isKnownFlag(opt) && SHORT_VALUE.indexOf(opt) < 0) {
                        p.issue(t.line, "--" + opt, "unknown curl option — ignored");
                    }
            }
        }

        if (url == null) {
            p.issue(r.line, "curl", "no URL — skipped");
            return p;
        }
        if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*") && !url.startsWith("${") && !url.startsWith("{{")) url = "http://" + url;
        url = ApiRequest.gatfVars(url);

        String body = null;
        if (!data.isEmpty()) body = String.join("&", data);
        if (json != null) {
            body = body == null ? json : body + json;
            if (r.header("Content-Type") == null) r.putHeader("Content-Type", "application/json");
            if (r.header("Accept") == null) r.putHeader("Accept", "application/json");
        }
        if (get && body != null) {
            url = url + (url.indexOf('?') >= 0 ? "&" : "?") + body;
            body = null;
        }
        r.body = body == null ? null : ApiRequest.gatfVars(body);
        for (java.util.Map.Entry<String, String> e : r.headers.entrySet()) e.setValue(ApiRequest.gatfVars(e.getValue()));

        if (method != null) r.method = method.toUpperCase(Locale.ROOT);
        else if (head) r.method = "HEAD";
        else if (get) r.method = "GET";
        else if (r.hasBody() || anyForm) r.method = "POST";
        else r.method = "GET";

        r.setFullUrl(url);
        r.normalizeContentType("application/x-www-form-urlencoded");   // curl's default for -d
        if ("GET".equals(r.method) && r.hasBody()) {
            p.issue(r.line, "-X GET with a body", "GATF does not send a body on GET — use -G to move the data to the query");
        }
        p.request = r;
        return p;
    }

    private static boolean takesLongValue(String opt) {
        switch (opt) {
            case "request": case "url": case "header": case "data": case "data-ascii": case "data-raw": case "data-binary":
            case "data-urlencode": case "json": case "form": case "form-string": case "user": case "oauth2-bearer":
            case "cookie": case "user-agent": case "referer":
                return true;
            default:
                return LONG_VALUE_IGNORED.contains(opt);
        }
    }

    private static final Set<String> KNOWN_FLAGS = Set.of("silent", "show-error", "location", "location-trusted",
            "verbose", "include", "fail", "fail-with-body", "compressed", "compressed-ssh", "http1.0", "http1.1", "http2",
            "http2-prior-knowledge", "http3", "http3-only", "globoff", "no-buffer", "no-keepalive", "no-sessionid",
            "progress-bar", "no-progress-meter", "remote-name", "remote-name-all", "remote-header-name", "junk-session-cookies",
            "ipv4", "ipv6", "tlsv1", "tlsv1.0", "tlsv1.1", "tlsv1.2", "tlsv1.3", "sslv2", "sslv3", "ssl", "ssl-reqd",
            "path-as-is", "raw", "tcp-nodelay", "tcp-fastopen", "digest", "basic", "ntlm", "negotiate", "anyauth",
            "netrc", "netrc-optional", "create-dirs", "disable", "disable-eprt", "disable-epsv", "styled-output",
            "retry-connrefused", "retry-all-errors", "parallel", "parallel-immediate", "list-only", "append", "crlf",
            "ignore-content-length", "proxy-insecure", "doh-insecure", "false-start", "suppress-connect-headers",
            "manual", "help", "version", "post301", "post302", "post303", "xattr", "skip-existing", "remove-on-error",
            "no-clobber", "clobber", "insecure", "get", "head");

    private static boolean isKnownFlag(String opt) {
        return KNOWN_FLAGS.contains(opt) || SHORT_FLAG.indexOf(opt) >= 0 && opt.length() == 1;
    }

    private static void header(ApiRequest r, String h, Parsed p, int line) {
        if (h.startsWith("@")) {
            p.issue(line, "-H " + h, "header file is not read — add the headers inline");
            return;
        }
        int colon = h.indexOf(':');
        if (colon < 0) {
            if (h.endsWith(";") && h.length() > 1) r.putHeader(h.substring(0, h.length() - 1).trim(), "");   // "X;" = empty
            else p.issue(line, "-H " + h, "header without ':' — ignored");
            return;
        }
        String name = h.substring(0, colon).trim(), value = h.substring(colon + 1).trim();
        if (name.isEmpty()) return;
        if (value.isEmpty()) { r.removeHeader(name); return; }                   // "X:" removes the header in curl
        if (name.equalsIgnoreCase("Cookie")) r.appendHeader("Cookie", value, "; ");
        else r.putHeader(name, value);
    }

    private static void fileBody(ApiRequest r, String file, Parsed p, int line, String opt) {
        if (file.equals("-")) {
            p.issue(line, opt + " @-", "body from stdin — paste the body inline");
            return;
        }
        if (r.contentFile != null) p.issue(line, opt + " @" + file, "more than one body file — only the first is used");
        else r.contentFile = file;
    }

    /** curl --data-urlencode forms: content, =content, name=content (the @file forms return null). */
    static String urlencodeArg(String v) {
        int eq = v.indexOf('='), at = v.indexOf('@');
        if (at >= 0 && (eq < 0 || at < eq)) return null;
        if (eq < 0) return enc(v);
        if (eq == 0) return enc(v.substring(1));
        return v.substring(0, eq) + "=" + enc(v.substring(eq + 1));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** -F name=value | name=@file[;type=mime] | name=&lt;file ; --form-string name=value (no @/&lt; meaning). */
    private static boolean form(ApiRequest r, String v, boolean literal, Parsed p, int line) {
        int eq = v.indexOf('=');
        if (eq <= 0) {
            p.issue(line, "-F " + v, "form part without name= — ignored");
            return false;
        }
        String name = v.substring(0, eq), value = v.substring(eq + 1);
        String mime = null;
        if (!literal) {
            int semi = value.indexOf(";type=");
            if (semi >= 0) {
                mime = value.substring(semi + 6).replaceAll(";.*$", "");
                value = value.substring(0, semi);
            }
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1, value.length() - 1);
        }
        if (!literal && value.startsWith("@")) {
            String path = value.substring(1).replaceAll(";.*$", "");
            if (!ApiRequest.formPartExpressible(name, path)) {
                p.issue(line, "-F " + v, "GATF multipart parts cannot contain ':' — add this part by hand");
                return true;
            }
            r.formParts.add(name + ":file:" + path + (mime == null ? "" : ":" + mime));
            return true;
        }
        if (!literal && value.startsWith("<")) {
            p.issue(line, "-F " + v, "part content from a file (<file) is not supported — use name=@file or inline text");
            return true;
        }
        if (!ApiRequest.formPartExpressible(name, value)) {
            p.issue(line, "-F " + v, "GATF multipart parts cannot contain ':' — add this part by hand");
            return true;
        }
        if (value.isEmpty()) {
            p.issue(line, "-F " + v, "empty text part — GATF skips empty parts");
            return true;
        }
        r.formParts.add(name + ":text:" + ApiRequest.gatfVars(value) + ":" + (mime == null ? "text/plain" : mime));
        return true;
    }

    private static String joinWords(List<Tok> cmd) {
        StringBuilder b = new StringBuilder();
        for (Tok t : cmd) {
            if (b.length() > 0) b.append(' ');
            b.append(t.text);
        }
        return b.toString();
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    // ------------------------------------------------------------------ writer

    private static final Pattern PLAIN = Pattern.compile("[^'\\p{Cntrl}]*");

    /** bash-quotes one word: {@code 'x'}, or {@code $'…'} when it holds a single quote or control characters. */
    public static String quote(String s) {
        if (s == null) s = "";
        if (PLAIN.matcher(s).matches()) return "'" + s + "'";
        StringBuilder b = new StringBuilder("$'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '\'': b.append("\\'"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (Character.isISOControl(c)) b.append(String.format("\\x%02x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('\'').toString();
    }

    /**
     * Single-line bash command for a request. {@code headers} are name/value pairs; {@code formParts} are
     * {kind("file"|"text"), name, value} and, when present, replace {@code body} (the multipart Content-Type header
     * is dropped since curl writes its own boundary).
     */
    public static String format(String method, String url, List<String[]> headers, String body, List<String[]> formParts,
            boolean compressed) {
        StringBuilder b = new StringBuilder("curl -X ").append(method == null ? "GET" : method.toUpperCase(Locale.ROOT));
        b.append(' ').append(quote(url));
        boolean form = formParts != null && !formParts.isEmpty();
        if (headers != null) {
            for (String[] h : headers) {
                if (form && h[0].equalsIgnoreCase("Content-Type")) continue;
                b.append(" -H ").append(quote(h[0] + ": " + h[1]));
            }
        }
        if (form) {
            for (String[] fp : formParts) {
                if ("file".equals(fp[0])) b.append(" -F ").append(quote(fp[1] + "=@" + fp[2]));
                else b.append(" --form-string ").append(quote(fp[1] + "=" + fp[2]));
            }
        } else if (body != null && !body.isEmpty()) {
            b.append(" --data-raw ").append(quote(body));
        }
        if (compressed) b.append(" --compressed");
        return b.toString();
    }
}
