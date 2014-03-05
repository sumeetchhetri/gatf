package com.gatf.executor.validator;

/*
Copyright 2013-2014, Sumeet Chhetri

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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.jayway.jsonpath.JsonPath;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * The validator that handles json level node validations after test case execution
 */
public class JSONResponseValidator implements ResponseValidator {

	public void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context) 
	{
		try
		{
			if(testCase.getAexpectedNodes()!=null && !testCase.getAexpectedNodes().isEmpty())
			{
				for (String node : testCase.getAexpectedNodes()) {
					String[] nodeCase = node.split(",");
					String nvalue = null;
					try {
						nvalue = JsonPath.read(response.getResponseBody(), nodeCase[0]).toString();
					} catch (Exception e) {
						throw new AssertionError("Expected Node " + nodeCase[0] + " not found");
					}
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
				context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase)
					.put(context.getGatfExecutorConfig().getAuthExtractAuthParams()[0], identifier);
				Assert.assertNotNull("Authentication token is null", context.getSessionIdentifier(testCase));
			}
			testCaseReport.setStatus("Success");
		} catch (Throwable e) {
			testCaseReport.setStatus("Failed");
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}
			e.printStackTrace();
		}
	}
}
