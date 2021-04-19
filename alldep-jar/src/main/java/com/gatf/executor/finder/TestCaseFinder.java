/*
    Copyright 2013-2019, Sumeet Chhetri
    
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
package com.gatf.executor.finder;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.AlphanumComparator;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;

/**
 * @author Sumeet Chhetri
 * Defines contract to find all test cases from files inside a given test case directory
 */
public abstract class TestCaseFinder {

	public enum TestCaseFileType
	{
		XML(".xml"),
		JSON(".json"),
		CSV(".csv"),
		XLS(".xls"),
		XLSX(".xlsx");
		
		public String ext;
		
		private TestCaseFileType(String ext)
		{
			this.ext = ext;
		}
	}
	
	protected abstract TestCaseFileType getFileType();
	public abstract List<TestCase> resolveTestCases(File testCaseFile) throws Exception;
	
	private List<String> targetFileNames;
	
	public static final FileFilter DIR_FILTER = new FileFilter() {
		public boolean accept(File file) {
			return file.isDirectory();
		}
	};
	
	public static final FileFilter FILE_FILTER = new FileFilter() {
		public boolean accept(File file) {
			return !file.isDirectory();
		}
	};
	
	public static final FileFilter NOZIP_FILE_FILTER = new FileFilter() {
		public boolean accept(File file) {
			return !file.isDirectory() && !file.getName().endsWith(".zip");
		}
	};
	
	public static void getFiles(File dir, FilenameFilter filter, List<File> fileLst)
	{
		if (dir.isDirectory()) {
			File[] files = dir.listFiles(filter);
			for (File file : files) {
				fileLst.add(file);
				getFiles(file, filter, fileLst);
			}
			
			files = dir.listFiles(DIR_FILTER);
			for (File file : files) {
				getFiles(file, filter, fileLst);
			}
		}
	}
	
	public List<String> getTargetFileNames() {
		if(targetFileNames==null) {
			targetFileNames = new ArrayList<String>();
		}
		return targetFileNames;
	}
	public void setTargetFileNames(List<String> targetFileNames) {
		this.targetFileNames = targetFileNames;
	}
	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context, boolean considerConfig,
			Set<String> relativeFileNames)
	{
		if(context==null)
			considerConfig = false;
		
		String[] ignoreFiles = considerConfig?context.getGatfExecutorConfig().getIgnoreFiles():null;
		String[] orderedFiles = considerConfig?context.getGatfExecutorConfig().getOrderedFiles():null;
		boolean isOrderByFileName = considerConfig?context.getGatfExecutorConfig().isOrderByFileName():false;
		
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File folder, String name) {
				if(targetFileNames!=null && targetFileNames.size()>0)
				{
					for (String tfileName : targetFileNames) {
						return name.equalsIgnoreCase(tfileName) && name.toLowerCase().endsWith(getFileType().ext);
					}
					return false;
				}
				else
				{
					return name.toLowerCase().endsWith(getFileType().ext);
				}
			}
		};
		
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			List<File> files = new ArrayList<File>();
			getFiles(dir, filter, files);
			
			if(considerConfig)
			{
				files = filterFiles(ignoreFiles, files, dir);
				files = orderFiles(orderedFiles, isOrderByFileName, files, dir);
			}
			
			for (File file : files) {
				String relativeFileName = getRelativePath(file, dir);
				try {
					List<TestCase> testcasesTemp = resolveTestCases(file);
					if(testcasesTemp != null)
					{
						for (TestCase testCase : testcasesTemp) {
							testCase.setSourcefileName(relativeFileName);
							if(testCase.getSimulationNumber()==null)
							{
								testCase.setSimulationNumber(0);
							}
							if(considerConfig)
							{
							    if(StringUtils.isBlank(testCase.getBaseUrl())) {
							        testCase.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
							    }
							}
							testCase.setExternalApi(false);
							testCase.setServerApiAuth(false);
							testCase.setServerApiTarget(false);
						}
						
						testcases.addAll(testcasesTemp);
						if(relativeFileNames!=null)relativeFileNames.add(relativeFileName);
					}
				} catch (Exception e) {
					System.out.println("Ignoring file due to invalid testcase format ... " + relativeFileName);
					//e.printStackTrace();
				}
			}
		}
		return testcases;
	}
	
	// returns null if file isn't relative to folder
	public static String getRelativePath(File file, File folder) {
	    String filePath = file.getAbsolutePath();
	    String folderPath = folder.getAbsolutePath();
	    if (filePath.startsWith(folderPath)) {
	        return filePath.substring(folderPath.length() + 1);
	    } else {
	        return null;
	    }
	}
	
	public static List<File> filterFiles(String[] ignoreFiles, List<File> testFiles, File dir)
	{
		if(ignoreFiles!=null)
		{
			List<File> files = new ArrayList<File>();
			for (String fileN : ignoreFiles) {
				fileN = fileN.trim();
				if(fileN.isEmpty()) {
					continue;
				}
				if(fileN.equals("*") || fileN.equals("*.*")) {
					return files;
				}
			}
			
			for (File file : testFiles) {
				boolean isIgnore = false;
				
				String completeFileName = getRelativePath(file, dir);
				String fileName = file.getName();
				
				for (String fileN : ignoreFiles) {
					fileN = fileN.trim();
					
					boolean isCompleteNameMatch = fileN.indexOf("\\")!=-1;
					
					if(fileN.isEmpty()) {
						continue;
					}
					
					if(fileN.startsWith("*.")) {
						String ext = fileN.substring(2);
						if((isCompleteNameMatch && completeFileName.endsWith(ext))
								|| (!isCompleteNameMatch && fileName.endsWith(ext))) {
							isIgnore = true;
							break;
						}
					} else if(fileN.endsWith("*")) {
						fileN = fileN.substring(0, fileN.lastIndexOf("*"));
						if((isCompleteNameMatch && completeFileName.startsWith(fileN))
								|| (!isCompleteNameMatch && fileName.startsWith(fileN))) {
							isIgnore = true; 
							break;
						}
					} else if(fileN.startsWith("*")) {
						fileN = fileN.substring(1);
						if((isCompleteNameMatch && completeFileName.endsWith(fileN))
								|| (!isCompleteNameMatch && fileName.endsWith(fileN))) {
							isIgnore = true;
							break;
						}
					} else if((isCompleteNameMatch && completeFileName.equals(fileN))
							|| (!isCompleteNameMatch && fileName.equals(fileN))) {
						isIgnore = true;
						break;
					}
				}
				if(isIgnore)
					continue;
				
				files.add(file);
			}
			return files;
		}
		return testFiles;
	}
	
	public static List<File> orderFiles(String[] orderedFiles, boolean isOrderByFileName, List<File> files, File dir)
	{
		if(orderedFiles!=null)
		{
			List<File> allFiles = new ArrayList<File>();
			for (String fileN : orderedFiles) {
				for (File file : files) {
					String completeFileName = getRelativePath(file, dir);
					if(completeFileName.equals(fileN)) {
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
			return allFiles;
		}
		else
		{
			if(isOrderByFileName) {
				AlphanumComparator<File> comparator = new AlphanumComparator<File>();
				Collections.sort(files, comparator);
			}
			return files;
		}
	}
	
	public static List<File> filterValidTestCaseFiles(List<File> testFiles)
	{
		List<File> allFiles = new ArrayList<File>();
		for (File file : testFiles) {
		    if(file.getName().toLowerCase().endsWith(".sel")) {
		        allFiles.add(file);
		        continue;
		    }
			try {
				new XMLTestCaseFinder().resolveTestCases(file);
				allFiles.add(file);
			} catch (Exception e) {
			}
		}
		return allFiles;
	}
}
