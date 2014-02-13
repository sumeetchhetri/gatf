package com.gatf.test;

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

import com.gatf.report.TestCaseReport;
import com.ning.http.client.Response;

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
					Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
					String xmlValue = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull(xmlValue);
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
				Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
				context.setSessionIdentifier(xmlNodeList.item(0).getNodeValue());
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
