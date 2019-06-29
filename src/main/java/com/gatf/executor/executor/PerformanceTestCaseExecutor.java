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
package com.gatf.executor.executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;

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
		reports.add(new TestCaseReport(testCaseReport));
		
		testCaseReport.setNumberOfRuns(1);
		testCaseReport.getExecutionTimes().add(testCaseReport.getExecutionTime());
		
		if(testCase.isStopOnFirstFailureForPerfTest() && testCase.isFailed())
		{
			return reports;
		}
		
		List<CompletableFuture<TestCaseReport>> futures = new ArrayList<CompletableFuture<TestCaseReport>>();
		for (int i = 0; i < testCase.getNumberOfExecutions(); i++) {
			
				testCaseReport.setNumberOfRuns(testCaseReport.getNumberOfRuns()+1);
				TestCase testCaseCopy = new TestCase(testCase);
				TestCaseReport testCaseReportCopy = new TestCaseReport(testCaseReport);
				reports.add(testCaseReportCopy);
			
			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, new HashMap<String, String>(), 
						testCaseExecutorUtil.getContext());
			} catch (Throwable e) {
				testCaseReportCopy.setExecutionTime(0L);
				testCaseReportCopy.setStatus(TestStatus.Failed.status);
				testCaseReport.setFailureReason(TestFailureReason.Exception.status);
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
			
			CompletableFuture<TestCaseReport> CompletableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReportCopy);
			futures.add(CompletableFuture);
		}
		
		for(int i = 0; i < testCase.getNumberOfExecutions(); i++) {
            CompletableFuture<TestCaseReport> CompletableFuture = futures.get(i);
            while(!CompletableFuture.isDone()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
		}
		
		for(int i = 0; i < testCase.getNumberOfExecutions(); i++) {
			CompletableFuture<TestCaseReport> CompletableFuture = futures.get(i);
			try {
				TestCaseReport tc = CompletableFuture.get();
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