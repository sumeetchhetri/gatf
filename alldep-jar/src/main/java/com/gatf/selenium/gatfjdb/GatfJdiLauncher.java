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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.ListeningConnector;

/**
 * Launches a debuggee JVM using PUBLIC JDI API only. Replaces GatfDebuggerLauncher + the shadowed
 * org.jdiscript.util.VMLauncher, which reflected into jdk.jdi internals (com.sun.tools.jdi.*) and so
 * failed on Java 17+ without --add-opens (InaccessibleObjectException → "quote is null" NPE).
 *
 * Flow: SocketListen on 127.0.0.1:0 (the loopback pinning the old launcher existed for) → start
 * {@code java -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:<port>,suspend=y,server=n} with
 * ProcessBuilder argv (classpaths/args with spaces are safe) → accept(). The debuggee's output is
 * inherited by GATF's stdout/stderr, so it can never block on a full pipe.
 */
public final class GatfJdiLauncher {

    public static final class Launched {
        public final VirtualMachine vm;
        public final Process process;

        Launched(VirtualMachine vm, Process process) {
            this.vm = vm;
            this.process = process;
        }
    }

    private GatfJdiLauncher() {}

    public static Launched launch(String classpath, String mainClass, List<String> args) throws Exception {
        ListeningConnector lc = null;
        for (ListeningConnector c : Bootstrap.virtualMachineManager().listeningConnectors()) {
            if ("com.sun.jdi.SocketListen".equals(c.name())) lc = c;
        }
        if (lc == null) throw new IllegalStateException("JDI SocketListen connector not available (need a full JDK/JRE with jdk.jdi)");
        Map<String, Connector.Argument> ca = lc.defaultArguments();
        ca.get("localAddress").setValue("127.0.0.1");
        ca.get("port").setValue("0");
        ca.get("timeout").setValue(System.getProperty("gatf.debug.acceptTimeoutMs", "30000"));
        String address = lc.startListening(ca);
        Process p = null;
        try {
            String port = address.substring(address.lastIndexOf(':') + 1);
            List<String> cmd = new ArrayList<String>();
            cmd.add(new File(new File(System.getProperty("java.home"), "bin"),
                    File.separatorChar == '\\' ? "java.exe" : "java").getAbsolutePath());
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("-agentlib:jdwp=transport=dt_socket,address=127.0.0.1:" + port + ",suspend=y,server=n");
            cmd.add(mainClass);
            if (args != null) cmd.addAll(args);
            p = new ProcessBuilder(cmd).inheritIO().start();
            VirtualMachine vm = lc.accept(ca);
            return new Launched(vm, p);
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            throw e;
        } finally {
            lc.stopListening(ca);
        }
    }
}
