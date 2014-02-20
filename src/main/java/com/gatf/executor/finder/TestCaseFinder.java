package com.gatf.executor.finder;

import java.io.File;
import java.util.List;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;

public interface TestCaseFinder {

	List<TestCase> findTestCases(File directory, AcceptanceTestContext context);
}
