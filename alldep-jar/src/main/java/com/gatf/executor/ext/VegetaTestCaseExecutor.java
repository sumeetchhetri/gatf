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
import com.gatf.executor.core.GatfFunctionHandler;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;

public class VegetaTestCaseExecutor {

	public static void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean isSingleExecutionContext) throws Exception {
		GatfExecutorConfig config = context.getGatfExecutorConfig();

		if(tc.getPerfConfig()!=null && tc.getPerfConfig().getType()!=null && tc.getPerfConfig().getType().startsWith("vegeta")) {
			List<String> builderList = new ArrayList<>();
			builderList.add(config.getVegetaPath());
			builderList.add("attack");
			
			Integer c = tc.getPerfConfig().getConnections();
			if(c>=0) {
				builderList.add("-connections");
				builderList.add(c.toString());
			}
			
			Integer t = tc.getPerfConfig().getThreads();
			if(t>=0) {
				builderList.add("-workers");
				builderList.add(t.toString());
			}
			
			Integer r = tc.getPerfConfig().getRate();
			if(r>0) {
				builderList.add("-rate");
				builderList.add(c.toString());
			}
			
			Integer d = tc.getPerfConfig().getDurationSeconds();
			if(d<=0) {
				d = 10;
			}
			builderList.add("-duration");
			builderList.add(d+"s");
			
			Integer to = tc.getPerfConfig().getTimeout();
			if(to>0) {
				builderList.add("-timeout");
				builderList.add(to+"s");
			}
			
			boolean isPlot = StringUtils.isNotBlank(tc.getPerfConfig().getExtras()) && tc.getPerfConfig().getExtras().indexOf("plot=1")!=-1;
			
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
			
			StringBuilder out = new StringBuilder();
			StringBuilder err = new StringBuilder();
			
			long start = System.currentTimeMillis();
 			
 			if(tcr.getTestCase()==null) {
 				tcr.setTestCase(tc);
 			}
 			
			execCmd(config.getVegetaPath(), out, err, tcr, tc, script, builderList, tc.getAurl(), tc.getBaseUrl(), isPlot);
			
			if(isSingleExecutionContext) {
				if(config.getGatfTestDataConfig()!=null && config.isCompareEnabled() && config.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null) {
					for (String burl : config.getGatfTestDataConfig().getCompareEnvBaseUrls()) {
						String aurl = tc.getAurl().replaceFirst(config.getBaseUrl(), burl);
						execCmd(config.getVegetaPath(), out, err, tcr, tc, script, builderList, aurl, burl, isPlot);
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
	
	@SuppressWarnings("unchecked")
	private static void execCmd(String vegetaPath, StringBuilder out, StringBuilder err, TestCaseReport tcr, TestCase tc, File script, List<String> bl, String url, String baseUrl, boolean isPlot) throws Exception {
		File results = File.createTempFile(UUID.randomUUID().toString(), ".bin");
		File target = null;
		List<String> builderList = new ArrayList<>();
		if(tc.getHeaders()!=null && tc.getHeaders().size()>0) {
			StringBuilder httpreq = new StringBuilder(tc.getMethod().toUpperCase() + " " + url);
			httpreq.append("\r\n");
			for (String hdr : tc.getHeaders().keySet()) {
				httpreq.append(hdr);
				httpreq.append(": ");
				httpreq.append(tc.getHeaders().get(hdr));
				httpreq.append("\r\n");
			}
			if(script!=null) {
				httpreq.append("@");
				httpreq.append(script.getAbsolutePath());
			}
			target = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
			FileUtils.write(target, httpreq.toString(), "UTF-8");
			
			bl.add("-format");
			bl.add("http");
			bl.add("-targets");
			bl.add(target.getAbsolutePath());
			builderList = bl;
		} else {
			builderList.add("echo");
			builderList.add("'GET");
			builderList.add(tc.getAurl());
			builderList.add("'|");
			builderList.addAll(bl);
		}
		
		builderList.add("|");
		builderList.add("tee");
		builderList.add(results.getAbsolutePath());
		builderList.add("|");
		builderList.add(vegetaPath);
		builderList.add("report");
		
		StringBuilder out1 = new StringBuilder();
		StringBuilder err1 = new StringBuilder();
		GatfFunctionHandler.executeCmd(builderList, out1, err1, true);
		if(out1.length()>0) {
			List<String> rbl = new ArrayList<>();
			rbl.add(vegetaPath);
			rbl.add("report");
			rbl.add("-type=json");
			rbl.add(results.getAbsolutePath());
			StringBuilder out2 = new StringBuilder();
			GatfFunctionHandler.executeCmd(rbl, out2, null, true);
			Map<String, Object> retval = WorkflowContextHandler.OM.readValue(out2.toString(), Map.class);
			tcr.getPerfResult().add(retval);
			
			if(isPlot) {
				File plothtml = File.createTempFile(UUID.randomUUID().toString(), ".html");
				rbl = new ArrayList<>();
				rbl.add("cat");
				rbl.add(results.getAbsolutePath());
				rbl.add("|");
				rbl.add(vegetaPath);
				rbl.add("plot");
				rbl.add(">");
				rbl.add(plothtml.getAbsolutePath());
				
				if(retval!=null) {
					GatfFunctionHandler.executeCmd(rbl, null, null, true);
					retval.put("_plot_html_", plothtml.getAbsolutePath());
				}
			}
			
			out.append(String.join(" ", builderList)+"\n\n");
			out.append("Result for ");
			out.append(baseUrl);
			out.append("\n");
			out.append(out1.toString());
			out.append("\n\n");
		}
		if(err1.length()>0) {
			err.append(String.join(" ", builderList)+"\n\n");
			err.append("Error for ");
			err.append(baseUrl);
			err.append("\n");
			err.append(err1.toString());
			err.append("\n\n");
		}
		
		if(target!=null) {
			target.delete();
		}
		
		results.delete();
	}
}
