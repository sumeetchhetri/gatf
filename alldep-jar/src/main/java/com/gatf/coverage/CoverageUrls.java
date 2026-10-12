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
package com.gatf.coverage;

import java.util.Locale;
import java.util.regex.Pattern;

import com.gatf.crawler.SpaCrawler;

/**
 * H6 (Δ155): the only two ways a URL enters a coverage index. Both drop the query string and any
 * {@code user:pass@} userinfo and any {@code ;jsessionid=…} path parameter (Δ155a), so tokens in a script or page URL
 * never reach a coverage file.
 *
 * <ul>
 * <li>{@link #script}: http(s) and file URLs only (data:, blob:, extension and eval scripts have no stable
 * identity, and a data: URL carries its source), fragment dropped.</li>
 * <li>{@link #route}: the crawler's own {@link SpaCrawler#norm} (plain #anchors dropped, #/ and #!/ hash routes
 * kept, trailing slash), applied after the query, including a hash route's own {@code ?…}, is dropped. Crawl
 * routes and visited routes both go through this one function, so they compare equal.</li>
 * </ul>
 *
 * @author Sumeet Chhetri
 */
public final class CoverageUrls {

    private CoverageUrls() {}

    // Δ155a: a servlet session id in the path (;jsessionid=…) is a secret and would split one script/route per session
    private static final Pattern JSESSIONID = Pattern.compile("(?i);jsessionid=[^/?#]*");

    static String stripSessionId(String u) {
        return JSESSIONID.matcher(u).replaceAll("");
    }

    /** A script/stylesheet URL as stored in the index, or null when it has no stable, shareable identity. */
    public static String script(String url) {
        if (url == null) return null;
        String u = stripSessionId(url.trim());
        if (u.isEmpty()) return null;
        String lc = u.toLowerCase(Locale.ROOT);
        if (!(lc.startsWith("http://") || lc.startsWith("https://") || lc.startsWith("file:"))) return null;
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return stripUserInfo(u);
    }

    /** A page route as stored in the index (and as crawl routes are keyed), or null when it isn't an http(s) page. */
    public static String route(String url) {
        if (url == null) return null;
        String u = stripSessionId(url.trim());
        int h = u.indexOf('#');
        String base = h >= 0 ? u.substring(0, h) : u;
        String frag = h >= 0 ? u.substring(h) : "";
        int q = base.indexOf('?');
        if (q >= 0) base = base.substring(0, q);
        int fq = frag.indexOf('?');
        if (fq >= 0) frag = frag.substring(0, fq);
        return SpaCrawler.norm(stripUserInfo(base) + frag);
    }

    static String stripUserInfo(String u) {
        int s = u.indexOf("://");
        if (s < 0) return u;
        int start = s + 3;
        int end = u.indexOf('/', start);
        if (end < 0) end = u.length();
        int at = u.lastIndexOf('@', end - 1);
        if (at >= start) return u.substring(0, start) + u.substring(at + 1);
        return u;
    }
}
