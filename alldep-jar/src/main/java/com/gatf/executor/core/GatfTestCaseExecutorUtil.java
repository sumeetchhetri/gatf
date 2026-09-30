/*
    Copyright 2013-2026, Sumeet Chhetri

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
package com.gatf.executor.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.logging.LoggingPreferences;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.distributed.DistributedAcceptanceContext;
import com.gatf.executor.distributed.DistributedGatfListener;
import com.gatf.executor.distributed.DistributedGatfTester;
import com.gatf.executor.distributed.DistributedGatfTester.DistributedConnection;
import com.gatf.executor.distributed.DistributedTestContext;
import com.gatf.executor.distributed.DistributedTestStatus;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.CSVTestCaseFinder;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.XLSTestCaseFinder;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.XLSXTestCaseFinder;
import com.gatf.executor.finder.JSONTestCaseFinder;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.LoadTestResource;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.ReportHandler.TestFileReporter;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.executor.report.RuntimeReportUtil.LoadTestEntry;
import com.gatf.executor.report.TestCaseExecutionLogGenerator;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.executor.report.TestExecutionPercentile;
import com.gatf.executor.report.TestSuiteStats;
import com.gatf.generator.core.ClassLoaderUtils;
import com.gatf.selenium.Command;
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumCodeGeneratorAndUtil;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.SeleniumTest;
import com.gatf.selenium.SeleniumTest.FailureException;
import com.gatf.selenium.SeleniumTest.GatfRunTimeError;
import com.gatf.selenium.SeleniumTest.GatfRunTimeErrors;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;
import com.gatf.selenium.SeleniumTestSession;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;
import com.gatf.ui.GatfConfigToolUtil;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;

import au.com.bytecode.opencsv.CSVWriter;

/**
 * @author Sumeet Chhetri The maven plugin main class for the Test case Executor/Workflow engine
 */
public class GatfTestCaseExecutorUtil implements GatfPlugin {

	Logger logger = LogManager.getLogger(GatfTestCaseExecutorUtil.class.getSimpleName());
    
    private String baseUrl;

    private String testCasesBasePath;

    private String testCaseDir;

    private String outFilesBasePath;

    private String outFilesDir;

    private boolean authEnabled;
    
    private String authUrl;

    private String authExtractAuth;

    private String authParamsDetails;
    
    private String wsdlLocFile;
    
    private boolean soapAuthEnabled;
    
    private String soapAuthWsdlKey;
    
    private String soapAuthOperation;

    private String soapAuthExtractAuth;
    
    private Integer numConcurrentExecutions;

    private String configFile;
    
    private boolean httpCompressionEnabled;
    
    private Integer httpConnectionTimeout;
    
    private Integer httpRequestTimeout;

    private Integer concurrentUserSimulationNum;

    private String testDataConfigFile;

    private String authDataProvider;
    
    private boolean compareEnabled;
    
    private String[] testCaseHooksPath;

    private Boolean enabled;

    private boolean loadTestingEnabled;

    private Long loadTestingTime;

    private GatfTestDataConfig gatfTestDataConfig;
    
    private Long concurrentUserRampUpTime;
    
    private Integer loadTestingReportSamples;
    
    private boolean debugEnabled;
    
    private boolean distributedLoadTests;
    
    private String[] distributedNodes;
    
    private String[] ignoreFiles;
    
    private String[] orderedFiles;
    
    private boolean isFetchFailureLogs;
    
    private boolean isServerLogsApiAuthEnabled;
    
    @SuppressWarnings("unused")
	private String serverLogsApiFileName;
    
    private String serverLogsApiAuthExtractAuth;
    
    private Integer repeatSuiteExecutionNum = 0;

    private boolean isGenerateExecutionLogs = false;
    
    private boolean isSeleniumExecutor = false;
    
    private String[] seleniumScripts;
    
    private SeleniumDriverConfig[] seleniumDriverConfigs;

    private String seleniumLoggerPreferences;

    private String javaHome;

    Long startTime = 0L;
    
    private boolean selConcWebdriver;

    AcceptanceTestContext context;

    private DistributedGatfTester distributedGatfTester;

    // Test case execution log generator
    Thread tclgenerator = null;

    private TestCase authTestCase;
    
    Function<Void, ClassLoader> fcl = new Function<Void, ClassLoader>() {
		@Override
		public ClassLoader apply(Void t) {
			return getClass().getClassLoader();
		}
	};

	public void setFcl(Function<Void, ClassLoader> fcl) {
		this.fcl = fcl;
	}

	public void setProject(Object project) {
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

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }
    
    public void setSelConcWebdriver(boolean selConcWebdriver) {
		this.selConcWebdriver = selConcWebdriver;
	}

	public void setContext(AcceptanceTestContext context) {
        this.context = context;
    }

    private void sortAndOrderTestCases(List<TestCase> allTestCases, GatfExecutorConfig configuration) {
        List<TestCase> pretestcases = new ArrayList<TestCase>();
        List<TestCase> posttestcases = new ArrayList<TestCase>();
        for (TestCase testCase : allTestCases) {
            if ((testCase.getUrl() != null && testCase.getUrl().equalsIgnoreCase(configuration.getAuthUrl())) || configuration.isSoapAuthTestCase(testCase)) {
                pretestcases.add(testCase);
            }
            testCase.setSimulationNumber(0);
        }
        if (configuration.isAuthEnabled() && pretestcases.size() > 0) {
            authTestCase = pretestcases.get(0);
        }
        for (TestCase testCase : allTestCases) {
            if ((testCase.getUrl() != null && !testCase.getUrl().equalsIgnoreCase(configuration.getAuthUrl()) && !configuration.isSoapAuthTestCase(testCase))
                    || (testCase.getUrl() == null && testCase.isSoapBase())) {
                posttestcases.add(testCase);
            }
        }

        Collections.sort(posttestcases, new Comparator<TestCase>() {
            public int compare(TestCase o1, TestCase o2) {
                return o1 == null ? (o2 == null ? 0 : Integer.MIN_VALUE) : (o2 == null ? Integer.MAX_VALUE : Integer.valueOf(o1.getSequence()).compareTo(o2.getSequence()));
            }
        });

        allTestCases.clear();
        allTestCases.addAll(pretestcases);
        allTestCases.addAll(posttestcases);

        int sequence = 0;
        for (TestCase testCase : allTestCases) {
            if (testCase.getSequence() <= 0)
                testCase.setSequence(sequence++);
            else
                sequence = testCase.getSequence() + 1;
        }
    }

    public List<TestCase> getAllTestCases(AcceptanceTestContext context, Set<String> relativeFileNames, List<String> targetFileNames) {
        List<TestCase> allTestCases = new ArrayList<TestCase>();
        File testCaseDirectory = context.getResourceFile(context.getGatfExecutorConfig().getTestCaseDir());

        TestCaseFinder finder = new XMLTestCaseFinder();
        finder.setTargetFileNames(targetFileNames);
        allTestCases.addAll(finder.findTestCases(testCaseDirectory, context, true, relativeFileNames));

        finder = new JSONTestCaseFinder();
        finder.setTargetFileNames(targetFileNames);
        allTestCases.addAll(finder.findTestCases(testCaseDirectory, context, true, relativeFileNames));

        finder = new CSVTestCaseFinder();
        finder.setTargetFileNames(targetFileNames);
        allTestCases.addAll(finder.findTestCases(testCaseDirectory, context, true, relativeFileNames));

        finder = new XLSTestCaseFinder();
        finder.setTargetFileNames(targetFileNames);
        allTestCases.addAll(finder.findTestCases(testCaseDirectory, context, true, relativeFileNames));

        finder = new XLSXTestCaseFinder();
        finder.setTargetFileNames(targetFileNames);
        allTestCases.addAll(finder.findTestCases(testCaseDirectory, context, true, relativeFileNames));

        sortAndOrderTestCases(allTestCases, context.getGatfExecutorConfig());

        return allTestCases;
    }

    public List<TestCase> copyTestCases(List<TestCase> testCases, Integer runNumber, String baseUrl) {
        List<TestCase> allTestCases = new ArrayList<TestCase>();
        for (TestCase testCase : testCases) {
            TestCase testCaseCopy = new TestCase(testCase);
            testCaseCopy.setSimulationNumber(runNumber);
            if (StringUtils.isNotBlank(baseUrl) && StringUtils.isBlank(testCase.getBaseUrl())) {
                testCaseCopy.setBaseUrl(baseUrl);
            }
            allTestCases.add(testCaseCopy);
        }
        return allTestCases;
    }

    public void execute() throws Exception {
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
        configuration.setSelConcWebdriver(selConcWebdriver);
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
                		configuration = WorkflowContextHandler.OM.readValue(resource, GatfExecutorConfig.class);
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

                    if (configuration.getSeleniumScriptRetryCount() == null)
                        configuration.setSeleniumScriptRetryCount(0);
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        
        configuration.setJavaVersion(System.getProperty("java.version"));
        RunOverrides.apply(configuration);   // Q7: `gatf test` --trace / --retries / --workers (no-op otherwise)
        
        try {
            doExecute(configuration, null);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            shutdown();
        }
    }
    
    public GatfSelDebugger debugSeleniumTest(GatfExecutorConfig configuration, String selscript, String configPath) {
        for (SeleniumDriverConfig selConf : configuration.getSeleniumDriverConfigs()) {
            if (selConf != null && selConf.getDriverName() != null && selConf.getPath()!=null && new File(selConf.getPath()).exists()) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        //System.setProperty("java.home", configuration.getJavaHome());
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");
        System.setProperty("webdriver.http.factory", "jdk-http-client");

        try {
            SeleniumCodeGeneratorAndUtil.clean();
        } catch (Exception e) {
            e.printStackTrace();
        }

        configuration.setSeleniumScripts(new String[] {selscript});
        
        if(configuration.getSeleniumScripts()==null || configuration.getSeleniumScripts().length==0) {
        	throw new RuntimeException("Please provide Selenium scripts for execution");
        }
        
        @SuppressWarnings("unused")
		String path = initOutFolderAndGetPath(context, System.nanoTime(), 0, "sel");
        com.gatf.selenium.gatfjdb.DebuggerSupport.requireDebugger();   // clear error on a JRE (no jdk.jdi / jdwp agent)
        GatfSelDebugger session = null;
    	try {
    		selscript = context.getWorkflowContextHandler().templatize(selscript);
    		Object[] retvals = new Object[4];
    		Command[] out = new Command[1];
    		Map<String, List<List<Integer[]>>> selToJavaLineMap = new LinkedHashMap<>();
    		SeleniumTest dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(selscript, fcl.apply(null), context, retvals, configuration, false, selToJavaLineMap, out);
    		String[] args = new String[] {dyn.getClass().getName(), configPath};
    		session = GatfSelDebugger.debugSession(dyn.getClass(), args, selToJavaLineMap, out[0]);
    		session.setSelscript(selscript);
    		session.setSrcCode(retvals[1].toString());
    	} catch (GatfSelCodeParseError e) {
    		e.printStackTrace();
    		logger.error("Unable to compile seleasy script " + selscript + ", " + e.getMessage());
    		if(e.getAllErrors()!=null && e.getAllErrors().size()>0) {
    			GatfRunTimeErrors errs = new GatfRunTimeErrors(e.getAllErrors());
    			throw errs;
    		} else {
    			throw e;
    		}
    		//throw e;
    	} catch (Exception e) {
    		e.printStackTrace();
    		throw new RuntimeException("Unable to compile seleasy script " + selscript);
    	}
    	return session;
    }

    public void doSeleniumTest(GatfPluginConfig configuration, List<String> files) throws Exception {
    	doSeleniumTest((GatfExecutorConfig) configuration, files);
    }

    /** M6: globalTeardown ran (or was not due) for the current run; reset when globalSetup / the first round starts. */
    private volatile boolean globalTeardownDone = true;
    /** M6: sessionId, path, context, lp of the current round, for a globalTeardown after a crash. */
    private Object[] setupArgs;

    /** E5: nesting of doSeleniumTest (a retry round calls it again); 0 = no run in progress. */
    private int seleniumRunDepth;

    public void doSeleniumTest(GatfExecutorConfig configuration, List<String> files) throws Exception {
        // E5: retry rounds narrow the scripts and advance retryCounter on the config; the console reuses one config, so
        // the outermost call restores them and the next run starts again at round 1 (globalSetup / loginOnce included)
        Object[] runStart = seleniumRunDepth++ > 0 ? null : new Object[] {configuration.getRetryCounter(), configuration.getSeleniumScriptRetryCount(),
                configuration.getSeleniumScripts(), configuration.getSeleniumScript()};
        try {
            doSeleniumTestRound(configuration, files);
        } catch (Exception e) {
            // M6: the run broke off before its end (the context is still up): globalTeardown still runs, once
            RuntimeException te = runGlobalTeardown(configuration);
            if (te != null) System.out.println(te.getMessage());
            throw e;
        } finally {
            seleniumRunDepth--;
            if (runStart != null) {
                configuration.setRetryCounter((Integer) runStart[0]);
                configuration.setSeleniumScriptRetryCount((Integer) runStart[1]);
                configuration.setSeleniumScripts((String[]) runStart[2]);
                configuration.setSeleniumScript((String) runStart[3]);
            }
        }
    }

    private void doSeleniumTestRound(GatfExecutorConfig configuration, List<String> files) throws Exception {

        List<DistributedConnection> distConnections = null;
        
        initilaizeContext(configuration, true);

        distributedGatfTester = new DistributedGatfTester();

        for (SeleniumDriverConfig selConf : configuration.getSeleniumDriverConfigs()) {
            if (selConf != null && selConf.getDriverName() != null && selConf.getPath()!=null && new File(selConf.getPath()).exists()) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        
        //System.setProperty("java.home", configuration.getJavaHome());
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");
        System.setProperty("webdriver.http.factory", "jdk-http-client");
        if(!SystemUtils.IS_OS_WINDOWS) {
        	System.setProperty("jna.library.path", "/usr/local/lib:/usr/lib");
        }
        
        if(configuration.getExtraProperties()!=null) {
        	if(configuration.getExtraProperties().containsKey("jdk.tls.client.protocols")) {
        		System.setProperty("jdk.tls.client.protocols", configuration.getExtraProperties().get("jdk.tls.client.protocols"));
        	} else if(configuration.getExtraProperties().containsKey("crypto.policy")) {
        		System.setProperty("crypto.policy", configuration.getExtraProperties().get("crypto.policy"));
        	} else if(configuration.getExtraProperties().containsKey("webdriver.http.factory")) {
        		System.setProperty("webdriver.http.factory", configuration.getExtraProperties().get("webdriver.http.factory"));
        	} else if(configuration.getExtraProperties().containsKey("jna.library.path")) {
        		System.setProperty("jna.library.path", configuration.getExtraProperties().get("jna.library.path"));
        	}
        }

        try {
            SeleniumCodeGeneratorAndUtil.clean();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (files != null && files.size() > 0) {
            configuration.setSeleniumScripts(files.toArray(new String[files.size()]));
        } else {
        	if(configuration.isSeleniumModuleTests()) {
            	File mdir = context.getResourceFile(configuration.getTestCaseDir());
            	Collection<File> dirs = FileUtils.listFilesAndDirs(mdir, FalseFileFilter.FALSE, TrueFileFilter.INSTANCE);
            	List<String> modules = new ArrayList<String>();
            	for (File f : dirs) {
        			if(new File(f, "main.sel").exists()) {
        				String sfpath = new File(f, "main.sel").getAbsolutePath().replaceFirst(mdir.getAbsolutePath(), StringUtils.EMPTY);
        				if(sfpath.charAt(0)==File.separatorChar) {
        					sfpath = sfpath.substring(1);
        				}
        				modules.add(sfpath);
        			}
        		}
            	configuration.setSeleniumScripts(modules.toArray(new String[modules.size()]));;
            }
        }
        
        if(configuration.getSeleniumScriptRetryCount()>0) {
        	configuration.setLoadTestingEnabled(false);
        	configuration.setConcurrentUserSimulationNum(0);
        }
        
        if(StringUtils.isNotBlank(configuration.getSeleniumScript())) {
        	try {
        		// E4: shared with `gatf docs`: a folder (every *.sel under it), one .sel, or a list file (one script per line)
        		List<String> sfiles = SetupScripts.expandSeleniumScript(configuration.getSeleniumScript(),
        				context.getResourceFile(context.getGatfExecutorConfig().getTestCaseDir()));
        		configuration.setSeleniumScripts(sfiles.toArray(new String[sfiles.size()]));
			} catch (IOException e) {
				System.out.println("seleniumScript ignored: " + e.getMessage());
			}
        }
        
        // M5/M6: loginOnce / globalSetup / globalTeardown run on their own around the suite, never as one of its scripts
        if (configuration.getSeleniumScripts() != null) {
            configuration.setSeleniumScripts(SetupScripts.excludeResolved(configuration.getSeleniumScripts(), context::getResourceFile,
                    configuration.getLoginOnce(), configuration.getGlobalSetup(), configuration.getGlobalTeardown()));
        }

        if (TestFilter.isActive() && configuration.getSeleniumScripts() != null && configuration.getSeleniumScripts().length > 0) {
            // Q5: `gatf test --grep/--tag` selects whole scripts by path + the @tags in their leading // comments
            List<String> keep = new ArrayList<String>();
            for (String s : configuration.getSeleniumScripts()) {
                if (TestFilter.active().matches(TestFilter.selTitle(s, TestFilter.selHeaderTags(selHeaderLines(context, configuration, s))))) {
                    keep.add(s);
                }
            }
            if (keep.isEmpty()) {
                throw new RuntimeException("No UI scripts match " + TestFilter.active().describe());
            }
            configuration.setSeleniumScripts(keep.toArray(new String[keep.size()]));
        }

        ShardSplitter shard = RunOverrides.shard();
        if (shard != null && configuration.getRetryCounter() <= 1 && configuration.getSeleniumScripts() != null
                && configuration.getSeleniumScripts().length > 0) {
            // M7: `gatf test --shard i/n`: this machine runs its deterministic share of the scripts (first round only;
            // retry rounds re-run this shard's own failures)
            String tcDir = configuration.getTestCaseDir() == null ? null : context.getResourceFile(configuration.getTestCaseDir()).getAbsolutePath();
            List<String> mine = shard.select(java.util.Arrays.asList(configuration.getSeleniumScripts()), p -> ShardSplitter.relativeTo(p, tcDir));
            if (mine.isEmpty()) {
                throw new ShardSplitter.EmptyShardException("No UI scripts in " + shard.describe() + " (" + configuration.getSeleniumScripts().length + " script(s) over " + shard.total + " shards)");
            }
            configuration.setSeleniumScripts(mine.toArray(new String[mine.size()]));
        }

        if(configuration.getSeleniumScripts()==null || configuration.getSeleniumScripts().length==0) {
        	throw new RuntimeException("Please provide Selenium scripts for execution");
        }
        
        String roundInfo = "";
        int retryCnter = 0;
        if(configuration.getSessionId()==null) configuration.setSessionId(System.nanoTime());
        if(configuration.getSeleniumScriptRetryCount()>0 && configuration.getRetryCounter()==0) {
        	configuration.setRetryCounter(1);
        	roundInfo = "[NORMAL-MODE] ";
        } else if(configuration.getRetryCounter()>0) {
        	retryCnter = configuration.getRetryCounter()-1;
        	roundInfo = "[RETRY-MODE ROUND " + (configuration.getRetryCounter()-1) + "] ";
        }
        Long sessionId = configuration.getSessionId();
        
        String path = initOutFolderAndGetPath(context, sessionId, retryCnter, "sel");
        
        System.out.println(roundInfo + "Executing following seleasy scripts....\n[\n");
        String tcfdet = "";
        for (String tcfile : configuration.getSeleniumScripts()) {
        	tcfdet += tcfile + "\n";
			System.out.println("\t --> " + tcfile);
		}
        System.out.println("]\n\n");
        if(configuration.getSeleniumScriptRetryCount()>0 && configuration.getRetryCounter()>0) {
        	try {
        		File resource = context.getOutDir();
				FileUtils.writeStringToFile(new File(resource.getAbsolutePath() + File.separator + path + File.separator + "RERTY_MODE_"+configuration.getRetryCounter()+"_TEST_CASES.txt"), tcfdet, "UTF-8");
			} catch (IOException e) {
			}
        }
        
        List<GatfRunTimeError> allErrors = new ArrayList<>();

        final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(configuration);
        setupArgs = new Object[] {sessionId, path, context, lp};   // M6: this round's, for a globalTeardown after a crash
        final List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        final List<Object[]> testdata = new ArrayList<Object[]>();
        final List<String> testClassNames = new ArrayList<String>();
        for (String selscript : configuration.getSeleniumScripts()) {
        	try {
        		selscript = context.getWorkflowContextHandler().templatize(selscript);
        		Object[] retvals = new Object[4];
        		SeleniumTest dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(selscript, fcl.apply(null), context, retvals, configuration, false, null, null);
        		tests.add(dyn);
        		testdata.add(retvals);
        		testClassNames.add(dyn.getClass().getName());
        	} catch (GatfSelCodeParseError e) {
        		e.printStackTrace();
        		if(e.getAllErrors()!=null && e.getAllErrors().size()>0) {
        			allErrors.addAll(e.getAllErrors());
        		} else {
        			allErrors.add(e);
        		}
        		//throw e;
        	} catch (RuntimeException e) {
        		if(e.getMessage().equals("No open command found...")) {
        			System.out.println("Skipping non-executable seleasy script " + selscript);
        		} else {
        			throw new RuntimeException("Unable to compile seleasy script " + selscript);
        		}
        	} catch (Exception e) {
        		e.printStackTrace();
        		throw new RuntimeException("Unable to compile seleasy script " + selscript);
        	}
        }
        
        if(allErrors.size()>0 && tests.size()==0) {
        	throw new GatfRunTimeErrors(allErrors);
        }

        if (configuration.getRetryCounter() <= 1) {
            // M6: a new run: globalTeardown is due at its end (not before a retry round), and globalSetup runs first
            globalTeardownDone = StringUtils.isBlank(configuration.getGlobalTeardown());
            if (StringUtils.isNotBlank(configuration.getGlobalSetup())) {
                runSetupScript(configuration.getGlobalSetup().trim(), "globalSetup", false, sessionId, path, context, configuration, lp);
            }
        }

        // M5: log in once (first round only); every UI script then loads the saved storage state as its browser opens
        if (StringUtils.isNotBlank(configuration.getLoginOnce()) && configuration.getRetryCounter() <= 1) {
        	if (StringUtils.isBlank(configuration.getStorageState())) {
        		System.out.println("loginOnce is set but storageState is not: the login is not saved, so scripts do not start logged in");
        	}
        	runSetupScript(configuration.getLoginOnce().trim(), "loginOnce", true, sessionId, path, context, configuration, lp);
        }

        String runPrefix = "LRun-1";

        int numberOfRuns = 1;
        if (configuration.getConcurrentUserSimulationNum() != null && configuration.getConcurrentUserSimulationNum() > 1) {
            numberOfRuns = configuration.getConcurrentUserSimulationNum();
        }

        // If load testing time is greater than 10sec only then run load tests..
        boolean isLoadTestingEnabled = configuration.isLoadTestingEnabled() && configuration.getLoadTestingTime() > 10000;



        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        // G8: sample clamp + interval + per-iteration decision + counters shared with the other run loop
        LoadRunSchedule schedule = new LoadRunSchedule(isLoadTestingEnabled, configuration);

        List<FutureTask<Object>> distTasks = new ArrayList<FutureTask<Object>>();
        List<String> taskNodes = new ArrayList<String>();
        
        Map<String, Map<Integer, String>> indexes = new HashMap<String, Map<Integer, String>>();
        indexes.put("local", new HashMap<Integer, String>());

        if (isLoadTestingEnabled) {
            if (configuration.isDistributedLoadTests() && configuration.getDistributedNodes() != null && configuration.getDistributedNodes().length > 0) {
                distConnections = new ArrayList<DistributedConnection>();

                for (String node : configuration.getDistributedNodes()) {
                    DistributedConnection conn = distributedGatfTester.distributeContext(node, context, testdata);
                    if (conn != null) {
                        distConnections.add(conn);
                    }
                }

                int dnumruns = 0/* , dtotnds = 0 */;
                if (numberOfRuns < distConnections.size()) {
                    // dtotnds = numberOfRuns - 1;
                    dnumruns = 1;
                } else {
                    // dtotnds = distConnections.size();
                    dnumruns = numberOfRuns / (distConnections.size() + 1);
                }

                if (distConnections.size() > 0) {
                    File selClsFilesZip = SeleniumCodeGeneratorAndUtil.zipSeleniumTests();
                    for (int i = 0; i < distConnections.size(); i++) {
                        DistributedConnection conn = distConnections.get(i);
                        indexes.put(conn.toString(), new HashMap<Integer, String>());
                        FutureTask<Object> task = distributedGatfTester.distributeSeleniumTests(conn, i + 2, dnumruns, selClsFilesZip, testClassNames, context);
                        distTasks.add(task);
                        taskNodes.add(conn.toString());
                    }
                }

                if (distConnections.size() == distTasks.size()) {
                    if (numberOfRuns < distTasks.size()) {
                        numberOfRuns = 1;
                    } else {
                        numberOfRuns = dnumruns + (numberOfRuns % (distConnections.size() + 1));
                    }
                } else {
                    if (numberOfRuns < distTasks.size()) {
                        numberOfRuns = numberOfRuns - distTasks.size();
                    } else {
                        numberOfRuns = dnumruns + (numberOfRuns % (distTasks.size() + 1));
                    }
                }
            }
        }

        if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;

        ExecutorService threadPool = null;
        boolean concExec = false;
        if (numberOfRuns > 1) {
            int threadNum = 100;
            if (numberOfRuns < 100)
                threadNum = numberOfRuns;

            threadPool = Executors.newFixedThreadPool(threadNum);
            context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
        } else if(context.getGatfExecutorConfig().getNumConcurrentExecutions()>1 && tests.size()>1) {
        	//Parallel selenium execution of multiple scripts (M7: no fixed cap of 10; never more workers than scripts)
        	int threadNum = ShardSplitter.poolSize(context.getGatfExecutorConfig().getNumConcurrentExecutions(), tests.size());
        	com.gatf.log.RunConsole.setWorkers(threadNum);   // the pool actually used, for gatf-results.json
        	threadPool = Executors.newFixedThreadPool(threadNum);
        	numberOfRuns = 1;
        	concExec = true;
        	context.getWorkflowContextHandler().initializeSuiteContext(tests.size());
        } else {
        	context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
        }

        startTime = System.currentTimeMillis();

        int runNum = 0;
        List<String> failedFiles = Collections.synchronizedList(new ArrayList<>());
        while (tests.size() > 0) {
            boolean dorep = false;

            boolean done = false;

            if (schedule.next(isLoadTestingEnabled, context.getGatfExecutorConfig(), configuration, startTime)) {
                break;
            }
            dorep = schedule.dorep;
            done = schedule.done;

            runNum++;

            Map<String, List<Map<String, Map<String, List<Object[]>>>>> summLstMap = new LinkedHashMap<>();
            Map<String, List<Map<String, Map<String, Integer[]>>>> sinfoMap = new LinkedHashMap<>();
            List<ImmutablePair<Method, Object[]>> reportLst = new ArrayList<>();
            summLstMap.put("local", new ArrayList<>());
            sinfoMap.put("local", new ArrayList<>());
            if (threadPool != null) {
                List<FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>> ltasks = new ArrayList<>();
                if(!concExec) {
                	for (int i = 0; i < numberOfRuns; i++) {
                        ltasks.add(new FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>(new ConcSeleniumTest(sessionId, path, i, -1, context, tests, testdata, lp, dorep, isLoadTestingEnabled, runNum, runPrefix, "local", failedFiles)));
                        threadPool.execute(ltasks.get(i));
                    	//Ramp up time only applied for load test scenarios
	                    try {
	                        Thread.sleep(concurrentUserRampUpTimeMs);
	                    } catch (InterruptedException e) {
	                    }
                    }
                    for (int i = 0; i < numberOfRuns; i++) {
                        try {
                        	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = ltasks.get(i).get();
                            summLstMap.get("local").add(o.getLeft());
                            reportLst.addAll(o.getRight().getLeft());
                            sinfoMap.get("local").add(o.getRight().getRight());
                            if(o.getMiddle()!=null && o.getMiddle().size()>0) {
                            	allErrors.addAll(o.getMiddle());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                	for (int i = 0; i < tests.size(); i++) {
                		List<SeleniumTest> ctests = new ArrayList<>();
                		ctests.add(tests.get(i));
                		List<Object[]> ctestdata = new ArrayList<>();
                		ctestdata.add(testdata.get(i));
                        ltasks.add(new FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>(new ConcSeleniumTest(sessionId, path, 0, i, context, ctests, ctestdata, lp, dorep, isLoadTestingEnabled, runNum, runPrefix, "local", failedFiles)));
                        threadPool.execute(ltasks.get(i));
                    }
                    for (int i = 0; i < tests.size(); i++) {
                        try {
                        	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = ltasks.get(i).get();
                            summLstMap.get("local").add(o.getLeft());
                            reportLst.addAll(o.getRight().getLeft());
                            sinfoMap.get("local").add(o.getRight().getRight());
                            if(o.getMiddle()!=null && o.getMiddle().size()>0) {
                            	allErrors.addAll(o.getMiddle());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (dorep && isLoadTestingEnabled) {
                    schedule.advance();
                }
            } else {
                try {
                	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = new ConcSeleniumTest(sessionId, path, 0, -1, context, tests, testdata, lp, dorep, isLoadTestingEnabled, runNum, runPrefix, "local", failedFiles).call();
                    summLstMap.get("local").add(o.getLeft());
                    reportLst.addAll(o.getRight().getLeft());
                    sinfoMap.get("local").add(o.getRight().getRight());
                    if(o.getMiddle()!=null && o.getMiddle().size()>0) {
                    	allErrors.addAll(o.getMiddle());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (dorep && isLoadTestingEnabled) {
                    schedule.advance();
                }
            }

            if (dorep) {
            	if(!isLoadTestingEnabled) {
            		try {
		            	String pdfReport = context.getOutDir().getAbsolutePath() + File.separator + path + File.separator + (runPrefix + "-" + (runNum) + "-RP-");
		            	if(configuration.getSeleniumScriptRetryCount()>0 && configuration.getRetryCounter()>0) {
		            		pdfReport += "RERTY_MODE_" + configuration.getRetryCounter() + "-";
		            	}
		            	pdfReport += System.currentTimeMillis();
		                String csvReport = pdfReport + ".csv";
		                pdfReport += ".pdf";
		            	Document document = new Document(new PdfDocument(new PdfWriter(pdfReport)));
		                CSVWriter csvdoc = new CSVWriter(new FileWriter(csvReport), ',', '"', '\\', "\n");
		                Table table = null;
		                boolean isNew = true;
		                for (ImmutablePair<Method, Object[]> p : reportLst) {
							switch (p.getLeft().getName()) {
								case "start":
									p.getRight()[p.getRight().length-3] = isNew;
									p.getRight()[p.getRight().length-2] = document;
									p.getRight()[p.getRight().length-1] = csvdoc;
									table = (Table)p.getLeft().invoke(null, p.getRight());
									isNew = false;
									break;
								case "addSubTest":
									p.getRight()[p.getRight().length-3] = table;
									p.getRight()[p.getRight().length-2] = document;
									p.getRight()[p.getRight().length-1] = csvdoc;
									p.getLeft().invoke(null, p.getRight());
									break;
								case "addErrorDetails":
									if(table!=null) {
										table.setMarginBottom(30.0f);
						                document.add(table);
						                table = null;
									}
									p.getRight()[p.getRight().length-1] = document;
									p.getLeft().invoke(null, p.getRight());
									break;
								default:
									break;
							}
						}
		                document.close();
		                csvdoc.close();
            		} catch (Exception e) {
                        e.printStackTrace();
                    }
            	}
            	
                ReportHandler.doSeleniumSummaryTestReport(sessionId, configuration.getRetryCounter(), path, summLstMap, sinfoMap, context, runNum, runPrefix);
                indexes.get("local").put(runNum, runPrefix + "-" + runNum + "-selenium-index.html");
            }

            if(concExec) {
            	context.getWorkflowContextHandler().initializeSuiteContext(tests.size());
            } else {
            	context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
            }

            if (done) {
                break;
            }
        }

        if (numberOfRuns > 1) {
            threadPool.shutdown();
        }

        if (distTasks != null && distTasks.size() > 0) {
            for (int j = 0; j < distTasks.size(); j++) {
                FutureTask<Object> futureTask = distTasks.get(j);
                try {
                    DistributedTestStatus test = (DistributedTestStatus)futureTask.get();
                    if(test!=null) {
                        indexes.putAll(test.getIndexes());
                    } else {
                        logger.info("A distributed node returned no result; its tests are omitted from the report");
                    }
                } catch (Exception e) {
                    logger.info("A distributed node failed; its results are omitted from the report: " + e.getMessage());
                }
            }
        }
        
        // M6: globalTeardown after the last round (a retry round follows when scripts failed and retries are left)
        RuntimeException teardownFailure = null;
        if (!(configuration.getSeleniumScriptRetryCount() > 0 && failedFiles.size() > 0)) {
            teardownFailure = runGlobalTeardown(configuration);
        }

        ReportHandler.doSeleniumFinalTestReport(sessionId, configuration.getRetryCounter(), path, indexes, context, configuration, failedFiles.size());

        shutdown();
        
        if(configuration.getSeleniumScriptRetryCount()>0 && failedFiles.size()>0) {
        	com.gatf.log.RunConsole.retrying(failedFiles.size(), configuration.getRetryCounter());
        	configuration.setSeleniumScriptRetryCount(configuration.getSeleniumScriptRetryCount() - 1);
        	configuration.setSeleniumScripts(failedFiles.toArray(new String[failedFiles.size()]));
        	configuration.setSeleniumScript(null);
        	configuration.setRetryCounter(configuration.getRetryCounter() + 1);
        	doSeleniumTest(configuration, null);
        } else {
        	ReportHandler.writeSessInfo(sessionId, context);
        }
        
        if(!isLoadTestingEnabled && allErrors.size()>0) {
        	throw new GatfRunTimeErrors(allErrors);
        }
        if (teardownFailure != null) {
        	throw teardownFailure;
        }
    }

    /** M6: runs globalTeardown once per run; returns its failure (the caller decides whether to throw) or null. */
    private RuntimeException runGlobalTeardown(GatfExecutorConfig configuration) {
        if (globalTeardownDone || setupArgs == null || StringUtils.isBlank(configuration.getGlobalTeardown())) return null;
        globalTeardownDone = true;
        try {
            runSetupScript(configuration.getGlobalTeardown().trim(), "globalTeardown", false, (Long) setupArgs[0], (String) setupArgs[1],
                    (AcceptanceTestContext) setupArgs[2], configuration, (LoggingPreferences) setupArgs[3]);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    static class ConcSeleniumTest implements Callable<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>> {
        private int index;
        private int findx;
        private AcceptanceTestContext context;
        private List<SeleniumTest> tests = new ArrayList<>();
        private List<Object[]> testdata = new ArrayList<>();
        private List<String> failedFiles = new ArrayList<>();
        private LoggingPreferences lp;
        private boolean dorep;
        private boolean isLoadTestingEnabled;
        private int runNum;
        private String runPrefix;
        private String node;
        private String path;
        private Long sessionId;

        public ConcSeleniumTest(Long sessionId, String path, int index, int findx, AcceptanceTestContext context, List<SeleniumTest> tests, List<Object[]> testdata, LoggingPreferences lp, boolean dorep, boolean isLoadTestingEnabled, int runNum, String runPrefix,
                String node, List<String> failedFiles) {
            this.findx = findx;
            this.index = index;
            this.context = context;
            this.tests = tests;
            this.testdata = testdata;
            this.lp = lp;
            this.runNum = runNum;
            this.runPrefix = runPrefix;
            this.node = node;
            this.dorep = dorep;
            this.isLoadTestingEnabled = isLoadTestingEnabled;
            this.failedFiles = failedFiles;
            this.path = path;
            this.sessionId = sessionId;
        }

        @SuppressWarnings("unused")
        @Override
        public ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> call() throws Exception {
            Map<String, Map<String, List<Object[]>>> summLst = new LinkedHashMap<String, Map<String, List<Object[]>>>();
            List<ImmutablePair<Method, Object[]>> reportList = new ArrayList<>();
            List<GatfRunTimeError> allErrors = new ArrayList<>();
            TestSuiteStats stats = new TestSuiteStats();
            int tot = 0, fal = 0, succ = 0, skp = 0;
            Map<String, Map<String, Integer[]>> tstSummary = new HashMap<>();
            tstSummary.put((index+1)+"", new HashMap<>());
            Date time = new Date();
            //Document document = new Document(new PdfDocument(new PdfWriter(pdfReport)));
            //CSVWriter csvdoc = new CSVWriter(new FileWriter(csvReport), ',', '"', '\\', "\n");
            for (int i = 0; i < tests.size(); i++) {
            	TestSuiteStats tstats = new TestSuiteStats();
                int ttot = 0, tfal = 0, tsucc = 0, tskp = 0;
                SeleniumTest dyn = tests.get(i).copy(context, index);
                Object[] retvals = testdata.get(i);
                
                int findex = findx!=-1?findx:i;
                
                long start = System.currentTimeMillis();
                List<SeleniumTestSession> sessions = null;
                try {
                	System.out.println("[" +Thread.currentThread().getName()+"]=======================Started execution gatf test case " + dyn.getName() + "=======================");
                    summLst.put((String) retvals[0], new LinkedHashMap<String, List<Object[]>>());
                    sessions = dyn.execute(lp, path);
                } catch (FailureException e) {
                    sessions = dyn.get__sessions__();
                } catch (Throwable e) {
                    dyn.pushResult(new SeleniumTestResult(dyn, e));
                    sessions = dyn.get__sessions__();
                } finally {
                	System.out.println("[" +Thread.currentThread().getName()+"]=======================Completed execution gatf test case " + dyn.getName() + "=======================");
                }
                dyn.quitAll();
                
                List<ImmutableTriple<SeleniumTestResult, String, String>> failDetails = new ArrayList<>();
                //Table table = TestFileReporter.start((String)retvals[0] + " [Time - " + (System.currentTimeMillis()-start)/1000 + " sec]", i==0, document, csvdoc);
                if (dorep && !isLoadTestingEnabled) {
                	reportList.add(TestFileReporter.startO((String)retvals[0] + " [Time - " + (System.currentTimeMillis()-start)/1000 + " sec]", null, null, findex==0));
                }

                int counter = 1;
                for (int u = 0; u < sessions.size(); u++) {
                    SeleniumTestSession sess = sessions.get(u);
                    for (Map.Entry<String, SeleniumResult> e : sess.getResult().entrySet()) {
                    	String sessname = sess.getSessionName() != null ? ("-" + sess.getSessionName()) : StringUtils.EMPTY;
                        String keykey = e.getKey() + sessname;
                        if (!summLst.get((String) retvals[0]).containsKey(keykey)) {
                            summLst.get((String) retvals[0]).put(keykey, new ArrayList<Object[]>());
                        }

                        SeleniumResult res = e.getValue();
                        if (res.getResult() == null) {
                            consoleSelResult((String) retvals[0], keykey, null, null);
                            summLst.get((String) retvals[0]).get(keykey).add(new Object[] {"-", "#", "UNKNOWN", StringUtils.EMPTY, "0s"});
                            skp++;
                            tskp++;
                        } else {
                            String tim = res.getResult().getExecutionTime() / Math.pow(10, 9) + StringUtils.EMPTY;
                            stats.setExecutionTime(stats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                            tstats.setExecutionTime(tstats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                            if (tim.indexOf(".") != -1) {
                                String[] parts = tim.split("\\.");
                                tim = parts[0] + "." + (parts[1].length() > 3 ? parts[1].substring(0, 3) : parts[1]);
                            }
                            tim += "s";
                            // Q9/Q10: budgets + passive security checks over the session's (stopped) trace, before the
                            // result is summarised and before the keep/discard decision (a breach keeps the trace)
                            if (dorep && sess.__trace__ != null) {
                                try {
                                    com.gatf.selenium.checks.TraceChecks.Report __chk = com.gatf.selenium.checks.TraceChecks.run(
                                            sess.__trace__.workDir(), context.getGatfExecutorConfig().getPerfBudgets(),
                                            java.util.Arrays.asList(String.valueOf(retvals[1]).split("\n", 81)),
                                            context.getGatfExecutorConfig().getSecurityChecks());
                                    if (__chk != null) {
                                        res.getResult().setChecks(__chk.toMap());
                                        if (__chk.failure() != null) res.getResult().failWith(__chk.failure());
                                        org.apache.logging.log4j.LogManager.getLogger(GatfTestCaseExecutorUtil.class)
                                                .info("{} [{}]: {}", retvals[0], keykey, __chk.summary());
                                    }
                                } catch (Exception __ce) {
                                    org.apache.logging.log4j.LogManager.getLogger(GatfTestCaseExecutorUtil.class)
                                            .warn("budget/security checks failed for {}: {}", retvals[0], __ce.toString());
                                }
                            } else if (dorep && res.getResult() != null && !checksWarned
                                    && context.getGatfExecutorConfig().getPerfBudgets() != null) {
                                checksWarned = true;
                                org.apache.logging.log4j.LogManager.getLogger(GatfTestCaseExecutorUtil.class)
                                        .warn("perfBudgets are set but no trace was captured for {} - budgets need traceMode on or retain-on-failure (Chromium or BiDi browsers)", retvals[0]);
                            }
                            String msg = "";
                            if(res.getResult().getLogs()!=null && res.getResult().getLogs().get("gatf")!=null && res.getResult().getLogs().get("gatf").getAll().size()>0) {
                            	msg = res.getResult().getLogs().get("gatf").getAll().get(0).getMessage();
                            }
                            String fileName = runPrefix + "-" + (index + 1) + "-" + (runNum) + "-" + (findex + 1) + "-" + keykey.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                            summLst.get((String) retvals[0]).get(keykey).add(new Object[] {"-", fileName + ".html", res.getResult().isStatus() ? "SUCCESS" : "FAILED",
                                    !res.getResult().isStatus() ? msg : StringUtils.EMPTY, tim});
                            // Q3: keep or drop the session's trace per traceMode (retain-on-failure drops passing scripts').
                            // "Failed" = the script or any of its subtests failed. One trace bundle per session.
                            String __traceRel = null, __traceAbs = null;
                            if (dorep && sess.__trace__ != null) {
                                boolean __failed = !res.getResult().isStatus();
                                for (SeleniumTestResult __st : res.getSubTestResults().values()) {
                                    if (__st != null && !__st.isStatus()) __failed = true;
                                }
                                if (context.getGatfExecutorConfig().keepTrace(__failed)) {
                                    java.nio.file.Path __tzp = sess.__trace__.writeZip(new File(context.getOutDir().getAbsolutePath() + File.separator + path, fileName + "-trace.zip").toPath());
                                    if(__tzp!=null) {
                                        __traceRel = fileName + "-trace.zip";
                                        __traceAbs = __tzp.toAbsolutePath().toString();
                                    }
                                } else {
                                    sess.__trace__.discard();
                                }
                                sess.__trace__ = null;
                            }
                            consoleSelResult((String) retvals[0], keykey, null, res.getResult(), __traceAbs);
                            if (dorep) {
                                ReportHandler.doSeleniumTestReport(path, fileName, retvals, res.getResult(), context, __traceRel);
                                if(!isLoadTestingEnabled) {
	                                String prefix = runPrefix + "-" + (index + 1) + "-" + (runNum) + "-" + (findex + 1) + "-" + counter;
	                                reportList.add(TestFileReporter.addSubTestO(counter++, node, runNum, keykey, "-", tim, res.getResult().isStatus(), msg, fileName + ".html", prefix, null, null, null));
	                                //TestFileReporter.addSubTest(counter++, node, runNum, keykey, "-", tim, res.getResult().isStatus(), msg, fileName + ".html", prefix, table, document, csvdoc);
	                            	fileName = context.getOutDir().getAbsolutePath() + File.separator + path + File.separator + fileName + ".html";
	                            	failDetails.add(new ImmutableTriple<SeleniumTestResult, String, String>(res.getResult(), fileName, prefix));
                                }
                            }
                            if(res.getResult().isStatus()) {
                                succ++;
                                tsucc++;
                            } else {
                                fal++;
                                tfal++;
                                if(res.getResult().getCause()!=null && res.getResult().getCause() instanceof GatfRunTimeError) {
                                	allErrors.add((GatfRunTimeError)res.getResult().getCause());
                                }
                            }
                        }
                        
                        tot++;
                        ttot++;

                        for (Map.Entry<String, SeleniumTestResult> e1 : res.getSubTestResults().entrySet()) {
                            if (e1.getValue() == null || e1.getValue().getSkipReason() != null) {
                                // not run: never reached, or M6 skip / fixme / not-only (reason kept in the report text)
                                String skipReason = e1.getValue() == null ? null : e1.getValue().getSkipReason();
                                String skipMsg = skipReason == null ? "Skipped" : SeleniumTestResult.skipMessage(skipReason);
                                consoleSelResult((String) retvals[0], keykey, e1.getKey(), null, null, skipReason == null ? null : skipMsg);
                                summLst.get((String) retvals[0]).get(keykey).add(new Object[] {e1.getKey(), "#", "UNKNOWN", skipReason == null ? StringUtils.EMPTY : skipMsg, "0s"});
                                skp++;
                                tskp++;
                                String prefix = runPrefix + "-" + (index + 1) + "-" + (runNum) + "-" + (findex + 1) + "-" + counter;
                                if (dorep && !isLoadTestingEnabled) {
                                	reportList.add(TestFileReporter.addSubTestO(counter++, node, runNum, keykey, e1.getKey(), "0s", skipReason != null || res.getResult().isStatus(), skipMsg, null, prefix, null, null, null));
                                }
                                //TestFileReporter.addSubTest(counter++, node, runNum, keykey, e1.getKey(), "0s", res.getResult().isStatus(), "Skipped", null, prefix, table, document, csvdoc);
                            } else {
                                String tim = e1.getValue().getExecutionTime() / Math.pow(10, 9) + StringUtils.EMPTY;
                                stats.setExecutionTime(stats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                                tstats.setExecutionTime(tstats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                                if (tim.indexOf(".") != -1) {
                                    String[] parts = tim.split("\\.");
                                    tim = parts[0] + "." + (parts[1].length() > 3 ? parts[1].substring(0, 3) : parts[1]);
                                }
                                tim += "s";
                                String msg = "";
                                if(e1.getValue().getLogs()!=null && e1.getValue().getLogs().get("gatf")!=null && e1.getValue().getLogs().get("gatf").getAll().size()>0) {
                                	msg = e1.getValue().getLogs().get("gatf").getAll().get(0).getMessage();
                                }
                                String fileName = runPrefix + "-" + (index + 1) + "-" + (runNum) + "-" + (findex + 1) + "-" + keykey.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "-"
                                        + e1.getKey().replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                                summLst.get((String) retvals[0]).get(keykey).add(new Object[] {e1.getKey(), fileName + ".html", e1.getValue().isStatus() ? "SUCCESS" : "FAILED",
                                        !e1.getValue().isStatus() ? msg : StringUtils.EMPTY, tim});
                                consoleSelResult((String) retvals[0], keykey, e1.getKey(), e1.getValue());
                                if (dorep) {
                                    ReportHandler.doSeleniumTestReport(path, fileName, retvals, e1.getValue(), context, null);
                                    if(!isLoadTestingEnabled) {
	                                    String prefix = runPrefix + "-" + (index + 1) + "-" + (runNum) + "-" + (findex + 1) + "-" + counter;
	                                    reportList.add(TestFileReporter.addSubTestO(counter++, node, runNum, keykey, e1.getValue().getSubtestName(), tim, e1.getValue().isStatus(), msg, fileName + ".html", prefix, null, null, null));
	                                    //TestFileReporter.addSubTest(counter++, node, runNum, keykey, e1.getValue().getSubtestName(), tim, e1.getValue().isStatus(), msg, fileName + ".html", prefix, table, document, csvdoc);
	                                	fileName = context.getOutDir().getAbsolutePath() + File.separator + path + File.separator + fileName + ".html";
	                                	failDetails.add(new ImmutableTriple<SeleniumTestResult, String, String>(e1.getValue(), fileName, prefix));
                                    }
                                }
                                
                                if(e1.getValue().isStatus()) {
                                    succ++;
                                    tsucc++;
                                } else {
                                    fal++;
                                    tfal++;
                                    if(e1.getValue().getCause()!=null && e1.getValue().getCause() instanceof GatfRunTimeError) {
                                    	allErrors.add((GatfRunTimeError)e1.getValue().getCause());
                                    }
                                }
                            }
                            
                            tot++;
                            ttot++;
                        }
                    }
                }
                // Q3: a session whose trace was never written (reporting off, or no result) must not leak its working dir
                for (SeleniumTestSession __s : sessions) {
                    if (__s.__trace__ != null) {
                        __s.__trace__.discard();
                        __s.__trace__ = null;
                    }
                }
                
                //table.setMarginBottom(30.0f);
                //document.add(table);
                
                if(failDetails.size()>0) {
                	for (ImmutableTriple<SeleniumTestResult, String, String> rep : failDetails) {
                		//estFileReporter.addErrorDetails(rep, document);
                		reportList.add(TestFileReporter.addErrorDetailsO(rep, null));
					}
                }
                
                tstSummary.get((index+1)+"").put(tests.get(i).getName().replace(context.getTestCasesDir().getAbsolutePath(), ""), new Integer[] {tfal, tskp, ttot});
                
                if(node.equals("local")) {
                    tstats.setTotalTestCount(ttot);
                    tstats.setFailedTestCount(tfal);
                    tstats.setSkippedTestCount(tskp);
                    
                    tstats.setTotalRuns(1);
                    tstats.setFailedRuns(1);
                    tstats.setFailedRuns(fal>0?1:0);
                    tstats.setTotalSuiteRuns(1);
                    RuntimeReportUtil.addEntry(node, runPrefix, runNum, runPrefix + "-" + runNum + "-selenium-index.html", tstats, time, dyn.getName());
                    if(!isLoadTestingEnabled && tstats.getFailedTestCount()>0) {
                    	failedFiles.add(dyn.getName());
                    }
                }
            }
            
            stats.setTotalTestCount(tot);
            stats.setFailedTestCount(fal);
            stats.setSkippedTestCount(skp);
            
            ReportHandler.addPathFailures(sessionId, path, "sel", fal, tstSummary);
            
            stats.setTotalRuns(1);
            stats.setFailedRuns(1);
            stats.setFailedRuns(fal>0?1:0);
            stats.setTotalSuiteRuns(1);
            
            if(!node.equals("local")) {
                RuntimeReportUtil.addEntry(node, runPrefix, runNum, runPrefix + "-" + runNum + "-selenium-index.html", stats, time, null);
            } else {
                LoadTestEntry lentry = new LoadTestEntry(node, runPrefix, runNum, runPrefix + "-" + runNum + "-selenium-index.html", stats, time);
                RuntimeReportUtil.addLEntry(lentry);
            }
            
            //document.close();
            //csvdoc.close();
            
            return new ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>(summLst, allErrors, new ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>(reportList, tstSummary));
        }
    }

    public void doExecute(GatfPluginConfig configuration, List<String> files) throws Exception {
        doExecute((GatfExecutorConfig) configuration, files);
    }

    @SuppressWarnings({"rawtypes"})
    public void doExecute(GatfExecutorConfig configuration, List<String> files) throws Exception {

        // CLI reporter (no-op unless GatfCli activated it): per-test lines only for functional single-user runs
        com.gatf.log.RunConsole.suite(configuration.isSeleniumExecutor() ? "UI (seleasy)" : "API",
                new File(configuration.getOutFilesBasePath() != null ? configuration.getOutFilesBasePath() : ".",
                        configuration.getOutFilesDir() != null ? configuration.getOutFilesDir() : "out").getAbsolutePath(),
                configuration.getNumConcurrentExecutions() != null ? configuration.getNumConcurrentExecutions() : 1,
                !(configuration.isLoadTestingEnabled() && configuration.getLoadTestingTime() != null && configuration.getLoadTestingTime() > 10000)
                        && (configuration.getConcurrentUserSimulationNum() == null || configuration.getConcurrentUserSimulationNum() <= 1));

        if (configuration.isGenerateExecutionLogs()) {
            tclgenerator = new Thread(new TestCaseExecutionLogGenerator(configuration));
            tclgenerator.start();
        }

        List<DistributedConnection> distConnections = null;

        if (configuration.isSeleniumExecutor()) {
            doSeleniumTest(configuration, files);
            return;
        }
        
        initilaizeContext(configuration, true);
        
        String path = initOutFolderAndGetPath(context, System.nanoTime(), 0, "api");

        distributedGatfTester = new DistributedGatfTester();

        final TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(context);

        boolean compareEnabledOnlySingleTestCaseExec = configuration.isCompareEnabled() && configuration.getGatfTestDataConfig() != null
                && configuration.getGatfTestDataConfig().getCompareEnvBaseUrls() != null && !configuration.getGatfTestDataConfig().getCompareEnvBaseUrls().isEmpty();

        Set<String> baseUrls = null;
        List<String> baseUrlList = null;
        if (compareEnabledOnlySingleTestCaseExec) {
            baseUrls = new LinkedHashSet<String>();
            baseUrls.add(configuration.getBaseUrl());
            baseUrls.addAll(configuration.getGatfTestDataConfig().getCompareEnvBaseUrls());

            if (baseUrls.size() == 1) {
                baseUrls = null;
                compareEnabledOnlySingleTestCaseExec = false;
            } else {
                configuration.setCompareBaseUrlsNum(baseUrls.size());
                baseUrlList = new ArrayList<String>(baseUrls);
            }
        }

        Set<String> relativeFileNames = new HashSet<String>();
        List<TestCase> allTestCases = getAllTestCases(context, relativeFileNames, files);
        if (TestFilter.isActive()) {
            // Q5: `gatf test --grep/--tag` selection; relatedTestName dependencies of selected tests are kept
            allTestCases = TestFilter.active().select(allTestCases,
                    tc -> TestFilter.apiTitle(tc.getSourcefileName(), tc.getName(), tc.getDescription()),
                    TestCase::getName, TestCase::getRelatedTestName);
            if (allTestCases.isEmpty()) {
                throw new RuntimeException("No API tests match " + TestFilter.active().describe());
            }
            Set<String> keptFiles = new HashSet<String>();
            for (TestCase tc : allTestCases) keptFiles.add(tc.getSourcefileName());
            relativeFileNames.retainAll(keptFiles);   // no empty groups in the report
        }
        if (RunOverrides.shard() != null && !allTestCases.isEmpty()) {
            // M7: `gatf test --shard i/n` splits by test file; relatedTestName prerequisites in other files come along
            ShardSplitter shard = RunOverrides.shard();
            allTestCases = shard.selectWithDependencies(allTestCases, TestCase::getSourcefileName, TestCase::getName, TestCase::getRelatedTestName);
            if (allTestCases.isEmpty()) {
                throw new ShardSplitter.EmptyShardException("No API test files in " + shard.describe());
            }
            Set<String> keptFiles = new HashSet<String>();
            for (TestCase tc : allTestCases) keptFiles.add(tc.getSourcefileName());
            relativeFileNames.retainAll(keptFiles);
        }

        List<TestCase> tempTestCases = new ArrayList<TestCase>(allTestCases);
        Map<String, Set<String>> relTsts = new HashMap<String, Set<String>>();

        for (TestCase testCase : allTestCases) {
            List<TestCase> depTests = null;
            if (testCase.getRelatedTestName() != null && (depTests = checkIfTestExists(testCase.getRelatedTestName(), allTestCases)) != null) {
                if (!relTsts.containsKey(testCase.getRelatedTestName())) {
                    relTsts.put(testCase.getRelatedTestName(), new HashSet<String>());
                }
                if (!context.getRelatedTestCases().containsKey(testCase.getRelatedTestName())) {
                    context.getRelatedTestCases().put(testCase.getRelatedTestName(), new ArrayList<TestCase>());
                }
                for (TestCase dtc : depTests) {
                    if (!relTsts.get(testCase.getRelatedTestName()).contains(dtc.getName())) {
                        relTsts.get(testCase.getRelatedTestName()).add(dtc.getName());
                        tempTestCases.remove(dtc);
                        context.getRelatedTestCases().get(testCase.getRelatedTestName()).add(dtc);
                    }
                }
            }
        }
        allTestCases = tempTestCases;

        int numberOfRuns = 1;
        if (compareEnabledOnlySingleTestCaseExec) {
            numberOfRuns = baseUrls.size();
        } else if (configuration.getConcurrentUserSimulationNum() != null && configuration.getConcurrentUserSimulationNum() > 1) {
            numberOfRuns = configuration.getConcurrentUserSimulationNum();
        }

        // If load testing time is greater than 10sec only then run load tests..
        boolean isLoadTestingEnabled = !compareEnabledOnlySingleTestCaseExec && configuration.isLoadTestingEnabled() && configuration.getLoadTestingTime() > 10000;


        TestSuiteStats loadStats = new TestSuiteStats();


        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        // G8: sample clamp + interval + per-iteration decision + counters shared with the other run loop
        LoadRunSchedule schedule = new LoadRunSchedule(isLoadTestingEnabled, configuration);

        validateTestCases(allTestCases, testCaseExecutorUtil);

        List<FutureTask<DistributedTestStatus>> distTasks = null;
        if (isLoadTestingEnabled) {

            if (configuration.isDistributedLoadTests() && configuration.getDistributedNodes() != null && configuration.getDistributedNodes().length > 0 && numberOfRuns > 1) {
                distConnections = new ArrayList<DistributedConnection>();

                for (String node : configuration.getDistributedNodes()) {
                    DistributedConnection conn = distributedGatfTester.distributeContext(node, context, null);
                    if (conn != null) {
                        distConnections.add(conn);
                    }
                }

                if (distConnections.size() > 0) {
                    distTasks = new ArrayList<FutureTask<DistributedTestStatus>>();

                    int dnumruns = 0, dtotnds = 0;
                    if (numberOfRuns < distConnections.size()) {
                        dtotnds = numberOfRuns - 1;
                        dnumruns = 1;
                    } else {
                        dtotnds = distConnections.size();
                        dnumruns = numberOfRuns / (distConnections.size() + 1);
                    }

                    for (int i = 0; i < dtotnds; i++) {
                        DistributedConnection conn = distConnections.get(i);
                        if (conn != null) {
                            FutureTask<DistributedTestStatus> task = distributedGatfTester.distributeTests(allTestCases, conn, true, i + 1, dnumruns, context, relativeFileNames);
                            if (task != null) {
                                distTasks.add(task);
                            }
                        }
                    }

                    if (distConnections.size() == distTasks.size()) {
                        if (numberOfRuns < distTasks.size()) {
                            numberOfRuns = 1;
                        } else {
                            numberOfRuns = dnumruns + (numberOfRuns % (distConnections.size() + 1));
                        }
                    } else {
                        if (numberOfRuns < distTasks.size()) {
                            numberOfRuns = numberOfRuns - distTasks.size();
                        } else {
                            numberOfRuns = dnumruns + (numberOfRuns % (distTasks.size() + 1));
                        }
                    }
                }
            }
        }

        context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
        
        if (distTasks != null && distTasks.size() > 0) {
            if (numberOfRuns == 1) {
                for (TestCase tc : allTestCases) {
                    tc.setSimulationNumber(1);
                }
            }
        }

        ExecutorService threadPool = null;
        if (numberOfRuns > 1) {
            // Virtual-thread-per-task executor: run every configured simulated user
            // concurrently instead of capping the load at 100 platform threads. Each
            // user-simulation thread only parks on a CompletableFuture (the HTTP call is
            // dispatched asynchronously via java.net.http's HttpClient.sendAsync), so the vthreads park
            // cleanly with no carrier pinning and cost ~nothing while idle.
            threadPool = Executors.newVirtualThreadPerTaskExecutor();
        }

        ExecutorService reportingThreadPool = Executors.newFixedThreadPool(Integer.getInteger("gatf.reportingThreads", 30));

        startTime = System.currentTimeMillis();

        List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();

        TestExecutionPercentile testPercentiles = new TestExecutionPercentile();

        TestExecutionPercentile runPercentiles = new TestExecutionPercentile();

        if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;

        try {
            while (allTestCases.size() > 0) {
                long suiteStartTime = isLoadTestingEnabled ? System.currentTimeMillis() : startTime;

                boolean dorep = false;

                boolean done = false;

                ReportHandler reportHandler = new ReportHandler();

                Integer runNums = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
                if (context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                    runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
                }

                for (String relativeFileName : relativeFileNames) {
                    reportHandler.initializeResultsHolders(runNums, relativeFileName);
                }

                if (schedule.next(isLoadTestingEnabled, context.getGatfExecutorConfig(), configuration, startTime)) {
                    break;
                }
                dorep = schedule.dorep;
                done = schedule.done;

                if (numberOfRuns > 1) {
                    Date execTime = new Date();
                    List<Future> userSimulations = doConcurrentRunExecution(compareEnabledOnlySingleTestCaseExec, numberOfRuns, allTestCases, baseUrlList, testCaseExecutorUtil, concurrentUserRampUpTimeMs,
                            threadPool, dorep, reportHandler);

                    concurrentUserRampUpTimeMs = 0;

                    long currentTime = System.currentTimeMillis();
                    String fileurl = isLoadTestingEnabled ? currentTime + ".html" : "index.html";
                    for (Future future : userSimulations) {
                        try {
                            future.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    doAsyncConcReporting(path, compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, schedule.loadTestRunNum(),
                            loadTestResources, numberOfRuns, loadStats, reportingThreadPool, userSimulations.size(), (System.currentTimeMillis() - suiteStartTime), execTime);

                    if (dorep && isLoadTestingEnabled) {
                        schedule.advance();
                    }
                } else {
                    Date execTime = new Date();
                    long currentTime = System.currentTimeMillis();
                    String fileurl = isLoadTestingEnabled ? currentTime + ".html" : "index.html";
                    executeTestCases(allTestCases, testCaseExecutorUtil, compareEnabledOnlySingleTestCaseExec, dorep, !isLoadTestingEnabled && !compareEnabledOnlySingleTestCaseExec, reportHandler);

                    doAsyncReporting(path, compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, schedule.loadTestRunNum(),
                            loadTestResources, numberOfRuns, loadStats, reportingThreadPool, (System.currentTimeMillis() - suiteStartTime), execTime);

                    if (dorep && isLoadTestingEnabled) {
                        schedule.advance();
                    }
                }

                context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

                if (done) {
                    break;
                }
            }
        } finally {
            reportingThreadPool.shutdown();
            try {
                reportingThreadPool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (numberOfRuns > 1) {
                threadPool.shutdown();
            }
        }

        loadStats.setTotalUserSuiteRuns(numberOfRuns);

        if (isLoadTestingEnabled) {
            String runPrefix = null;
            if (distTasks != null && distTasks.size() > 0) {
                runPrefix = "LRun-";
            }

            loadStats.setExecutionTime(System.currentTimeMillis() - startTime);
            ReportHandler.doFinalLoadTestReport(path, runPrefix, loadStats, context, null, null, loadTestResources);
        }

        logger.info(loadStats.show());

        if (distTasks != null && distTasks.size() > 0) {
            TestSuiteStats finalDistStats = loadStats;
            List<String> nodes = new ArrayList<String>();
            List<String> nodesurls = new ArrayList<String>();

            nodes.add("local-node");
            nodesurls.add("LRun-index.html");

            for (FutureTask<DistributedTestStatus> futureTask : distTasks) {
                try {
                    DistributedTestStatus stats = futureTask.get();
                    if (stats != null && stats.getSuiteStats() != null) {
                        logger.info(stats.getSuiteStats().show());
                        nodes.add(stats.getNode());
                        nodesurls.add(stats.getIdentifier() + "-index.html");
                        finalDistStats.updateStats(stats.getSuiteStats(), false);

                        testPercentiles.mergePercentileTimes(stats.getTestPercentileTimes());
                        runPercentiles.mergePercentileTimes(stats.getRunPercentileTimes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ReportHandler.doFinalLoadTestReport(path, null, finalDistStats, context, nodes, nodesurls, loadTestResources);
            loadStats = finalDistStats;
        }

        ReportHandler.doTAReporting(path, null, context, isLoadTestingEnabled, testPercentiles, runPercentiles);

        if (loadStats != null) {
            logger.info(loadStats.show());
            if (loadStats.getFailedTestCount() > 0) {
                throw new RuntimeException(loadStats.getFailedTestCount() + " testcases have failed");
            }
        }

        testCaseExecutorUtil.shutdown();
    }

    private void doAsyncConcReporting(String path, final boolean compareEnabledOnlySingleTestCaseExec, final ReportHandler reportHandler, final long suiteStartTime, final String fileurl,
            final boolean isLoadTestingEnabled, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum,
            final List<LoadTestResource> loadTestResources, final int numberOfRuns, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final int concrunNos,
            final long suiteExecTime, final Date time) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                for (int y = 0; y < concrunNos; y++) {
                    try {
                        if (dorep && !compareEnabledOnlySingleTestCaseExec) {
                            String afileurl = fileurl;
                            if (!isLoadTestingEnabled) {
                                afileurl = null;
                            }
                            reportHandler.doConcurrentRunReporting(path, context, suiteStartTime, afileurl, (y + 1), loadTestRunNum == 1, testPercentiles, runPercentiles, null);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                doAsyncReporting(path, compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, loadTestRunNum,
                        loadTestResources, numberOfRuns, loadStats, null, suiteExecTime, time);
            }
        });
    }

    private void doAsyncReporting(String path, final boolean compareEnabledOnlySingleTestCaseExec, final ReportHandler reportHandler, final long suiteStartTime, final String fileurl,
            final boolean isLoadTestingEnabled, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum,
            final List<LoadTestResource> loadTestResources, final int numberOfRuns, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final long suiteExecTime, Date time) {
        Runnable runnable = new Runnable() {
            public void run() {
                if (compareEnabledOnlySingleTestCaseExec) {
                    TestSuiteStats stats = reportHandler.doReporting(path, context, suiteStartTime, fileurl, null, isLoadTestingEnabled, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        loadStats.copy(stats);
                        RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats, time, null);
                    }
                } else if (dorep) {
                    TestSuiteStats stats = null;
                    if (numberOfRuns > 1) {
                        stats = reportHandler.doReportingIndex(path, context, suiteStartTime, fileurl, numberOfRuns, null, isLoadTestingEnabled);
                    } else {
                        stats = reportHandler.doReporting(path, context, suiteStartTime, fileurl, null, isLoadTestingEnabled, testPercentiles, runPercentiles);
                    }
                    stats.setExecutionTime(suiteExecTime);
                    if (isLoadTestingEnabled) {
                        reportHandler.addToLoadTestResources(null, loadTestRunNum, fileurl, loadTestResources);
                        synchronized (loadStats) {
                            if (loadStats.getExecutionTime() == 0) {
                                loadStats.copy(stats);
                                loadStats.setTotalUserSuiteRuns(numberOfRuns);
                            } else {
                                stats.setGroupStats(null);
                                loadStats.updateStats(stats, false);
                            }
                            RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats, time, null);
                        }
                        reportHandler.clearForLoadTests(context);
                    } else {
                        synchronized (loadStats) {
                            loadStats.copy(stats);
                            loadStats.setTotalUserSuiteRuns(numberOfRuns);
                            RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats, time, null);
                        }
                    }
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(path, context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addEntry(null, null, loadTestRunNum, null, stats, time, null);
                    }
                }
            }
        };
        if (reportingThreadPool != null)
            reportingThreadPool.execute(runnable);
        else
            runnable.run();
    }

    public void initilaizeContext(GatfExecutorConfig configuration, boolean flag) throws Exception {
        if(context!=null) {
            context.projectClassLoader = fcl.apply(null);
        } else {
            context = new AcceptanceTestContext(configuration, fcl.apply(null));
        }
        try {
            context.validateAndInit(flag);
            if (flag) {
                setupTestCaseHooks(context);
            }
        } catch (Throwable e) {
            logger.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Configuration is invalid", e);
        }
    }

    private List<TestCase> checkIfTestExists(String relatedTestCase, List<TestCase> allTestCases) {
        List<TestCase> depTests = new ArrayList<TestCase>();
        for (TestCase tc : allTestCases) {
            if (relatedTestCase != null && tc.getName().equals(relatedTestCase)) {
                depTests.add(tc);
            }
        }
        return depTests;
    }

    private void validateTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil) {
        for (TestCase testCase : allTestCases) {
            try {
                testCase.validate(testCaseExecutorUtil.getContext().getHttpHeaders(), testCaseExecutorUtil.getContext().getGatfExecutorConfig().getBaseUrl());
                if (testCase.getRepeatScenarios() != null) {
                    testCase.setRepeatScenariosOrig(testCase.getRepeatScenarios());
                }
            } catch (RuntimeException e) {
                logger.error("Got exception while running acceptance test " + testCase.getName() + "/" + testCase.getDescription() + "\n" + ExceptionUtils.getStackTrace(e));
                throw e;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    List<Future> doConcurrentRunExecution(boolean compareEnabledOnlySingleTestCaseExec, Integer numberOfRuns, List<TestCase> allTestCases, List<String> baseUrlList,
            final TestCaseExecutorUtil testCaseExecutorUtil, Long concurrentUserRampUpTimeMs, ExecutorService threadPool, boolean dorep, final ReportHandler reportHandler) {
        final boolean onlySingleTestCaseExecl = compareEnabledOnlySingleTestCaseExec;

        List<Future> userSimulations = new ArrayList<Future>();
        for (int i = 0; i < numberOfRuns; i++) {
            List<TestCase> simTestCases = null;
            if (onlySingleTestCaseExecl) {
                simTestCases = copyTestCases(allTestCases, i + 1, baseUrlList.get(i));
            } else {
                simTestCases = copyTestCases(allTestCases, i + 1, null);
            }

            final List<TestCase> simTestCasesCopy = simTestCases;
            final boolean doRep = dorep;
            userSimulations.add(threadPool.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    executeTestCases(simTestCasesCopy, testCaseExecutorUtil, onlySingleTestCaseExecl, doRep, false, reportHandler);
                    return null;
                }
            }));

            try {
                Thread.sleep(concurrentUserRampUpTimeMs);
            } catch (InterruptedException e) {
            }
        }
        return userSimulations;
    }

    private boolean handleTestCaseExecution(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil, boolean onlySingleTestCaseExec, boolean dorep, boolean isFetchFailureLogs,
            ReportHandler reportHandler) throws Exception {
        boolean success = true;

        AcceptanceTestContext context = testCaseExecutorUtil.getContext();

        List<Map<String, String>> sceanrios = testCase.getRepeatScenariosOrig();
        if (testCase.getRepeatScenarioProviderName() != null) {
            // actual repeat scenario map
            List<Map<String, String>> sceanriosp = context.getProviderTestDataMap(testCase.getRepeatScenarioProviderName());
            if (sceanriosp != null) {
                if (sceanrios != null) {
                    sceanrios.addAll(sceanriosp);
                } else {
                    sceanrios = sceanriosp;
                }
            }
            // repeat scenario map obtained from #responseMapped functions
            else {
                List<Map<String, String>> sceanriowk = context.getWorkflowContextHandler().getSuiteWorkflowScenarioContextValues(testCase, testCase.getRepeatScenarioProviderName());
                if (sceanriowk != null) {
                    if (sceanrios != null) {
                        sceanrios.addAll(sceanriowk);
                    } else {
                        sceanrios = sceanriowk;
                    }
                }
            }
            testCase.setRepeatScenarios(sceanrios);
        } else {
            testCase.setRepeatScenarios(null);
            testCase.setNumberOfExecutions(1);
        }

        List<TestCaseReport> reports = null;
        boolean isPerfTest = false;
        if (testCase.getPerfConfig()==null && testCase.getNumberOfExecutions() != null && testCase.getNumberOfExecutions() > 1) {
            isPerfTest = true;
            reports = context.getPerformanceTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
        } else if (testCase.getPerfConfig()==null && sceanrios != null && !sceanrios.isEmpty()) {
        	reports = context.getScenarioTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
        } else {
            reports = context.getSingleTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
        }

        if (reports != null) {
            for (int index = 0; index < reports.size(); index++) {
                TestCaseReport testCaseReport = reports.get(index);

                if (!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
                    success = false;
                    if (testCase.getRepeatScenarios() != null && !testCase.getRepeatScenarios().isEmpty()) {
                        testCase.getRepeatScenarios().remove(0);
                    }
                }

                if ((isPerfTest && index == 0) || !isPerfTest) {
                    reportHandler.addTestCaseReport(testCaseReport);
                    consoleApiResult(testCaseReport);
                }

                if (context.getGatfExecutorConfig().isGenerateExecutionLogs() && ((isPerfTest && index > 0) || !isPerfTest)) {
                    TestCaseExecutionLogGenerator.log(testCaseReport);
                }

                if (context.getGatfExecutorConfig().isDebugEnabled() && testCase.isDetailedLog()) {
                    logger.info(testCaseReport.toString());
                }

                if (!testCase.isExternalApi() && !testCase.isDisablePostHooks()) {
                    List<Method> postHook = context.getPrePostHook(testCase, false);
                    if (postHook != null) {
                        try {
                            TestCaseReport report = new TestCaseReport(testCaseReport);
                            for (Method method : postHook) {
                                method.invoke(null, new Object[] {report});
                            }
                            testCaseReport.setResponseContent(report.getResponseContent());
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    if (testCase.getPostExecutionDataSourceHookName() != null) {
                        try {
                            context.executeDataSourceHook(testCase.getPostExecutionDataSourceHookName());
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }

                invokeServerLogApi(success, testCaseReport, testCaseExecutorUtil, isFetchFailureLogs);
            }
        }
        return success;
    }

    public void invokeServerLogApi(boolean success, TestCaseReport testCaseReport, TestCaseExecutorUtil testCaseExecutorUtil, boolean isFetchFailureLogs) {
        if (!success && isFetchFailureLogs) {
            List<TestCase> serverLogsApis = context.getServerLogsApiLst();
            if (serverLogsApis.size() > 0) {
                TestCase api = context.getServerLogApi(true);
                if (api != null && !api.isSkipTest() && context.getSessionIdentifier(api) == null) {
                    context.getSingleTestCaseExecutor().execute(api, testCaseExecutorUtil);
                }
                api = context.getServerLogApi(false);
                List<TestCaseReport> logData = context.getSingleTestCaseExecutor().execute(api, testCaseExecutorUtil);
                if (logData != null && logData.size() > 0) {
                    if (logData.get(0).getStatus().equals(TestStatus.Success.status))
                        testCaseReport.setServerLogs(logData.get(0).getResponseContent());
                    else {
                        String content = logData.get(0).getError() != null ? logData.get(0).getError() : StringUtils.EMPTY;
                        content += "\n";
                        content += logData.get(0).getErrorText() != null ? logData.get(0).getErrorText() : StringUtils.EMPTY;
                        testCaseReport.setServerLogs(content);
                    }
                }
            }
        }
    }

    private void addSkippedTestCase(TestCase testCase, String skipReason, ReportHandler reportHandler) {
        TestCaseReport testCaseReport = new TestCaseReport();
        testCaseReport.setTestCase(testCase);
        testCaseReport.setStatus(TestStatus.Skipped.status);
        if (skipReason != null) {
            testCaseReport.setError(String.format("Execute Condition Failed (%s)", skipReason));
            testCaseReport.setFailureReason(TestFailureReason.ExecuteConditionFailed.status);
        }
        testCaseReport.setNumberOfRuns(1);
        testCaseReport.setExecutionTime(0L);
        reportHandler.addTestCaseReport(testCaseReport);
    }

    private boolean executeSingleTestCase(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil, boolean onlySingleTestCaseExec, boolean dorep, boolean isFetchFailureLogs,
            ReportHandler reportHandler) {
        boolean success = false;
        try {

            logger.info("Running acceptance test for " + testCase.getName() + "/" + testCase.getDescription());
            if (testCase.isSkipTest()) {
                logger.info("Skipping acceptance test for " + testCase.getName() + "/" + testCase.getDescription());
                logger.info("============================================================\n\n\n");
                addSkippedTestCase(testCase, null, reportHandler);
                return success;
            }

            if (testCase.getExecuteOnCondition() != null
                    && !testCaseExecutorUtil.getContext().getWorkflowContextHandler().velocityValidate(testCase, testCase.getExecuteOnCondition(), null, context)) {
                logger.info("Execute Condition for Testcase " + testCase.getName() + " returned false." + " Condition was (" + testCase.getExecuteOnCondition() + ")");
                logger.info("============================================================\n\n\n");
                addSkippedTestCase(testCase, testCase.getExecuteOnCondition(), reportHandler);
                return success;
            }

            if (testCaseExecutorUtil.getContext().getGatfExecutorConfig().isDebugEnabled() && testCase.isDetailedLog()) {
                logger.info(testCase.toString());
            }

            success = handleTestCaseExecution(testCase, testCaseExecutorUtil, onlySingleTestCaseExec, dorep, isFetchFailureLogs, reportHandler);

            if (success) {
                logger.info("Successfully ran acceptance test " + testCase.getName() + "/" + testCase.getDescription());
                logger.info("============================================================\n\n\n");

                // Execute all the related tests if the test is a success
                Map<String, List<TestCase>> relTstcs = testCaseExecutorUtil.getContext().getRelatedTestCases();
                if (testCase.getRelatedTestName() != null && relTstcs.containsKey(testCase.getRelatedTestName())) {
                    List<TestCase> relatedTests = relTstcs.get(testCase.getRelatedTestName());
                    if (relatedTests != null && relatedTests.size() > 0) {
                        if (testCase.getRepeatScenarios() != null && testCase.getRepeatScenarios().size() > 0) {
                            for (Map<String, String> scenarioMap : testCase.getRepeatScenarios()) {
                                for (TestCase rTc : relatedTests) {
                                    TestCase rTcCopy = new TestCase(rTc);
                                    rTcCopy.setBaseUrl(testCase.getBaseUrl());
                                    rTcCopy.setSimulationNumber(testCase.getSimulationNumber());
                                    if (rTcCopy.getCarriedOverVariables() != null) {
                                        rTcCopy.getCarriedOverVariables().putAll(scenarioMap);
                                    } else {
                                        rTcCopy.setCarriedOverVariables(scenarioMap);
                                    }
                                    executeSingleTestCase(rTcCopy, testCaseExecutorUtil, onlySingleTestCaseExec, dorep, isFetchFailureLogs, reportHandler);
                                }
                            }
                        } else {
                            for (TestCase rTc : relatedTests) {
                                rTc.setBaseUrl(testCase.getBaseUrl());
                                rTc.setSimulationNumber(testCase.getSimulationNumber());
                                executeSingleTestCase(rTc, testCaseExecutorUtil, onlySingleTestCaseExec, dorep, isFetchFailureLogs, reportHandler);
                            }
                        }
                    }
                }
            } else {
                logger.info("Failed while acceptance test " + testCase.getName() + "/" + testCase.getDescription());
                logger.info("============================================================\n\n\n");
            }
        } catch (Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        } catch (Error e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
        return success;
    }

    void executeTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil, boolean onlySingleTestCaseExec, boolean dorep, boolean isFetchFailureLogs,
            ReportHandler reportHandler) {
        for (TestCase testCase : allTestCases) {
            boolean success = executeSingleTestCase(testCase, testCaseExecutorUtil, onlySingleTestCaseExec, dorep, isFetchFailureLogs, reportHandler);
            if (!success)
                continue;
        }
    }

    @SuppressWarnings("rawtypes")
    public void setupTestCaseHooks(AcceptanceTestContext context) {
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(fcl.apply(null));
            logger.info("Searching for pre/post testcase execution hooks....");
            List<Class> allClasses = new ArrayList<Class>();
            if (context.getGatfExecutorConfig().getTestCaseHooksPaths() != null) {
                for (String item : context.getGatfExecutorConfig().getTestCaseHooksPaths()) {
                    if (item.endsWith(".*")) {
                        List<Class> classes = ClassLoaderUtils.getClasses(item.substring(0, item.indexOf(".*")));
                        if (classes != null && classes.size() > 0) {
                            allClasses.addAll(classes);
                            logger.info("Adding pre/post testcase execution hook package " + item);
                        } else {
                            logger.error("Error:pre/post testcase execution hook package not found - " + item);
                        }
                    } else {
                        try {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            logger.info("Adding pre/post testcase execution hook class " + item);
                        } catch (Exception e) {
                            logger.error("Error:pre/post testcase execution hook class not found - " + item);
                        }
                    }
                }
                if (!allClasses.isEmpty()) {
                    loadAllPrePostHooks(allClasses, context);
                }
            } else {
                logger.info("No pre/post testcase execution hooks found..");
            }

            logger.info("Done scanning pre/post testcase execution hooks...");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }

    @SuppressWarnings("rawtypes")
    private void loadAllPrePostHooks(List<Class> classes, AcceptanceTestContext context) {
        try {
            for (Class claz : classes) {
                Method[] methods = claz.getMethods();
                for (Method method : methods) {
                    context.addTestCaseHooks(method);
                }
            }
        } catch (Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /** Q9: warn once per JVM when budgets are configured but a script ran without a trace. */
    private static volatile boolean checksWarned;

    /** Q5: the first lines of a configured .sel script (for its header tags); empty if the file can't be found/read. */
    private static List<String> selHeaderLines(AcceptanceTestContext context, GatfExecutorConfig configuration, String script) {
        List<File> candidates = new ArrayList<File>();
        candidates.add(new File(script));
        if (configuration.getTestCaseDir() != null) candidates.add(new File(context.getResourceFile(configuration.getTestCaseDir()), script));
        candidates.add(context.getResourceFile(script));
        for (File f : candidates) {
            if (f != null && f.isFile()) {
                try (java.util.stream.Stream<String> lines = java.nio.file.Files.lines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    return lines.limit(60).collect(java.util.stream.Collectors.toList());
                } catch (Exception e) {
                    return java.util.Collections.emptyList();   // unreadable (e.g. not UTF-8): no header tags
                }
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * M5/M6: runs one setup script (loginOnce / globalSetup / globalTeardown) on its own, reported like any script; a
     * failure aborts the run with a clear message (the suite would only fail without it).
     */
    private void runSetupScript(String script, String kind, boolean loginOnce, Long sessionId, String path, AcceptanceTestContext context,
            GatfExecutorConfig configuration, LoggingPreferences lp) {
        Object[] retvals = new Object[4];
        SeleniumTest dyn;
        try {
            dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(context.getWorkflowContextHandler().templatize(script), fcl.apply(null), context,
                    retvals, configuration, false, null, null);
        } catch (Exception e) {
            throw new RuntimeException(kind + " script " + script + " could not be compiled: " + e.getMessage(), e);
        }
        System.out.println("Running " + kind + " script " + script);
        List<String> failed = Collections.synchronizedList(new ArrayList<String>());
        if (loginOnce) SeleniumTest.LOGIN_ONCE_RUN.set(Boolean.TRUE);
        // E14: globalSetup runs before loginOnce replaces storageState: it must not start from the previous run's login
        boolean skipState = "globalSetup".equals(kind) && StringUtils.isNotBlank(configuration.getLoginOnce());
        if (skipState) SeleniumTest.SKIP_STATE_LOAD.set(Boolean.TRUE);
        try {
            List<SeleniumTest> one = new ArrayList<SeleniumTest>();
            one.add(dyn);
            List<Object[]> data = new ArrayList<Object[]>();
            data.add(retvals);
            new ConcSeleniumTest(sessionId, path, 0, -1, context, one, data, lp, true, false, 1, SetupScripts.runPrefix(kind), "local", failed).call();
        } catch (Exception e) {
            throw new RuntimeException(kind + " script " + script + " failed: " + e.getMessage(), e);
        } finally {
            if (loginOnce) SeleniumTest.LOGIN_ONCE_RUN.remove();
            SeleniumTest.clearSemanticOpts();
            if (skipState) SeleniumTest.SKIP_STATE_LOAD.remove();
        }
        if (!failed.isEmpty()) throw new RuntimeException(kind + " script " + script + " failed" + ("globalTeardown".equals(kind) ? "" : " - the UI suite was not run"));
    }

    // ---- CLI reporter hooks (no-ops unless GatfCli activated RunConsole) --------------------------------------------

    private static void consoleApiResult(TestCaseReport r) {
        if (!com.gatf.log.RunConsole.isActive() || r == null || r.getTestCase() == null) return;
        TestCase tc = r.getTestCase();
        String file = tc.getSourcefileName() != null ? tc.getSourcefileName() : "api";
        String title = file + " \u203a " + (tc.getName() != null ? tc.getName() : String.valueOf(tc.getIdentifier()));
        com.gatf.log.RunConsole.Status st = TestStatus.Success.status.equals(r.getStatus()) ? com.gatf.log.RunConsole.Status.PASSED
                : TestStatus.Skipped.status.equals(r.getStatus()) ? com.gatf.log.RunConsole.Status.SKIPPED : com.gatf.log.RunConsole.Status.FAILED;
        String tname = tc.getName() != null ? tc.getName() : String.valueOf(tc.getIdentifier());
        if (!com.gatf.log.RunConsole.wantsDetail()) { com.gatf.log.RunConsole.result(file, tname, title, st, 0L, null, null); return; }
        String err = r.getError() != null && !r.getError().trim().isEmpty() ? r.getError() : r.getErrorText();
        String detail = (r.getMethod() != null ? r.getMethod() : tc.getMethod()) + " " + (r.getActualUrl() != null ? r.getActualUrl() : tc.getUrl())
                + " -> " + r.getResponseStatusCode() + (r.getErrorText() != null && !r.getErrorText().trim().isEmpty() ? "\n" + r.getErrorText() : "");
        com.gatf.log.RunConsole.result(file, tname, title, st, r.getExecutionTime() != null ? r.getExecutionTime() : 0L, err, detail);
    }

    private static void consoleSelResult(String selFile, String browserKey, String subtest, SeleniumTestResult res) {
        consoleSelResult(selFile, browserKey, subtest, res, null);
    }

    /** Q3: {@code tracePath} = the kept trace.zip (absolute) or null; the failure screenshot is taken from the result. */
    private static void consoleSelResult(String selFile, String browserKey, String subtest, SeleniumTestResult res, String tracePath) {
        consoleSelResult(selFile, browserKey, subtest, res, tracePath, null);
    }

    /** M6: {@code skipReason} ("Skipped (fixme)" …) is shown on a skipped subtest's line and in JUnit {@code <skipped message>}. */
    private static void consoleSelResult(String selFile, String browserKey, String subtest, SeleniumTestResult res, String tracePath, String skipReason) {
        if (!com.gatf.log.RunConsole.isActive()) return;
        boolean sub = subtest != null && !subtest.equals("-");
        String title = "[" + browserKey + "] \u203a " + selFile + (sub ? " \u203a " + subtest : "");
        String tname = "[" + browserKey + "]" + (sub ? " " + subtest : "");
        if (res == null) {
            com.gatf.log.RunConsole.result(selFile, tname, title, com.gatf.log.RunConsole.Status.SKIPPED, 0L, skipReason, null);
            return;
        }
        if (!com.gatf.log.RunConsole.wantsDetail()) {
            com.gatf.log.RunConsole.result(selFile, tname, title, res.isStatus() ? com.gatf.log.RunConsole.Status.PASSED : com.gatf.log.RunConsole.Status.FAILED, 0L, null, null);
            return;
        }
        String msg = null;
        if (res.getLogs() != null && res.getLogs().get("gatf") != null && res.getLogs().get("gatf").getAll().size() > 0) {
            msg = res.getLogs().get("gatf").getAll().get(0).getMessage();
        }
        if (msg == null && res.getCause() != null) msg = String.valueOf(res.getCause().getMessage());
        String detail = res.getCause() != null ? org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(res.getCause()) : null;
        java.util.List<String> att = new java.util.ArrayList<String>(2);
        if (!res.isStatus() && res.getSubtestImg() != null && new File(res.getSubtestImg()).isFile()) {
            att.add(new File(res.getSubtestImg()).getAbsolutePath());   // failure screenshot (taken by the step's failure path)
        }
        if (tracePath != null) att.add(tracePath);
        com.gatf.log.RunConsole.result(selFile, tname, title, res.isStatus() ? com.gatf.log.RunConsole.Status.PASSED : com.gatf.log.RunConsole.Status.FAILED,
                res.getExecutionTime() / 1_000_000L, res.isStatus() ? null : msg, res.isStatus() ? null : detail, att, res.getSoftFailures());
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("-executor") && !args[1].trim().isEmpty()) {
            GatfTestCaseExecutorUtil mojo = new GatfTestCaseExecutorUtil();
            mojo.setConfigFile(args[1]);
            mojo.setNumConcurrentExecutions(1);
            mojo.setHttpConnectionTimeout(10000);
            mojo.setHttpRequestTimeout(10000);
            mojo.setHttpCompressionEnabled(true);
            mojo.setNumConcurrentExecutions(1);
            mojo.setConcurrentUserSimulationNum(0);
            mojo.setTestCaseDir("data");
            mojo.setOutFilesDir("out");
            mojo.setLoadTestingReportSamples(3);
            mojo.setConcurrentUserRampUpTime(0L);
            mojo.setEnabled(true);
            String cwd = System.getProperty("user.dir");
			if(!new File(cwd).isAbsolute()) {
				throw new RuntimeException("Invalid root dir, current Working directory should be an absolute path");
			}
            mojo.setTestCasesBasePath(cwd);
            mojo.setOutFilesBasePath(cwd);
            mojo.execute();
        } else if (args.length > 1 && args[0].startsWith("-selenium") && !args[1].trim().isEmpty()) {
            GatfTestCaseExecutorUtil mojo = new GatfTestCaseExecutorUtil();
            mojo.setConfigFile(args[1]);
            mojo.setNumConcurrentExecutions(1);
            mojo.setHttpConnectionTimeout(10000);
            mojo.setHttpRequestTimeout(10000);
            mojo.setHttpCompressionEnabled(true);
            mojo.setNumConcurrentExecutions(1);
            mojo.setConcurrentUserSimulationNum(0);
            mojo.setTestCaseDir("data");
            mojo.setOutFilesDir("out");
            mojo.setLoadTestingReportSamples(3);
            mojo.setConcurrentUserRampUpTime(0L);
            mojo.setEnabled(true);
            String cwd = System.getProperty("user.dir");
			if(!new File(cwd).isAbsolute()) {
				throw new RuntimeException("Invalid root dir, current Working directory should be an absolute path");
			}
            mojo.setTestCasesBasePath(cwd);
            mojo.setOutFilesBasePath(cwd);
            mojo.execute();
        }
        else if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
                && !args[2].trim().isEmpty() && !args[3].trim().isEmpty())
        {
            GatfConfigToolUtil.main(args);
        }
        else if(args.length > 1 && args[0].equals("-listener"))
        {
            DistributedGatfListener.main(args);
        }
        else
        {
            System.out.println("Please specify proper arguments to the program - valid invocation options are, \n" +
                    "java -jar gatf-plugin-{version}.jar -generator {generator-config-file}.xml\n" +
                    "java -jar gatf-plugin-{version}.jar -executor {executor-config-file}.xml\n" +
                    "java -jar gatf-plugin-{version}.jar -configtool {http_port} {ip_address} {project_folder}\n" + 
                    "java -jar gatf-plugin-{version}.jar -listener\n");
        }
    }

    public DistributedTestStatus handleDistributedTests(DistributedAcceptanceContext dContext, DistributedTestContext tContext) {
        return new DistributedExecution(this).handleDistributedTests(dContext, tContext);   // G8: moved to DistributedExecution
    }

    public DistributedTestStatus handleDistributedSeleniumTests(DistributedAcceptanceContext dContext, List<Class<SeleniumTest>> classes, DistributedTestContext tContext) throws Exception {
        return new DistributedExecution(this).handleDistributedSeleniumTests(dContext, classes, tContext);   // G8: moved to DistributedExecution
    }

    @SuppressWarnings("unused")
    private static class ConcSeleniumTestDT implements Callable<List<List<SeleniumTestSession>>> {
        private int index;
        private AcceptanceTestContext context;
        private List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        private LoggingPreferences lp;
        private String path;

        public ConcSeleniumTestDT(String path, int index, AcceptanceTestContext context, List<SeleniumTest> tests, LoggingPreferences lp) {
            this.index = index;
            this.context = context;
            this.tests = tests;
            this.lp = lp;
            this.path = path;
        }

        @Override
        public List<List<SeleniumTestSession>> call() throws Exception {
            List<List<SeleniumTestSession>> lglist = new ArrayList<List<SeleniumTestSession>>();
            for (int i = 0; i < tests.size(); i++) {
                SeleniumTest dyn = tests.get(i).copy(context, index + 1);
                List<SeleniumTestSession> result = null;
                try {
                    result = dyn.execute(lp, path);
                    lglist.add(result);
                } catch (FailureException e) {
                	lglist.add(dyn.get__sessions__());
                } catch (Throwable e) {
                    dyn.pushResult(new SeleniumTestResult(dyn, e));
                    lglist.add(dyn.get__sessions__());
                }
                dyn.quitAll();
            }
            return lglist;
        }
    }


    // G8: doAsyncDistributed(Conc)Reporting moved to DistributedExecution

    public static GatfExecutorConfig getConfig(InputStream resource, boolean isXml) throws Exception {
    	GatfExecutorConfig configuration = null;
    	if(isXml) {
	        configuration = WorkflowContextHandler.XOM.readValue(resource, GatfExecutorConfig.class);
    	} else {
    		configuration = WorkflowContextHandler.OM.readValue(resource, GatfExecutorConfig.class);
    	}
        configuration.setJavaVersion(System.getProperty("java.version"));
        return configuration;
    }

    public static String getConfigStrJson(GatfExecutorConfig configuration) throws JsonProcessingException {
    	return WorkflowContextHandler.OM.writerWithDefaultPrettyPrinter().writeValueAsString(configuration);
    }

    public static String getConfigStrXml(GatfExecutorConfig configuration) throws JsonProcessingException {
        return WorkflowContextHandler.XOM.writeValueAsString(configuration);
    }

    public AcceptanceTestContext getContext() {
        return context;
    }

    public TestCase getAuthTestCase() {
        return authTestCase;
    }

    public void shutdown() {
        if (context != null) {
            context.shutdown();
        }
        if (tclgenerator != null) {
            tclgenerator.interrupt();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
    
    String initOutFolderAndGetPath(AcceptanceTestContext context, Long sessionId, int retryCnter, String type) {
    	String path = sessionId + (retryCnter==0?"":("-"+retryCnter));
        File resource = context.getOutDir();
        // Phase 3.x Slice E: gatf-resources retired. Reports now render in the React viewer (/app) via
        // .data.json sidecars + client-side redirect stubs, so the legacy asset bundle is no longer
        // copied into each report's output directory.
        new File(resource.getAbsolutePath() + File.separator + path).mkdir();
        ReportHandler.pushPath(sessionId, retryCnter, path, type);
        return path;
    }
}
