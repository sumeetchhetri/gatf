package com.gatf.executor.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.validator.SOAPResponseValidator;
import com.jayway.jsonpath.JsonPath;


public class WorkflowContextHandler {

	private final VelocityEngine engine = new VelocityEngine();
	
	public VelocityEngine getEngine() {
		return engine;
	}

	public void init() {
		try {
			engine.init();
		} catch (Exception e) {
		}
	}
	
	private final Map<String, String> globalworkflowContext = new ConcurrentHashMap<String, String>();
	
	public void addGlobalVariables(Map<String, String> variableMap) {
		if(variableMap!=null) {
			globalworkflowContext.putAll(variableMap);
		}
	}
	
	public void handleContextVariables(TestCase testCase, Map<String, String> variableMap) throws Exception {
		
		Map<String, String> nmap = new HashMap<String, String>(globalworkflowContext);
		if(variableMap!=null && !variableMap.isEmpty()) {
			nmap.putAll(variableMap);
		}
		
		if(testCase!=null && !nmap.isEmpty()) {
			
			VelocityContext context = new VelocityContext(nmap);
			if(testCase.getUrl()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getUrl());
				testCase.setAurl(writer.toString());
			}
			if(testCase.getContent()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getContent());
				testCase.setAcontent(writer.toString());
			}
			if(testCase.getExQueryPart()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getExQueryPart());
				testCase.setAexQueryPart(writer.toString());
			}
			if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty()) {
				List<String> expectedNodes = new ArrayList<String>();
				for (String nodecase : testCase.getExpectedNodes()) {
					StringWriter writer = new StringWriter();
					engine.evaluate(context, writer, "ERROR", nodecase);
					expectedNodes.add(writer.toString());
				}
				testCase.setAexpectedNodes(expectedNodes);
			}
		} else if(testCase!=null) {
			testCase.setAurl(testCase.getUrl());
			testCase.setAcontent(testCase.getContent());
			testCase.setAexQueryPart(testCase.getExQueryPart());
			testCase.setAexpectedNodes(testCase.getExpectedNodes());
		}
	}
	
	public void extractJsonWorkflowVariables(TestCase testCase, String json)
	{
		if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
		{
			for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
				String jsonValue = JsonPath.read(json, entry.getValue());
				Assert.assertNotNull("Workflow json variable " + entry.getValue() +" is null", jsonValue);
				globalworkflowContext.put(entry.getKey(), jsonValue);
			}
		}
	}
	
	public void extractSoapWorkflowVariables(TestCase testCase, Document xmlDocument) throws Exception
	{
		if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
		{
			for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
				Node envelope = SOAPResponseValidator.getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
				Node body = SOAPResponseValidator.getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = SOAPResponseValidator.getNextElement(body);
				Node returnBody = SOAPResponseValidator.getNextElement(requestBody);
				String expression = SOAPResponseValidator.createXPathExpression(entry.getValue(), envelope, body, requestBody, returnBody);
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue("Workflow soap variable " + entry.getValue() +" is null",  
						xmlNodeList!=null && xmlNodeList.getLength()>0);
				String xmlValue = xmlNodeList.item(0).getNodeValue();
				Assert.assertNotNull("Workflow soap variable " + entry.getValue() +" is null", xmlValue);
				globalworkflowContext.put(entry.getKey(), xmlValue);
			}
		}
	}
	
	public void extractXmlWorkflowVariables(TestCase testCase, Document xmlDocument) throws Exception
	{
		if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
		{
			for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
				String expression = entry.getValue().replaceAll("\\.", "\\/");
				if(expression.charAt(0)!='/')
					expression = "/" + expression;
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue("Workflow xml variable " + entry.getValue() +" is null", 
						xmlNodeList!=null && xmlNodeList.getLength()>0);
				String xmlValue = xmlNodeList.item(0).getNodeValue();
				Assert.assertNotNull("Workflow xml variable " + entry.getValue() +" is null", xmlValue);
				globalworkflowContext.put(entry.getKey(), xmlValue);
			}
		}
	}
}
