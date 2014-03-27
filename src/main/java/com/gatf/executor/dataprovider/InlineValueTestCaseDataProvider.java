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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;

/**
 * @author Sumeet Chhetri
 * The inline test case data provider implementation
 */
public class InlineValueTestCaseDataProvider implements TestDataProvider {

	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the InlineValueTestCaseDataProvider");
		}
		
		if(args.length<2) {
			throw new AssertionError("Insufficient arguments passed, need all parameters namely," +
					" variableNames, variable values");
		}
		
		Assert.assertNotNull("variableNames cannot be empty", args[0]);
		String variableNames = args[0].trim();
		
		Assert.assertTrue("variable values not defined", args[1]==null || args[1].trim().isEmpty());
		
		List<String> variableNamesArr = new ArrayList<String>();
		for (String varName : variableNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				variableNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single variable name", 
				!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
		
		for (int j = 1; j < args.length; j++) {
			Assert.assertNotNull("variable value cannot be empty", args[j]);
			String[] values = args[j].split(",");
			Assert.assertNotNull(String.format("need %d values for variables defined", variableNamesArr.size()), args[j]);
			
			Map<String, String> row = new HashMap<String, String>();
			for (int i = 0; i < variableNamesArr.size(); i++) {
				row.put(variableNamesArr.get(i), values[i]);
			}
			result.add(row);
		}
		
		return result;
	}
}
