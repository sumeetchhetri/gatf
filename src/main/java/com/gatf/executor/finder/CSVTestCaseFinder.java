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
import java.util.Map;
import java.util.Scanner;

import com.gatf.executor.core.TestCase;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the csv files inside a given test case directory
 */
public class CSVTestCaseFinder extends TestCaseFinder {
	
	private Map<Integer, Integer> mappings;
	
	private boolean ignoreHeaderLine = false;
	
	public CSVTestCaseFinder(){}
	
	public CSVTestCaseFinder(Map<Integer, Integer> mappings, boolean ignoreHeaderLine)
	{
		this.mappings = mappings;
		this.ignoreHeaderLine = ignoreHeaderLine;
	}

	protected TestCaseFileType getFileType() {
		return TestCaseFileType.CSV;
	}

	public List<TestCase> resolveTestCases(File testCaseFile) throws Exception {
		Scanner s = new Scanner(testCaseFile);
		s.useDelimiter("\n");
		List<String> list = new ArrayList<String>();
		boolean isFirstLine = true;
		while (s.hasNext()) {
			String csvLine = s.next().replace("\r", "");
			if(ignoreHeaderLine && isFirstLine)
			{
				isFirstLine = false;
				continue;
			}
			if(!csvLine.trim().isEmpty() && !csvLine.trim().startsWith("//")) {
				list.add(csvLine);
			}
		}
		s.close();

		List<TestCase> testcases = new ArrayList<TestCase>();
		if(!list.isEmpty())
		{
			List<TestCase> testcasesTemp = new ArrayList<TestCase>();
			//boolean invalid = false;
			for (String csvLine : list) {
				TestCase testCase = mappings==null?new TestCase(csvLine):new TestCase(csvLine, mappings);
				testcasesTemp.add(testCase);
				/*} catch (Exception e) {
					System.out.println("Invalid testcase format in file - " + testCaseFile.getPath() + ", Ignoring....");
					invalid = true;
					break;
				}*/
			}
			/*if(!invalid) {
				testcases.addAll(testcasesTemp);
			}*/
		}
		return testcases;
	}
}
