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

import com.gatf.selenium.route.RouteSpec;

/**
 * M4 — {@code route …} / {@code unroute …}: network mocking for the current browser session (see
 * {@link RouteSpec} for the grammar). The statement is checked when the script compiles (unless it holds a
 * {@code $} variable) and applied at run time by {@code SeleniumTest.__route__}.
 */
public class RouteCommand extends Command {

    final String statement;

    RouteCommand(String statement, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        this.statement = statement.trim();
        if (this.statement.indexOf('$') < 0) {
            try {
                RouteSpec.parse(this.statement);
            } catch (IllegalArgumentException e) {
                throwParseError(cmdDetails, new RuntimeException(e.getMessage()));
            }
        }
    }

    String toCmd() {
        return statement;
    }

    String javacode() {
        return "__route__(get___d___(), evaluate(\"" + esc(statement) + "\"));\n";
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Mock network requests (route)",
            "\troute {METHOD}? {url-glob} fulfill {status} {'body'|body-file}? {type content-type}? {header 'Name: value'}*",
            "\troute {METHOD}? {url-glob} abort {reason}?",
            "\troute {METHOD}? {url-glob} continue {header 'Name: value'}*",
            "\tunroute {url-glob|all}",
            "\turl-glob: ** = anything, * = anything but '/', {a,b} = alternatives; the first matching route wins",
            "Examples :-",
            "\troute **/api/users fulfill 200 users.json",
            "\troute POST **/api/orders fulfill 201 '{\"id\": 7}'",
            "\troute **/*.{png,jpg} abort",
            "\troute **/api/** continue header 'X-Env: test'",
            "\tunroute all",
        };
    }
}
