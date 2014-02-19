package com.gatf.test.validator;

import com.gatf.report.TestCaseReport;
import com.gatf.test.core.AcceptanceTestContext;
import com.gatf.test.core.TestCase;
import com.ning.http.client.Response;

public interface ResponseValidator {

	void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context);
}
