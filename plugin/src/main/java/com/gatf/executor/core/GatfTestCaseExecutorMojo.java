/*
 * Copyright 2013-2019, Sumeet Chhetri
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gatf.executor.core;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

/**
 * @author Sumeet Chhetri The maven plugin main class for the Test case Executor/Workflow engine
 */
@Mojo(
		name = "gatf-executor", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		defaultPhase = LifecyclePhase.INTEGRATION_TEST, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = false, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfTestCaseExecutorMojo extends AbstractMojo implements GatfPlugin {

    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    @Parameter(alias = "baseUrl")
    private String baseUrl;

    @Parameter(alias = "testCasesBasePath", defaultValue = "${project.build.testOutputDirectory}")
    private String testCasesBasePath;

    @Parameter(alias = "testCaseDir", defaultValue = "data")
    private String testCaseDir;

    @Parameter(alias = "outFilesBasePath", defaultValue = "${project.build.testOutputDirectory}")
    private String outFilesBasePath;

    @Parameter(alias = "outFilesDir", defaultValue = "out")
    private String outFilesDir;

    @Parameter(alias = "authEnabled")
    private boolean authEnabled;

    @Parameter(alias = "authUrl")
    private String authUrl;

    @Parameter(alias = "authExtractAuth")
    private String authExtractAuth;

    @Parameter(alias = "authParamsDetails")
    private String authParamsDetails;

    @Parameter(alias = "wsdlLocFile")
    private String wsdlLocFile;

    @Parameter(alias = "soapAuthEnabled")
    private boolean soapAuthEnabled;

    @Parameter(alias = "soapAuthWsdlKey")
    private String soapAuthWsdlKey;

    @Parameter(alias = "soapAuthOperation")
    private String soapAuthOperation;

    @Parameter(alias = "soapAuthExtractAuth")
    private String soapAuthExtractAuth;

    @Parameter(alias = "numConcurrentExecutions", defaultValue = "1")
    private Integer numConcurrentExecutions;

    @Parameter(alias = "configFile")
    private String configFile;

    @Parameter(alias = "httpCompressionEnabled", defaultValue = "true")
    private boolean httpCompressionEnabled;

    @Parameter(alias = "httpConnectionTimeout", defaultValue = "10000")
    private Integer httpConnectionTimeout;

    @Parameter(alias = "httpRequestTimeout", defaultValue = "10000")
    private Integer httpRequestTimeout;

    @Parameter(alias = "concurrentUserSimulationNum", defaultValue = "0")
    private Integer concurrentUserSimulationNum;

    @Parameter(alias = "testDataConfigFile")
    private String testDataConfigFile;

    @Parameter(alias = "authDataProvider")
    private String authDataProvider;

    @Parameter(alias = "compareEnabled")
    private boolean compareEnabled;

    @Parameter(alias = "testCaseHooksPaths")
    private String[] testCaseHooksPath;

    @Parameter(alias = "enabled", defaultValue = "true")
    private Boolean enabled;

    @Parameter(alias = "loadTestingEnabled")
    private boolean loadTestingEnabled;

    @Parameter(alias = "loadTestingTime")
    private Long loadTestingTime;

    @Parameter(alias = "gatfTestDataConfig")
    private GatfTestDataConfig gatfTestDataConfig;

    @Parameter(alias = "concurrentUserRampUpTime", defaultValue = "0")
    private Long concurrentUserRampUpTime;

    @Parameter(alias = "loadTestingReportSamples", defaultValue = "3")
    private Integer loadTestingReportSamples;

    @Parameter(alias = "debugEnabled", defaultValue = "false")
    private boolean debugEnabled;

    @Parameter(alias = "distributedLoadTests", defaultValue = "false")
    private boolean distributedLoadTests;

    @Parameter(alias = "distributedNodes")
    private String[] distributedNodes;

    @Parameter(alias = "ignoreFiles")
    private String[] ignoreFiles;

    @Parameter(alias = "orderedFiles")
    private String[] orderedFiles;

    @Parameter(alias = "isFetchFailureLogs", defaultValue = "false")
    private boolean isFetchFailureLogs;

    @Parameter(alias = "isServerLogsApiAuthEnabled", defaultValue = "false")
    private boolean isServerLogsApiAuthEnabled;

    @Parameter(alias = "serverLogsApiFileName")
    private String serverLogsApiFileName;

    @Parameter(alias = "serverLogsApiAuthExtractAuth")
    private String serverLogsApiAuthExtractAuth;

    @Parameter(alias = "repeatSuiteExecutionNum")
    private Integer repeatSuiteExecutionNum = 0;

    @Parameter(alias = "isGenerateExecutionLogs")
    private boolean isGenerateExecutionLogs = false;

    @Parameter(alias = "isSeleniumExecutor")
    private boolean isSeleniumExecutor = false;

    @Parameter(alias = "seleniumScripts")
    private String[] seleniumScripts;

    @Parameter(alias = "seleniumDriverConfigs")
    private SeleniumDriverConfig[] seleniumDriverConfigs;

    @Parameter(alias = "seleniumLoggerPreferences")
    private String seleniumLoggerPreferences;

    @Parameter(alias = "javaHome")
    private String javaHome;

    private GatfTestCaseExecutorUtil util = new GatfTestCaseExecutorUtil();
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Function<Void, ClassLoader> fcl = new Function<Void, ClassLoader>() {
		@Override
		public ClassLoader apply(Void t) {
			if (project != null) {
	            try {
	                
					List classpathElements = project.getCompileClasspathElements();
	                classpathElements.addAll(project.getTestClasspathElements());
	                classpathElements.add(project.getBuild().getOutputDirectory());
	                classpathElements.add(project.getBuild().getTestOutputDirectory());
	                URL[] urls = new URL[classpathElements.size()];
	                for (int i = 0; i < classpathElements.size(); i++) {
	                    urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
	                }
	                return new URLClassLoader(urls, getClass().getClassLoader());
	            } catch (Exception e) {
	                getLog().error("Couldn't get the classloader.");
	            }
	        }
	        return getClass().getClassLoader();
		}
	};

    public void setProject(Object project) {
        this.project = (MavenProject)project;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setTestCasesBasePath(String testCasesBasePath) {
        this.testCasesBasePath = testCasesBasePath;
    }

    public void setTestCaseDir(String testCaseDir) {
        this.testCaseDir = testCaseDir;
    }

    public void setOutFilesBasePath(String outFilesBasePath) {
        this.outFilesBasePath = outFilesBasePath;
    }

    public void setOutFilesDir(String outFilesDir) {
        this.outFilesDir = outFilesDir;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public void setAuthExtractAuth(String authExtractAuth) {
        this.authExtractAuth = authExtractAuth;
    }

    public void setAuthParamsDetails(String authParamsDetails) {
        this.authParamsDetails = authParamsDetails;
    }

    public void setWsdlLocFile(String wsdlLocFile) {
        this.wsdlLocFile = wsdlLocFile;
    }

    public void setSoapAuthEnabled(boolean soapAuthEnabled) {
        this.soapAuthEnabled = soapAuthEnabled;
    }

    public void setSoapAuthWsdlKey(String soapAuthWsdlKey) {
        this.soapAuthWsdlKey = soapAuthWsdlKey;
    }

    public void setSoapAuthOperation(String soapAuthOperation) {
        this.soapAuthOperation = soapAuthOperation;
    }

    public void setSoapAuthExtractAuth(String soapAuthExtractAuth) {
        this.soapAuthExtractAuth = soapAuthExtractAuth;
    }

    public void setNumConcurrentExecutions(Integer numConcurrentExecutions) {
        this.numConcurrentExecutions = numConcurrentExecutions;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public void setHttpCompressionEnabled(boolean httpCompressionEnabled) {
        this.httpCompressionEnabled = httpCompressionEnabled;
    }

    public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
        this.httpConnectionTimeout = httpConnectionTimeout;
    }

    public void setHttpRequestTimeout(Integer httpRequestTimeout) {
        this.httpRequestTimeout = httpRequestTimeout;
    }

    public void setConcurrentUserSimulationNum(Integer concurrentUserSimulationNum) {
        this.concurrentUserSimulationNum = concurrentUserSimulationNum;
    }

    public void setTestDataConfigFile(String testDataConfigFile) {
        this.testDataConfigFile = testDataConfigFile;
    }

    public void setAuthDataProvider(String authDataProvider) {
        this.authDataProvider = authDataProvider;
    }

    public void setCompareEnabled(boolean compareEnabled) {
        this.compareEnabled = compareEnabled;
    }

    public void setTestCaseHooksPath(String[] testCaseHooksPath) {
        this.testCaseHooksPath = testCaseHooksPath;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLoadTestingEnabled(boolean loadTestingEnabled) {
        this.loadTestingEnabled = loadTestingEnabled;
    }

    public void setLoadTestingTime(Long loadTestingTime) {
        this.loadTestingTime = loadTestingTime;
    }

    public void setConcurrentUserRampUpTime(Long concurrentUserRampUpTime) {
        this.concurrentUserRampUpTime = concurrentUserRampUpTime;
    }

    public void setLoadTestingReportSamples(Integer loadTestingReportSamples) {
        this.loadTestingReportSamples = loadTestingReportSamples;
    }

    public void setContext(AcceptanceTestContext context) {
        util.setContext(context);
    }

    public void execute() throws MojoFailureException {
        GatfExecutorConfig configuration = new GatfExecutorConfig();
        configuration.setAuthEnabled(authEnabled);
        configuration.setAuthExtractAuth(authExtractAuth);
        configuration.setAuthUrl(authUrl);
        configuration.setAuthParamsDetails(authParamsDetails);
        configuration.setBaseUrl(baseUrl);
        configuration.setNumConcurrentExecutions(numConcurrentExecutions);
        configuration.setOutFilesBasePath(outFilesBasePath);
        configuration.setOutFilesDir(outFilesDir);
        configuration.setSoapAuthEnabled(soapAuthEnabled);
        configuration.setSoapAuthExtractAuth(soapAuthExtractAuth);
        configuration.setSoapAuthOperation(soapAuthOperation);
        configuration.setSoapAuthWsdlKey(soapAuthWsdlKey);
        configuration.setTestCaseDir(testCaseDir);
        configuration.setTestCasesBasePath(testCasesBasePath);
        configuration.setWsdlLocFile(wsdlLocFile);
        configuration.setHttpCompressionEnabled(httpCompressionEnabled);
        configuration.setHttpConnectionTimeout(httpConnectionTimeout);
        configuration.setHttpRequestTimeout(httpRequestTimeout);
        configuration.setConcurrentUserSimulationNum(concurrentUserSimulationNum);
        configuration.setTestDataConfigFile(testDataConfigFile);
        configuration.setAuthDataProvider(authDataProvider);
        configuration.setCompareEnabled(compareEnabled);
        configuration.setEnabled(enabled);
        configuration.setLoadTestingEnabled(loadTestingEnabled);
        configuration.setLoadTestingTime(loadTestingTime);
        configuration.setConcurrentUserRampUpTime(concurrentUserRampUpTime);
        configuration.setLoadTestingReportSamples(loadTestingReportSamples);
        configuration.setDebugEnabled(debugEnabled);
        configuration.setGatfTestDataConfig(gatfTestDataConfig);
        configuration.setTestCaseHooksPaths(testCaseHooksPath);
        configuration.setDistributedLoadTests(distributedLoadTests);
        configuration.setDistributedNodes(distributedNodes);
        configuration.setIgnoreFiles(ignoreFiles);
        configuration.setOrderedFiles(orderedFiles);
        configuration.setFetchFailureLogs(isFetchFailureLogs);
        configuration.setServerLogsApiAuthExtractAuth(serverLogsApiAuthExtractAuth);
        configuration.setServerLogsApiAuthEnabled(isServerLogsApiAuthEnabled);
        configuration.setRepeatSuiteExecutionNum(repeatSuiteExecutionNum);
        configuration.setGenerateExecutionLogs(isGenerateExecutionLogs);
        configuration.setSeleniumExecutor(isSeleniumExecutor);
        configuration.setSeleniumScripts(seleniumScripts);
        configuration.setSeleniumDriverConfigs(seleniumDriverConfigs);
        configuration.setSeleniumLoggerPreferences(seleniumLoggerPreferences);
        configuration.setJavaHome(javaHome);

        if (configFile != null) {
            try {
                File resource = null;
                File basePath = new File(testCasesBasePath);
                resource = new File(basePath, configFile);
                if (resource.exists()) {
                	if(configFile.trim().endsWith(".xml")) {
	                    configuration = WorkflowContextHandler.XOM.readValue(resource, GatfExecutorConfig.class);
                	} else if(configFile.trim().endsWith(".json")) {
                		configuration = new ObjectMapper().readValue(resource, GatfExecutorConfig.class);
                	} else {
                		throw new RuntimeException("Invalid Config file, please provide either an xml or a json config file");
                	}
                    
                    if (configuration.getTestCasesBasePath() == null)
                        configuration.setTestCasesBasePath(testCasesBasePath);

                    if (configuration.getOutFilesBasePath() == null)
                        configuration.setOutFilesBasePath(outFilesBasePath);

                    if (configuration.getTestCaseDir() == null)
                        configuration.setTestCaseDir(testCaseDir);

                    if (configuration.getNumConcurrentExecutions() == null)
                        configuration.setNumConcurrentExecutions(numConcurrentExecutions);

                    if (configuration.getHttpConnectionTimeout() == null)
                        configuration.setHttpConnectionTimeout(httpConnectionTimeout);

                    if (configuration.getHttpRequestTimeout() == null)
                        configuration.setHttpRequestTimeout(httpRequestTimeout);

                    if (!configuration.isHttpCompressionEnabled())
                        configuration.setHttpCompressionEnabled(httpCompressionEnabled);

                    if (configuration.getConcurrentUserSimulationNum() == null)
                        configuration.setConcurrentUserSimulationNum(concurrentUserSimulationNum);

                    if (configuration.getLoadTestingReportSamples() == null)
                        configuration.setLoadTestingReportSamples(loadTestingReportSamples);

                    if (configuration.getConcurrentUserRampUpTime() == null)
                        configuration.setConcurrentUserRampUpTime(concurrentUserRampUpTime);

                    if (configuration.isEnabled() == null)
                        configuration.setEnabled(true);

                    if (configuration.getRepeatSuiteExecutionNum() == null)
                        configuration.setRepeatSuiteExecutionNum(repeatSuiteExecutionNum);
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        
        configuration.setJavaVersion(System.getProperty("java.version"));
        
        try {
        	util.setFcl(fcl);
        	util.doExecute(configuration, null);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            shutdown();
        }
    }

    public void doExecute(GatfPluginConfig configuration, List<String> files) throws Exception {
    	util.setFcl(fcl);
    	util.doExecute((GatfExecutorConfig) configuration, files);
    }

    public void initilaizeContext(GatfExecutorConfig configuration, boolean flag) throws Exception {
    	util.setFcl(fcl);
        util.initilaizeContext(configuration, flag);
    }

    public void invokeServerLogApi(boolean success, TestCaseReport testCaseReport, TestCaseExecutorUtil testCaseExecutorUtil, boolean isFetchFailureLogs) {
    	util.setFcl(fcl);
        util.invokeServerLogApi(success, testCaseReport, testCaseExecutorUtil, isFetchFailureLogs);
    }

    public AcceptanceTestContext getContext() {
        return util.getContext();
    }

    public TestCase getAuthTestCase() {
        return util.getAuthTestCase();
    }

    public void shutdown() {
    	util.setFcl(fcl);
        util.shutdown();
    }

	@Override
	public List<TestCase> getAllTestCases(AcceptanceTestContext context, Set<String> relativeFileNames, List<String> targetFileNames) {
		util.setFcl(fcl);
		return util.getAllTestCases(context, relativeFileNames, targetFileNames);
	}

	@Override
	public void doSeleniumTest(GatfPluginConfig configuration, List<String> files) throws Exception {
		util.setFcl(fcl);
		util.doSeleniumTest((GatfExecutorConfig) configuration, files);
	}

	@Override
	public GatfSelDebugger debugSeleniumTest(GatfExecutorConfig configuration, String selscript, String configPath) {
		util.setFcl(fcl);
		return util.debugSeleniumTest(configuration, selscript, configPath);
	}
}
