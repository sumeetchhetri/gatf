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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import com.gatf.executor.core.AcceptanceTestContext;
import com.jayway.jsonpath.JsonPath;

/**
 * @author Sumeet Chhetri
 * The randome value test case data provider implementation
 */
public class RandomValueTestCaseDataProvider implements TestDataProvider {

	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the RandomValueTestCaseDataProvider");
		}
		
		if(args.length!=3) {
			throw new AssertionError("Insufficient arguments passed, need all parameters namely," +
					" variableNames, variableTypes and variable count");
		}
		
		Assert.assertNotNull("variableNames cannot be empty", args[0]);
		Assert.assertNotNull("variableTypes cannot be empty", args[1]);
		
		String variableNames = args[0].trim();
		String variableTypes = args[1].trim();
		
		Assert.assertTrue("variable count not defined", args[2]==null || args[2].trim().isEmpty());
		
		int varCount = 0;
		try {
			varCount = Integer.valueOf(args[2].trim());
		} catch (Exception e) {
			throw new AssertionError("variable count is not valid, should be a number");
		}
		
		List<String> variableNamesArr = new ArrayList<String>();
		for (String varName : variableNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				variableNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single variable name", 
				!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
		
		List<String> variableTypesArr = new ArrayList<String>();
		for (String varType : variableTypes.split(",")) {
			if(!varType.trim().isEmpty()) {
				if(!varType.equals("+number") && !varType.equals("alpha") && !varType.equals("alphanum")
						&& !varType.equals("boolean") && !varType.equals("-number") && !varType.equals("number")
						&& !varType.equals("float") && !varType.matches("^date\\([a-zA-Z\\-:\\s]*\\)"))
				{
					throw new AssertionError("variable type needs to be one of alpha, alphanum, number, boolean," +
							" -number, +number, float, boolean and date(format)");
				}
				if(varType.matches("^date\\([a-zA-Z\\-:\\s]*\\)"))
				{
					String pattr = varType.substring(varType.indexOf("(")+1, varType.lastIndexOf(")"));
					try {
						new SimpleDateFormat(pattr);
					} catch (Exception e) {
						throw new AssertionError("Invalid date format provided - " + pattr);
					}
				}
				variableTypesArr.add(varType);
			}
		}
		Assert.assertTrue("the variable names don't match the variables types defined", 
				variableNamesArr.size()==variableTypesArr.size());
		
		for (int j = 0; j < varCount; j++) {
			Map<String, String> row = new HashMap<String, String>();
			for (int i = 0; i < variableNamesArr.size(); i++) {
				String varName = variableNamesArr.get(j);
				String varType = variableTypesArr.get(j);
				
				String value = getPrimitiveValue(varType);
				row.put(varName, value);
			}
			result.add(row);
		}
		
		return result;
	}
	
	public static String getPrimitiveValue(String type) {
		if(type.equals("boolean")) {
			Random rand = new Random();
			return String.valueOf(rand.nextBoolean());
		} else if(type.matches("^date\\([a-zA-Z\\-:/\\s]*\\)")) {
			String pattr = type.substring(type.indexOf("(")+1, type.lastIndexOf(")"));
			SimpleDateFormat format = new SimpleDateFormat(pattr);
			return format.format(new Date());
		} else if(type.equals("float")) {
			Random rand = new Random(12345678L);
			return String.valueOf(rand.nextFloat());
		} else if(type.equals("alpha")) {
			return RandomStringUtils.randomAlphabetic(10);
		} else if(type.equals("alphanum")) {
			return RandomStringUtils.randomAlphanumeric(10);
		} else if(type.equals("+number")) {
			Random rand = new Random();
			return String.valueOf(rand.nextInt(1234567));
		} else if(type.equals("-number")) {
			Random rand = new Random();
			return String.valueOf(-rand.nextInt(1234567));
		} else if(type.equals("number")) {
			Random rand = new Random();
			boolean bool = rand.nextBoolean();
			return bool?String.valueOf(rand.nextInt(1234567)):String.valueOf(-rand.nextInt(1234567));
		}
		return null;
	}
	
	public static void main(String[] args) {
		System.out.println("date(mm/dd/yyyy hh:mm:ss )".matches("^date\\([a-zA-Z\\-:/\\s]*\\)"));
		
		final Map<String, Integer> finalTestReportsDups = new ConcurrentHashMap<String, Integer>();
		finalTestReportsDups.put("asdasd", 1);
		finalTestReportsDups.put("asdasd1", 1);
		System.out.println(finalTestReportsDups);
		finalTestReportsDups.clear();
		System.out.println(finalTestReportsDups);
		System.out.println(finalTestReportsDups.containsKey("asdasd1"));
		
		Integer valeu = JsonPath.read("[{\"id\":1336,\"createdAt\":\"2012-08-31T13:36:58.000+0000\",\"updatedAt\":\"2012-08-31T13:36:58.000+0000\",\"address\":\"william.park@direct.development.wellogic.com\",\"folders\":[{\"id\":1339,\"createdAt\":\"2012-08-31T13:36:58.000+0000\",\"updatedAt\":\"2012-08-31T13:36:58.000+0000\",\"name\":\"Trash\",\"inboxFolder\":false,\"draftFolder\":false,\"defaultFolder\":true,\"sentFolder\":false,\"mailbox_id\":\"1336\",\"trashFolder\":true},{\"id\":1337,\"createdAt\":\"2012-08-31T13:36:58.000+0000\",\"updatedAt\":\"2012-08-31T13:36:58.000+0000\",\"name\":\"Inbox\",\"inboxFolder\":true,\"draftFolder\":false,\"defaultFolder\":true,\"sentFolder\":false,\"mailbox_id\":\"1336\",\"trashFolder\":false},{\"id\":1338,\"createdAt\":\"2012-08-31T13:36:58.000+0000\",\"updatedAt\":\"2012-08-31T13:36:58.000+0000\",\"name\":\"Sent\",\"inboxFolder\":false,\"draftFolder\":false,\"defaultFolder\":true,\"sentFolder\":true,\"mailbox_id\":\"1336\",\"trashFolder\":false},{\"id\":1340,\"createdAt\":\"2012-08-31T13:36:58.000+0000\",\"updatedAt\":\"2012-08-31T13:36:58.000+0000\",\"name\":\"Drafts\",\"inboxFolder\":false,\"draftFolder\":true,\"defaultFolder\":true,\"sentFolder\":false,\"mailbox_id\":\"1336\",\"trashFolder\":false}],\"person_id\":\"cedc6356-5c32-42be-9ec3-60107952cc12\"}]", 
				"[0].folders[?(@.name == 'Inbox')].[0].id");
		System.out.println(valeu);
		
		System.out.println(new SimpleDateFormat("X").format(new Date()));
		System.out.println(Runtime.getRuntime().availableProcessors());
	}
}
