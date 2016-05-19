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
 * The validator that handles text level node validations after test case execution
 */
public class TextResponseValidator extends ResponseValidator {

	protected Object getInternalObject(TestCaseReport testCaseReport) throws Exception {
		return testCaseReport.getResponseContent();
	}

	protected ResponseType getType() {
		return ResponseType.PLAIN;
	}

	protected String getNodeValue(Object intObj, String node) throws Exception {
		String nvalue = null;
		try {
			String cont = (String)intObj;
			if(cont!=null && cont.indexOf("&")!=-1) {
				String[] params = cont.split("&");
				for (String pdef : params) {
					String[] pv = pdef.split("=");
					if(pv.length>1 && pv[0].equals(node)) {
						nvalue = pv[1];
						break;
					}
				}
			}
		} catch (Exception e) {
			throw new AssertionError("Expected Node " + node + " not found");
		}
		return nvalue;
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
