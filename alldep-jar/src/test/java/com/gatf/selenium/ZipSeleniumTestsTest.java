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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ163o sweep: the distributed class zip carries only the run's classes, not the whole shared gatf-code dir. */
class ZipSeleniumTestsTest {

    @TempDir
    Path tmp;

    @Test
    void onlyTheNamedClassesAndTheirNestedClasses() throws Exception {
        Path pkg = Files.createDirectories(tmp.resolve("com/gatf/selenium"));
        for (String n : new String[] { "STC_1.class", "STC_1$1.class", "STC_1$Inner.class", "STC_1.java", "STC_12.class", "STC_2.class", "STC_2.java" }) {
            Files.write(pkg.resolve(n), new byte[] { 1 });
        }
        assertThat(SeleniumCodeGeneratorAndUtil.classFilesFor(tmp.toFile(), List.of("com.gatf.selenium.STC_1")))
                .containsExactly("com/gatf/selenium/STC_1$1.class", "com/gatf/selenium/STC_1$Inner.class", "com/gatf/selenium/STC_1.class");
        assertThat(SeleniumCodeGeneratorAndUtil.classFilesFor(tmp.toFile(), List.of("com.gatf.selenium.STC_2", "com.gatf.selenium.Missing")))
                .containsExactly("com/gatf/selenium/STC_2.class");
        assertThat(SeleniumCodeGeneratorAndUtil.classFilesFor(tmp.toFile(), List.of("../../etc/x", "a..b", ""))).isEmpty();
    }
}
