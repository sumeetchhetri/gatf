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
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.AlphanumComparator;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * @author Sumeet Chhetri
 * Defines contract to find all test cases from files inside a given test case directory
 */
public abstract class TestCaseFinder {

	public enum TestCaseFileType
	{
		XML(".xml"),
		JSON(".json"),
		CSV(".csv");
		
		public String ext;
		
		private TestCaseFileType(String ext)
		{
			this.ext = ext;
		}
	}
	
	protected abstract TestCaseFileType getFileType();
	public abstract List<TestCase> resolveTestCases(File testCaseFile) throws Exception;
	
	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context)
	{
		String[] ignoreFiles = context.getGatfExecutorConfig().getIgnoreFiles();
		String[] orderedFiles = context.getGatfExecutorConfig().getOrderedFiles();
		boolean isOrderByFileName = context.getGatfExecutorConfig().isOrderByFileName();
		
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			File[] files = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(getFileType().ext);
				}
			});
			
			List<File> allFiles = new ArrayList<File>();
			if(orderedFiles!=null)
			{
				for (String fileN : orderedFiles) {
					for (File file : files) {
						if(file.getName().equals(fileN)) {
							allFiles.add(file);
							break;
						}
					}
				}
				for (File file : files) {
					if(!allFiles.contains(file)) {
						allFiles.add(file);
					}
				}
			}
			else
			{
				for (File file : files) {
					allFiles.add(file);
				}
				AlphanumComparator comparator = new AlphanumComparator();
				if(isOrderByFileName) {
					Collections.sort(allFiles, comparator);
				}
			}

			if(ignoreFiles!=null)
			{
				for (String fileN : ignoreFiles) {
					fileN = fileN.trim();
					if(fileN.isEmpty()) {
						continue;
					}
					if(fileN.equals("*") || fileN.equals("*.*")) {
						return testcases;
					}
				}
			}
			
			for (File file : allFiles) {
				boolean isIgnore = false;
				
				if(ignoreFiles!=null)
				{
					for (String fileN : ignoreFiles) {
						fileN = fileN.trim();
						if(fileN.isEmpty()) {
							continue;
						}
						
						if(fileN.startsWith("*.")) {
							String ext = fileN.substring(2);
							if(file.getName().endsWith(ext)) {
								isIgnore = true; 
							}
						} else if(fileN.endsWith("*")) {
							fileN = fileN.substring(fileN.length()-1);
							if(file.getName().startsWith(fileN)) {
								isIgnore = true; 
							}
						} else if(fileN.startsWith("*")) {
							fileN = fileN.substring(1);
							if(file.getName().endsWith(fileN)) {
								isIgnore = true; 
							}
						} else if(file.getName().equals(fileN)) {
							isIgnore = true;
						}
					}
					if(isIgnore)
						continue;
				}
				
				try {
					List<TestCase> testcasesTemp = resolveTestCases(file);
					if(testcasesTemp != null)
					{
						for (TestCase testCase : testcasesTemp) {
							testCase.setSourcefileName(file.getName());
							if(testCase.getSimulationNumber()==null)
							{
								testCase.setSimulationNumber(0);
							}
							testCase.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
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
						testcases.addAll(testcasesTemp);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return testcases;
	}
}
