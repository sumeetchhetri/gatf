package com.gatf.test.executor;

import java.util.List;

import com.gatf.report.TestCaseReport;
import com.gatf.test.core.TestCase;

public interface TestCaseExecutor {

	List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil);
}
