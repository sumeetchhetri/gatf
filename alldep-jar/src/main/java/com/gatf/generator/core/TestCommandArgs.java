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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Q7: arguments of {@code gatf test} (the one documented entry point for CLI runs):
 * <pre>
 *   gatf test [config] [--grep RE]... [--grep-invert RE]... [--tag T]... [--reporter junit,json,github,docs|none]
 *             [--trace off|on|retain-on-failure|on-first-retry] [--retries N] [--workers N]
 * </pre>
 * The config defaults to gatf-config.json, else gatf-config.xml, in the working directory. Whether the suite is
 * API or UI comes from the config's isSeleniumExecutor, exactly as for -executor / -selenium. Pure parsing; no side
 * effects, so it is unit-testable.
 */
public final class TestCommandArgs {

    public String config;
    public final List<String> grep = new ArrayList<String>();
    public final List<String> grepInvert = new ArrayList<String>();
    public final List<String> tags = new ArrayList<String>();
    public String reporters;
    public String traceMode;
    public Integer retries;
    public Integer workers;

    public static final String USAGE =
            "gatf test [config] [--grep RE]... [--grep-invert RE]... [--tag T]... [--reporter junit,json,github,docs|none]\n"
          + "          [--trace off|on|retain-on-failure|on-first-retry] [--retries N] [--workers N]";

    /** Parses {@code args} (everything after "test"). Throws IllegalArgumentException with a usage message on error. */
    public static TestCommandArgs parse(String[] args, File cwd) {
        TestCommandArgs a = new TestCommandArgs();
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            String v = null;
            int eq = s.startsWith("--") ? s.indexOf('=') : -1;
            if (eq > 0) {                       // --grep=foo form
                v = s.substring(eq + 1);
                s = s.substring(0, eq);
            }
            switch (s) {
                case "--grep": case "-g":          a.grep.add(val(args, i, s, v)); break;
                case "--grep-invert":              a.grepInvert.add(val(args, i, s, v)); break;
                case "--tag": case "-t":           a.tags.add(val(args, i, s, v)); break;
                case "--reporter":                 a.reporters = val(args, i, s, v); break;
                case "--trace":                    a.traceMode = val(args, i, s, v); break;
                case "--retries":                  a.retries = num(val(args, i, s, v), s); break;
                case "--workers": case "-j":       a.workers = num(val(args, i, s, v), s); break;
                default:
                    if (s.startsWith("-")) throw new IllegalArgumentException("Unknown option " + s + "\n" + USAGE);
                    if (a.config != null) throw new IllegalArgumentException("Only one config file may be given (got " + a.config + " and " + s + ")\n" + USAGE);
                    a.config = s;
                    continue;
            }
            if (v == null) i++;                 // the value was the next argument
        }
        if (a.config == null) {
            for (String d : new String[] { "gatf-config.json", "gatf-config.xml" }) {
                if (new File(cwd, d).isFile()) { a.config = d; break; }
            }
            if (a.config == null) {
                throw new IllegalArgumentException("No config given and no gatf-config.json / gatf-config.xml in " + cwd.getAbsolutePath() + "\n" + USAGE);
            }
        }
        if (a.retries != null && a.retries < 0) throw new IllegalArgumentException("--retries must be >= 0");
        if (a.workers != null && a.workers < 1) throw new IllegalArgumentException("--workers must be >= 1");
        return a;
    }

    private static String val(String[] args, int i, String opt, String inline) {
        if (inline != null) return inline;
        if (i + 1 >= args.length) throw new IllegalArgumentException(opt + " needs a value\n" + USAGE);
        return args[i + 1];
    }

    private static Integer num(String v, String opt) {
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(opt + " expects a number, got '" + v + "'");
        }
    }
}
