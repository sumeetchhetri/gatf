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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** --requirements-jql: canned Jira search pages → requirement rows, paging and auth header. */
class JiraRequirementsTest {

    private static final String PAGE1 = "{\"startAt\":0,\"maxResults\":2,\"total\":3,\"issues\":["
            + "{\"key\":\"PROJ-1\",\"fields\":{\"summary\":\" Login with OTP \",\"components\":[{\"name\":\"Auth\"},{\"name\":\"UI\"}],\"project\":{\"key\":\"PROJ\"}}},"
            + "{\"key\":\"PROJ-2\",\"fields\":{\"summary\":\"Refunds\",\"components\":[],\"project\":{\"key\":\"PROJ\"}}}]}";
    private static final String PAGE2 = "{\"startAt\":2,\"maxResults\":2,\"total\":3,\"issues\":["
            + "{\"key\":\"OPS-7\",\"fields\":{\"summary\":\"Audit log\",\"project\":{\"key\":\"OPS\"}}},"
            + "{\"fields\":{\"summary\":\"no key\"}}]}";

    private static JsonNode json(String s) throws Exception {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void mapsKeySummaryAndFirstComponentElseProject() throws Exception {
        List<Traceability.Req> r = JiraRequirements.map(json(PAGE1));
        assertThat(r).extracting(q -> q.id).containsExactly("PROJ-1", "PROJ-2");
        assertThat(r).extracting(q -> q.title).containsExactly("Login with OTP", "Refunds");
        assertThat(r).extracting(q -> q.module).containsExactly("Auth", "PROJ");
        List<Traceability.Req> r2 = JiraRequirements.map(json(PAGE2));
        assertThat(r2).extracting(q -> q.id).containsExactly("OPS-7");
        assertThat(r2.get(0).module).isEqualTo("OPS");
    }

    @Test
    void pagesUntilTotal() throws Exception {
        assertThat(JiraRequirements.nextStartAt(json(PAGE1))).isEqualTo(2);
        assertThat(JiraRequirements.nextStartAt(json(PAGE2))).isEqualTo(-1);
        assertThat(JiraRequirements.nextStartAt(json("{\"startAt\":0,\"total\":5,\"issues\":[]}"))).isEqualTo(-1);
    }

    @Test
    void searchUriEncodesJql() {
        assertThat(JiraRequirements.searchUri("https://jira.example.com/", "project = PROJ AND type = Story", 100, 50).toString())
                .isEqualTo("https://jira.example.com/rest/api/2/search?jql=project+%3D+PROJ+AND+type+%3D+Story&startAt=100&maxResults=50"
                        + "&fields=summary,components,project");
    }

    @Test
    void bearerOrBasic() {
        assertThat(JiraRequirements.authorization(null, "tok")).isEqualTo("Bearer tok");
        assertThat(JiraRequirements.authorization("me@x.io", "tok")).isEqualTo("Basic bWVAeC5pbzp0b2s=");
    }
}
