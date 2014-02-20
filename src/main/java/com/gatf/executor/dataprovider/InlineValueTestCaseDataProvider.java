package com.gatf.executor.dataprovider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.gatf.executor.core.AcceptanceTestContext;

public class InlineValueTestCaseDataProvider implements TestDataProvider {

	public List<Map<String, String>> provide(String[] args, AcceptanceTestContext context) {
		
		List<Map<String, String>> result = new ArrayList<Map<String,String>>();
		
		if(args==null || args.length==0) {
			throw new AssertionError("No arguments passed to the InlineValueTestCaseDataProvider");
		}
		
		Assert.assertNotNull("variableNames cannot be empty", args[0]);
		String variableNames = args[0].trim();
		
		Assert.assertTrue("variable values not defined", args.length>1);
		
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
