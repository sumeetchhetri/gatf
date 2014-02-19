package com.gatf.test.executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.report.TestCaseReport;
import com.gatf.test.core.TestCase;
import com.gatf.test.core.WorkflowContextHandler;
import com.ning.http.client.ListenableFuture;

public class SingleTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		boolean isReportingEnabled = testCaseExecutorUtil.getContext().getGatfExecutorConfig().isReportingEnabled();
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		TestCaseReport testCaseReport = null;
		
		if(isReportingEnabled)
		{
			testCaseReport = new TestCaseReport();
			testCaseReport.setTestCase(testCase);
			testCaseReport.setNumberOfRuns(1);
		}
		
		try {
			workflowContextHandler.handleContextVariables(testCase, new HashMap<String, String>());
		} catch (Exception e) {
			if(isReportingEnabled)
			{
				testCaseReport.setExecutionTime(0L);
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
					testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
				}
				testCaseExecutorUtil.getContext().getFinalTestResults().get(testCase.getIdentifier()).add(testCaseReport);
			}
			e.printStackTrace();
			List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
			lst.add(testCaseReport);
			return lst;
		}
		
		ListenableFuture<TestCaseReport> report = testCaseExecutorUtil.executeTestCase(testCase, testCaseReport);
		try {
			testCaseReport = report.get();
		} catch (Exception e) {
			if(isReportingEnabled)
			{
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(e.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			}
			e.printStackTrace();
		}
		
		if(isReportingEnabled && testCaseReport!=null)
		{
			testCaseExecutorUtil.getContext().getFinalTestResults().get(testCaseReport.getTestCase().getIdentifier()).add(testCaseReport);
			List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
			lst.add(testCaseReport);
			return lst;
		}
		
		return null;
	}
}
