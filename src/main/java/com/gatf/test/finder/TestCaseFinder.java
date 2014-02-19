package com.gatf.test.finder;

import java.io.File;
import java.util.List;

import com.gatf.test.core.AcceptanceTestContext;
import com.gatf.test.core.TestCase;

public interface TestCaseFinder {

	List<TestCase> findTestCases(File directory, AcceptanceTestContext context);
}
