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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin Jira search client for {@code gatf docs --requirements-jql}: {@code GATF_JIRA_URL} (base URL),
 * {@code GATF_JIRA_TOKEN} and optional {@code GATF_JIRA_USER} (Basic user:token; without it the token is sent as Bearer).
 * The token is never logged or put in error messages.
 */
public final class JiraClient {

    static final int PAGE = 100, MAX_PAGES = 500;

    private JiraClient() {}

    public static List<Traceability.Req> requirements(String jql, Map<String, String> env) throws IOException, InterruptedException {
        String base = env.get("GATF_JIRA_URL"), token = env.get("GATF_JIRA_TOKEN");
        if (base == null || base.isBlank() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("--requirements-jql needs GATF_JIRA_URL and GATF_JIRA_TOKEN (optionally GATF_JIRA_USER)");
        }
        String auth = JiraRequirements.authorization(env.get("GATF_JIRA_USER"), token);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();   // never replay the token to a redirect target
        ObjectMapper om = new ObjectMapper();
        List<Traceability.Req> out = new ArrayList<Traceability.Req>();
        int startAt = 0;
        for (int p = 0; p < MAX_PAGES && startAt >= 0; p++) {
            HttpRequest req = HttpRequest.newBuilder(JiraRequirements.searchUri(base.trim(), jql, startAt, PAGE))
                    .timeout(Duration.ofSeconds(60)).header("Accept", "application/json").header("Authorization", auth).GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 3) {
                throw new IOException("Jira search redirected (HTTP " + r.statusCode() + ") to " + r.headers().firstValue("Location").orElse("?")
                        + " — set GATF_JIRA_URL to the final base URL");
            }
            if (r.statusCode() != 200) {
                String b = r.body() == null ? "" : r.body().replaceAll("\\s+", " ");
                throw new IOException("Jira search failed: HTTP " + r.statusCode() + (b.isEmpty() ? "" : " " + b.substring(0, Math.min(200, b.length()))));
            }
            JsonNode page = om.readTree(r.body());
            out.addAll(JiraRequirements.map(page));
            startAt = JiraRequirements.nextStartAt(page);
        }
        return out;
    }
}
