package com.gatf.executor.finder;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

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
							if(testCase.getSimulationNumber()==null)
							{
								testCase.setSimulationNumber(0);
							}
							testCase.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
							testcases.add(testCase);
						}
						
						Integer runNums = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
						if(context.getGatfExecutorConfig().getCompareBaseUrlsNum()!=null)
						{
							runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
						}
						
						if(runNums!=null && runNums>1)
						{
							for (int i = 0; i < runNums; i++)
							{
								context.getFinalTestResults().put("Run-" + (i+1), new ConcurrentLinkedQueue<TestCaseReport>());
							}
						}
						else
						{
							context.getFinalTestResults().put(file.getName(), new ConcurrentLinkedQueue<TestCaseReport>());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return testcases;
	}
}
