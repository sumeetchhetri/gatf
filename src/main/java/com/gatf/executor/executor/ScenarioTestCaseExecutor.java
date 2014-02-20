package com.gatf.executor.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.ning.http.client.ListenableFuture;

public class ScenarioTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		List<ListenableFuture<TestCaseReport>> futures = new ArrayList<ListenableFuture<TestCaseReport>>();
		for (Map<String, String> scenarioMap : testCase.getRepeatScenarios()) {
			
			TestCase testCaseCopy = new TestCase(testCase);
			TestCaseReport testCaseReport = new TestCaseReport();
			testCaseReport.setTestCase(testCaseCopy);
			testCaseReport.setNumberOfRuns(1);

			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, scenarioMap);
			} catch (Exception e) {
				testCaseReport.setExecutionTime(0L);
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
					testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
				}
				
				testCaseExecutorUtil.getContext().addTestCaseReport(testCaseReport);
				lst.add(testCaseReport);

				e.printStackTrace();
				continue;
			}
				
			ListenableFuture<TestCaseReport> listenableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReport);
			
			if(!testCaseReport.getTestCase().isRepeatScenariosConcurrentExecution())
			{
				try {
					testCaseReport = listenableFuture.get();
				} catch (Exception e) {
					testCaseReport.setStatus("Failed");
					testCaseReport.setError(e.getMessage());
					testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
					e.printStackTrace();
				}
				
				testCaseExecutorUtil.getContext().addTestCaseReport(testCaseReport);
				lst.add(testCaseReport);
			}
			else
			{
				futures.add(listenableFuture);
			}
		}
		
		for (ListenableFuture<TestCaseReport> listenableFuture : futures) {
			
			TestCaseReport testCaseReport = null;
			
			try {
				testCaseReport = listenableFuture.get();
			} catch (Exception e) {
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				e.printStackTrace();
			}
			
			testCaseExecutorUtil.getContext().addTestCaseReport(testCaseReport);
			lst.add(testCaseReport);
		}
		
		return lst;
	}

}
