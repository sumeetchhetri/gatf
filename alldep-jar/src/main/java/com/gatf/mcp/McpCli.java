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
package com.gatf.mcp;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M14: {@code gatf mcp [--root <project dir>]} — serves the GATF tools to an MCP client (Claude Code, Claude Desktop,
 * IDE agents) over stdio. stdout carries only JSON-RPC: {@code System.out} is pointed at stderr before anything else
 * can print (log4j, the executor, validateSel all write to System.out).
 */
public final class McpCli {

    public static final String USAGE = "gatf mcp [--root <project-dir>]   (MCP stdio server: list_tests, lint, run, get_report, summarise_trace, record, crawl)";

    private McpCli() {}

    public static int run(String[] args, File cwd, InputStream in, PrintStream rpcOut, PrintStream err) {
        File root = cwd;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("--root") || args[i].equals("-r")) && i + 1 < args.length) root = new File(args[++i]);
            else if (args[i].startsWith("--root=")) root = new File(args[i].substring(7));
            else if (args[i].equals("-h") || args[i].equals("--help")) { err.println(USAGE); return 0; }
            else { err.println("gatf mcp: unknown option " + args[i] + "\n" + USAGE); return 1; }
        }
        if (!root.isDirectory()) { err.println("gatf mcp: not a directory: " + root); return 1; }
        // keep the protocol channel clean: log4j's console appender writes to the real fd (direct=true), so it is pointed
        // at stderr before log4j initialises; everything else that prints to System.out goes to stderr too
        System.setProperty("gatf.console.target", "SYSTEM_ERR");
        System.setOut(err);
        String ver = McpCli.class.getPackage() == null ? null : McpCli.class.getPackage().getImplementationVersion();
        McpServer server = new McpServer("gatf", ver, GatfMcpTools.INSTRUCTIONS);
        GatfMcpTools tools = new GatfMcpTools(root.toPath(), new ObjectMapper(), err);
        tools.register(server);
        err.println("gatf mcp: serving " + server.tools().size() + " tools for " + root.getAbsolutePath());
        try {
            server.serve(new InputStreamReader(in, StandardCharsets.UTF_8), new OutputStreamWriter(rpcOut, StandardCharsets.UTF_8));
            return 0;
        } catch (Exception e) {
            err.println("gatf mcp: " + e);
            return 1;
        } finally {
            tools.close();   // client gone: stop open recordings / crawls (their browsers)
        }
    }
}
