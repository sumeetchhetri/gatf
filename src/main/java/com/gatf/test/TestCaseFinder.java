package com.gatf.test;

import java.io.File;
import java.util.List;

public interface TestCaseFinder {

	List<TestCase> findTestCases(File directory, AcceptanceTestContext context);
}
