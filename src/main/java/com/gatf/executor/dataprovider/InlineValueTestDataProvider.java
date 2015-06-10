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
public class InlineValueTestDataProvider implements TestDataProvider {

	public List<Map<String, String>> provide(GatfTestDataProvider provider, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(provider.getArgs()==null || provider.getArgs().length==0) {
			throw new AssertionError("Insufficient arguments passed, need variable values");
		}
		
		Assert.assertNotNull("variableNames cannot be empty", provider.getProviderProperties());
		String variableNames = provider.getProviderProperties().trim();
		
		List<String> variableNamesArr = new ArrayList<String>();
		for (String varName : variableNames.split(",")) {
			if(!varName.trim().isEmpty()) {
				variableNamesArr.add(varName);
			}
		}
		Assert.assertTrue("need to define at-least a single variable name", 
				!variableNames.isEmpty() && variableNames.split(",").length>0 && variableNamesArr.size()>0);
		
		boolean quoted = false;
		int start = 0;
		String splitStr = ",";
		if(provider.getArgs()[0].trim().equalsIgnoreCase("quoted=true")) {
			quoted = true;
			start = 1;
			splitStr = "\"[\t ]*,[\t ]*\"";
		}
		
		for (int j = start; j < provider.getArgs().length; j++) {
			Assert.assertNotNull("variable value cannot be empty", provider.getArgs()[j]);
			String[] values = null;
			if(quoted) {
				values = provider.getArgs()[j].trim().substring(1, provider.getArgs()[j].length()-1).split(splitStr);
			} else {
				values = provider.getArgs()[j].trim().split(splitStr);
			}
			
			Assert.assertNotNull(String.format("need %d values for variables defined", variableNamesArr.size()), 
					provider.getArgs()[j]);
			
			Map<String, String> row = new HashMap<String, String>();
			for (int i = 0; i < variableNamesArr.size(); i++) {
				row.put(variableNamesArr.get(i), values[i]);
			}
			result.add(row);
		}
		return result;
	}
}
