package com.gatf.test;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;

import com.gatf.report.TestCaseReport;
import com.jayway.jsonpath.JsonPath;
import com.ning.http.client.Response;

public class JSONResponseValidator implements ResponseValidator {

	public void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context) 
	{
		try
		{
			if(testCase.getAexpectedNodes()!=null && !testCase.getAexpectedNodes().isEmpty())
			{
				for (String node : testCase.getAexpectedNodes()) {
					String[] nodeCase = node.split(",");
					String nvalue = JsonPath.read(response.getResponseBody(), nodeCase[0]);
					Assert.assertNotNull(nvalue);
					if(nodeCase.length==2) {
						Assert.assertEquals(nvalue, nodeCase[1]);
					}
				}
			}
			context.getWorkflowContextHandler().extractJsonWorkflowVariables(testCase, response.getResponseBody());
			
			String url = testCase.getAurl();
			if(url.indexOf("?")!=-1) {
				url = url.substring(0, url.indexOf("?"));
			}
			
			if(context.getGatfExecutorConfig().isAuthEnabled() && context.getGatfExecutorConfig().getAuthUrl().equals(testCase.getUrl())
					&& context.getGatfExecutorConfig().getAuthExtractAuthParams()[1].equalsIgnoreCase("json")) {
				String identifier = JsonPath.read(response.getResponseBody(), context.getGatfExecutorConfig().getAuthExtractAuthParams()[0]);
				context.setSessionIdentifier(identifier);
				Assert.assertNotNull(context.getSessionIdentifier());
			}
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Success");
			}
		} catch (Exception e) {
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			}
			e.printStackTrace();
		} catch (Error e) {
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			}
			e.printStackTrace();
		}
	}
}
