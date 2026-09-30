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
package com.gatf.docs;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Jira search JSON → requirement rows (pure): id = issue key, title = summary, module = first component, else the
 * project key. Paging follows {@code startAt / maxResults / total}. Networking lives in {@link JiraClient}.
 */
public final class JiraRequirements {

    public static final String FIELDS = "summary,components,project";

    private JiraRequirements() {}

    /** {@code <base>/rest/api/2/search?jql=…&startAt=…&maxResults=…&fields=summary,components,project}. */
    public static URI searchUri(String baseUrl, String jql, int startAt, int maxResults) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(b + "/rest/api/2/search?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8) + "&startAt=" + startAt
                + "&maxResults=" + maxResults + "&fields=" + FIELDS);
    }

    /** The requirement rows of one search page (issues without a key are skipped). */
    public static List<Traceability.Req> map(JsonNode page) {
        List<Traceability.Req> out = new ArrayList<Traceability.Req>();
        for (JsonNode is : page.path("issues")) {
            String key = is.path("key").asText("").trim();
            if (key.isEmpty()) continue;
            JsonNode f = is.path("fields");
            String module = "";
            for (JsonNode c : f.path("components")) {
                module = c.path("name").asText("").trim();
                if (!module.isEmpty()) break;
            }
            if (module.isEmpty()) module = f.path("project").path("key").asText("").trim();
            out.add(new Traceability.Req(key, f.path("summary").asText("").trim(), module));
        }
        return out;
    }

    /** The next page's {@code startAt}, or -1 when this page was the last (or empty). */
    public static int nextStartAt(JsonNode page) {
        int n = page.path("issues").size();
        int next = page.path("startAt").asInt(0) + n;
        return n > 0 && next < page.path("total").asInt(0) ? next : -1;
    }

    /** {@code Bearer <token>} (Jira DC personal access token), or {@code Basic user:token} (Jira Cloud API token). */
    public static String authorization(String user, String token) {
        if (user == null || user.isBlank()) return "Bearer " + token;
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
    }
}
