package com.gatf.test;

import java.util.List;

import com.gatf.report.TestCaseReport;

public interface TestCaseExecutor {

	List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil);
}
