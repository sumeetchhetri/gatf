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
package com.gatf.generator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Δ160 — secrets: a password (and any other value) in gatf-config never reaches an exported spec, the report file,
 * stdout or stderr. {@code ${password}} is exported as the environment variable name only, and the CLI never reads
 * the config.
 */
class ExportSecretsTest {

    static final String SECRET = "Sup3r-S3cret!pw";
    static final String SECRET_HOST = "secret-host.internal";

    @Test
    void configValuesNeverReachTheOutput(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("gatf-config.xml"), ("<gatf-execute-config>\n  <testCaseDir>tests</testCaseDir>\n  <gatfTestDataConfig>\n"
                + "    <globalVariables>\n      <password>" + SECRET + "</password>\n      <baseUrl>https://" + SECRET_HOST + "</baseUrl>\n"
                + "    </globalVariables>\n  </gatfTestDataConfig>\n  <isSeleniumExecutor>true</isSeleniumExecutor>\n</gatf-execute-config>\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("app.properties"), ("password=" + SECRET + "\n").getBytes(StandardCharsets.UTF_8));
        Path tests = Files.createDirectories(dir.resolve("tests"));
        Files.write(tests.resolve("login.sel"), ("open chrome\nconfig app.properties\ngoto ${baseUrl}/login\n"
                + "type ${password} label@Password\ntype '!password!' id@p2\n?? id@who text ='${password}'\n").getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        int rc = ExportCli.run(new String[] { "playwright", "tests", "-o", "out" }, dir.toFile(), new PrintStream(out, true, "UTF-8"), new PrintStream(err, true, "UTF-8"));
        assertEquals(0, rc, err.toString("UTF-8"));
        rc = ExportCli.run(new String[] { "playwright", "tests/login.sel", "-o", "single.spec.ts" }, dir.toFile(), new PrintStream(out, true, "UTF-8"), new PrintStream(err, true, "UTF-8"));
        assertEquals(0, rc, err.toString("UTF-8"));

        String spec = read(dir.resolve("out/login.spec.ts"));
        assertTrue(spec.contains("env('GATF_PASSWORD')"), spec);
        assertTrue(spec.contains("env('GATF_BASEURL')"), spec);
        StringBuilder everything = new StringBuilder(out.toString("UTF-8")).append(err.toString("UTF-8"));
        try (Stream<Path> w = Files.walk(dir)) {
            for (Path p : w.filter(Files::isRegularFile).collect(Collectors.toList())) {
                if (p.getFileName().toString().equals("gatf-config.xml") || p.getFileName().toString().equals("app.properties")) continue;
                everything.append(read(p));
            }
        }
        assertTrue(everything.indexOf("GATF_PASSWORD") >= 0);
        assertFalse(everything.indexOf(SECRET) >= 0, "the config password reached the export output");
        assertFalse(everything.indexOf(SECRET_HOST) >= 0, "a config value reached the export output");
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    static File file(Path p) {
        return p.toFile();
    }
}
