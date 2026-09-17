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

import org.junit.jupiter.api.Test;

/** Q1: plugins.txt entry validation (Command.pluginSignatureError). */
class PluginSignatureTest {

    public static Object good(Object[] a) { return null; }
    public static Object good2(String name, Object[] a) { return null; }
    public static void voidMethod(Object[] a) { }
    public Object instanceMethod(Object[] a) { return null; }

    private static final String CLS = PluginSignatureTest.class.getName();

    @Test
    void acceptsBothSupportedSignatures() {
        assertThat(Command.pluginSignatureError(CLS, "good")).isNull();
        assertThat(Command.pluginSignatureError(CLS, "good2")).isNull();
        assertThat(Command.pluginSignatureError("com.gatf.selenium.plugins.JsonPlugin", "path")).isNull();
    }

    @Test
    void rejectsVoidNonStaticMissingAndUnknownClass() {
        assertThat(Command.pluginSignatureError(CLS, "voidMethod")).contains("not void");          // Void.class check never matched
        assertThat(Command.pluginSignatureError(CLS, "instanceMethod")).contains("must be static"); // used to NPE at invoke(null, ..)
        assertThat(Command.pluginSignatureError(CLS, "nope")).contains("no public method");
        assertThat(Command.pluginSignatureError("com.example.NoSuch", "x")).contains("class not found");
    }
}
