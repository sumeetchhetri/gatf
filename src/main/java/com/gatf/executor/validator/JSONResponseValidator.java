/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
package com.gatf.executor.validator;

import java.util.List;
import java.util.Map;

import com.gatf.executor.core.WorkflowContextHandler.ResponseType;
import com.gatf.executor.report.TestCaseReport;
import com.jayway.jsonpath.JsonPath;

/**
 * @author Sumeet Chhetri
 * The validator that handles json level node validations after test case execution
 */
public class JSONResponseValidator extends ResponseValidator {

	protected Object getInternalObject(TestCaseReport testCaseReport) throws Exception
	{
		return testCaseReport.getResponseContent();
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
		String nvalue = null;
		try {
			nvalue = JsonPath.read((String)intObj, node).toString();
		} catch (Exception e) {
			//throw new AssertionError("Expected Node " + node + " not found");
		}
		return nvalue;
	}

	protected ResponseType getType() {
		return ResponseType.JSON;
	}

	protected List<Map<String, String>> getResponseMappedValue(String expression, String propNames, Object nodeLst) throws Exception {
		if(expression.equals("")) {
			expression = "$";
		}
		
		List<Map<String, String>> jsonValues = JsonPath.read((String)nodeLst, expression);
		return jsonValues;
	}

	protected int getResponseMappedCount(String expression, Object nodeLst) throws Exception {
		String responseMappedCount = JsonPath.read((String)nodeLst, expression).toString();
		
		int responseCount = -1;
		try {
			responseCount = Integer.valueOf(responseMappedCount);
		} catch (Exception e) {
			throw new AssertionError("Invalid responseMappedCount variable defined, " +
					"derived value should be number - "+expression);
		}
		
		return responseCount;
	}
}
