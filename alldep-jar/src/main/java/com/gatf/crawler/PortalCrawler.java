/*
    Copyright 2013-2019, Sumeet Chhetri

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
package com.gatf.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 12 — credentialed/uncredentialed portal crawler that BFS-walks a web portal and emits a
 * starter seleasy suite plus a coverage map. This is the Java counterpart of the verified reference
 * implementation tools/crawler/portal_crawler.py; the two are kept in lock-step so the emitted
 * grammar stays lint-clean against tools/smoke/sel-lint.py.
 *
 * The emitted grammar is exactly the FgRecorder grammar (the recorder shipped with GATF):
 *   open chrome / maximize / goto &lt;url&gt; / type "v" xpath@"&lt;xpath&gt;" /
 *   click xpath@"&lt;xpath&gt;" / select index@N xpath@"&lt;xpath&gt;" / sleep &lt;seconds&gt;
 * so a generated suite reads like a recorded walkthrough that the user then enriches with assertions.
 *
 * HTML is scanned with lightweight regexes (no jsoup dependency) — adequate for a starter suite. For
 * JavaScript-rendered portals, drive the same emission from a Selenium session instead (see the notes
 * in docs/crawler); the seleasy contract (xpathFor / sample-by-type / fill rules) is identical.
 *
 * CLI: java -cp gatf.jar com.gatf.crawler.PortalCrawler --seed http://host/ [flags...]
 */
public class PortalCrawler {

    private static final String UA = "gatf-portal-crawler";
    private static final ObjectMapper OM = new ObjectMapper();

    // ---- discovered-element model ------------------------------------------------------------

    static class El {
        String tag = "";
        String type = "";
        String name = "";
        String id = "";
        String cls = "";
        int idx = 1;
    }

    static class Form {
        String action = "";
        String method = "get";
        List<El> inputs = new ArrayList<El>();
    }

    static class Page {
        String url;
        String finalUrl;
        int depth;
        int status;
        String contentType = "";
        String title;
        List<String> links = new ArrayList<String>();
        List<Form> forms = new ArrayList<Form>();
    }

    // ---- seleasy xpath + value mapping (shared contract with the Python reference) ------------

    static String xpathFor(El el) {
        if (notBlank(el.id)) return "//*[@id='" + el.id + "']";
        if (notBlank(el.name)) return "//" + el.tag + "[@name='" + el.name + "']";
        if (notBlank(el.cls)) return "//" + el.tag + "[contains(@class,'" + el.cls.trim().split("\\s+")[0] + "')]";
        return "(//" + el.tag + ")[" + el.idx + "]";
    }

    static String sampleByType(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.equals("email")) return "user@example.com";
        if (t.equals("password")) return "P@ssw0rd";
        if (t.equals("number")) return "1";
        if (t.equals("tel")) return "9999999999";
        if (t.equals("url")) return "https://example.com";
        if (t.equals("date")) return "2020-01-01";
        return "sample"; // text / search / "" and anything unrecognised
    }

    // seleasy selector for a discovered element (id@ / name@ / xpath@)
    static String selectorFor(El el) {
        if (notBlank(el.id)) return "id@" + el.id;
        if (notBlank(el.name)) return "name@" + el.name;
        return "xpath@\"" + xpathFor(el) + "\"";
    }

    /** Emit FgRecorder-style fill lines for one form. */
    static int[] fillLinesForForm(Form form, boolean submitForms, List<String> out) {
        int filled = 0, submitEmitted = 0;
        El submit = null;
        for (El el : form.inputs) {
            String tag = el.tag, typ = el.type == null ? "" : el.type.toLowerCase(Locale.ROOT);
            String xp = xpathFor(el);
            if (tag.equals("textarea")) {
                out.add("type \"sample text\" xpath@\"" + xp + "\""); filled++;
            } else if (tag.equals("select")) {
                out.add("select index@1 xpath@\"" + xp + "\""); filled++;
            } else if (tag.equals("button") || typ.equals("submit")) {
                if (submit == null) submit = el;
            } else if (typ.equals("checkbox") || typ.equals("radio")) {
                out.add("click xpath@\"" + xp + "\""); filled++;
            } else if (typ.equals("hidden") || typ.equals("file") || typ.equals("image") || typ.equals("reset")) {
                // skip
            } else {
                out.add("type \"" + sampleByType(typ) + "\" xpath@\"" + xp + "\""); filled++;
            }
        }
        // Submit GET forms by default (non-mutating searches/filters); POST needs submitForms.
        if (submit != null && (submitForms || form.method.equals("get"))) {
            out.add("click xpath@\"" + xpathFor(submit) + "\"");
            out.add("sleep 1");
            submitEmitted = 1;
        }
        return new int[] { filled, submitEmitted };
    }

    // ---- lightweight HTML scan (mirrors the Python HTMLParser extraction) ---------------------

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANCHOR = Pattern.compile("<a\\s[^>]*href\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM = Pattern.compile("<form\\b(.*?)>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FIELD = Pattern.compile("<(input|select|textarea|button)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:-]+)\\s*=\\s*([\"'])(.*?)\\2");

    static String attr(String tagBody, String name) {
        Matcher m = ATTR.matcher(tagBody);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(name)) return m.group(3);
        }
        return "";
    }

    static String extractTitle(String html) {
        Matcher m = TITLE.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    static List<String> extractLinks(String html) {
        List<String> links = new ArrayList<String>();
        Matcher m = ANCHOR.matcher(html);
        while (m.find()) links.add(m.group(2));
        return links;
    }

    static List<Form> extractForms(String html) {
        List<Form> forms = new ArrayList<Form>();
        Map<String, Integer> tagCounts = new LinkedHashMap<String, Integer>();
        // running per-tag index across the whole page, for positional xpath fallback (matches Python)
        Matcher fm = FIELD.matcher(html);
        Map<Integer, Integer> posByOffset = new LinkedHashMap<Integer, Integer>();
        while (fm.find()) {
            String tag = fm.group(1).toLowerCase(Locale.ROOT);
            int c = (tagCounts.containsKey(tag) ? tagCounts.get(tag) : 0) + 1;
            tagCounts.put(tag, c);
            posByOffset.put(fm.start(), c);
        }
        Matcher m = FORM.matcher(html);
        while (m.find()) {
            String head = m.group(1), body = m.group(2);
            Form f = new Form();
            f.action = attr(head, "action");
            String meth = attr(head, "method");
            f.method = meth.isEmpty() ? "get" : meth.toLowerCase(Locale.ROOT);
            Matcher em = FIELD.matcher(body);
            while (em.find()) {
                El el = new El();
                el.tag = em.group(1).toLowerCase(Locale.ROOT);
                String ab = em.group(2);
                el.type = attr(ab, "type").toLowerCase(Locale.ROOT);
                el.name = attr(ab, "name");
                el.id = attr(ab, "id");
                el.cls = attr(ab, "class");
                // approximate the whole-page positional index by matching this field's absolute offset
                int abs = m.start(2) + em.start();
                el.idx = nearestPos(posByOffset, abs);
                f.inputs.add(el);
            }
            forms.add(f);
        }
        return forms;
    }

    private static int nearestPos(Map<Integer, Integer> posByOffset, int absOffset) {
        int best = 1, bestDelta = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : posByOffset.entrySet()) {
            int d = Math.abs(e.getKey() - absOffset);
            if (d < bestDelta) { bestDelta = d; best = e.getValue(); }
        }
        return best;
    }

    // ---- login discovery ---------------------------------------------------------------------

    static String[] discoverLogin(String loginHtml) {
        // returns {userSel, passSel, submitSel}
        for (Form form : extractForms(loginHtml)) {
            El pw = null;
            for (El e : form.inputs) if (e.type.equals("password")) { pw = e; break; }
            if (pw == null) continue;
            El user = null;
            for (El e : form.inputs) {
                if (e == pw) break;
                if (e.tag.equals("input") && (e.type.equals("text") || e.type.equals("email") || e.type.isEmpty() || e.type.equals("tel"))) {
                    user = e;
                }
            }
            El submit = null;
            for (El e : form.inputs) if (e.tag.equals("button") || e.type.equals("submit")) { submit = e; break; }
            return new String[] { user != null ? selectorFor(user) : null, selectorFor(pw),
                    submit != null ? selectorFor(submit) : null };
        }
        return new String[] { null, null, null };
    }

    // ---- crawl -------------------------------------------------------------------------------

    private final CrawlSpec spec;
    private final Map<String, Integer> stats = new LinkedHashMap<String, Integer>();

    public PortalCrawler(CrawlSpec spec) {
        this.spec = spec;
        for (String k : new String[] { "visited", "off_origin", "skipped_scheme", "forms", "inputs", "links" }) stats.put(k, 0);
    }

    private void bump(String k, int by) { stats.put(k, stats.get(k) + by); }

    public List<Page> crawl() throws Exception {
        String seed = spec.getSeed();
        Deque<String[]> queue = new ArrayDeque<String[]>(); // [url, depth]
        Set<String> seen = new HashSet<String>();
        seen.add(norm(seed));
        queue.add(new String[] { seed, "0" });
        List<Page> pages = new ArrayList<Page>();

        while (!queue.isEmpty() && pages.size() < spec.getMaxPages()) {
            String[] item = queue.poll();
            String url = item[0];
            int depth = Integer.parseInt(item[1]);
            String[] fetched = fetch(url); // {status, finalUrl, contentType, body}
            bump("visited", 1);
            Page rec = new Page();
            rec.url = url;
            rec.finalUrl = fetched[1];
            rec.depth = depth;
            rec.status = Integer.parseInt(fetched[0]);
            rec.contentType = fetched[2];
            String html = fetched[3];
            if (html != null && !html.isEmpty() && rec.contentType.contains("html")) {
                rec.title = extractTitle(html);
                for (Form f : extractForms(html)) {
                    f.action = f.action.isEmpty() ? rec.finalUrl : absUrl(rec.finalUrl, f.action);
                    rec.forms.add(f);
                    bump("inputs", f.inputs.size());
                }
                bump("forms", rec.forms.size());
                for (String href : extractLinks(html)) {
                    String absu = norm(absUrl(rec.finalUrl, href));
                    String scheme = schemeOf(absu);
                    if (!scheme.equals("http") && !scheme.equals("https")) { bump("skipped_scheme", 1); continue; }
                    rec.links.add(absu);
                    boolean offOrigin = spec.isSameOrigin() && !sameOrigin(absu, seed);
                    if (!offOrigin) {
                        if (!seen.contains(absu) && depth + 1 <= spec.getMaxDepth()) {
                            seen.add(absu);
                            queue.add(new String[] { absu, String.valueOf(depth + 1) });
                        }
                    } else {
                        bump("off_origin", 1);
                    }
                }
                bump("links", rec.links.size());
            }
            pages.add(rec);
        }
        return pages;
    }

    // ---- emit --------------------------------------------------------------------------------

    public String emitSeleasy(List<Page> pages, int[] emitStatsOut) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by GATF Phase 12 portal crawler — a starter suite; enrich with assertions.\n");
        sb.append("// seed: ").append(spec.getSeed()).append("\n");
        sb.append("open chrome\n").append("maximize\n\n");
        int formsEmitted = 0, filledTotal = 0, submits = 0;

        if (spec.isCredentialed()) {
            String userSel = spec.getUserSel(), passSel = spec.getPassSel(), submitSel = spec.getSubmitSel();
            if (userSel == null || passSel == null) {
                try {
                    String[] disc = discoverLogin(fetch(spec.getLoginUrl())[3]);
                    if (userSel == null) userSel = disc[0];
                    if (passSel == null) passSel = disc[1];
                    if (submitSel == null) submitSel = disc[2];
                } catch (Exception ignore) {}
            }
            sb.append("// --- login ---\n").append("goto ").append(spec.getLoginUrl()).append("\n");
            if (userSel != null && passSel != null) {
                sb.append("type \"").append(spec.getUsername()).append("\" ").append(userSel).append("\n");
                sb.append("type \"").append(spec.getPassword()).append("\" ").append(passSel).append("\n");
                if (submitSel != null) sb.append("click ").append(submitSel).append("\n");
            }
            sb.append("sleep 2\n\n");
        }

        int idx = 0;
        for (Page pg : pages) {
            idx++;
            if (pg.status == 0) continue;
            sb.append("// page ").append(idx).append(": ").append(pg.url);
            if (pg.title != null) sb.append(" — ").append(pg.title);
            sb.append("\n").append("goto ").append(pg.url).append("\n").append("sleep 1\n");
            for (Form form : pg.forms) {
                List<String> lines = new ArrayList<String>();
                int[] r = fillLinesForForm(form, spec.isSubmitForms(), lines);
                if (!lines.isEmpty()) {
                    formsEmitted++;
                    filledTotal += r[0];
                    submits += r[1];
                    for (String l : lines) sb.append(l).append("\n");
                }
            }
            sb.append("\n");
        }
        emitStatsOut[0] = formsEmitted;
        emitStatsOut[1] = filledTotal;
        emitStatsOut[2] = submits;
        return sb.toString();
    }

    public void run() throws Exception {
        List<Page> pages = crawl();
        int[] emit = new int[3];
        String sel = emitSeleasy(pages, emit);

        File outDir = new File(spec.getOutDir());
        outDir.mkdirs();
        File selFile = new File(outDir, spec.getSuiteName() + ".sel");
        writeString(selFile, sel);

        Map<String, Object> coverage = new LinkedHashMap<String, Object>();
        coverage.put("seed", spec.getSeed());
        Map<String, Object> specOut = new LinkedHashMap<String, Object>();
        specOut.put("max_pages", spec.getMaxPages());
        specOut.put("max_depth", spec.getMaxDepth());
        specOut.put("same_origin", spec.isSameOrigin());
        specOut.put("submit_forms", spec.isSubmitForms());
        coverage.put("spec", specOut);
        coverage.put("credentialed", spec.isCredentialed());
        Map<String, Object> statOut = new LinkedHashMap<String, Object>(stats);
        statOut.put("formsEmitted", emit[0]);
        statOut.put("inputsFilled", emit[1]);
        statOut.put("submitsEmitted", emit[2]);
        int emitted = 0;
        for (Page p : pages) if (p.status != 0) emitted++;
        statOut.put("pagesEmitted", emitted);
        coverage.put("stats", statOut);
        coverage.put("pages", pagesToJson(pages));

        File covFile = new File(outDir, "crawl-coverage.json");
        writeString(covFile, OM.writerWithDefaultPrettyPrinter().writeValueAsString(coverage));
        System.out.println("wrote " + selFile.getAbsolutePath() + " and " + covFile.getName());
        System.out.println("stats: " + OM.writeValueAsString(statOut));
    }

    private List<Map<String, Object>> pagesToJson(List<Page> pages) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Page p : pages) {
            Map<String, Object> pm = new LinkedHashMap<String, Object>();
            pm.put("url", p.url);
            pm.put("finalUrl", p.finalUrl);
            pm.put("depth", p.depth);
            pm.put("status", p.status);
            pm.put("contentType", p.contentType);
            pm.put("title", p.title);
            pm.put("links", p.links);
            List<Map<String, Object>> forms = new ArrayList<Map<String, Object>>();
            for (Form f : p.forms) {
                Map<String, Object> fm = new LinkedHashMap<String, Object>();
                fm.put("action", f.action);
                fm.put("method", f.method);
                List<Map<String, Object>> inputs = new ArrayList<Map<String, Object>>();
                for (El e : f.inputs) {
                    Map<String, Object> im = new LinkedHashMap<String, Object>();
                    im.put("tag", e.tag);
                    im.put("type", e.type);
                    im.put("name", e.name);
                    im.put("id", e.id);
                    im.put("xpath", xpathFor(e));
                    inputs.add(im);
                }
                fm.put("inputs", inputs);
                forms.add(fm);
            }
            pm.put("forms", forms);
            out.add(pm);
        }
        return out;
    }

    // ---- fetch + url helpers -----------------------------------------------------------------

    // returns {status, finalUrl, contentType, body}
    static String[] fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", UA);
            int code = conn.getResponseCode();
            String finalUrl = conn.getURL().toString();
            String ctype = conn.getContentType();
            if (ctype == null) ctype = "";
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readCapped(in, 2_000_000);
            return new String[] { String.valueOf(code), finalUrl, ctype, body };
        } catch (Exception e) {
            return new String[] { "0", url, "", "" };
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readCapped(InputStream in, int cap) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n, total = 0;
        while ((n = in.read(buf)) > 0 && total < cap) { bos.write(buf, 0, n); total += n; }
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    static String norm(String url) {
        int h = url.indexOf('#');
        return h >= 0 ? url.substring(0, h) : url;
    }

    static String absUrl(String base, String href) {
        try {
            return new URL(new URL(base), href).toString();
        } catch (Exception e) {
            return href;
        }
    }

    static String schemeOf(String url) {
        try {
            String s = URI.create(url).getScheme();
            return s == null ? "" : s.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            int c = url.indexOf(':');
            return c > 0 ? url.substring(0, c).toLowerCase(Locale.ROOT) : "";
        }
    }

    static boolean sameOrigin(String u, String seed) {
        try {
            URL a = new URL(u), b = new URL(seed);
            return a.getProtocol().equals(b.getProtocol()) && a.getHost().equals(b.getHost())
                    && a.getPort() == b.getPort();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static void writeString(File f, String content) throws Exception {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---- CLI ---------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        CrawlSpec spec = new CrawlSpec();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--seed")) spec.setSeed(args[++i]);
            else if (a.equals("--max-pages")) spec.setMaxPages(Integer.parseInt(args[++i]));
            else if (a.equals("--max-depth")) spec.setMaxDepth(Integer.parseInt(args[++i]));
            else if (a.equals("--same-origin")) spec.setSameOrigin(true);
            else if (a.equals("--allow-off-origin")) spec.setSameOrigin(false);
            else if (a.equals("--submit-forms")) spec.setSubmitForms(true);
            else if (a.equals("--out-dir")) spec.setOutDir(args[++i]);
            else if (a.equals("--suite-name")) spec.setSuiteName(args[++i]);
            else if (a.equals("--login-url")) spec.setLoginUrl(args[++i]);
            else if (a.equals("--username")) spec.setUsername(args[++i]);
            else if (a.equals("--password")) spec.setPassword(args[++i]);
            else if (a.equals("--user-sel")) spec.setUserSel(args[++i]);
            else if (a.equals("--pass-sel")) spec.setPassSel(args[++i]);
            else if (a.equals("--submit-sel")) spec.setSubmitSel(args[++i]);
        }
        if (spec.getSeed() == null) {
            System.err.println("usage: PortalCrawler --seed http://host/ [--max-pages N] [--max-depth N] "
                    + "[--allow-off-origin] [--submit-forms] [--out-dir DIR] [--suite-name NAME] "
                    + "[--login-url URL --username U --password P [--user-sel S --pass-sel S --submit-sel S]]");
            System.exit(2);
        }
        new PortalCrawler(spec).run();
    }
}
