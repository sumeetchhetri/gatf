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
package com.gatf.executor.core;

/** M5/M6 — helpers for the scripts GATF runs around the UI suite (loginOnce, globalSetup, globalTeardown). Pure. */
public final class SetupScripts {

    private SetupScripts() {}

    /** Same script regardless of separators, a leading "./" or letter case of the extension. */
    public static boolean sameScript(String a, String b) {
        return a != null && b != null && norm(a).equals(norm(b));
    }

    static String norm(String p) {
        String s = p.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".sel")) s = s.substring(0, s.length() - 4) + ".sel";
        return s;
    }

    /** Report run prefix for a setup script, e.g. "loginOnce" → "Setup-loginOnce". */
    public static String runPrefix(String kind) {
        return "Setup-" + kind;
    }
}
