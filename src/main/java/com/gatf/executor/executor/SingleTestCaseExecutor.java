/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
 * The simple single test case executor
 */
public class SingleTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		TestCaseReport testCaseReport = new TestCaseReport();
		testCaseReport.setTestCase(testCase);
		testCaseReport.setNumberOfRuns(1);
		
		try {
			workflowContextHandler.handleContextVariables(testCase, new HashMap<String, String>(), 
					testCaseExecutorUtil.getContext());
		} catch (Exception e) {
			testCaseReport.setExecutionTime(0L);
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.Exception.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}

			e.printStackTrace();
			
			List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
			lst.add(testCaseReport);
			return lst;
		}
		
		CompletableFuture<TestCaseReport> report = testCaseExecutorUtil.executeTestCase(testCase, testCaseReport);
		try {
		    while(!report.isDone()) {
		        Thread.sleep(1);
		    }
			testCaseReport = report.get();
		} catch (Exception e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			e.printStackTrace();
		}
		
		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		lst.add(testCaseReport);
		return lst;
	}
	
	public List<TestCaseReport> executeDirectTestCase(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		TestCaseReport testCaseReport = new TestCaseReport();
		testCaseReport.setTestCase(testCase);
		testCaseReport.setNumberOfRuns(1);
		
		CompletableFuture<TestCaseReport> report = testCaseExecutorUtil.executeTestCase(testCase, testCaseReport);
		try {
            while(!report.isDone()) {
                Thread.sleep(1);
            }
			testCaseReport = report.get();
		} catch (Exception e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			e.printStackTrace();
		}
		
		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		lst.add(testCaseReport);
		return lst;
	}
}
