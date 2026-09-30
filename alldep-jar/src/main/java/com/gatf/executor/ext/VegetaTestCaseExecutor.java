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
package com.gatf.executor.ext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.PerformanceConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;

public class VegetaTestCaseExecutor {

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext) throws Exception {
		if(tc.getPerfConfig()!=null && tc.getPerfConfig().getType()!=null && tc.getPerfConfig().getType().startsWith("vegeta")) {
			run(context, tc, tcr, isSingleExecutionContext, null);
		}
	}

	/**
	 * wrk / wrk2 / wperfk test on a machine with no such binary (and, for wrk/wrk2, no Docker): run it on vegeta and say so.
	 * wrk → max-throughput attack (-rate=0, -max-workers=connections); wrk2 → constant rate (-rate=R);
	 * wperfk → constant rate when rate is set, else like wrk.
	 */
	public static void executeAsFallback(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext, String from) throws Exception {
		run(context, tc, tcr, isSingleExecutionContext, from);
	}

	static String fallbackNotice(String from, boolean hadScript) {
		return "NOTICE: " + from + " is not available here (no configured/PATH binary" + ("wperfk".equals(from) ? "" : ", no Docker for the gatf-loadgen container") + ") — "
				+ "this test ran on vegeta instead. Reduced capabilities: no Lua scripting"
				+ (hadScript ? " (perfConfig.filePath was ignored)" : "")
				+ "; vegeta's report format/percentiles instead of wrk's; "
				+ ("wrk2".equals(from) ? "constant-rate open-loop attack (same model as wrk2 -R)."
						: "wperfk".equals(from) ? "constant-rate attack when perfConfig.rate is set (as wperfk -R), else an open-loop max-rate attack."
						: "open-loop max-rate attack approximates wrk's closed-loop max throughput.")
				+ ("wperfk".equals(from)
						? " For native wperfk: download a release from github.com/sumeetchhetri/wperfk and set wperfkPath, or put it on PATH (see docs/load-tools.md)."
						: " For native " + from + ": install it (brew/apt), set " + from + "Path, or run Docker (see docs/load-tools.md).");
	}

	private static void run(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext, String fallbackFrom) throws Exception {
		GatfExecutorConfig config = context.getGatfExecutorConfig();
		PerformanceConfig pc = tc.getPerfConfig();
		if(tcr.getTestCase()==null) {
			tcr.setTestCase(tc);
		}
		List<String> vegeta = PerfToolProvisioner.command("vegeta", config);
		if(vegeta==null) {
			tcr.setErrorText("vegeta is not available and could not be downloaded — set vegetaPath (or check network/proxy to github.com)");
			tcr.setStatus(TestStatus.Failed.status);
			return;
		}
		int c = pc.getConnections(), t = pc.getThreads(), rate = pc.getRate(), to = pc.getTimeout();
		int d = pc.getDurationSeconds()<=0 ? 10 : pc.getDurationSeconds();
		int maxWorkers = 0;
		String notice = null;
		if(fallbackFrom!=null) {
			if("wrk2".equals(fallbackFrom)) {
				rate = rate>0 ? rate : 1000;               // wrk2's default in PerfArgs.wrk
			} else if("wperfk".equals(fallbackFrom) && rate>0) {
				maxWorkers = 0;                            // wperfk -R: constant rate
			} else {
				rate = 0;
				maxWorkers = c>0 ? c : 10;                 // wrk's connection count as the concurrency cap
			}
			t = 0;
			notice = fallbackNotice(fallbackFrom, StringUtils.isNotBlank(pc.getFilePath()));
			System.err.println("[gatf] " + notice);
		}
		boolean isPlot = StringUtils.isNotBlank(pc.getExtras()) && pc.getExtras().indexOf("plot=1")!=-1;

		// request body: perfConfig.filePath (native vegeta only — for wrk it is a Lua script), else contentFile, else content
		File body = null;
		boolean tempBody = false;
		if(fallbackFrom==null && StringUtils.isNotBlank(pc.getFilePath()) && context.getResourceFile(pc.getFilePath().trim()).exists()) {
			body = context.getResourceFile(pc.getFilePath().trim());
		}
		if(body==null && StringUtils.isNotBlank(tc.getContentFile()) && context.getResourceFile(tc.getContentFile().trim()).exists()) {
			body = context.getResourceFile(tc.getContentFile().trim());
		}
		if(body==null && StringUtils.isNotBlank(tc.getContent())) {
			body = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
			FileUtils.write(body, tc.getContent(), "UTF-8");
			tempBody = true;                                // only files WE created are deleted (was: the user's file too)
		}

		List<String[]> targets = new ArrayList<>();
		targets.add(new String[] {tc.getAurl(), tc.getBaseUrl()});
		if(isSingleExecutionContext && config.getGatfTestDataConfig()!=null && config.isCompareEnabled() && config.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null) {
			for (String burl : config.getGatfTestDataConfig().getCompareEnvBaseUrls()) {
				targets.add(new String[] {tc.getAurl().replaceFirst(config.getBaseUrl(), burl), burl});   // per-env URL (was: always the primary)
			}
		}

		StringBuilder out = new StringBuilder(), err = new StringBuilder();
		long start = System.currentTimeMillis();
		try {
			for (String[] tg : targets) {
				VegetaRunner.Result r = VegetaRunner.run(vegeta, tc.getMethod(), tg[0], tc.getHeaders(), body, c, t, rate, maxWorkers, d, to, isPlot);
				if(r.json!=null) {
					@SuppressWarnings("unchecked")
					Map<String, Object> retval = WorkflowContextHandler.OM.readValue(r.json, Map.class);
					if(r.plotHtml!=null) retval.put("_plot_html_", r.plotHtml.getAbsolutePath());
					retval.put("tool", "vegeta");
					if(fallbackFrom!=null) retval.put("fallback_from", fallbackFrom);
					tcr.getPerfResult().add(retval);
				}
				out.append(String.join(" ", r.attackCmd)).append("\n\nResult for ").append(tg[1]).append("\n").append(r.text).append("\n\n");
				if(r.rc!=0 || r.json==null) {
					err.append(String.join(" ", r.attackCmd)).append("\n\nError for ").append(tg[1]).append("\n").append(r.err).append("\n\n");
				}
			}
		} finally {
			if(tempBody) body.delete();
		}
		tcr.setResponseContent((notice!=null ? notice + "\n\n" : "") + out);
		tcr.setResponseContentType(MediaType.TEXT_PLAIN);
		tcr.setStatus(TestStatus.Success.status);
		if(err.length()>0) {
			tcr.setErrorText(err.toString());
			tcr.setStatus(TestStatus.Failed.status);
		}
		tcr.setExecutionTime(System.currentTimeMillis() - start);
	}
}
