package com.gatf.executor.ext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfFunctionHandler;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;

public class AutocannonTestCaseExecutor {

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext) throws Exception {
		GatfExecutorConfig config = context.getGatfExecutorConfig();

		if(tc.getPerfConfig()!=null && tc.getPerfConfig().getType()!=null && tc.getPerfConfig().getType().startsWith("autocannon")) {
			List<String> builderList = new ArrayList<>();
			builderList.add(config.getAutocannonPath());
			
			Integer c = tc.getPerfConfig().getConnections();
			if(c>=0) {
				builderList.add("-c");
				builderList.add(c.toString());
			}
			
			Integer t = tc.getPerfConfig().getThreads();
			if(t>=0) {
				builderList.add("-w");
				builderList.add(t.toString());
			}
			
			Integer r = tc.getPerfConfig().getRate();
			if(r>0) {
				builderList.add("-R");
				builderList.add(c.toString());
			}
			
			builderList.add("--no-progress");
			
			Integer d = tc.getPerfConfig().getDurationSeconds();
			if(d<=0) {
				d = 10;
			}
			builderList.add("-d");
			builderList.add(d.toString());
			
			Integer to = tc.getPerfConfig().getTimeout();
			if(to>0) {
				builderList.add("-t");
				builderList.add(to.toString());
			}
			
			if(tc.getPerfConfig().isLatency()) {
				builderList.add("--latency");
			}
			
			File script = null;
			
			if(StringUtils.isNotBlank(tc.getPerfConfig().getFilePath()) && context.getResourceFile(tc.getPerfConfig().getFilePath().trim()).exists()) {
				script = context.getResourceFile(tc.getPerfConfig().getFilePath().trim());
			}
			
			if(script==null && StringUtils.isNotBlank(tc.getContentFile())) {
				if(context.getResourceFile(tc.getContentFile().trim()).exists()) {
					script = context.getResourceFile(tc.getContentFile().trim());
				}
			}
			
			if(script==null && StringUtils.isNotBlank(tc.getContent())) {
				script = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
				FileUtils.write(script, tc.getContent(), "UTF-8");
			}
			
			if(!tc.getMethod().equalsIgnoreCase("GET")) {
				if(script!=null) {
					builderList.add("-b");
					builderList.add(script.getAbsolutePath());
				}
			}
			
			for (String hdr : tc.getHeaders().keySet()) {
				builderList.add("-H");
				builderList.add(hdr+"="+tc.getHeaders().get(hdr));
			}
			
			builderList.add(tc.getAurl());
			
			StringBuilder out = new StringBuilder();
			StringBuilder err = new StringBuilder();
			
			long start = System.currentTimeMillis();
 			
 			if(tcr.getTestCase()==null) {
 				tcr.setTestCase(tc);
 			}
			executeInt(builderList, out, err, tcr, tc.getBaseUrl());
			
			if(isSingleExecutionContext) {
				if(config.getGatfTestDataConfig()!=null && config.isCompareEnabled() && config.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null) {
					for (String burl : config.getGatfTestDataConfig().getCompareEnvBaseUrls()) {
						String aurl = tc.getAurl().replaceFirst(config.getBaseUrl(), burl);
						builderList.set(builderList.size()-1, aurl);
						executeInt(builderList, out, err, tcr, burl);
					}
				}
			}
			
			if(script!=null) {
				script.delete();
			}

			if(out.length()>0) {
				tcr.setResponseContent(out.toString());
				tcr.setResponseContentType(MediaType.TEXT_PLAIN);
				tcr.setStatus(TestStatus.Success.status);
			}
 			if(err.length()>0) {
 				tcr.setErrorText(err.toString());
				tcr.setStatus(TestStatus.Failed.status);
			}
 			
 			tcr.setExecutionTime(System.currentTimeMillis() - start);
		}
	}
	
	public static void executeInt(List<String> builderList, StringBuilder out, StringBuilder err, TestCaseReport tcr, String baseUrl) {
		StringBuilder out1 = new StringBuilder();
		StringBuilder err1 = new StringBuilder();
		GatfFunctionHandler.executeCmd(builderList, out1, err1, true);
		if(err1.length()>0) {
			//Map<String, Object> retval = parse_autocannon_output(out1);
			//tcr.getPerfResult().add(retval);
			out.append(String.join(" ", builderList)+"\n\n");
			out.append("Result for ");
			out.append(baseUrl);
			out.append("\n");
			String errd = err1.toString();
			errd = errd.replace('?', '*');
			out.append(errd);
			out.append("\n\n");
		}
		/*if(err1.length()>0) {
			err.append(String.join(" ", builderList)+"\n\n");
			err.append("Error for ");
			err.append(baseUrl);
			err.append("\n");
			err.append(err1.toString());
			err.append("\n\n");
		}*/
	}
}
