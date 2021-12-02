/*
    Copyright 2013-2019, Sumeet Chhetri
    
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
package com.gatf.executor.core;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ResourceCopy;
import com.gatf.executor.validator.XMLResponseValidator;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

/**
 * @author Sumeet Chhetri
 * Holds global/suite level workflow variable/values
 */
public class WorkflowContextHandler {
	
	public static final ObjectMapper OM = new ObjectMapper();

	private final VelocityEngine engine = new VelocityEngine();
	
	public enum ResponseType {
		JSON,
		XML,
		SOAP,
		PLAIN,
		NONE
	}
	
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
	
	private final Map<Integer, Map<String, String>> cookies = new ConcurrentHashMap<Integer, Map<String, String>>();
	
	public void initializeSuiteContext(int numberOfRuns) {
		suiteWorkflowContext.clear();
		suiteWorkflowScenarioContext.clear();
		cookies.clear();
		
		for (int i = -2; i < numberOfRuns+1; i++) {
			suiteWorkflowContext.put(i, new ConcurrentHashMap<String, String>());
			suiteWorkflowScenarioContext.put(i, new ConcurrentHashMap<String, List<Map<String, String>>>());
			cookies.put(i, new ConcurrentHashMap<String, String>());
		}
	}
	
	public void initializeSuiteContextWithnum(int index) {
		suiteWorkflowContext.put(index, new ConcurrentHashMap<String, String>());
		suiteWorkflowScenarioContext.put(index, new ConcurrentHashMap<String, List<Map<String, String>>>());
		cookies.put(index, new ConcurrentHashMap<String, String>());
	}
	
	void addGlobalVariables(Map<String, String> variableMap) {
		if(variableMap!=null) {
			for (String property : variableMap.keySet()) {
				String value = variableMap.get(property);
				if(value.equals("env.var")) {
					try {
						value = System.getenv(property);
					} catch (Exception e) {
					}
				} else if(value.equals("property.var")) {
					try {
						value = System.getProperty(property);
					} catch (Exception e) {
					}
				} else if(value.startsWith("env.")) {
					try {
						value = System.getenv(value.substring(4));
					} catch (Exception e) {
					}
				} else if(value.startsWith("property.")) {
					try {
						value = System.getProperty(value.substring(9));
					} catch (Exception e) {
					}
				}
				if(value==null) {
					value = variableMap.get(property);
				}
				globalworkflowContext.put(property, value);
			}
		}
	}
	
	public Map<String, String> getSuiteWorkflowContext(TestCase testCase) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return suiteWorkflowContext.get(-1);
		} else if(testCase.isExternalApi()) {
			return suiteWorkflowContext.get(-2);
		} else if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowContext.get(0);
		} else {
			return suiteWorkflowContext.get(testCase.getSimulationNumber());
		}
	}
	
	public Map<String, List<Map<String, String>>> getSuiteWorkflowScnearioContext(TestCase testCase) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return suiteWorkflowScenarioContext.get(-1);
		} else if(testCase.isExternalApi()) {
			return suiteWorkflowScenarioContext.get(-2);
		} else if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber());
		}
	}
	
	public List<Map<String, String>> getSuiteWorkflowScenarioContextValues(TestCase testCase, String varName) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return suiteWorkflowScenarioContext.get(-1).get(varName);
		} else if(testCase.isExternalApi()) {
			return suiteWorkflowScenarioContext.get(-2).get(varName);
		} else if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0).get(varName);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber()).get(varName);
		}
	}
	
	public String getCookie(TestCase testCase, String varName) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return cookies.get(-1).get(varName);
		} else if(testCase.isExternalApi()) {
			return cookies.get(-2).get(varName);
		} else if(testCase.getSimulationNumber()==null) {
			return cookies.get(0).get(varName);
		} else {
			return cookies.get(testCase.getSimulationNumber()).get(varName);
		}
	}
	
	public Map<String, String> getCookies(TestCase testCase) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return cookies.get(-1);
		} else if(testCase.isExternalApi()) {
			return cookies.get(-2);
		} else if(testCase.getSimulationNumber()==null) {
			return cookies.get(0);
		} else {
			return cookies.get(testCase.getSimulationNumber());
		}
	}
	
	public List<Cookie> storeCookies(TestCase testCase, List<String> cookieLst) {
		int simNumber = testCase.getSimulationNumber()==null?0:testCase.getSimulationNumber();
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			simNumber = -1;
		} else if(testCase.isExternalApi()) {
			simNumber = -2;
		} else if(testCase.getSimulationNumber()==null) {
			simNumber = 0;
		}
		List<Cookie> cooklst = new ArrayList<Cookie>();
		if(cookieLst!=null && cookies.get(simNumber)!=null)
		{
			for (String cookie : cookieLst) {
				Cookie c = Cookie.parse(HttpUrl.parse(testCase.getBaseUrl()), cookie);
				cooklst.add(c);
				cookies.get(simNumber).put(c.name(), c.value());
			}
		}
		return cooklst;
	}
	
	public Map<String, String> getGlobalSuiteAndTestLevelParameters(TestCase testCase, Map<String, String> variableMap, int index) {
	    Map<String, String> nmap = new HashMap<String, String>(globalworkflowContext);
	    if(testCase!=null) {
	        index = testCase.getSimulationNumber();
	        if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
	            index = -1;
	        } else if(testCase.isExternalApi()) {
	            index = -2;
	        } else if(testCase.getSimulationNumber()==null) {
	            index = 0;
	        }
	        nmap.putAll(suiteWorkflowContext.get(index));
	        if(variableMap!=null && !variableMap.isEmpty()) {
	            nmap.putAll(variableMap);
	        }
	        if(testCase.getCarriedOverVariables()!=null && !testCase.getCarriedOverVariables().isEmpty()) {
	            nmap.putAll(testCase.getCarriedOverVariables());
	        }
	    } else {
	        nmap.putAll(suiteWorkflowContext.get(index));
	        if(variableMap!=null && !variableMap.isEmpty()) {
                nmap.putAll(variableMap);
            }
	    }
		return nmap;
	}
	
	public void addSuiteLevelParameter(int index, String name, String value) {
	    if(suiteWorkflowContext.containsKey(index) && value!=null) {
	        suiteWorkflowContext.get(index).put(name, value);
	    }
	}
	
	public String evaluateTemplate(TestCase testCase, String template, AcceptanceTestContext acontext) {
		StringWriter writer = new StringWriter();
		try {
			Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, null, -3);
			if(testCase!=null && !nmap.isEmpty()) {
				if(template!=null) {
					VelocityContext context = new VelocityContext(new HashMap<String, Object>(nmap));
					DataProviderAccessor dpa = new DataProviderAccessor(acontext, testCase);
					context.put("_DPA_", dpa);
					engine.evaluate(context, writer, "ERROR", template);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return writer.toString();
	}
	
	public String templatize(Map<String, Object> variableMap, String template) throws Exception {
	    VelocityContext context = new VelocityContext(variableMap);
	    StringWriter writer = new StringWriter();
        engine.evaluate(context, writer, "ERROR", template);
        return writer.toString();
	}
    
    public String templatize(String template) throws Exception {
        VelocityContext context = new VelocityContext(Collections.<String, Object>unmodifiableMap(globalworkflowContext));
        StringWriter writer = new StringWriter();
        engine.evaluate(context, writer, "ERROR", template);
        return writer.toString();
    }
	
	public void handleContextVariables(TestCase testCase, Map<String, String> variableMap, AcceptanceTestContext acontext) 
			throws Exception {
		
		Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, variableMap, -3);
		
		VelocityContext context = new VelocityContext(new HashMap<String, Object>(nmap));
		DataProviderAccessor dpa = new DataProviderAccessor(acontext, testCase);
		context.put("_DPA_", dpa);
		
		//initialize cookies and headers
		if(testCase != null) {
			Map<String, String> cookieMap = getCookies(testCase);
			if(cookieMap!=null) {
				for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
					if(testCase.getHeaders()==null)
					{
						testCase.setHeaders(new HashMap<String, String>());
					}
					testCase.getHeaders().put("Cookie", entry.getKey() + "=" + entry.getValue());
				}
			}
			if(MapUtils.isNotEmpty(testCase.getHeaders())) {
			    for (Map.Entry<String, String> entry : testCase.getHeaders().entrySet()) {
			        StringWriter writer = new StringWriter();
			        engine.evaluate(context, writer, "ERROR", entry.getValue());
			        testCase.getHeaders().put(entry.getKey(), writer.toString());
			    }
			}
		}
		
		if(testCase!=null && !nmap.isEmpty()) {
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
	
	public boolean velocityValidate(TestCase testCase, String template, Map<String, String> smap, 
			AcceptanceTestContext acontext) {
		if(testCase!=null && template!=null) {
			Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, null, -3);
			if(smap!=null) {
				nmap.putAll(smap);
			}
			StringWriter writer = new StringWriter();
			String condition = "#if(" +  template + ")true#end";
			try {
				VelocityContext context = new VelocityContext(new HashMap<String, Object>(nmap));
				DataProviderAccessor dpa = new DataProviderAccessor(acontext, testCase);
				context.put("_DPA_", dpa);
				engine.evaluate(context, writer, "ERROR", condition);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return "true".equals(writer.toString());
		}
		return true;
	}
	
	public static List<Map<String, String>> getNodeCountMapList(String xmlValue, String nodeName)
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
	
	public static List<Map<String, String>> getNodeValueMapList(String propNames, NodeList xmlNodeList)
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
						String xmlValue = XMLResponseValidator.getXMLNodeValue(node.getChildNodes().item(j));
						if(xmlValue!=null)
							row.put(node.getChildNodes().item(j).getNodeName(), xmlValue);
					}
				}
				
				String xmlValue = XMLResponseValidator.getXMLNodeValue(node);
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
								String xmlValue = XMLResponseValidator.getXMLNodeValue(node.getChildNodes().item(j));
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
	
	public static void copyResourcesToDirectory(String resPath, String directory) {
		if(new File(directory).exists()) {
			try {
				FileUtils.deleteDirectory(new File(directory));
			} catch (IOException e) {
			}
		}
		Optional<JarFile> jarFile = ResourceCopy.jar(WorkflowContextHandler.class);
		if(jarFile.isPresent()) {
			try {
				ResourceCopy.copyResourceDirectory(jarFile.get(), resPath, new File(directory));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else {
			URL resourcesUrl = WorkflowContextHandler.class.getResource("/" + resPath);
	        if (resourcesUrl != null)
	        {
	        	try {
	        		FileUtils.copyDirectory(new File(resourcesUrl.getPath()), new File(directory));
	        	} catch (Exception e) {
	        		throw new RuntimeException(e);
	        	}
	        }
		}
	}
}
