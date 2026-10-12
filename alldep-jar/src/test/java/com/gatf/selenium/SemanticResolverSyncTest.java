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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Δ148: the Trace Viewer's pick locator runs {@link SemanticLocators#RESOLVER_FN}, {@link SemanticLocators#CANDIDATES_FN}
 * and {@link SemanticLocators#FALLBACK_FN} from a generated module (tools/gen-semantic-resolver.mjs). This fails when
 * that module no longer holds the exact Java constants: re-run {@code node tools/gen-semantic-resolver.mjs}.
 */
class SemanticResolverSyncTest {

    static final Path MODULE = Paths.get("../frontend/src/trace/semanticResolver.generated.js");

    @Test
    void generatedModuleHoldsTheJavaScriptsVerbatim() throws Exception {
        assertTrue(Files.isRegularFile(MODULE), MODULE.toAbsolutePath() + " is missing (node tools/gen-semantic-resolver.mjs)");
        String js = new String(Files.readAllBytes(MODULE), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String[][] blocks = { { "RESOLVER_FN", SemanticLocators.RESOLVER_FN }, { "CANDIDATES_FN", SemanticLocators.CANDIDATES_FN },
                { "FALLBACK_FN", SemanticLocators.FALLBACK_FN } };
        for (String[] b : blocks) {
            String start = "// ---- " + b[0] + " ----\n";
            int i = js.indexOf(start);
            assertTrue(i >= 0, b[0] + " section missing in " + MODULE);
            int from = i + start.length(), to = js.indexOf("\n// ---- ", from);
            assertTrue(to > from, b[0] + " section unterminated");
            assertEquals(b[1], js.substring(from, to + 1),
                    b[0] + " in " + MODULE + " differs from SemanticLocators: run node tools/gen-semantic-resolver.mjs");
        }
        assertTrue(js.contains("return { semantic: __gatfSemantic, candidates: __gatfCandidates, fallback: __gatfFallback };"));
    }
}
