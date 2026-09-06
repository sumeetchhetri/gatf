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
package com.gatf.selenium.gatfjdb;

import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.List;

/**
 * GATF runs fine on a plain Java 21+ runtime (JRE): tests are compiled by the bundled ECJ. Only the
 * online debugger needs JDK-only modules — jdk.jdi (GATF side, com.sun.jdi) and jdk.jdwp.agent (the
 * debuggee JVM, started from the same java.home with -agentlib:jdwp). This class has NO JDI imports, so
 * it can be called before anything JDI-related loads and turn a NoClassDefFoundError into a clear message.
 */
public final class DebuggerSupport {

    private DebuggerSupport() {}

    /** Modules the debugger needs that this runtime image lacks (empty = debugger available). */
    public static List<String> missingModules() {
        List<String> miss = new ArrayList<String>();
        ModuleFinder sys = ModuleFinder.ofSystem();   // the runtime image, not just the resolved boot layer
        for (String m : new String[] { "jdk.jdi", "jdk.jdwp.agent" }) {
            if (!sys.find(m).isPresent()) miss.add(m);
        }
        return miss;
    }

    public static void requireDebugger() {
        List<String> miss = missingModules();
        if (!miss.isEmpty()) {
            throw new IllegalStateException("The online debugger needs a JDK (missing " + String.join(", ", miss)
                    + " in " + System.getProperty("java.home") + "). Running and recording tests is unaffected. "
                    + "Point GATF at a JDK 21+: set GATF_JAVA_HOME, or rerun the installer with --jdk <dir>.");
        }
    }
}
