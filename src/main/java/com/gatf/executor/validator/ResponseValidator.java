package com.gatf.executor.validator;

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

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * Defines contract for response level node validations after test case execution
 */
public interface ResponseValidator {

	void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context);
}
