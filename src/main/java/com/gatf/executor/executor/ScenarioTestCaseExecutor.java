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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.executor.validator.ResponseValidator;
import com.ning.http.client.ListenableFuture;

/**
 * @author Sumeet Chhetri
 * The scenario based test case executor, handles repeatable test case execution
 * based on scenario values/provider
 */
public class ScenarioTestCaseExecutor implements TestCaseExecutor {

	private Logger logger = Logger.getLogger(ScenarioTestCaseExecutor.class.getSimpleName());
	
	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		List<ListenableFuture<TestCaseReport>> futures = new ArrayList<ListenableFuture<TestCaseReport>>();
		for (Map<String, String> scenarioMap : testCase.getRepeatScenarios()) {
			
			logger.info("Running with Scenario map = " + scenarioMap);
			
			TestCase testCaseCopy = new TestCase(testCase);
			TestCaseReport testCaseReport = new TestCaseReport();
			testCaseReport.setTestCase(testCaseCopy);
			testCaseReport.setNumberOfRuns(1);
			testCaseCopy.setCurrentScenarioVariables(scenarioMap);

			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, scenarioMap, 
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
				lst.add(testCaseReport);

				e.printStackTrace();
				continue;
			}
				
			ListenableFuture<TestCaseReport> listenableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReport);
			
			if(!testCaseReport.getTestCase().isRepeatScenariosConcurrentExecution())
			{
				try {
	                while(!listenableFuture.isDone()) {
	                    Thread.sleep(1);
	                }
					testCaseReport = listenableFuture.get();
					ResponseValidator.validateLogicalConditions(testCaseReport.getTestCase(), 
							testCaseExecutorUtil.getContext(), scenarioMap);
					testCaseReport.getTestCase().setCurrentScenarioVariables(null);
				} catch (Exception e) {
					testCaseReport.setStatus(TestStatus.Failed.status);
					testCaseReport.setFailureReason(TestFailureReason.Exception.status);
					testCaseReport.setError(e.getMessage());
					testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
					e.printStackTrace();
				} catch (AssertionError e) {
					testCaseReport.setStatus(TestStatus.Failed.status);
					testCaseReport.setFailureReason(TestFailureReason.NodeValidationFailed.status);
					testCaseReport.setError(e.getMessage());
					testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
					e.printStackTrace();
				}
				lst.add(testCaseReport);
			}
			else
			{
				futures.add(listenableFuture);
			}
		}
		
		for (ListenableFuture<TestCaseReport> listenableFuture : futures) {
		    while(!listenableFuture.isDone()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }
		
		for (ListenableFuture<TestCaseReport> listenableFuture : futures) {
			TestCaseReport testCaseReport = null;
			try {
				testCaseReport = listenableFuture.get();
				ResponseValidator.validateLogicalConditions(testCaseReport.getTestCase(), 
						testCaseExecutorUtil.getContext(), testCaseReport.getTestCase().getCurrentScenarioVariables());
				testCaseReport.getTestCase().setCurrentScenarioVariables(null);
			} catch (Exception e) {
				testCaseReport.setStatus(TestStatus.Failed.status);
				testCaseReport.setFailureReason(TestFailureReason.Exception.status);
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				e.printStackTrace();
			} catch (AssertionError e) {
				testCaseReport.setStatus(TestStatus.Failed.status);
				testCaseReport.setFailureReason(TestFailureReason.NodeValidationFailed.status);
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				e.printStackTrace();
			}
			lst.add(testCaseReport);
		}
		
		return lst;
	}

}
