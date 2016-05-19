/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
package com.gatf.executor.validator;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.core.WorkflowContextHandler.ResponseType;
import com.gatf.executor.report.TestCaseReport;

/**
 * @author Sumeet Chhetri
 * The validator that handles soap level node validations after test case execution
 */
public class SOAPResponseValidator extends ResponseValidator {

	public static String getLocalNodeName(String nodeName) {
		if(nodeName!=null && nodeName.indexOf(":")!=-1) {
			return nodeName.substring(nodeName.indexOf(":")+1);
		}
		return nodeName;
	}
	
	public static Node getNodeByNameCaseInsensitive(Node node, String nodeName) {
		if(node.getNodeName().equalsIgnoreCase(nodeName) ||
				getLocalNodeName(node.getNodeName()).equalsIgnoreCase(nodeName))
			return node;
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				if(node.getChildNodes().item(i).getNodeName().equalsIgnoreCase(nodeName) ||
						getLocalNodeName(node.getChildNodes().item(i).getNodeName()).equalsIgnoreCase(nodeName)) {
					return node.getChildNodes().item(i);
				}
			}
		}
		return null;
	}
	
	public static Node getNextElement(Node node) {
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				return node.getChildNodes().item(i);
			}
		}
		return null;
	}
	
	public static String createXPathExpression(String suffix, Node... nodes) {
		StringBuilder build = new StringBuilder();
		for (Node node : nodes) {
			build.append("/");
			build.append(getLocalNodeName(node.getNodeName()));
		}
		build.append("/");
		build.append(suffix.replaceAll("\\.", "\\/"));
		return build.toString();
	}
	
	public static void processSOAPRequest(Document soapMessage, TestCase testCase) throws Exception
	{
		if(testCase.getSoapParameterValues()==null)return;
		for (Map.Entry<String, String> entry : testCase.getSoapParameterValues().entrySet()) {
			Node envelope = getNodeByNameCaseInsensitive(soapMessage.getFirstChild(), "envelope");
			Node body = getNodeByNameCaseInsensitive(envelope, "body");
			Node requestBody = getNextElement(body);
			String expression = createXPathExpression(entry.getKey(), envelope, body, requestBody);
			XPath xPath = XPathFactory.newInstance().newXPath();
			NodeList nodelist = (NodeList) xPath.compile(expression).evaluate(soapMessage, XPathConstants.NODESET);
			Assert.assertNotNull("Cannot find soap parameter " + entry.getKey(), 
					nodelist!=null && nodelist.getLength()>0);
			nodelist.item(0).getFirstChild().setNodeValue(entry.getValue());
		}
	}
	
	protected Object getInternalObject(TestCaseReport testCaseReport) throws Exception
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document xmlDocument = db.parse(new ByteArrayInputStream(testCaseReport.getResponseContent().getBytes()));
		return xmlDocument;
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
		Document xmlDocument = (Document)intObj;
		Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
		Node body = getNodeByNameCaseInsensitive(envelope, "body");
		Node requestBody = getNextElement(body);
		Node returnBody = getNextElement(requestBody);
		String expression = createXPathExpression(node, envelope, body, requestBody, returnBody);
		if(expression.indexOf("/[")!=-1)
			expression = expression.replaceAll("/[", "[");
		NodeList xmlNodeList = getNodeByXpath(expression, (Document)intObj, null);
		String xmlValue = getXMLNodeValue(xmlNodeList.item(0));
		return xmlValue;
	}

	protected ResponseType getType() {
		return ResponseType.SOAP;
	}

	protected List<Map<String, String>> getResponseMappedValue(String expression, String propNames, Object nodeLst) throws Exception {
		expression = expression.replaceAll("\\.", "\\/");
		if(expression.length()>1 && expression.charAt(0)!='/')
			expression = "/" + expression;
		
		Node envelope = getNodeByNameCaseInsensitive(((Document)nodeLst).getFirstChild(), "envelope");
		Node body = getNodeByNameCaseInsensitive(envelope, "body");
		Node requestBody = getNextElement(body);
		Node returnBody = getNextElement(requestBody);
		if(expression.equals(""))
			expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody) + "*";
		else	
			expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody, returnBody);
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate((Document)nodeLst, XPathConstants.NODESET);
		Assert.assertTrue("Workflow soap variable " + expression +" is null",  
				xmlNodeList!=null && xmlNodeList.getLength()>0);
		
		List<Map<String, String>> soapValues = WorkflowContextHandler.getNodeValueMapList(propNames, xmlNodeList);
		return soapValues;
	}

	protected int getResponseMappedCount(String expression, Object nodeLst) throws Exception {
		expression = expression.replaceAll("\\.", "\\/");
		if(expression.length()>1 && expression.charAt(0)!='/')
			expression = "/" + expression;
		
		Node envelope = getNodeByNameCaseInsensitive(((Document)nodeLst).getFirstChild(), "envelope");
		Node body = getNodeByNameCaseInsensitive(envelope, "body");
		Node requestBody = getNextElement(body);
		Node returnBody = getNextElement(requestBody);
		if(expression.equals(""))
			expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody) + "*";
		else	
			expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody, returnBody);
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate((Document)nodeLst, XPathConstants.NODESET);
		Assert.assertTrue("Workflow soap variable " + expression +" is null",  
				xmlNodeList!=null && xmlNodeList.getLength()>0);

		String xmlValue = XMLResponseValidator.getXMLNodeValue(xmlNodeList.item(0));
		Assert.assertNotNull("Workflow soap variable " + expression +" is null", xmlValue);
		
		int responseCount = -1;
		try {
			responseCount = Integer.valueOf(xmlValue);
		} catch (Exception e) {
			throw new AssertionError("Invalid responseMappedCount variable defined, " +
					"derived value should be number - "+expression);
		}
		return responseCount;
	}
}
