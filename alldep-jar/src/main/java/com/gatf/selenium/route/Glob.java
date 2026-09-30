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
package com.gatf.selenium.route;

import java.util.regex.Pattern;

/**
 * M4 — URL globs for {@code route}, matched against the full request URL (Playwright semantics): {@code **} = any
 * characters, {@code *} = any characters except '/', {@code {a,b}} = alternatives; everything else, including
 * {@code ?}, is literal; a backslash makes the next character literal ({@code \*}, {@code \{}). The whole URL must
 * match, so {@code **}{@code /api/users} does not match {@code /api/users?page=2} (write {@code **}{@code /api/users*}).
 * A glob starting with '/' is matched against the path and query of any origin.
 */
public final class Glob {

    private Glob() {}

    public static Pattern toRegex(String glob) {
        if (glob == null || glob.isEmpty()) throw new IllegalArgumentException("empty URL pattern");
        StringBuilder b = new StringBuilder("^");
        if (glob.startsWith("/")) b.append("[A-Za-z][A-Za-z0-9+.-]*://[^/]*");
        int depth = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '\\' && i + 1 < glob.length()) {
                c = glob.charAt(++i);
                if (!Character.isLetterOrDigit(c)) b.append('\\');
                b.append(c);
            } else if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    b.append(".*");
                    i++;
                } else {
                    b.append("[^/]*");
                }
            } else if (c == '{') {
                depth++;
                b.append("(?:");
            } else if (c == '}' && depth > 0) {
                depth--;
                b.append(')');
            } else if (c == ',' && depth > 0) {
                b.append('|');
            } else {
                if ("\\.[]()^$|+?".indexOf(c) >= 0 || c == '{' || c == '}') b.append('\\');
                b.append(c);
            }
        }
        if (depth != 0) throw new IllegalArgumentException("unbalanced '{' in URL pattern " + glob);
        return Pattern.compile(b.append('$').toString());
    }
}
