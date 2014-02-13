package com.gatf.test;

import com.gatf.report.TestCaseReport;
import com.ning.http.client.Response;

public interface ResponseValidator {

	void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context);
}
