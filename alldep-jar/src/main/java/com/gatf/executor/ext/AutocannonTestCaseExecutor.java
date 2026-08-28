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

public class AutocannonTestCaseExecutor {

	/** autocannon argv after the launch prefix (no shell — header values/URLs are passed verbatim). */
	static List<String> args(String method, String url, Map<String, String> headers, File body, int connections,
			int workers, int rate, int durationSecs, int timeoutSecs) {
		List<String> a = new ArrayList<>();
		a.add("--json");
		a.add("--no-progress");
		if(connections>0) { a.add("-c"); a.add(String.valueOf(connections)); }
		if(workers>0) { a.add("-w"); a.add(String.valueOf(workers)); }
		if(rate>0) { a.add("-R"); a.add(String.valueOf(rate)); }          // was: the connection count
		a.add("-d"); a.add(String.valueOf(durationSecs));
		if(timeoutSecs>0) { a.add("-t"); a.add(String.valueOf(timeoutSecs)); }
		if(StringUtils.isNotBlank(method)) { a.add("-m"); a.add(method.trim().toUpperCase()); }  // was: never sent → always GET
		if(body!=null) { a.add("-i"); a.add(body.getAbsolutePath()); }     // was: -b <path> → sent the PATH as the body
		if(headers!=null) {
			for (Map.Entry<String, String> h : headers.entrySet()) {
				a.add("-H");
				a.add(h.getKey() + "=" + (h.getValue()==null ? "" : h.getValue()));
			}
		}
		a.add(url);
		return a;
	}

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext) throws Exception {
		GatfExecutorConfig config = context.getGatfExecutorConfig();
		if(tc.getPerfConfig()==null || tc.getPerfConfig().getType()==null || !tc.getPerfConfig().getType().startsWith("autocannon")) {
			return;
		}
		PerformanceConfig pc = tc.getPerfConfig();
		if(tcr.getTestCase()==null) {
			tcr.setTestCase(tc);
		}
		List<String> prefix = PerfToolProvisioner.command("autocannon", config);
		if(prefix==null) {
			tcr.setErrorText("autocannon is not available: no autocannonPath, no Node.js on PATH/nodePath, and the portable Node download failed (check network/proxy to nodejs.org and registry.npmjs.org)");
			tcr.setStatus(TestStatus.Failed.status);
			return;
		}
		int d = pc.getDurationSeconds()<=0 ? 10 : pc.getDurationSeconds();

		// body for non-GET: perfConfig.filePath, else contentFile, else content (only our temp file is deleted)
		File body = null;
		boolean tempBody = false;
		if(!"GET".equalsIgnoreCase(tc.getMethod())) {
			if(StringUtils.isNotBlank(pc.getFilePath()) && context.getResourceFile(pc.getFilePath().trim()).exists()) {
				body = context.getResourceFile(pc.getFilePath().trim());
			} else if(StringUtils.isNotBlank(tc.getContentFile()) && context.getResourceFile(tc.getContentFile().trim()).exists()) {
				body = context.getResourceFile(tc.getContentFile().trim());
			} else if(StringUtils.isNotBlank(tc.getContent())) {
				body = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
				FileUtils.write(body, tc.getContent(), "UTF-8");
				tempBody = true;
			}
		}

		List<String[]> targets = new ArrayList<>();
		targets.add(new String[] {tc.getAurl(), tc.getBaseUrl()});
		if(isSingleExecutionContext && config.getGatfTestDataConfig()!=null && config.isCompareEnabled() && config.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null) {
			for (String burl : config.getGatfTestDataConfig().getCompareEnvBaseUrls()) {
				targets.add(new String[] {tc.getAurl().replaceFirst(config.getBaseUrl(), burl), burl});
			}
		}
		StringBuilder out = new StringBuilder(), err = new StringBuilder();
		long start = System.currentTimeMillis();
		try {
			for (String[] tg : targets) {
				List<String> cmd = new ArrayList<>(prefix);
				cmd.addAll(args(tc.getMethod(), tg[0], tc.getHeaders(), body, pc.getConnections(), pc.getThreads(), pc.getRate(), d, pc.getTimeout()));
				File so = File.createTempFile("gatf-ac-", ".out"), se = File.createTempFile("gatf-ac-", ".err");
				try {
					int rc = PerfToolProvisioner.exec(cmd, so, se);
					String json = FileUtils.readFileToString(so, "UTF-8").trim();
					String stderr = FileUtils.readFileToString(se, "UTF-8");
					out.append(String.join(" ", cmd)).append("\n\nResult for ").append(tg[1]).append("\n").append(json).append("\n\n");
					if(rc==0 && json.startsWith("{")) {
						@SuppressWarnings("unchecked")
						Map<String, Object> retval = WorkflowContextHandler.OM.readValue(json, Map.class);
						retval.put("tool", "autocannon");
						tcr.getPerfResult().add(retval);
					} else {
						err.append(String.join(" ", cmd)).append("\n\nError for ").append(tg[1]).append(" (rc=").append(rc).append(")\n").append(stderr).append("\n\n");
					}
				} finally {
					so.delete();
					se.delete();
				}
			}
		} finally {
			if(tempBody) body.delete();
		}
		tcr.setResponseContent(out.toString());
		tcr.setResponseContentType(MediaType.TEXT_PLAIN);
		tcr.setStatus(TestStatus.Success.status);
		if(err.length()>0) {
			tcr.setErrorText(err.toString());
			tcr.setStatus(TestStatus.Failed.status);
		}
		tcr.setExecutionTime(System.currentTimeMillis() - start);
	}
}
