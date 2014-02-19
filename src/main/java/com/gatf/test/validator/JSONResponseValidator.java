package com.gatf.test.validator;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;

import com.gatf.report.TestCaseReport;
import com.gatf.test.core.AcceptanceTestContext;
import com.gatf.test.core.TestCase;
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
					Assert.assertNotNull("Expected Node " + nodeCase[0] + " is null", nvalue);
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
				context.setSessionIdentifier(identifier, testCase);
				Assert.assertNotNull("Authentication token is null", context.getSessionIdentifier(testCase));
			}
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Success");
			}
		} catch (Throwable e) {
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
					testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
				}
			}
			e.printStackTrace();
		}
	}
}
