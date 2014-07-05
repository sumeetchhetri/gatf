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
