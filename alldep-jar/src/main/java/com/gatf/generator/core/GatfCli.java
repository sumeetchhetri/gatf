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

import java.io.FileInputStream;
import java.io.InputStream;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.RunOverrides;
import com.gatf.executor.core.TestFilter;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.distributed.DistributedGatfListener;
import com.gatf.log.GatfLogging;
import com.gatf.log.RunConsole;
import com.gatf.selenium.Command;
import com.gatf.ui.GatfConfigToolUtil;

/**
 * G8 — the CLI entry for every mode (-generator / -executor / -selenium / -configtool / -listener /
 * -validate-sel), extracted from GatfTestGeneratorUtil.main (still the jar's Main-Class; it delegates).
 * Fixes: a -generator config that is neither .xml nor .json showed an NPE instead of the help text, and
 * the process always exited 0 even on invalid usage or failure.
 */
public final class GatfCli {

	private GatfCli() {}

    public static void main(String[] args) throws Exception
    {
		// Install ByteBuddy agent programmatically
        //ByteBuddyAgent.install();

    	System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    	boolean showHelp = false;
    	int exitCode = 0;
    	if(args.length>=1) {
    		if(args.length>1 && args[0].equals("-generator") && !args[1].trim().isEmpty())
    		{
    			GatfConfiguration config = null;
    			
    			if(args[1].trim().endsWith(".xml")) {
    				InputStream io = new FileInputStream(args[1]);
		    		config = (GatfConfiguration) WorkflowContextHandler.XOM.readValue(io, GatfConfiguration.class);
    			} else if(args[1].trim().endsWith(".json")) {
    				InputStream io = new FileInputStream(args[1]);
    				config = WorkflowContextHandler.OM.readValue(io, GatfConfiguration.class);
    			} else {
    				showHelp = true;
    			}
	    		
	    		if(config != null) {
	    		GatfTestGeneratorUtil testGenerator = new GatfTestGeneratorUtil();
	    		testGenerator.setDebugEnabled(config.isDebugEnabled());
	    		testGenerator.setEnabled(config.isEnabled());
	    		testGenerator.setRequestDataType(config.getRequestDataType());
	    		testGenerator.setTestPaths(config.getTestPaths());
	    		testGenerator.setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
	    		testGenerator.setUrlPrefix(config.getUrlPrefix());
	    		testGenerator.setResourcepath(config.getResourcepath());
	    		testGenerator.setOutDataType(config.getResponseDataType()); 
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.setUrlSuffix(config.getUrlSuffix());
	    		testGenerator.setUseSoapClient(config.isUseSoapClient());
	    		testGenerator.setTestCaseFormat(config.getTestCaseFormat());
	    		testGenerator.setPostmanCollectionVersion(config.getPostmanCollectionVersion());
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.execute();
	    		}
    		}
    		else if(args.length>1 && (args[0].equals("-executor") || args[0].equals("-selenium")) && !args[1].trim().isEmpty())
    		{
    			exitCode = runSuite(args);
    		}
    		else if(args[0].equals("test"))
    		{
    			// Q7: `gatf test [config] [--grep ..] [--tag ..] [--reporter ..] [--trace ..] [--retries N] [--workers N]`
    			TestCommandArgs t = null;
    			try {
    				t = TestCommandArgs.parse(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")));
    				TestFilter f = new TestFilter(t.grep, t.grepInvert, t.tags);   // invalid regex -> IllegalArgumentException
    				RunOverrides.set(t.traceMode, t.retries, t.workers, t.forbidOnly);          // invalid --trace -> IllegalArgumentException
    				RunOverrides.setShard(t.shard);                                              // M7: --shard i/n
    				RunOverrides.setUpdateSnapshots(t.updateSnapshots);                          // M9
    				TestFilter.activate(f);
    				String note = f.isEmpty() ? null : f.describe();
    				if (RunOverrides.shard() != null) note = (note == null ? "" : note + ", ") + RunOverrides.shard().describe();
    				RunConsole.setFilterNote(note);
    				RunConsole.setShard(RunOverrides.shard() == null ? null : RunOverrides.shard().toString());
    				if (t.reporters != null) {
    					// Q11: the docs reporter needs the JSON results
    					String rep = t.reporters.toLowerCase();
    					if (rep.contains("docs") && !rep.contains("json")) rep += ",json";
    					System.setProperty("gatf.reporters", rep);
    				}
    			} catch (IllegalArgumentException e) {
    				System.err.println("gatf test: " + e.getMessage());
    				t = null;
    				exitCode = 1;
    			}
    			if (t != null && t.watch) {
    				// M15: `gatf test --watch` — each run is a child JVM; this process only watches
    				exitCode = WatchMode.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.in, System.out);
    			} else if (t != null) {
    				exitCode = runSuite(new String[] { "-executor", t.config });
    				if (t.reporters != null && t.reporters.toLowerCase().contains("docs")) {
    					postRunDocs(t.config);
    				}
    			}
    		}
    		else if(args[0].equals("docs"))
    		{
    			// Q11: test-case documents + traceability matrix + coverage (xlsx/csv)
    			exitCode = DocsCli.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.out, System.err);
    		}
    		else if(args[0].equals("import"))
    		{
    			// Q2: `gatf import <playwright|jmeter|curl|postman|har|openapi> <file|-> [-o out] [--name ..] [--filter ..] [--force]`
    			exitCode = ImportCli.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.out, System.err);
    		}
    		else if(args[0].equals("export"))
    		{
    			// Δ160: `gatf export playwright <file.sel|dir> [-o out] [--strict-locators] [--force]` (seleasy -> Playwright TS)
    			exitCode = ExportCli.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.out, System.err);
    		}
    		else if(args[0].equals("merge-reports"))
    		{
    			// M7: `gatf merge-reports <dir>... [-o out]` joins the out dirs of a sharded run into one report
    			exitCode = com.gatf.log.ReportMerger.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.out, System.err);
    		}
    		else if(args[0].equals("mcp"))
    		{
    			// M14: `gatf mcp [--root dir]` — MCP stdio server for coding agents (stdout is the protocol channel)
    			exitCode = com.gatf.mcp.McpCli.run(java.util.Arrays.copyOfRange(args, 1, args.length), new java.io.File(System.getProperty("user.dir")), System.in, System.out, System.err);
    		}
    		else if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
    				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty())
    		{
    			GatfConfigToolUtil.main(args);
    		}
    		else if(args[0].equals("-listener"))
    		{
    			DistributedGatfListener.main(args);
    		}
    		else if(args[0].equals("-validate-sel"))
    		{
    			Command.validateSel(args, null, false);
    		}
    		else
    		{
    			showHelp = true;
    		}
    	} else {
    		showHelp = true;
    	}
    	
    	if(showHelp) {
    		System.out.println("Invalid invocation - valid invocation options are, \n" +
					"java -jar gatf-plugin-{version}.jar " + TestCommandArgs.USAGE.substring("gatf ".length()).replace("\n", "\n    ") + "\n" +
					"java -jar gatf-plugin-{version}.jar " + ImportCli.USAGE.substring("gatf ".length()) + "\n" +
					"java -jar gatf-plugin-{version}.jar " + ExportCli.USAGE.substring("gatf ".length()) + "\n" +
					"java -jar gatf-plugin-{version}.jar " + DocsCli.USAGE.substring("gatf ".length()) + "\n" +
					"java -jar gatf-plugin-{version}.jar " + com.gatf.log.ReportMerger.USAGE.substring("gatf ".length()) + "\n" +
					"java -jar gatf-plugin-{version}.jar " + com.gatf.mcp.McpCli.USAGE.substring("gatf ".length()) + "\n" +
					"java -jar gatf-plugin-{version}.jar -generator {generator-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -executor {executor-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -selenium {selenium-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -configtool {http_port} {ip_address} {project_folder}\n" + 
					"java -jar gatf-plugin-{version}.jar -validate-sel {file-name/relative/to/workdir}\n" + 
					"java -jar gatf-plugin-{version}.jar -listener\n");
    	}
    	
		System.exit(showHelp ? 1 : exitCode);   // non-zero on invalid usage, an aborted run, or any failed test
    }

    /** Q11: `--reporter …,docs` — documents with this run's status into <report dir>/docs. Never changes the exit code. */
    private static void postRunDocs(String config) {
    	try {
    		String dir = RunConsole.reportDir();
    		if (dir == null) return;
    		com.gatf.docs.TestDocsGenerator.Options o = new com.gatf.docs.TestDocsGenerator.Options();
    		o.outDir = new java.io.File(dir, "docs").toPath();
    		java.io.File res = new java.io.File(dir, "gatf-results.json");
    		if (res.isFile()) o.resultsFile = res.toPath();
    		DocsCli.generate(java.util.Collections.singletonList(config), new java.io.File(System.getProperty("user.dir")), o, GatfLogging.out());
    	} catch (Exception e) {
    		System.err.println("gatf test: docs not written: " + e.getMessage());
    	}
    }

    /**
     * Runs an API or UI suite with the CLI reporter: quiet console, a detailed log file, CI reports, and an exit code
     * of 1 when any test failed or the run aborted. {@code args} = {"-executor"|"-selenium", config}.
     */
    private static int runSuite(String[] args) {
    	// must precede log4j initialisation
    	GatfLogging.initForRun();
    	RunConsole.activate();
    	try {
    		GatfTestCaseExecutorUtil.main(args);
    		return RunConsole.finish() > 0 ? 1 : 0;
    	} catch (Throwable e) {
    		// Throwable, not Exception: execute() wraps every failure in an AssertionError, so a UI run whose scripts
    		// failed (GatfRunTimeErrors) used to escape here with no summary and no CI reports.
    		Throwable cause = (e instanceof AssertionError && e.getCause() != null) ? e.getCause() : e;
    		for (Throwable c = cause; c != null; c = c.getCause()) {
    			if (c instanceof com.gatf.executor.core.ShardSplitter.EmptyShardException) {
    				// M7: more shards than files: this shard has nothing to run, which is not a failure
    				GatfLogging.out().println("  " + c.getMessage());
    				return RunConsole.finish() > 0 ? 1 : 0;
    			}
    			if (c.getCause() == c) break;
    		}
    		// Failed tests explain the exception: print the summary, not "aborted". Nothing ran (e.g. no test matched the
    		// filter, or the config failed to load): just the reason.
    		if (!RunConsole.hasResults() || RunConsole.finish() == 0) RunConsole.abort(cause);
    		return 1;
    	}
    }
}
