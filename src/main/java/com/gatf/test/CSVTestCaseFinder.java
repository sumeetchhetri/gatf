package com.gatf.test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gatf.report.TestCaseReport;

public class CSVTestCaseFinder implements TestCaseFinder {

	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context)
	{
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			File[] csvFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".csv");
				}
			});

			for (File file : csvFiles) {
				try {
					Scanner s = new Scanner(file);
					s.useDelimiter("\n");
					List<String> list = new ArrayList<String>();
					while (s.hasNext()) {
						String csvLine = s.next().replace("\r", "");
						if(!csvLine.trim().isEmpty() && !csvLine.trim().startsWith("//")) {
							list.add(csvLine);
						}
					}
					s.close();
	
					if(!list.isEmpty())
					{
						for (String csvLine : list) {
							TestCase testCase = new TestCase(csvLine);
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
