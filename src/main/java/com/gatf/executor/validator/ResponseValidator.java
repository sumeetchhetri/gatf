package com.gatf.executor.validator;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.ning.http.client.Response;

public interface ResponseValidator {

	void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context);
}
