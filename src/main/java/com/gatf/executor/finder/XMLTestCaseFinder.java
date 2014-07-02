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
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the xml files inside a given test case directory
 */
public class XMLTestCaseFinder extends TestCaseFinder {

	protected TestCaseFileType getFileType() {
		return TestCaseFileType.XML;
	}

	@SuppressWarnings("unchecked")
	public List<TestCase> resolveTestCases(File testCaseFile) throws Exception {
		XStream xstream = new XStream(new DomDriver("UTF-8"));
		xstream.processAnnotations(new Class[]{TestCase.class});
		xstream.alias("TestCases", List.class);
		try {
			List<TestCase> xmlTestCases = (List<TestCase>)xstream.fromXML(testCaseFile);
			return xmlTestCases;
		} catch (Exception e) {
			System.out.println("Invalid testcase format in file - " + testCaseFile.getPath() + ", Ignoring....");
			return null;
		}
	}
}
