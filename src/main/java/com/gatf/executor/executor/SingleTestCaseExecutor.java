package com.gatf.executor.executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.TestCaseReport;
import com.ning.http.client.ListenableFuture;

public class SingleTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		TestCaseReport testCaseReport = new TestCaseReport();
		testCaseReport.setTestCase(testCase);
		testCaseReport.setNumberOfRuns(1);
		
		try {
			workflowContextHandler.handleContextVariables(testCase, new HashMap<String, String>());
		} catch (Exception e) {
			testCaseReport.setExecutionTime(0L);
			testCaseReport.setStatus("Failed");
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}

			e.printStackTrace();
			
			testCaseExecutorUtil.getContext().addTestCaseReport(testCaseReport);
			List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
			lst.add(testCaseReport);
			return lst;
		}
		
		ListenableFuture<TestCaseReport> report = testCaseExecutorUtil.executeTestCase(testCase, testCaseReport);
		try {
			testCaseReport = report.get();
		} catch (Exception e) {
			testCaseReport.setStatus("Failed");
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			e.printStackTrace();
		}
		
		testCaseExecutorUtil.getContext().addTestCaseReport(testCaseReport);
		
		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		lst.add(testCaseReport);
		return lst;
	}
}
