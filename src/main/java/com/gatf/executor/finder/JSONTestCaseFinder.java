package com.gatf.executor.finder;

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

import java.io.File;
import java.util.List;
import com.gatf.executor.core.TestCase;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the json files inside a given test case directory
 */
public class JSONTestCaseFinder extends TestCaseFinder {

	protected TestCaseFileType getFileType() {
		return TestCaseFileType.JSON;
	}

	@SuppressWarnings("unchecked")
	public List<TestCase> resolveTestCases(File testCaseFile) throws Exception {
		org.codehaus.jackson.map.ObjectMapper jsonMapper = new org.codehaus.jackson.map.ObjectMapper();
		List<TestCase> jsonTestCases = (List<TestCase>)jsonMapper.readValue(testCaseFile, new org.codehaus.jackson.type.TypeReference<List<TestCase>>(){});
		return jsonTestCases;
	}
}
