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

/**
 * @author Sumeet Chhetri
 * The validator that handles cases where there is no content-type header set in response after test case execution
 */
public class NoContentResponseValidator extends ResponseValidator {

	protected Object getInternalObject(TestCaseReport testCaseReport) throws Exception {
		return null;
	}

	protected ResponseType getType() {
		return ResponseType.NONE;
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
		return null;
	}

	protected List<Map<String, String>> getResponseMappedValue(
			String expression, String propNames, Object nodeLst)
			throws Exception {
		return null;
	}

	protected int getResponseMappedCount(
			String expression, Object nodeLst) throws Exception {
		return -1;
	}
}
