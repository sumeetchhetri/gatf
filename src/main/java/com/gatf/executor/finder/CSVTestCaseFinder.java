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
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.gatf.executor.core.TestCase;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the csv files inside a given test case directory
 */
public class CSVTestCaseFinder extends TestCaseFinder {

	protected TestCaseFileType getFileType() {
		return TestCaseFileType.CSV;
	}

	protected List<TestCase> resolveTestCases(File testCaseFile) throws Exception {
		Scanner s = new Scanner(testCaseFile);
		s.useDelimiter("\n");
		List<String> list = new ArrayList<String>();
		while (s.hasNext()) {
			String csvLine = s.next().replace("\r", "");
			if(!csvLine.trim().isEmpty() && !csvLine.trim().startsWith("//")) {
				list.add(csvLine);
			}
		}
		s.close();

		List<TestCase> testcases = new ArrayList<TestCase>();
		if(!list.isEmpty())
		{
			for (String csvLine : list) {
				TestCase testCase = new TestCase(csvLine);
				testcases.add(testCase);
			}
		}
		return testcases;
	}
}
