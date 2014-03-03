package com.gatf.executor.report;

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

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

/**
 * @author Sumeet Chhetri
 * This entity holds comparitive test case reports against a given baseURL
 */
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestCaseCompareStatus {

	private String identifer;
	
	private String testSuiteKey;
	
	private String testCaseName;
	
	private String compareStatusError;

	public String getIdentifer() {
		return identifer;
	}

	public void setIdentifer(String identifer) {
		this.identifer = identifer;
	}
	
	public String getTestSuiteKey() {
		return testSuiteKey;
	}

	public void setTestSuiteKey(String testSuiteKey) {
		this.testSuiteKey = testSuiteKey;
	}

	public String getTestCaseName() {
		return testCaseName;
	}

	public void setTestCaseName(String testCaseName) {
		this.testCaseName = testCaseName;
	}

	public String getCompareStatusError() {
		return compareStatusError;
	}

	public void setCompareStatusError(String compareStatusError) {
		this.compareStatusError = compareStatusError;
	}
}
