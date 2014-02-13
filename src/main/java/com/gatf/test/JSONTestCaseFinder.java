package com.gatf.test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.gatf.report.TestCaseReport;

public class JSONTestCaseFinder implements TestCaseFinder {

	@SuppressWarnings("unchecked")
	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context)
	{
		ObjectMapper jsonMapper = new ObjectMapper();
		
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			File[] jsonFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".json");
				}
			});

			for (File file : jsonFiles) {
				try {
					List<TestCase>jsonTestCases = (List<TestCase>)jsonMapper.readValue(file, new TypeReference<List<TestCase>>(){});
					if(jsonTestCases!=null && !jsonTestCases.isEmpty())
					{
						for (TestCase testCase : jsonTestCases) {
							testCase.setSourcefileName(file.getName());
							testcases.add(testCase);
						}
						context.getFinalTestResults().put(file.getName(), new ConcurrentLinkedQueue<TestCaseReport>());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return testcases;
	}
}
