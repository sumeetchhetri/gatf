/*
    Copyright 2013-2019, Sumeet Chhetri
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.gatf;

import java.util.List;
import java.util.Set;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

public interface GatfPlugin {
	void doExecute(GatfPluginConfig configuration, List<String> files) throws Exception;
	void setProject(Object project);
	void shutdown();
	void initilaizeContext(GatfExecutorConfig configuration, boolean flag) throws Exception;
	AcceptanceTestContext getContext();
	void setContext(AcceptanceTestContext context);
	List<TestCase> getAllTestCases(AcceptanceTestContext context, Set<String> relativeFileNames, List<String> targetFileNames);
	TestCase getAuthTestCase();
	void invokeServerLogApi(boolean success, TestCaseReport testCaseReport, TestCaseExecutorUtil testCaseExecutorUtil, boolean isFetchFailureLogs);
	void doSeleniumTest(GatfPluginConfig configuration, List<String> files) throws Exception;
	GatfSelDebugger debugSeleniumTest(GatfExecutorConfig configuration, String selscript, String configPath);
}
