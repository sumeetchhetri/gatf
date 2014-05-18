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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.dataprovider.RandomValueTestDataProvider;
import com.gatf.executor.validator.SOAPResponseValidator;
import com.gatf.executor.validator.XMLResponseValidator;
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
	
	private final Map<Integer, Map<String, List<Map<String, String>>>> suiteWorkflowScenarioContext = 
			new ConcurrentHashMap<Integer, Map<String, List<Map<String, String>>>>();
	
	void initializeSuiteContext(int numberOfRuns) {
		
		suiteWorkflowContext.clear();
		suiteWorkflowScenarioContext.clear();
		
		int start = (numberOfRuns>1?1:0);
		int end = (numberOfRuns>1?numberOfRuns+1:numberOfRuns);
		for (int i = start; i < end; i++) {
			suiteWorkflowContext.put(i, new ConcurrentHashMap<String, String>());
			suiteWorkflowScenarioContext.put(i, new ConcurrentHashMap<String, List<Map<String, String>>>());
		}
	}
	
	void initializeSuiteContextWithnum(int index) {
		suiteWorkflowContext.clear();
		suiteWorkflowScenarioContext.clear();
		suiteWorkflowContext.put(index, new ConcurrentHashMap<String, String>());
		suiteWorkflowScenarioContext.put(index, new ConcurrentHashMap<String, List<Map<String, String>>>());
	}
	
	void addGlobalVariables(Map<String, String> variableMap) {
		if(variableMap!=null) {
			globalworkflowContext.putAll(variableMap);
		}
	}
	
	public Map<String, String> getSuiteWorkflowContext(TestCase testCase) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowContext.get(0);
		} else {
			return suiteWorkflowContext.get(testCase.getSimulationNumber());
		}
	}
	
	private Map<String, List<Map<String, String>>> getSuiteWorkflowScnearioContext(TestCase testCase) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber());
		}
	}
	
	public List<Map<String, String>> getSuiteWorkflowScenarioContextValues(TestCase testCase, String varName) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0).get(varName);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber()).get(varName);
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
		if(testCase.getCarriedOverVariables()!=null && !testCase.getCarriedOverVariables().isEmpty()) {
			nmap.putAll(testCase.getCarriedOverVariables());
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
				String nodeName = entry.getValue().trim();
				if(nodeName.startsWith("#responseMappedValue[") && nodeName.endsWith("]")) {
					nodeName = nodeName.substring(21, nodeName.length()-1);
					
					String propNames = nodeName.substring(nodeName.indexOf(" ")+1).trim();
					String path = nodeName.substring(0, nodeName.indexOf(" ")).trim();
					if(path.equals("")) {
						path = "$";
					}
					
					List<Map<String, String>> jsonValues = JsonPath.read(json, path);
					
					if(!propNames.endsWith("*")) {
						String[] props = propNames.split(",");
						for (Map<String, String> jsonV : jsonValues) {
							for (String propName : props) {
								if(!jsonV.containsKey(propName)) {
									jsonV.remove(propName);
								}
							}
						}
					}
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), jsonValues);
					Assert.assertNotNull("Workflow json mapping variable " + nodeName +" is null", jsonValues);
				} else if(nodeName.startsWith("#responseMappedCount[") && nodeName.endsWith("]")) {
					nodeName = nodeName.substring(21, nodeName.length()-1);
					String responseMappedCount = JsonPath.read(json, nodeName).toString();
					
					int responseCount = -1;
					try {
						responseCount = Integer.valueOf(responseMappedCount);
					} catch (Exception e) {
						throw new AssertionError("Invalid responseMappedCount variable defined, " +
								"derived value should be number - "+entry.getValue());
					}
					
					List<Map<String, String>> jsonValues = new ArrayList<Map<String,String>>();
					for (int i = 0; i < responseCount; i++) {
						Map<String, String> row = new HashMap<String, String>();
						row.put("index", (i+1)+"");
						jsonValues.add(row);
					}
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), jsonValues);
					Assert.assertNotNull("Workflow json mapping variable " + entry.getValue() +" is null", jsonValues);
				} else if(nodeName.startsWith("#")) {
					String jsonValue = RandomValueTestDataProvider.getPrimitiveValue(nodeName.substring(1));
					Assert.assertNotNull("Workflow function " + entry.getValue() +" is not valid, only " +
							"one of alpha, alphanum, number, boolean," +
							" -number, +number and date(format) allowed", jsonValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), jsonValue);
				} else {
					String jsonValue = null;
					try {
						jsonValue = JsonPath.read(json, nodeName).toString();
					} catch (Exception e) {
						throw new AssertionError("Workflow json variable " + nodeName + " not found");
					}
					Assert.assertNotNull("Workflow json variable " + entry.getValue() +" is null", jsonValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), jsonValue);
				}
			}
		}
	}
	
	public void extractSoapWorkflowVariables(TestCase testCase, Document xmlDocument) throws Exception
	{
		if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
		{
			for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
				String nodeName = entry.getValue().trim();
				if(nodeName.startsWith("#") && !nodeName.startsWith("#responseMappedValue")
						 && !nodeName.startsWith("#responseMappedCount")) {
					String jsonValue = RandomValueTestDataProvider.getPrimitiveValue(nodeName.substring(1));
					Assert.assertNotNull("Workflow function " + entry.getValue() +" is not valid, only " +
							"one of alpha, alphanum, number, boolean," +
							" -number, +number and date(format) allowed", jsonValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), jsonValue);
					continue;
				}

				Node envelope = SOAPResponseValidator.getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
				Node body = SOAPResponseValidator.getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = SOAPResponseValidator.getNextElement(body);
				Node returnBody = SOAPResponseValidator.getNextElement(requestBody);
				
				String expression = nodeName.replaceAll("\\.", "\\/");

				if(expression.startsWith("#responseMappedValue[") && expression.endsWith("]")) {
					expression = expression.substring(21, expression.length()-1);
					
					String propNames = expression.substring(expression.indexOf(" ")+1).trim();
					String path = expression.substring(0, expression.indexOf(" ")).trim();
					
					if(path.length()>1 && path.charAt(0)!='/')
						path = "/" + path;
					
					if(path.equals(""))
						path = SOAPResponseValidator.createXPathExpression(path, envelope, body, requestBody) + "*";
					else	
						path = SOAPResponseValidator.createXPathExpression(path, envelope, body, requestBody, returnBody);
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(path).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow soap variable " + expression +" is null",  
							xmlNodeList!=null && xmlNodeList.getLength()>0);
					
					List<Map<String, String>> soapValues = getNodeValueMapList(propNames, xmlNodeList);
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), soapValues);
					Assert.assertNotNull("Workflow soap mapping variable " + entry.getValue() +" is null", soapValues);
				} else if(expression.startsWith("#responseMappedCount[") && expression.endsWith("]")) {
					expression = expression.substring(21, expression.length()-1);
					
					if(expression.length()>1 && expression.charAt(0)!='/')
						expression = "/" + expression;
					
					if(expression.equals(""))
						expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody) + "*";
					else	
						expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody, returnBody);
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow soap variable " + expression +" is null",  
							xmlNodeList!=null && xmlNodeList.getLength()>0);

					String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
					Assert.assertNotNull("Workflow soap variable " + expression +" is null", xmlValue);
					
					List<Map<String, String>> soapValues = getNodeCountMapList(xmlValue, expression);
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), soapValues);
					Assert.assertNotNull("Workflow json mapping variable " + entry.getValue() +" is null", soapValues);
				} else {
					
					if(expression.length()>1 && expression.charAt(0)!='/')
						expression = "/" + expression;
					
					if(expression.equals(""))
						expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody) + "*";
					else	
						expression = SOAPResponseValidator.createXPathExpression(expression, envelope, body, requestBody, returnBody);
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow soap variable " + entry.getValue() +" is null",  
							xmlNodeList!=null && xmlNodeList.getLength()>0);

					String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
					Assert.assertNotNull("Workflow soap variable " + entry.getValue() +" is null", xmlValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), xmlValue);
				}
			}
		}
	}
	
	public void extractXmlWorkflowVariables(TestCase testCase, Document xmlDocument) throws Exception
	{
		if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
		{
			for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
				String nodeName = entry.getValue().trim();
				if(nodeName.startsWith("#") && !nodeName.startsWith("#responseMappedValue")
						 && !nodeName.startsWith("#responseMappedCount")) {
					String jsonValue = RandomValueTestDataProvider.getPrimitiveValue(nodeName.substring(1));
					Assert.assertNotNull("Workflow function " + entry.getValue() +" is not valid, only " +
							"one of alpha, alphanum, number, boolean," +
							" -number, +number and date(format) allowed", jsonValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), jsonValue);
					continue;
				}
				
				String expression = nodeName.replaceAll("\\.", "\\/");
				
				if(expression.startsWith("#responseMappedValue[") && expression.endsWith("]")) {
					expression = expression.substring(21, expression.length()-1);
					
					String propNames = expression.substring(expression.indexOf(" ")+1).trim();
					String path = expression.substring(0, expression.indexOf(" ")).trim();
					if(path.equals("")) {
						path = "/*";
					}
					
					if(path.charAt(0)!='/')
						path = "/" + path;
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(path).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow xml variable " + expression +" is null",  
							xmlNodeList!=null && xmlNodeList.getLength()>0);
					
					List<Map<String, String>> xmlValues = getNodeValueMapList(propNames, xmlNodeList);
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), xmlValues);
					Assert.assertNotNull("Workflow xml mapping variable " + expression +" is null", xmlValues);
				} else if(expression.startsWith("#responseMappedCount[") && expression.endsWith("]")) {
					expression = expression.substring(21, expression.length()-1);
					
					if(expression.charAt(0)!='/')
						expression = "/" + expression;
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow xml variable " + entry.getValue() +" is null", 
							xmlNodeList!=null && xmlNodeList.getLength()>0);

					String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
					Assert.assertNotNull("Workflow xml variable " + expression +" is null", xmlValue);
					
					List<Map<String, String>> xmlValues = getNodeCountMapList(xmlValue, expression);
					getSuiteWorkflowScnearioContext(testCase).put(entry.getKey(), xmlValues);
					Assert.assertNotNull("Workflow xml mapping variable " + entry.getValue() +" is null", xmlValues);
				} else {
					
					if(expression.charAt(0)!='/')
						expression = "/" + expression;
					
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Workflow xml variable " + entry.getValue() +" is null", 
							xmlNodeList!=null && xmlNodeList.getLength()>0);

					String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
					Assert.assertNotNull("Workflow xml variable " + entry.getValue() +" is null", xmlValue);
					getSuiteWorkflowContext(testCase).put(entry.getKey(), xmlValue);
				}
			}
		}
	}
	
	private List<Map<String, String>> getNodeCountMapList(String xmlValue, String nodeName)
	{
		int responseCount = -1;
		try {
			responseCount = Integer.valueOf(xmlValue);
		} catch (Exception e) {
			throw new AssertionError("Invalid responseMappedCount variable defined, " +
					"derived value should be number - "+nodeName);
		}
		
		List<Map<String, String>> xmlValues = new ArrayList<Map<String,String>>();
		for (int i = 0; i < responseCount; i++) {
			Map<String, String> row = new HashMap<String, String>();
			row.put("index", (i+1)+"");
			xmlValues.add(row);
		}
		
		return xmlValues;
	}
	
	private List<Map<String, String>> getNodeValueMapList(String propNames, NodeList xmlNodeList)
	{
		List<Map<String, String>> nodeValues = new ArrayList<Map<String,String>>();
		if(propNames.endsWith("*")) 
		{
			for (int i = 0; i < xmlNodeList.getLength(); i++) {
				Map<String, String> row = new HashMap<String, String>();
				
				Node node = xmlNodeList.item(i);
				if(node.getAttributes()!=null && node.getAttributes().getLength()>0)
				{
					for (int j = 0; j < node.getAttributes().getLength(); j++) {
						Attr attr = (Attr) node.getAttributes().item(j);
						row.put(attr.getName(), attr.getValue());
					}
				}
				
				if(node.getChildNodes()!=null && node.getChildNodes().getLength()>0)
				{
					for (int j = 0; j < node.getChildNodes().getLength(); j++) {
						String xmlValue = XMLResponseValidator.getNodeValue(node.getChildNodes().item(j));
						if(xmlValue!=null)
							row.put(node.getChildNodes().item(j).getNodeName(), xmlValue);
					}
				}
				
				String xmlValue = XMLResponseValidator.getNodeValue(node);
				if(xmlValue!=null)
					row.put("this", xmlValue);
				
				if(row.size()>0)
					nodeValues.add(row);
			}
		} 
		else
		{
			String[] props = propNames.split(",");
			
			for (int i = 0; i < xmlNodeList.getLength(); i++) {
				Map<String, String> row = new HashMap<String, String>();
				
				Node node = xmlNodeList.item(i);
				
				boolean found = false;
				
				if(node.getAttributes()!=null && node.getAttributes().getLength()>0)
				{
					for (int j = 0; j < node.getAttributes().getLength(); j++) {
						Attr attr = (Attr) node.getAttributes().item(j);
						for (String propName : props) {
							if(attr.getName().equals(propName)) {
								found = true;
								row.put(propName, attr.getValue());
								break;
							}
						}
					}
				}
				
				if(!found && node.getChildNodes()!=null && node.getChildNodes().getLength()>0)
				{
					for (int j = 0; j < node.getChildNodes().getLength(); j++) {
						for (String propName : props) {
							if(node.getChildNodes().item(j).getNodeName().equals(propName)) {
								found = true;
								String xmlValue = XMLResponseValidator.getNodeValue(node.getChildNodes().item(j));
								row.put(propName, xmlValue);
								break;
							}
						}
					}
				}
				if(row.size()>0)
					nodeValues.add(row);
			}
		}
		return nodeValues;
	}
}
