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

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;

/**
 * @author Sumeet Chhetri
 * The randome value test case data provider implementation
 */
public class RandomValueTestDataProvider implements TestDataProvider {

	public List<Map<String, String>> provide(GatfTestDataProvider provider, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(provider.getArgs()==null || provider.getArgs().length==0) {
			throw new AssertionError("No arguments passed to the RandomValueTestCaseDataProvider");
		}
		
		if(provider.getArgs().length!=2) {
			throw new AssertionError("Insufficient arguments passed, need all parameters namely," +
					" variableTypes and count");
		}
		
		Assert.assertNotNull("variableNames cannot be empty", provider.getProviderProperties());
		Assert.assertNotNull("variableTypes cannot be empty", provider.getArgs()[0]);
		
		String variableNames = provider.getProviderProperties().trim();
		String variableTypes = provider.getArgs()[0].trim();
		
		Assert.assertFalse("count not defined", provider.getArgs()[1]==null || provider.getArgs()[1].trim().isEmpty());
		
		int varCount = 0;
		try {
			varCount = Integer.valueOf(provider.getArgs()[1].trim());
		} catch (Exception e) {
			throw new AssertionError("count is not valid, should be a number");
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
				String varName = variableNamesArr.get(i);
				String varType = variableTypesArr.get(i);
				
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
}
