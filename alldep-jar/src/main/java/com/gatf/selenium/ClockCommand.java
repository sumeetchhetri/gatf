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

import com.gatf.selenium.emulation.FakeClock;

/**
 * M11: {@code clock install [time] | set <time> | fastforward <duration> | pause | resume | uninstall} — a fake clock
 * in the page (Date, performance.now, timers, animation frames), see {@link FakeClock}.
 */
public class ClockCommand extends Command {

    final String spec;

    ClockCommand(String val, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        spec = val.trim();
        if (!spec.contains("$")) {
            try {
                FakeClock.parse(spec);
            } catch (IllegalArgumentException e) {
                throwParseError(cmdDetails, e);
            }
        }
    }

    String toCmd() {
        return "clock " + spec;
    }

    String javacode() {
        return "__clock__(get___d___(), evaluate(\"" + esc(spec) + "\"));\n";
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Control the page's clock (fake Date, timers and animation frames)",
            "\tclock install {optional time: ISO date-time, date or epoch ms}",
            "\tclock set {time}",
            "\tclock fastforward {duration: ms, 30s, 5m, 1h30m or mm:ss}",
            "\tclock pause|resume",
            "\tclock uninstall",
            "Examples :-",
            "\tclock install 2026-01-01T09:00:00Z",
            "\tclock set 2026-12-31T23:59:50Z",
            "\tclock fastforward 10s",
            "\tclock fastforward 01:30",
            "\tclock resume",
            "\tclock pause",
            "\tclock uninstall",
        };
    }
}
