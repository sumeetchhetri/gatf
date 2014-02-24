package com.gatf.executor.dataprovider;

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
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.AcceptanceTestContext;
import com.jayway.jsonpath.JsonPath;

public class FileTestCaseDataProvider implements TestDataProvider {

	private Logger logger = Logger.getLogger(DatabaseTestCaseDataProvider.class.getSimpleName());
	
	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the FileProvider");
		}
		
		if(args.length<3) {
			throw new AssertionError("FileProvider needs 3 arguments, namely filePath, fileType and variableNames");
		}
		
		Assert.assertNotNull("filePath cannot be empty", args[0]);
		Assert.assertNotNull("fileType cannot be empty", args[1]);
		Assert.assertNotNull("variableNames cannot be empty", args[2]);
		
		String filePath = args[0].trim();
		String fileType = args[1].trim();
		String variableNames = args[2].trim();
		
		Assert.assertFalse("filePath cannot be empty", filePath.isEmpty());
		Assert.assertFalse("fileType cannot be empty", fileType.isEmpty());
		
		List<String> variableNamesArr = new ArrayList<String>();
		for (String varName : variableNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				variableNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single variable name", 
				!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
		
		StringBuilder build = new StringBuilder();
		build.append("FileProvider configuration [\n");
		build.append(String.format("filePath is %s\n", filePath));
		build.append(String.format("fileType is %s\n", fileType));
		build.append(String.format("variableNames is %s]", variableNames));
		logger.info(build.toString());
		
		File provFile = null;
		try {
			provFile = context.getResourceFile(filePath);
			Assert.assertTrue(String.format("Unable to find %s", filePath), provFile!=null && provFile.exists());
		} catch (Exception e) {
			throw new AssertionError(String.format("Unable to find %s", filePath));
		}
		
		if(fileType.equalsIgnoreCase("csv")) {
			handleCSVFile(provFile, variableNamesArr, result);
		} else if(fileType.equalsIgnoreCase("xml")) {
			handleXMLFile(provFile, variableNamesArr, result);
		} else if(fileType.equalsIgnoreCase("json")) {
			handleJSONFile(provFile, variableNamesArr, result);
		} else {
			throw new AssertionError(String.format("Invalid fileType %s, only csv, xml and json allowed", fileType));
		}
		
		return result;
	}

	private void handleCSVFile(File file, List<String> variableNamesArr, List<Map<String, String>> result) {
		Scanner s = null;
		try {
			s = new Scanner(file);
			s.useDelimiter("\n");
			while (s.hasNext()) {
				String csvLine = s.next().replace("\r", "");
				String[] parts = csvLine.split(",");
				if(parts.length!=variableNamesArr.size()) {
					s.close();
					throw new AssertionError("The number of columns in the csv line do not match the variable Names provided");
				}
				Map<String, String> row = new HashMap<String, String>();
				for (int i = 0; i < variableNamesArr.size(); i++) {
					row.put(variableNamesArr.get(i), parts[i]);
				}
				result.add(row);
			}
			s.close();
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			if(s!=null)s.close();
		}
	}
	
	private void handleXMLFile(File file, List<String> variableNamesArr, List<Map<String, String>> result) {
		Scanner s = null;
		try
		{
			s = new Scanner(file);
			String content = s.useDelimiter("\\Z").next();
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xmlDocument = db.parse(new ByteArrayInputStream(content.getBytes()));

			if(xmlDocument!=null)
			{
				List<NodeList> nodes = new ArrayList<NodeList>(variableNamesArr.size());
				for (int i = 0; i < variableNamesArr.size(); i++) {
					
					String expression = variableNamesArr.get(i).replaceAll("\\.", "\\/");
					if(expression.charAt(0)!='/')
						expression = "/" + expression;
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Expected Node " + variableNamesArr.get(i) + " is null", 
							xmlNodeList!=null && xmlNodeList.getLength()>0);
					
					nodes.add(xmlNodeList);
					String xmlValue = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull("Expected Node " + variableNamesArr.get(i) + " is null", xmlValue);
					
				}
				
				int nodeLength = nodes.get(0).getLength();
				for (NodeList nodeList : nodes) {
					if(nodeList.getLength()!=nodeLength) {
						throw new AssertionError("Variable set sizes don't match");
					}
				}
				
				for (int j = 0; j < nodeLength; j++) {
					Map<String, String> row = new HashMap<String, String>();
					for (int i = 0; i < variableNamesArr.size(); i++) {
						row.put(variableNamesArr.get(i), nodes.get(i).item(j).getNodeValue());
					}
					result.add(row);
				}
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			if(s!=null)s.close();
		}
	}
	
	private void handleJSONFile(File file, List<String> variableNamesArr, List<Map<String, String>> result) {
		Scanner s = null;
		try
		{
			s = new Scanner(file);
			String content = s.useDelimiter("\\Z").next();
			
			if(content!=null && !content.isEmpty()) {
				List<List<String>> varValues = new ArrayList<List<String>>();
				for (int i = 0; i < variableNamesArr.size(); i++) {
					List<String> values = JsonPath.read(content, variableNamesArr.get(i));
					Assert.assertNotNull(String.format("No values found for %s", variableNamesArr.get(i)), values);
					varValues.add(values);
				}
				
				int nodeLength = varValues.get(0).size();
				for (List<String> nodeList : varValues) {
					if(nodeList.size()!=nodeLength) {
						throw new AssertionError("Variable set sizes don't match");
					}
				}
				
				for (int j = 0; j < nodeLength; j++) {
					Map<String, String> row = new HashMap<String, String>();
					for (int i = 0; i < variableNamesArr.size(); i++) {
						row.put(variableNamesArr.get(i), varValues.get(i).get(j));
					}
					result.add(row);
				}
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			if(s!=null)s.close();
		}
	}
}
