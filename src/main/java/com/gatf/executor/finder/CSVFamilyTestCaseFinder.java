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

import com.gatf.executor.core.TestCase;
import com.gatf.executor.dataprovider.FileTestDataProvider;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the csv files inside a given test case directory
 */
public abstract class CSVFamilyTestCaseFinder extends TestCaseFinder {
	
	protected Map<Integer, Integer> mappings;
	
	protected boolean ignoreHeaderLine = false;
	
	protected int sheet = 0;
	
	protected char separator = ',';
	
	public CSVFamilyTestCaseFinder(){}

	public List<TestCase> resolveTestCases(File testCaseFile) throws Exception {
		
		String fileType = getFileType().ext.substring(1);
		List<String[]> list = FileTestDataProvider.readCsvFamilyFile(fileType, testCaseFile, separator, sheet);

		List<TestCase> testcases = new ArrayList<TestCase>();
		if(!list.isEmpty())
		{
			List<TestCase> testcasesTemp = new ArrayList<TestCase>();
			for (int i=0;i<list.size();i++) {
				String[] csvLine = list.get(0);
				if(ignoreHeaderLine && i==0)continue;
				TestCase testCase = mappings==null?new TestCase(csvLine):new TestCase(csvLine, mappings);
				testcasesTemp.add(testCase);
			}
		}
		return testcases;
	}
	
	public static class CSVTestCaseFinder extends CSVFamilyTestCaseFinder {
		
		public CSVTestCaseFinder(){}
		
		public CSVTestCaseFinder(Map<Integer, Integer> mappings,
				boolean ignoreHeaderLine, int sheet, char separator) {
			this.mappings = mappings;
			this.ignoreHeaderLine = ignoreHeaderLine;
			this.sheet = sheet;
			this.separator = separator;
		}
		
		@Override
		protected TestCaseFileType getFileType() {
			return TestCaseFileType.CSV;
		}
	}
	
	public static class XLSTestCaseFinder extends CSVFamilyTestCaseFinder {
		
		public XLSTestCaseFinder(){}
		
		public XLSTestCaseFinder(Map<Integer, Integer> mappings,
				boolean ignoreHeaderLine, int sheet, char separator) {
			this.mappings = mappings;
			this.ignoreHeaderLine = ignoreHeaderLine;
			this.sheet = sheet;
			this.separator = separator;
		}
		
		@Override
		protected TestCaseFileType getFileType() {
			return TestCaseFileType.XLS;
		}
	}
	
	public static class XLSXTestCaseFinder extends CSVFamilyTestCaseFinder {
		
		public XLSXTestCaseFinder(){}
		
		public XLSXTestCaseFinder(Map<Integer, Integer> mappings,
				boolean ignoreHeaderLine, int sheet, char separator) {
			this.mappings = mappings;
			this.ignoreHeaderLine = ignoreHeaderLine;
			this.sheet = sheet;
			this.separator = separator;
		}
		
		@Override
		protected TestCaseFileType getFileType() {
			return TestCaseFileType.XLSX;
		}
	}
}
