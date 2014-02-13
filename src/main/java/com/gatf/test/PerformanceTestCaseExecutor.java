package com.gatf.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.gatf.report.TestCaseReport;
import com.ning.http.client.ListenableFuture;

public class PerformanceTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {
	
		boolean isReportingEnabled = testCaseExecutorUtil.getContext().getGatfExecutorConfig().isReportingEnabled();
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		SingleTestCaseExecutor singleTestCaseExecutor = new SingleTestCaseExecutor();
		List<TestCaseReport> reports = singleTestCaseExecutor.execute(testCase, testCaseExecutorUtil);
		
		TestCaseReport testCaseReport = reports==null || reports.isEmpty() ? null : reports.get(0);
		
		if(testCaseReport!=null)
		{
			testCaseReport.setNumberOfRuns(1);
			testCaseReport.getExecutionTimes().add(testCaseReport.getExecutionTime());
		}
		
		if(testCase.isStopOnFirstFailureForPerfTest() && testCase.isFailed())
		{
			return reports;
		}
		
		List<ListenableFuture<TestCaseReport>> futures = new ArrayList<ListenableFuture<TestCaseReport>>();
		for (int i = 0; i < testCase.getNumberOfExecutions()-1; i++) {
			
			TestCase testCaseCopy = null;
			
			TestCaseReport testCaseReportCopy = null;
			
			if(isReportingEnabled)
			{
				testCaseReport.setNumberOfRuns(testCaseReport.getNumberOfRuns()+1);
				testCaseCopy = new TestCase(testCase);
				testCaseReportCopy = new TestCaseReport(testCaseReport);
			}
			
			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, new HashMap<String, String>());
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			
			ListenableFuture<TestCaseReport> listenableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReportCopy);
			futures.add(listenableFuture);
		}
		
		for (ListenableFuture<TestCaseReport> listenableFuture : futures) {
			
			try {
				TestCaseReport tc = listenableFuture.get();
				if(isReportingEnabled)
				{
					testCaseReport.setExecutionTime(testCaseReport.getExecutionTime() + tc.getExecutionTime());
					testCaseReport.getExecutionTimes().add(tc.getExecutionTime());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if(isReportingEnabled)
		{
			testCaseReport.setAverageExecutionTime(testCaseReport.getExecutionTime()/testCaseReport.getNumberOfRuns());
		}
		
		return reports;
	}

}
