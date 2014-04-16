package com.gatf.executor.executor;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.ning.http.client.ListenableFuture;

/**
 * @author Sumeet Chhetri
 * The performance test case executor, handles concurrent execution of test cases
 */
public class PerformanceTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {
	
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		SingleTestCaseExecutor singleTestCaseExecutor = new SingleTestCaseExecutor();
		List<TestCaseReport> reports = singleTestCaseExecutor.execute(testCase, testCaseExecutorUtil);
		
		TestCaseReport testCaseReport = reports==null || reports.isEmpty() ? null : reports.get(0);
		
		testCaseReport.setNumberOfRuns(1);
		testCaseReport.getExecutionTimes().add(testCaseReport.getExecutionTime());
		
		if(testCase.isStopOnFirstFailureForPerfTest() && testCase.isFailed())
		{
			return reports;
		}
		
		int numParallel = Runtime.getRuntime().availableProcessors();
		
		int counter = 0;
		
		List<ListenableFuture<TestCaseReport>> futures = new ArrayList<ListenableFuture<TestCaseReport>>();
		for (int i = 0; i < testCase.getNumberOfExecutions()-1; i++) {
			
				testCaseReport.setNumberOfRuns(testCaseReport.getNumberOfRuns()+1);
				TestCase testCaseCopy = new TestCase(testCase);
				TestCaseReport testCaseReportCopy = new TestCaseReport(testCaseReport);
			
			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, new HashMap<String, String>());
			} catch (Throwable e) {
				testCaseReportCopy.setExecutionTime(0L);
				testCaseReportCopy.setStatus(TestStatus.Failed.status);
				testCaseReportCopy.setErrorText(ExceptionUtils.getStackTrace(e));
				testCaseReportCopy.setError(e.getMessage());
				if(e.getMessage()==null && testCaseReportCopy.getErrorText()!=null && testCaseReportCopy.getErrorText().indexOf("\n")!=-1) {
					testCaseReportCopy.setError(testCaseReportCopy.getErrorText().substring(0, testCaseReportCopy.getErrorText().indexOf("\n")));
				}
				
				testCaseReport.setExecutionTime(testCaseReport.getExecutionTime() + testCaseReportCopy.getExecutionTime());
				testCaseReport.getExecutionTimes().add(testCaseReportCopy.getExecutionTime());
				testCaseReport.getErrors().put(i+2+"", testCaseReportCopy.getError());

				e.printStackTrace();
				continue;
			}
			
			ListenableFuture<TestCaseReport> listenableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReportCopy);
			futures.add(listenableFuture);
			
			if(futures.size()==numParallel) {
				for (ListenableFuture<TestCaseReport> listenableFutureT : futures) {
					
					try {
						TestCaseReport tc = listenableFutureT.get();
						testCaseReport.setExecutionTime(testCaseReport.getExecutionTime() + tc.getExecutionTime());
						testCaseReport.getExecutionTimes().add(tc.getExecutionTime());
						if(tc.getError()!=null)
						{
							testCaseReport.getErrors().put(i+2+"", tc.getError());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					counter ++;
				}
				futures.clear();
			}
		}
		
		for (int i=counter;i<counter+futures.size();i++) {
			
			ListenableFuture<TestCaseReport> listenableFuture = futures.get(i);
			try {
				TestCaseReport tc = listenableFuture.get();
				testCaseReport.setExecutionTime(testCaseReport.getExecutionTime() + tc.getExecutionTime());
				testCaseReport.getExecutionTimes().add(tc.getExecutionTime());
				if(tc.getError()!=null)
				{
					testCaseReport.getErrors().put(i+2+"", tc.getError());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		testCaseReport.setAverageExecutionTime(testCaseReport.getExecutionTime()/testCaseReport.getNumberOfRuns());
		return reports;
	}
}