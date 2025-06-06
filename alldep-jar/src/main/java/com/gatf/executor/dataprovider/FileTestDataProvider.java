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
package com.gatf.executor.dataprovider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.AcceptanceTestContext;
import com.jayway.jsonpath.JsonPath;

import au.com.bytecode.opencsv.CSVReader;

/**
 * @author Sumeet Chhetri
 * The file test case data provider implementation
 */
public class FileTestDataProvider implements TestDataProvider {
	
	private Logger logger = LogManager.getLogger(FileTestDataProvider.class.getSimpleName());
	
	public static List<Map<String, String>> provide(String filePath, String variableNames, AcceptanceTestContext context) {
		GatfTestDataProvider provider = new GatfTestDataProvider();
		String addl = "";
		if(filePath.contains("@")) {
			addl = filePath.substring(filePath.indexOf("@")+1);
			filePath = filePath.substring(0, filePath.indexOf("@"));
		}
		provider.setArgs(new String[] {filePath, variableNames, addl});
		provider.setProviderProperties(variableNames);
		return new FileTestDataProvider().provide(provider, context);
	}
	
	public List<Map<String, String>> provide(GatfTestDataProvider provider, AcceptanceTestContext context) {
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(provider.getArgs()==null || provider.getArgs().length==0) {
			throw new AssertionError("No arguments passed to the FileProvider");
		}
		
		if(provider.getArgs().length<2) {
			throw new AssertionError("FileProvider needs 2 arguments, namely filePath and fileType");
		}
		
		Assert.assertNotNull("filePath cannot be empty", provider.getArgs()[0]);
		Assert.assertNotNull("fileType cannot be empty", provider.getArgs()[1]);
		Assert.assertNotNull("variableNames cannot be empty", provider.getProviderProperties());
		
		String filePath = provider.getArgs()[0].trim();
		String fileType = provider.getArgs()[1].trim();
		String variableNames = provider.getProviderProperties().trim();
		
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
		build.append("Provider configuration [\n");
		build.append(String.format("filePath is %s\n", filePath));
		build.append(String.format("fileType is %s\n", fileType));
		build.append(String.format("args[2] is %s\n", provider.getArgs().length>2?provider.getArgs()[2]:null));
		build.append(String.format("variableNames is %s]", variableNames));
		logger.info(build.toString());
		
		File provFile = null;
		try {
			provFile = context.getResourceFile(filePath);
			Assert.assertTrue(String.format("Unable to find %s", filePath), provFile!=null && provFile.exists());
		} catch (Exception e) {
			throw new AssertionError(String.format("Unable to find %s", filePath));
		}
		
		if(fileType.equalsIgnoreCase("csv") || fileType.equalsIgnoreCase("xls") || fileType.equalsIgnoreCase("xlsx")) {
			handleCsvFamilyFile(provFile, provider.getArgs(), fileType, variableNamesArr, result);
		} else if(fileType.equalsIgnoreCase("xml")) {
			handleXMLFile(provFile, variableNamesArr, result);
		} else if(fileType.equalsIgnoreCase("json")) {
			handleJSONFile(provFile, variableNamesArr, result);
		} else {
			throw new AssertionError(String.format("Invalid fileType %s, only csv, xml and json allowed", fileType));
		}
		
		return result;
	}
	
	public String getHash(GatfTestDataProvider provider, AcceptanceTestContext context) {
		File provFile = null;
		String filePath = provider.getArgs()[0].trim();
		try {
			provFile = context.getResourceFile(filePath);
		} catch (Exception e) {
			throw new AssertionError(String.format("Unable to find %s", filePath));
		}
		if(provFile!=null && provFile.exists()) {
			try {
				return DigestUtils.sha512Hex(new FileInputStream(provFile));
			} catch (Exception e) {
			}
		}
		return null;
	}
	
	private void handleCsvFamilyFile(File provFile, String[] args, String fileType, List<String> variableNamesArr,  List<Map<String, String>> result) {
		List<String[]> list = new ArrayList<String[]>();
		try {
			boolean header = false;
			if(args.length>2 && args[2].trim().toLowerCase().matches("header=(true|false|yes|no|on|off|1|0)")) {
				header = args[2].trim().toLowerCase().matches("header=(true|yes|on|1)");
			}
			if(fileType.equalsIgnoreCase("csv")) {
				char splitStr = ',';
				if(args.length>2 && args[2].trim().toLowerCase().matches("separator=(.*)")) {
					splitStr = args[2].trim().charAt(10);
				}
				list = readCsvFamilyFile(fileType, provFile, splitStr, -1);
				if(header) list.remove(0);
			} else if(fileType.equalsIgnoreCase("xls") || fileType.equalsIgnoreCase("xlsx")) {
				int sheet = 0;
				if(args.length>2 && args[2].trim().toLowerCase().matches("sheet=([0-9]+)")) {
					sheet = Integer.parseInt(args[2].trim().substring(6));
				}
				list = readCsvFamilyFile(fileType, provFile, ',', sheet);
				if(header) list.remove(0);
			}
			
			int counter = 1;
			for (String[] parts : list) {
				if(parts.length<variableNamesArr.size()) {
					throw new AssertionError("The number of columns in the file record do not match the " +
							"variable Names provided at position " + counter++);
				}
				Map<String, String> row = new HashMap<String, String>();
				String drt = "";
				for (int i = 0; i < variableNamesArr.size(); i++) {
					if(variableNamesArr.get(i).equals("") || variableNamesArr.get(i).equals("_")) continue;
					row.put(variableNamesArr.get(i), parts[i]);
					drt += parts[i];
				}
				if(!drt.trim().isBlank()) {
					result.add(row);
				}
			}
		} catch(AssertionError e) {
			throw e;
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/*private void handleCSVFile(File file, List<String> variableNamesArr, List<Map<String, String>> result,
			 char splitStr) {
		CSVReader reader = null;
		try {
			reader = new CSVReader(new FileReader(file), splitStr);
			List<String[]> list = reader.readAll();
			for (String[] parts : list) {
				if(parts.length!=variableNamesArr.size()) {
					reader.close();
					throw new AssertionError("The number of columns in the csv line do not match the variable Names provided");
				}
				Map<String, String> row = new HashMap<String, String>();
				for (int i = 0; i < variableNamesArr.size(); i++) {
					if(variableNamesArr.get(i).equals("") || variableNamesArr.get(i).equals("_")) continue;
					row.put(variableNamesArr.get(i), parts[i]);
				}
				result.add(row);
			}
			reader.close();
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			if(reader!=null)
			{
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
	}*/
	
	private void handleXMLFile(File provFile, List<String> variableNamesArr, List<Map<String, String>> result) {
		try
		{
			String content = FileUtils.readFileToString(provFile, "UTF-8");
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
					String drt = "";
					for (int i = 0; i < variableNamesArr.size(); i++) {
						if(variableNamesArr.get(i).equals("") || variableNamesArr.get(i).equals("_")) continue;
						row.put(variableNamesArr.get(i), nodes.get(i).item(j).getNodeValue());
						drt += nodes.get(i).item(j).getNodeValue();
					}
					if(!drt.trim().isBlank()) {
						result.add(row);
					}
				}
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
	
	private void handleJSONFile(File provFile, List<String> variableNamesArr, List<Map<String, String>> result) {
		try
		{
			String content = FileUtils.readFileToString(provFile, "UTF-8");
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
					String drt = "";
					for (int i = 0; i < variableNamesArr.size(); i++) {
						if(variableNamesArr.get(i).equals("") || variableNamesArr.get(i).equals("_")) continue;
						row.put(variableNamesArr.get(i), varValues.get(i).get(j));
						drt += varValues.get(i).get(j);
					}
					if(!drt.trim().isBlank()) {
						result.add(row);
					}
				}
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
	
	public static List<String[]> readCsvFamilyFile(String fileType, File file, char splitStr, int sheet)
	{
		Workbook hssfWorkbook = null;
		CSVReader reader = null;
		List<String[]> list = new ArrayList<String[]>();
		try {
			if(fileType.equalsIgnoreCase("csv")) {
				reader = new CSVReader(new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")), splitStr);
				list = reader.readAll();
			} else if(fileType.equalsIgnoreCase("xls") || fileType.equalsIgnoreCase("xlsx")) {
				if(fileType.equalsIgnoreCase("xls")) {
					hssfWorkbook = new HSSFWorkbook(new FileInputStream(file));
				} else {
					hssfWorkbook = new XSSFWorkbook(new FileInputStream(file));
				}
				if(sheet >= hssfWorkbook.getNumberOfSheets()) {
					throw new AssertionError("Invalid sheet number specified for file " + file.getName());
				}
				Sheet hssfSheet = hssfWorkbook.getSheetAt(sheet);
				Iterator<Row> rows = hssfSheet.rowIterator();
				while (rows.hasNext()) {
					Row hssfRow = rows.next();
					String[] parts = new String[hssfRow.getLastCellNum()];
					String drt = "";
					for(int cn=0; cn<hssfRow.getLastCellNum(); cn++) {
						Cell hssfCell = hssfRow.getCell(cn, MissingCellPolicy.CREATE_NULL_AS_BLANK);
						DataFormatter fmt = new DataFormatter();
						parts[cn] = fmt.formatCellValue(hssfCell);
						drt += parts[cn].toString();
					}
					if(!drt.trim().isBlank()) {
						list.add(parts);
					}
				}
			}
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			if(hssfWorkbook!=null) {
				try {
					hssfWorkbook.close();
				} catch (IOException e) {
				}
			}
			if(reader!=null)
			{
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		return list;
	}
}
