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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfFunctionHandler;

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
			    Object val = GatfFunctionHandler.handleFunction(varType);
				if(val==null)
				{
					throw new AssertionError("variable type needs to be one of alpha, alphanum, number, boolean," +
							" -number, +number, float, boolean and date(format)");
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
				
				String value = GatfFunctionHandler.handleFunction(varType);
				row.put(varName, value);
			}
			result.add(row);
		}
		
		return result;
	}
}
