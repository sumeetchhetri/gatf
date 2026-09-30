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
package com.gatf.selenium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** M3 — semantic locator parsing (the in-page resolver is covered by tools/smoke/test-semantic.mjs). */
class SemanticLocatorsTest {

    @Test
    void recognisesPrefixesAndSemanticText() {
        for (String p : SemanticLocators.PREFIXES) assertThat(SemanticLocators.isSemantic(p, "x")).isTrue();
        assertThat(SemanticLocators.isSemantic("ROLE", "button")).isTrue();
        assertThat(SemanticLocators.isSemantic("text", "=Save")).isTrue();
        assertThat(SemanticLocators.isSemantic("text", "/sav(e|ed)/i")).isTrue();
        assertThat(SemanticLocators.isSemantic("text", "Save")).isFalse();      // legacy contains-XPath
        assertThat(SemanticLocators.isSemantic("text", "/home")).isFalse();
        assertThat(SemanticLocators.isSemantic("css", "#x")).isFalse();
    }

    @Test
    void roleWithNameAndStates() {
        Map<String, Object> s = SemanticLocators.parse("role", "button[name=Save]");
        assertThat(s).containsEntry("kind", "role").containsEntry("role", "button").containsEntry("mode", "full").containsEntry("value", "Save");
        s = SemanticLocators.parse("role", "heading[name='Orders (3)'][level=2]");
        assertThat(s).containsEntry("value", "Orders (3)").containsEntry("level", 2);
        s = SemanticLocators.parse("role", "checkbox[checked=false][include-hidden]");
        assertThat(s).containsEntry("checked", false).containsEntry("includeHidden", true);
        assertThat(SemanticLocators.parse("role", "button[name=~sav]")).containsEntry("mode", "contains").containsEntry("value", "sav");
        assertThat(SemanticLocators.parse("role", "button[name==Save]")).containsEntry("mode", "exact").containsEntry("value", "Save");
        assertThat(SemanticLocators.parse("role", "link[name=/^Home$/i]")).containsEntry("mode", "regex")
                .containsEntry("value", "^Home$").containsEntry("flags", "i");
    }

    @Test
    void valueModes() {
        assertThat(SemanticLocators.parse("label", "Email address")).containsEntry("mode", "full");
        assertThat(SemanticLocators.parse("placeholder", "~search")).containsEntry("mode", "contains");
        assertThat(SemanticLocators.parse("title", "=Close")).containsEntry("mode", "exact").containsEntry("value", "Close");
        assertThat(SemanticLocators.parse("testid", "save-btn")).containsEntry("mode", "exact").containsEntry("value", "save-btn");
        assertThat(SemanticLocators.parse("text", "=Saved at noon")).containsEntry("kind", "text").containsEntry("mode", "exact");
        assertThat(SemanticLocators.parse("alt", "/logo/gi")).containsEntry("mode", "regex").containsEntry("flags", "i");
    }

    @Test
    void malformedLocatorsAreRejected() {
        assertThatThrownBy(() -> SemanticLocators.parse("role", "button[bogus=1]")).hasMessageContaining("unknown attribute [bogus]");
        assertThatThrownBy(() -> SemanticLocators.parse("role", "heading[level=two]")).hasMessageContaining("[level] must be a number");
        assertThatThrownBy(() -> SemanticLocators.parse("role", "[name=x]")).hasMessageContaining("Invalid role locator");
        assertThatThrownBy(() -> SemanticLocators.parse("label", "")).hasMessageContaining("needs a value");
    }

    @Test
    void strictViolationListsCandidates() {
        String m = SemanticLocators.strictViolation("role@button", List.of("<button#a> \"Save\"", "<button> \"Cancel\""));
        assertThat(m).isEqualTo("Strict locator role@button matched 2 elements\n  1) <button#a> \"Save\"\n  2) <button> \"Cancel\"");
    }

    @Test
    void strictViolationReportsTheRealCount() {
        List<String> eleven = java.util.Collections.nCopies(11, "<button> \"Go\"");   // the resolver caps descriptions at 11
        String m = SemanticLocators.strictViolation("role@button", eleven, 40);
        assertThat(m).startsWith("Strict locator role@button matched 40 elements").endsWith("\n  ...");
        assertThat(m.split("\n")).hasSize(12);
    }

    @Test
    void invalidRegexesFailAtParse() {
        assertThatThrownBy(() -> SemanticLocators.parse("placeholder", "/(/")).hasMessageContaining("Invalid regex /(/");
        assertThatThrownBy(() -> SemanticLocators.parse("text", "/a/ii")).hasMessageContaining("duplicate flag 'i'");
        assertThatThrownBy(() -> SemanticLocators.parse("role", "button[name=/a/uv]")).hasMessageContaining("u and v");
        assertThat(SemanticLocators.parse("text", "/^a.b$/dimsuy")).containsEntry("flags", "dimsuy");
        assertThat(SemanticLocators.parse("text", "/[^]x/v")).containsEntry("mode", "regex");          // JS-only syntax accepted
        assertThat(SemanticLocators.parse("text", "/(?<first_name>\\w+) \\k<first_name>/")).containsEntry("mode", "regex");
        assertThat(SemanticLocators.parse("text", "/\\u{1F600}/u")).containsEntry("mode", "regex");
    }

    @Test
    void testIdEqualsAndEmptyValues() {
        assertThat(SemanticLocators.parse("testid", "=foo")).containsEntry("mode", "exact").containsEntry("value", "foo");
        assertThat(SemanticLocators.parse("testid", "'=foo'")).containsEntry("value", "foo");
        assertThatThrownBy(() -> SemanticLocators.parse("label", "~")).hasMessageContaining("empty value");
        assertThatThrownBy(() -> SemanticLocators.parse("text", "=  ")).hasMessageContaining("empty value");
        assertThatThrownBy(() -> SemanticLocators.parse("testid", "=")).hasMessageContaining("empty value");
        assertThatThrownBy(() -> SemanticLocators.parse("role", "button[name=~]")).hasMessageContaining("empty [name]");
    }

    @Test
    void includeHiddenSuffixOnAnyPrefix() {
        assertThat(SemanticLocators.parse("text", "=Saved[include-hidden]")).containsEntry("value", "Saved").containsEntry("includeHidden", true);
        assertThat(SemanticLocators.isSemantic("text", "/sav/i[include-hidden]")).isTrue();
        assertThat(SemanticLocators.parse("placeholder", "Search")).doesNotContainKey("includeHidden");
    }
}
