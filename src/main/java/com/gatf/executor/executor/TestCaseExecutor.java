package com.gatf.executor.executor;

import java.util.List;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

public interface TestCaseExecutor {

	List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil);
}
