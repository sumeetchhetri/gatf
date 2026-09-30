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

import com.gatf.selenium.emulation.Emulation;

/**
 * M8: {@code emulate device "<name>" [landscape] | viewport | geolocation | locale | timezone | permissions |
 * colorscheme | offline | online | network | reset} (see {@link Emulation}). Validated when the script is compiled
 * (unless it uses {@code ${…}}); applied over CDP or WebDriver BiDi at run time.
 */
public class EmulateCommand extends Command {

    final String spec;

    EmulateCommand(String val, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        spec = val.trim();
        if (!spec.contains("$")) {
            try {
                Emulation.parse(spec);
            } catch (IllegalArgumentException e) {
                throwParseError(cmdDetails, e);
            }
        }
    }

    String toCmd() {
        return "emulate " + spec;
    }

    String javacode() {
        return "__emulate__(get___d___(), evaluate(\"" + esc(spec) + "\"));\n";
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Emulate a device, location, locale, time zone, permissions, colour scheme or network (CDP / WebDriver BiDi)",
            "\temulate device {device-name} {optional landscape}",
            "\temulate viewport {width} {height} {optional scale}",
            "\temulate geolocation {latitude} {longitude} {optional accuracy}",
            "\temulate locale {BCP 47 tag}",
            "\temulate timezone {IANA time zone}",
            "\temulate permissions {name,name,...}|clear",
            "\temulate colorscheme dark|light|no-preference",
            "\temulate offline|online",
            "\temulate network slow3g|slow4g|fast4g|off|{down kbit/s} {up kbit/s} {latency ms}",
            "\temulate reset",
            "Examples :-",
            "\temulate device \"iPhone 13\"",
            "\temulate device \"Pixel 7\" landscape",
            "\temulate viewport 1024 768",
            "\temulate geolocation 51.5072 -0.1276",
            "\temulate locale de-DE",
            "\temulate timezone Europe/Berlin",
            "\temulate permissions geolocation,notifications",
            "\temulate colorscheme dark",
            "\temulate offline",
            "\temulate network slow3g",
            "\temulate reset",
        };
    }
}
