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

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * The validator that handles xml level node validations after test case execution
 */
public class XMLResponseValidator implements ResponseValidator {

	public void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context) 
	{
		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xmlDocument = db.parse(new ByteArrayInputStream(response.getResponseBody().getBytes()));

			if(testCase.getAexpectedNodes()!=null && !testCase.getAexpectedNodes().isEmpty())
			{
				for (String node : testCase.getAexpectedNodes()) {
					String[] nodeCase = node.split(",");
					
					String expression = nodeCase[0].replaceAll("\\.", "\\/");
					if(expression.charAt(0)!='/')
						expression = "/" + expression;
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Expected Node " + nodeCase[0] + " is null", 
							xmlNodeList!=null && xmlNodeList.getLength()>0);
					String xmlValue = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull("Expected Node " + nodeCase[0] + " is null", xmlValue);
					if(nodeCase.length==2) {
						Assert.assertEquals(xmlValue, nodeCase[1]);
					}
				}
			}
			
			context.getWorkflowContextHandler().extractXmlWorkflowVariables(testCase, xmlDocument);
			
			String url = testCase.getAurl();
			if(url.indexOf("?")!=-1) {
				url = url.substring(0, url.indexOf("?"));
			}
			
			if(context.getGatfExecutorConfig().isAuthEnabled() && context.getGatfExecutorConfig().getAuthUrl().equals(url) 
					&& context.getGatfExecutorConfig().getAuthExtractAuthParams()[1].equalsIgnoreCase("xml")) {
				String expression = context.getGatfExecutorConfig().getAuthExtractAuthParams()[0].replaceAll("\\.", "\\/");
				if(expression.charAt(0)!='/')
					expression = "/" + expression;
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue("Authentication token is null", 
						xmlNodeList!=null && xmlNodeList.getLength()>0);
				context.setSessionIdentifier(xmlNodeList.item(0).getNodeValue(), testCase);
				Assert.assertNotNull("Authentication token is null", 
						context.getSessionIdentifier(testCase));
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
