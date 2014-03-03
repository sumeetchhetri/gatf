package com.gatf.executor.core;

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

/**
 * @author Sumeet Chhetri
 * Holds global/suite level workflow variable/values
 */
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
	
	private final Map<Integer, Map<String, String>> suiteWorkflowContext = new ConcurrentHashMap<Integer, Map<String, String>>();
	
	void initializeSuiteContext(int numberOfRuns) {
		
		suiteWorkflowContext.clear();
		
		int start = (numberOfRuns>1?1:0);
		int end = (numberOfRuns>1?numberOfRuns+1:numberOfRuns);
		for (int i = start; i < end; i++) {
			suiteWorkflowContext.put(i, new ConcurrentHashMap<String, String>());
		}
	}
	
	void addGlobalVariables(Map<String, String> variableMap) {
		if(variableMap!=null) {
			globalworkflowContext.putAll(variableMap);
		}
	}
	
	private Map<String, String> getSuiteWorkflowContext(TestCase testCase) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowContext.get(0);
		} else {
			return suiteWorkflowContext.get(testCase.getSimulationNumber());
		}
	}
	
	private Map<String, String> getGlobalSuiteAndTestLevelParameters(TestCase testCase, Map<String, String> variableMap) {
		Map<String, String> nmap = new HashMap<String, String>(globalworkflowContext);
		if(testCase.getSimulationNumber()==null) {
			nmap.putAll(suiteWorkflowContext.get(0));
		} else {
			nmap.putAll(suiteWorkflowContext.get(testCase.getSimulationNumber()));
		}
		if(variableMap!=null && !variableMap.isEmpty()) {
			nmap.putAll(variableMap);
		}
		return nmap;
	}
	
	public void handleContextVariables(TestCase testCase, Map<String, String> variableMap) throws Exception {
		
		Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, variableMap);
		
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
				getSuiteWorkflowContext(testCase).put(entry.getKey(), jsonValue);
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
				getSuiteWorkflowContext(testCase).put(entry.getKey(), xmlValue);
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
				getSuiteWorkflowContext(testCase).put(entry.getKey(), xmlValue);
			}
		}
	}
}
