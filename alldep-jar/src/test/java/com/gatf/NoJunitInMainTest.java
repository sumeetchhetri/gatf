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
package com.gatf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * M16 guard: JUnit is test-only — nothing under src/main (code, codegen strings, templates) may reference it, nor library
 * classes whose supertypes are JUnit's (xmlunit 1.x's XMLAssert / XMLTestCase extend JUnit 3's Assert / TestCase).
 */
class NoJunitInMainTest {

    @Test
    void mainSourcesDoNotReferenceJunit() throws Exception {
        Path main = Paths.get("src", "main");   // surefire runs with the module directory as cwd
        assertTrue(Files.isDirectory(main), "run from the alldep-jar module: " + main.toAbsolutePath());
        // split so this file does not match itself if it ever moves
        List<String> needles = java.util.Arrays.asList("org." + "junit", "junit." + "framework", "xmlunit." + "XMLAssert", "xmlunit." + "XMLTestCase");
        List<String> hits;
        try (Stream<Path> s = Files.walk(main)) {
            hits = s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(java|vm|txt|properties|xml|json)$"))
                    .filter(p -> {
                        try {
                            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            return needles.stream().anyMatch(text::contains);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(Path::toString)
                    .collect(Collectors.toList());
        }
        assertEquals("[]", hits.toString());
    }
}
