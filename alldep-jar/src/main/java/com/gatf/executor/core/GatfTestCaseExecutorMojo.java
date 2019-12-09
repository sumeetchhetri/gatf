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
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.openqa.selenium.logging.LoggingPreferences;

import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
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
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.executor.report.RuntimeReportUtil.LoadTestEntry;
import com.gatf.executor.report.TestCaseExecutionLogGenerator;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.executor.report.TestExecutionPercentile;
import com.gatf.executor.report.TestSuiteStats;
import com.gatf.generator.core.ClassLoaderUtils;
import com.gatf.selenium.SeleniumCodeGeneratorAndUtil;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.SeleniumTest;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;
import com.gatf.selenium.SeleniumTestSession;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.gatf.ui.GatfConfigToolMojo;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * @author Sumeet Chhetri The maven plugin main class for the Test case Executor/Workflow engine
 */
public class GatfTestCaseExecutorMojo extends AbstractMojo implements GatfPlugin {

	private Logger logger = Logger.getLogger(GatfTestCaseExecutorMojo.class.getSimpleName());
    
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

    private Long startTime = 0L;

    private AcceptanceTestContext context;

    private DistributedGatfTester distributedGatfTester;

    // Test case execution log generator
    private Thread tclgenerator = null;

    private TestCase authTestCase;

    public void setProject(MavenProject project) {
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
                return o1 == null ? (o2 == null ? 0 : Integer.MIN_VALUE) : (o2 == null ? Integer.MAX_VALUE : new Integer(o1.getSequence()).compareTo(o2.getSequence()));
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
            if (baseUrl != null) {
                testCaseCopy.setBaseUrl(baseUrl);
            }
            allTestCases.add(testCaseCopy);
        }
        return allTestCases;
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
                    XStream xstream = new XStream(new DomDriver("UTF-8"));
                    XStream.setupDefaultSecurity(xstream);
                    xstream.allowTypes(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                            SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
                    xstream.processAnnotations(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                            SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
                    xstream.alias("gatf-testdata-source", GatfTestDataSource.class);
                    xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
                    xstream.alias("gatf-testdata-source-hook", GatfTestDataSourceHook.class);
                    xstream.alias("gatfTestDataConfig", GatfTestDataConfig.class);
                    xstream.alias("seleniumDriverConfigs", SeleniumDriverConfig[].class);
                    xstream.alias("seleniumDriverConfig", SeleniumDriverConfig.class);
                    xstream.alias("testCaseHooksPaths", String[].class);
                    xstream.alias("testCaseHooksPath", String.class);
                    xstream.alias("args", String[].class);
                    xstream.alias("arg", String.class);
                    xstream.alias("testCaseHooksPaths", String[].class);
                    xstream.alias("testCaseHooksPath", String.class);
                    xstream.alias("queryStrs", String[].class);
                    xstream.alias("queryStr", String.class);
                    xstream.alias("distributedNodes", String[].class);
                    xstream.alias("distributedNode", String.class);
                    xstream.alias("ignoreFiles", String[].class);
                    xstream.alias("orderedFiles", String[].class);
                    xstream.alias("string", String.class);
                    xstream.alias("seleniumScripts", String[].class);
                    xstream.alias("seleniumScript", String.class);

                    configuration = (GatfExecutorConfig) xstream.fromXML(resource);

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
            doExecute(configuration, null);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            shutdown();
        }
    }

    public void doSeleniumTest(GatfExecutorConfig configuration, List<String> files) {

        List<DistributedConnection> distConnections = null;

        distributedGatfTester = new DistributedGatfTester();

        for (SeleniumDriverConfig selConf : configuration.getSeleniumDriverConfigs()) {
            if (selConf != null && selConf.getDriverName() != null) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        //System.setProperty("java.home", configuration.getJavaHome());
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");

        try {
            SeleniumCodeGeneratorAndUtil.clean();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (files != null && files.size() > 0) {
            configuration.setSeleniumScripts(files.toArray(new String[files.size()]));
        }

        final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(configuration);
        final List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        final List<Object[]> testdata = new ArrayList<Object[]>();
        final List<String> testClassNames = new ArrayList<String>();
        for (String selscript : configuration.getSeleniumScripts()) {
            try {
                Object[] retvals = new Object[4];
                SeleniumTest dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(selscript, getClassLoader(), context, retvals, configuration);
                tests.add(dyn);
                testdata.add(retvals);
                testClassNames.add(dyn.getClass().getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String runPrefix = "LRun-1";

        int numberOfRuns = 1;
        if (configuration.getConcurrentUserSimulationNum() != null && configuration.getConcurrentUserSimulationNum() > 1) {
            numberOfRuns = configuration.getConcurrentUserSimulationNum();
        }

        // If load testing time is greater than 10sec only then run load tests..
        boolean isLoadTestingEnabled = configuration.isLoadTestingEnabled() && configuration.getLoadTestingTime() > 10000;

        int loadTstReportsCount = 0;

        int loadTestRunNum = 1;

        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;

        List<FutureTask<Object>> distTasks = new ArrayList<FutureTask<Object>>();
        List<String> taskNodes = new ArrayList<String>();
        long reportSampleTimeMs = 0;
        
        Map<String, Map<Integer, String>> indexes = new HashMap<String, Map<Integer, String>>();
        indexes.put("local", new HashMap<Integer, String>());

        if (isLoadTestingEnabled) {
            reportSampleTimeMs = configuration.getLoadTestingTime() / (loadTestingReportSamplesNum - 1);
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
        if (numberOfRuns > 1) {
            int threadNum = 100;
            if (numberOfRuns < 100)
                threadNum = numberOfRuns;

            threadPool = Executors.newFixedThreadPool(threadNum);
        }

        context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

        startTime = System.currentTimeMillis();

        int runNum = 0;
        while (tests.size() > 0) {
            boolean dorep = false;

            boolean done = false;

            if (isLoadTestingEnabled) {
                long currentTime = System.currentTimeMillis();

                if (context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                    done = context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                    dorep = true;
                } else {
                    done = (currentTime - startTime) > configuration.getLoadTestingTime();

                    long elapsedFraction = (currentTime - startTime);

                    dorep = done || loadTstReportsCount == 0;

                    if (!dorep && elapsedFraction >= reportSampleTimeMs * loadTstReportsCount) {
                        dorep = true;
                    }
                }
                if (done) {
                    break;
                }
            } else {
                dorep = true;
                done = true;
            }

            runNum++;

            Map<String, List<Map<String, Map<String, List<Object[]>>>>> summLstMap = new LinkedHashMap<String, List<Map<String, Map<String, List<Object[]>>>>>();

            summLstMap.put("local", new ArrayList<Map<String, Map<String, List<Object[]>>>>());
            if (threadPool != null) {
                List<FutureTask<Map<String, Map<String, List<Object[]>>>>> ltasks = new ArrayList<FutureTask<Map<String, Map<String, List<Object[]>>>>>();
                for (int i = 0; i < numberOfRuns; i++) {
                    ltasks.add(new FutureTask<Map<String, Map<String, List<Object[]>>>>(new ConcSeleniumTest(i - 1, context, tests, testdata, lp, dorep, runNum, runPrefix, "local")));
                    threadPool.execute(ltasks.get(i));
                    try {
                        Thread.sleep(concurrentUserRampUpTimeMs);
                    } catch (InterruptedException e) {
                    }
                }
                for (int i = 0; i < numberOfRuns; i++) {
                    try {
                        Map<String, Map<String, List<Object[]>>> o = ltasks.get(i).get();
                        summLstMap.get("local").add(o);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            } else {
                try {
                    summLstMap.get("local").add(new ConcSeleniumTest(-1, context, tests, testdata, lp, dorep, runNum, runPrefix, "local").call());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            }

            if (dorep) {
                ReportHandler.doSeleniumSummaryTestReport(summLstMap, context, runNum, runPrefix);
                indexes.get("local").put(runNum, runPrefix + "-" + runNum + "-selenium-index.html");
            }

            context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

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
                    }
                } catch (Exception e) {
                }
            }
        }
        
        ReportHandler.doSeleniumFinalTestReport(indexes, context);

        shutdown();
    }

    private static class ConcSeleniumTest implements Callable<Map<String, Map<String, List<Object[]>>>> {
        private int index;
        private AcceptanceTestContext context;
        private List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        private List<Object[]> testdata = new ArrayList<Object[]>();
        private LoggingPreferences lp;
        private boolean dorep;
        private int runNum;
        private String runPrefix;
        private String node;

        public ConcSeleniumTest(int index, AcceptanceTestContext context, List<SeleniumTest> tests, List<Object[]> testdata, LoggingPreferences lp, boolean dorep, int runNum, String runPrefix,
                String node) {
            this.index = index;
            this.context = context;
            this.tests = tests;
            this.testdata = testdata;
            this.lp = lp;
            this.runNum = runNum;
            this.runPrefix = runPrefix;
            this.node = node;
            this.dorep = dorep;
        }

        @SuppressWarnings("unused")
        @Override
        public Map<String, Map<String, List<Object[]>>> call() throws Exception {
            Map<String, Map<String, List<Object[]>>> summLst = new LinkedHashMap<String, Map<String, List<Object[]>>>();
            TestSuiteStats stats = new TestSuiteStats();
            int tot = 0, fal = 0, succ = 0, skp = 0;
            for (int i = 0; i < tests.size(); i++) {
                SeleniumTest dyn = tests.get(i).copy(context, index + 1);
                Object[] retvals = testdata.get(i);
                List<SeleniumTestSession> sessions = null;
                try {
                    summLst.put((String) retvals[0], new LinkedHashMap<String, List<Object[]>>());
                    sessions = dyn.execute(lp);
                } catch (Throwable e) {
                    dyn.pushResult(new SeleniumTestResult(dyn, e));
                    sessions = dyn.get__sessions__();
                }
                dyn.quitAll();

                for (int u = 0; u < sessions.size(); u++) {
                    SeleniumTestSession sess = sessions.get(u);
                    for (Map.Entry<String, SeleniumResult> e : sess.getResult().entrySet()) {
                        String keykey = e.getKey() + (sess.getSessionName() != null ? ("-" + sess.getSessionName()) : "");
                        if (!summLst.get((String) retvals[0]).containsKey(keykey)) {
                            summLst.get((String) retvals[0]).put(keykey, new ArrayList<Object[]>());
                        }

                        SeleniumResult res = e.getValue();
                        if (res.getResult() == null) {
                            summLst.get((String) retvals[0]).get(keykey).add(new Object[] {"-", "#", "UNKNOWN", "", "0s"});
                            skp++;
                        } else {
                            String tim = res.getResult().getExecutionTime() / Math.pow(10, 9) + "";
                            stats.setExecutionTime(stats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                            if (tim.indexOf(".") != -1) {
                                String[] parts = tim.split("\\.");
                                tim = parts[0] + "." + (parts[1].length() > 3 ? parts[1].substring(0, 3) : parts[1]);
                            }
                            tim += "s";
                            String fileName = runPrefix + "-" + (index + 2) + "-" + (runNum) + "-" + (i + 1) + "-" + keykey.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                            summLst.get((String) retvals[0]).get(keykey).add(new Object[] {"-", fileName + ".html", res.getResult().isStatus() ? "SUCCESS" : "FAILED",
                                    !res.getResult().isStatus() ? res.getResult().getLogs().get("gatf").getAll().get(0).getMessage() : "", tim});
                            if (dorep) {
                                ReportHandler.doSeleniumTestReport(fileName, retvals, res.getResult(), context);
                            }
                            if(res.getResult().isStatus()) {
                                succ++;
                            } else {
                                fal++;
                            }
                        }
                        
                        tot++;

                        for (Map.Entry<String, SeleniumTestResult> e1 : res.getSubTestResults().entrySet()) {
                            if (e1.getValue() == null) {
                                summLst.get((String) retvals[0]).get(keykey).add(new Object[] {e1.getKey(), "#", "UNKNOWN", "", "0s"});
                                skp++;
                            } else {
                                String tim = e1.getValue().getExecutionTime() / Math.pow(10, 9) + "";
                                stats.setExecutionTime(stats.getExecutionTime() + res.getResult().getExecutionTime()/1000000);
                                if (tim.indexOf(".") != -1) {
                                    String[] parts = tim.split("\\.");
                                    tim = parts[0] + "." + (parts[1].length() > 3 ? parts[1].substring(0, 3) : parts[1]);
                                }
                                tim += "s";
                                String fileName = runPrefix + "-" + (index + 2) + "-" + (runNum) + "-" + (i + 1) + "-" + keykey.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "-"
                                        + e1.getKey().replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                                summLst.get((String) retvals[0]).get(keykey).add(new Object[] {e1.getKey(), fileName + ".html", e1.getValue().isStatus() ? "SUCCESS" : "FAILED",
                                        !e1.getValue().isStatus() ? e1.getValue().getLogs().get("gatf").getAll().get(0).getMessage() : "", tim});
                                if (dorep) {
                                    ReportHandler.doSeleniumTestReport(fileName, retvals, e1.getValue(), context);
                                }
                            }
                            if(res.getResult().isStatus()) {
                                succ++;
                            } else {
                                fal++;
                            }
                            
                            tot++;
                        }
                    }
                }
            }
            
            stats.setTotalTestCount(tot);
            stats.setFailedTestCount(fal);
            stats.setSkippedTestCount(skp);
            
            stats.setTotalRuns(1);
            stats.setFailedRuns(1);
            stats.setFailedRuns(fal>0?1:0);
            stats.setTotalSuiteRuns(1);
            
            if(node.equals("local")) {
                RuntimeReportUtil.addEntry(node, runPrefix, runNum, runPrefix + "-" + runNum + "-selenium-index.html", stats);
            } else {
                LoadTestEntry lentry = new LoadTestEntry(node, runPrefix, runNum, runPrefix + "-" + runNum + "-selenium-index.html", stats);
                RuntimeReportUtil.addLEntry(lentry);
            }
            return summLst;
        }
    }

    public void doExecute(GatfPluginConfig configuration, List<String> files) throws MojoFailureException {
        doExecute((GatfExecutorConfig) configuration, files);
    }

    @SuppressWarnings({"rawtypes"})
    public void doExecute(GatfExecutorConfig configuration, List<String> files) throws MojoFailureException {

        if (configuration.isGenerateExecutionLogs() && !configuration.isSeleniumExecutor()) {
            tclgenerator = new Thread(new TestCaseExecutionLogGenerator(configuration));
            tclgenerator.start();
        }

        initilaizeContext(configuration, true);

        List<DistributedConnection> distConnections = null;

        if (configuration.isSeleniumExecutor()) {
            if(configuration.isValidSeleniumRequest()) {
                doSeleniumTest(configuration, files);
            } else {
                throw new RuntimeException("Please provide Selenium scripts for execution");
            }
            return;
        }

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

        int loadTstReportsCount = 0;

        TestSuiteStats loadStats = new TestSuiteStats();

        int loadTestRunNum = 1;

        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;

        validateTestCases(allTestCases, testCaseExecutorUtil);

        List<FutureTask<DistributedTestStatus>> distTasks = null;
        long reportSampleTimeMs = 0;
        if (isLoadTestingEnabled) {
            reportSampleTimeMs = configuration.getLoadTestingTime() / (loadTestingReportSamplesNum - 1);

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
            int threadNum = 100;
            if (numberOfRuns < 100)
                threadNum = numberOfRuns;

            threadPool = Executors.newFixedThreadPool(threadNum);
        }

        ExecutorService reportingThreadPool = Executors.newFixedThreadPool(30);

        startTime = System.currentTimeMillis();

        List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();

        TestExecutionPercentile testPercentiles = new TestExecutionPercentile();

        TestExecutionPercentile runPercentiles = new TestExecutionPercentile();

        if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;

        while (allTestCases.size() > 0) {
            long suiteStartTime = isLoadTestingEnabled ? System.currentTimeMillis() : startTime;

            boolean dorep = false;

            boolean done = false;

            ReportHandler reportHandler = new ReportHandler(null, null);

            Integer runNums = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
            if (context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
            }

            for (String relativeFileName : relativeFileNames) {
                reportHandler.initializeResultsHolders(runNums, relativeFileName);
            }

            if (isLoadTestingEnabled) {
                long currentTime = System.currentTimeMillis();

                if (context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                    done = context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                    dorep = true;
                } else {
                    done = (currentTime - startTime) > configuration.getLoadTestingTime();

                    long elapsedFraction = (currentTime - startTime);

                    dorep = done || loadTstReportsCount == 0;

                    if (!dorep && elapsedFraction >= reportSampleTimeMs * loadTstReportsCount) {
                        dorep = true;
                    }
                }
                if (done) {
                    break;
                }
            } else {
                dorep = true;
                done = true;
            }

            if (numberOfRuns > 1) {
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

                doAsyncConcReporting(compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, loadTestRunNum,
                        loadTestResources, numberOfRuns, loadStats, reportingThreadPool, userSimulations.size(), (System.currentTimeMillis() - suiteStartTime));

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            } else {
                long currentTime = System.currentTimeMillis();
                String fileurl = isLoadTestingEnabled ? currentTime + ".html" : "index.html";
                executeTestCases(allTestCases, testCaseExecutorUtil, compareEnabledOnlySingleTestCaseExec, dorep, !isLoadTestingEnabled && !compareEnabledOnlySingleTestCaseExec, reportHandler);

                doAsyncReporting(compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, loadTestRunNum,
                        loadTestResources, numberOfRuns, loadStats, reportingThreadPool, (System.currentTimeMillis() - suiteStartTime));

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            }

            context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

            if (done) {
                break;
            }
        }

        reportingThreadPool.shutdown();
        while (!reportingThreadPool.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (numberOfRuns > 1) {
            threadPool.shutdown();
        }

        loadStats.setTotalUserSuiteRuns(numberOfRuns);

        if (isLoadTestingEnabled) {
            String runPrefix = null;
            if (distTasks != null && distTasks.size() > 0) {
                runPrefix = "LRun-";
            }

            loadStats.setExecutionTime(System.currentTimeMillis() - startTime);
            ReportHandler.doFinalLoadTestReport(runPrefix, loadStats, context, null, null, loadTestResources);
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

            ReportHandler.doFinalLoadTestReport(null, finalDistStats, context, nodes, nodesurls, loadTestResources);
            loadStats = finalDistStats;
        }

        ReportHandler.doTAReporting(null, context, isLoadTestingEnabled, testPercentiles, runPercentiles);

        if (loadStats != null) {
            logger.info(loadStats.show());
            if (loadStats.getFailedTestCount() > 0) {
                throw new MojoFailureException(loadStats.getFailedTestCount() + " testcases have failed");
            }
        }

        testCaseExecutorUtil.shutdown();
    }

    private void doAsyncConcReporting(final boolean compareEnabledOnlySingleTestCaseExec, final ReportHandler reportHandler, final long suiteStartTime, final String fileurl,
            final boolean isLoadTestingEnabled, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum,
            final List<LoadTestResource> loadTestResources, final int numberOfRuns, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final int concrunNos,
            final long suiteExecTime) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                for (int y = 0; y < concrunNos; y++) {
                    try {
                        if (dorep && !compareEnabledOnlySingleTestCaseExec) {
                            String afileurl = fileurl;
                            if (!isLoadTestingEnabled) {
                                afileurl = null;
                            }
                            reportHandler.doConcurrentRunReporting(context, suiteStartTime, afileurl, (y + 1), loadTestRunNum == 1, testPercentiles, runPercentiles, null);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                doAsyncReporting(compareEnabledOnlySingleTestCaseExec, reportHandler, suiteStartTime, fileurl, isLoadTestingEnabled, testPercentiles, runPercentiles, dorep, loadTestRunNum,
                        loadTestResources, numberOfRuns, loadStats, null, suiteExecTime);
            }
        });
    }

    private void doAsyncReporting(final boolean compareEnabledOnlySingleTestCaseExec, final ReportHandler reportHandler, final long suiteStartTime, final String fileurl,
            final boolean isLoadTestingEnabled, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum,
            final List<LoadTestResource> loadTestResources, final int numberOfRuns, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final long suiteExecTime) {
        Runnable runnable = new Runnable() {
            public void run() {
                if (compareEnabledOnlySingleTestCaseExec) {
                    TestSuiteStats stats = reportHandler.doReporting(context, suiteStartTime, fileurl, null, isLoadTestingEnabled, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        loadStats.copy(stats);
                        RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats);
                    }
                } else if (dorep) {
                    TestSuiteStats stats = null;
                    if (numberOfRuns > 1) {
                        stats = reportHandler.doReportingIndex(context, suiteStartTime, fileurl, numberOfRuns, null, isLoadTestingEnabled);
                    } else {
                        stats = reportHandler.doReporting(context, suiteStartTime, fileurl, null, isLoadTestingEnabled, testPercentiles, runPercentiles);
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
                            RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats);
                        }
                        reportHandler.clearForLoadTests(context);
                    } else {
                        synchronized (loadStats) {
                            loadStats.copy(stats);
                            loadStats.setTotalUserSuiteRuns(numberOfRuns);
                            RuntimeReportUtil.addEntry(null, null, loadTestRunNum, fileurl, stats);
                        }
                    }
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addEntry(null, null, loadTestRunNum, null, stats);
                    }
                }
            }
        };
        if (reportingThreadPool != null)
            reportingThreadPool.execute(runnable);
        else
            runnable.run();
    }

    public void initilaizeContext(GatfExecutorConfig configuration, boolean flag) throws MojoFailureException {
        context = new AcceptanceTestContext(configuration, getClassLoader());
        try {
            context.validateAndInit(flag);
            if (flag) {
                setupTestCaseHooks(context);
            }
        } catch (Throwable e) {
            logger.severe(ExceptionUtils.getStackTrace(e));
            throw new MojoFailureException("Configuration is invalid", e);
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
                logger.severe("Got exception while running acceptance test " + testCase.getName() + "/" + testCase.getDescription() + "\n" + ExceptionUtils.getStackTrace(e));
                throw e;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private List<Future> doConcurrentRunExecution(boolean compareEnabledOnlySingleTestCaseExec, Integer numberOfRuns, List<TestCase> allTestCases, List<String> baseUrlList,
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
        if (testCase.getNumberOfExecutions() != null && testCase.getNumberOfExecutions() > 1) {
            isPerfTest = true;
            reports = context.getPerformanceTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
        } else if (sceanrios == null || sceanrios.isEmpty()) {
            reports = context.getSingleTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
        } else {
            reports = context.getScenarioTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
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
                        String content = logData.get(0).getError() != null ? logData.get(0).getError() : "";
                        content += "\n";
                        content += logData.get(0).getErrorText() != null ? logData.get(0).getErrorText() : "";
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
            logger.severe(ExceptionUtils.getStackTrace(e));
        } catch (Error e) {
            logger.severe(ExceptionUtils.getStackTrace(e));
        }
        return success;
    }

    private void executeTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil, boolean onlySingleTestCaseExec, boolean dorep, boolean isFetchFailureLogs,
            ReportHandler reportHandler) {
        for (TestCase testCase : allTestCases) {
            boolean success = executeSingleTestCase(testCase, testCaseExecutorUtil, onlySingleTestCaseExec, dorep, isFetchFailureLogs, reportHandler);
            if (!success)
                continue;
        }
    }

    private ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    @SuppressWarnings("rawtypes")
    public void setupTestCaseHooks(AcceptanceTestContext context) {
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(getClassLoader());
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
                            logger.severe("Error:pre/post testcase execution hook package not found - " + item);
                        }
                    } else {
                        try {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            logger.info("Adding pre/post testcase execution hook class " + item);
                        } catch (Exception e) {
                            logger.severe("Error:pre/post testcase execution hook class not found - " + item);
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
            logger.severe(ExceptionUtils.getStackTrace(e));
        }
    }

    public static void main(String[] args) throws Exception {

        if (args.length > 1 && args[0].equals("-executor") && !args[1].trim().isEmpty()) {
            GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
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
            mojo.setTestCasesBasePath(System.getProperty("user.dir"));
            mojo.setOutFilesBasePath(System.getProperty("user.dir"));
            mojo.execute();
        } else if (args.length > 1 && args[0].equals("-selenium") && !args[1].trim().isEmpty()) {
            GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
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
            mojo.setTestCasesBasePath(System.getProperty("user.dir"));
            mojo.setOutFilesBasePath(System.getProperty("user.dir"));
            mojo.execute();
        }
        else if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
                && !args[2].trim().isEmpty() && !args[3].trim().isEmpty())
        {
            GatfConfigToolMojo.main(args);
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

    @SuppressWarnings("rawtypes")
    public DistributedTestStatus handleDistributedTests(DistributedAcceptanceContext dContext, DistributedTestContext tContext) {
        context = new AcceptanceTestContext(dContext, getClassLoader());
        context.handleTestDataSourcesAndHooks(context.getGatfExecutorConfig().getGatfTestDataConfig());

        GatfExecutorConfig configuration = context.getGatfExecutorConfig();

        if (configuration.isGenerateExecutionLogs()) {
            tclgenerator = new Thread(new TestCaseExecutionLogGenerator(configuration));
            tclgenerator.start();
        }

        final TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(context);

        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;

        int numberOfRuns = tContext.getNumberOfRuns();

        int loadTstReportsCount = 0;

        TestSuiteStats loadStats = new TestSuiteStats();

        int loadTestRunNum = 1;

        long reportSampleTimeMs = configuration.getLoadTestingTime() / (loadTestingReportSamplesNum - 1);

        ExecutorService threadPool = null;
        if (numberOfRuns > 1) {
            int threadNum = 100;
            if (numberOfRuns < 100)
                threadNum = numberOfRuns;

            threadPool = Executors.newFixedThreadPool(threadNum);
        }

        String runPrefix = "DRun-" + tContext.getIndex();

        boolean isLoadTestingEnabled = true;

        for (TestCase tc : tContext.getSimTestCases()) {
            tc.setIdentifierPrefix(runPrefix);
        }

        if (numberOfRuns == 1) {
            for (TestCase tc : tContext.getSimTestCases()) {
                tc.setSimulationNumber(1);
            }
        }

        ExecutorService reportingThreadPool = Executors.newFixedThreadPool(30);

        context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

        startTime = System.currentTimeMillis();

        List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();

        TestExecutionPercentile testPercentiles = new TestExecutionPercentile();

        TestExecutionPercentile runPercentiles = new TestExecutionPercentile();

        if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;

        while (tContext.getSimTestCases().size() > 0) {
            ReportHandler reportHandler = new ReportHandler(dContext.getNode(), runPrefix);

            Integer runNums = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
            if (context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
            }

            for (String relativeFileName : tContext.getRelativeFileNames()) {
                reportHandler.initializeResultsHolders(runNums, relativeFileName);
            }

            for (int i = 0; i < numberOfRuns; i++) {
                reportHandler.getFinalTestResults().put(runPrefix + "-" + (i + 1), new ConcurrentLinkedQueue<TestCaseReport>());
            }

            long suiteStartTime = isLoadTestingEnabled ? System.currentTimeMillis() : startTime;

            boolean dorep = false;

            boolean done = false;

            if (isLoadTestingEnabled) {
                long currentTime = System.currentTimeMillis();

                if (context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                    done = context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                    dorep = true;
                } else {
                    done = (currentTime - startTime) > configuration.getLoadTestingTime();

                    long elapsedFraction = (currentTime - startTime);

                    dorep = done || loadTstReportsCount == 0;

                    if (!dorep && elapsedFraction >= reportSampleTimeMs * loadTstReportsCount) {
                        dorep = true;
                    }
                }
                if (done) {
                    break;
                }
            } else {
                dorep = true;
                done = true;
            }

            if (numberOfRuns > 1) {
                List<Future> userSimulations =
                        doConcurrentRunExecution(false, numberOfRuns, tContext.getSimTestCases(), null, testCaseExecutorUtil, concurrentUserRampUpTimeMs, threadPool, dorep, reportHandler);

                concurrentUserRampUpTimeMs = 0;

                long currentTime = System.currentTimeMillis();
                String fileurl = "D" + tContext.getIndex() + "-" + currentTime + ".html";
                for (Future future : userSimulations) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                doAsyncDistributedConcReporting(reportHandler, suiteStartTime, fileurl, testPercentiles, runPercentiles, dorep, loadTestRunNum, loadTestResources, numberOfRuns, loadStats,
                        reportingThreadPool, userSimulations.size(), runPrefix, dContext, (System.currentTimeMillis() - suiteStartTime));

                if (dorep) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            } else {
                executeTestCases(tContext.getSimTestCases(), testCaseExecutorUtil, false, dorep, false, reportHandler);

                doAsyncDistributedReporting(reportHandler, suiteStartTime, testPercentiles, runPercentiles, dorep, loadTestRunNum, loadTestResources, loadStats, reportingThreadPool, tContext,
                        runPrefix, dContext, (System.currentTimeMillis() - suiteStartTime));

                if (dorep) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            }

            context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

            if (done) {
                break;
            }
        }

        if (numberOfRuns > 1) {
            threadPool.shutdown();
        }

        reportingThreadPool.shutdown();
        while (!reportingThreadPool.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (isLoadTestingEnabled) {
            loadStats.setExecutionTime(System.currentTimeMillis() - startTime);
            ReportHandler.doFinalLoadTestReport(runPrefix + "-", loadStats, context, null, null, loadTestResources);
        }

        if (loadStats != null) {
            logger.info(loadStats.show());
        }

        DistributedTestStatus finalStats = new DistributedTestStatus();
        finalStats.setNode(dContext.getNode());
        finalStats.setIdentifier(runPrefix);
        finalStats.setTestPercentileTimes(testPercentiles.getPercentileTimes());
        finalStats.setRunPercentileTimes(runPercentiles.getPercentileTimes());
        finalStats.setSuiteStats(loadStats);
        return finalStats;
    }

    public DistributedTestStatus handleDistributedSeleniumTests(DistributedAcceptanceContext dContext, List<Class<SeleniumTest>> classes, DistributedTestContext tContext) throws MojoFailureException {
        context = new AcceptanceTestContext(dContext, getClassLoader());
        context.handleTestDataSourcesAndHooks(dContext.getConfig().getGatfTestDataConfig());

        long concurrentUserRampUpTimeMs = 0;
        if (dContext.getConfig().getConcurrentUserSimulationNum() > 1 && dContext.getConfig().getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = dContext.getConfig().getConcurrentUserRampUpTime() / dContext.getConfig().getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = dContext.getConfig().getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;

        int numberOfRuns = tContext.getNumberOfRuns();

        int loadTstReportsCount = 0;

        String runPrefix = "DRun-" + tContext.getIndex();

        boolean isLoadTestingEnabled = true;

        startTime = System.currentTimeMillis();

        if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;


        int loadTestRunNum = 1;

        long reportSampleTimeMs = dContext.getConfig().getLoadTestingTime() / (loadTestingReportSamplesNum - 1);

        Map<String, Map<Integer, String>> indexes = new HashMap<String, Map<Integer, String>>();
        indexes.put(dContext.getNode(), new HashMap<Integer, String>());
        
        if (dContext.getConfig().isSeleniumExecutor()) {

            for (SeleniumDriverConfig selConf : dContext.getConfig().getSeleniumDriverConfigs()) {
                if (selConf.getDriverName() != null) {
                    System.setProperty(selConf.getDriverName(), selConf.getPath());
                }
            }
            System.setProperty("java.home", dContext.getConfig().getJavaHome());

            final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(dContext.getConfig());

            final List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
            for (Class<SeleniumTest> dynC : classes) {
                try {
                    tests.add(dynC.getConstructor(new Class[] {AcceptanceTestContext.class, int.class}).newInstance(new Object[] {context, 1}));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ExecutorService threadPool = null;
            if (numberOfRuns > 1) {
                threadPool = Executors.newFixedThreadPool(numberOfRuns);
            }
            context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

            if (isLoadTestingEnabled) {
                reportSampleTimeMs = context.getGatfExecutorConfig().getLoadTestingTime() / (loadTestingReportSamplesNum - 1);
            }

            if (!isLoadTestingEnabled && context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
                isLoadTestingEnabled = true;

            int runNum = 0;
            while (tests.size() > 0) {
                // long suiteStartTime = isLoadTestingEnabled?System.currentTimeMillis():startTime;

                boolean dorep = false;

                boolean done = false;

                // ReportHandler reportHandler = new ReportHandler(null, null);

                // Integer runNums =
                // context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
                if (context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                    // runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
                }

                if (isLoadTestingEnabled) {
                    long currentTime = System.currentTimeMillis();

                    if (context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                        done = context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                        dorep = true;
                    } else {
                        done = (currentTime - startTime) > context.getGatfExecutorConfig().getLoadTestingTime();

                        long elapsedFraction = (currentTime - startTime);

                        dorep = done || loadTstReportsCount == 0;

                        if (!dorep && elapsedFraction >= reportSampleTimeMs * loadTstReportsCount) {
                            dorep = true;
                        }
                    }
                    if (done) {
                        break;
                    }
                } else {
                    dorep = true;
                    done = true;
                }

                runNum++;

                Map<String, List<Map<String, Map<String, List<Object[]>>>>> summLstMap = new LinkedHashMap<String, List<Map<String, Map<String, List<Object[]>>>>>();

                summLstMap.put(dContext.getNode(), new ArrayList<Map<String, Map<String, List<Object[]>>>>());
                if (threadPool != null) {
                    List<FutureTask<Map<String, Map<String, List<Object[]>>>>> ltasks = new ArrayList<FutureTask<Map<String, Map<String, List<Object[]>>>>>();
                    for (int i = 0; i < numberOfRuns; i++) {
                        ltasks.add(new FutureTask<Map<String, Map<String, List<Object[]>>>>(
                                new ConcSeleniumTest(i - 1, context, tests, dContext.getSelTestdata(), lp, dorep, runNum, runPrefix, dContext.getNode())));
                        threadPool.execute(ltasks.get(i));
                        try {
                            Thread.sleep(concurrentUserRampUpTimeMs);
                        } catch (InterruptedException e) {
                        }
                    }
                    for (int i = 0; i < numberOfRuns; i++) {
                        try {
                            Map<String, Map<String, List<Object[]>>> o = ltasks.get(i).get();
                            summLstMap.get(dContext.getNode()).add(o);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (dorep && isLoadTestingEnabled) {
                        loadTestRunNum++;
                        loadTstReportsCount++;
                    }
                } else {
                    try {
                        summLstMap.get(dContext.getNode()).add(new ConcSeleniumTest(-1, context, tests, dContext.getSelTestdata(), lp, dorep, runNum, runPrefix, dContext.getNode()).call());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (dorep && isLoadTestingEnabled) {
                        loadTestRunNum++;
                        loadTstReportsCount++;
                    }
                }

                if (dorep) {
                    ReportHandler.doSeleniumSummaryTestReport(summLstMap, context, runNum, runPrefix);
                    indexes.get(dContext.getNode()).put(runNum, runPrefix + "-" + runNum + "-selenium-index.html");
                }

                context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

                if (done) {
                    break;
                }
            }
        }

        DistributedTestStatus finalStats = new DistributedTestStatus();
        finalStats.setNode(dContext.getNode());
        finalStats.setIdentifier(runPrefix);
        finalStats.setIndexes(indexes);
        return finalStats;
    }

    @SuppressWarnings("unused")
    private static class ConcSeleniumTestDT implements Callable<List<List<SeleniumTestSession>>> {
        private int index;
        private AcceptanceTestContext context;
        private List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        private LoggingPreferences lp;

        public ConcSeleniumTestDT(int index, AcceptanceTestContext context, List<SeleniumTest> tests, LoggingPreferences lp) {
            this.index = index;
            this.context = context;
            this.tests = tests;
            this.lp = lp;
        }

        @Override
        public List<List<SeleniumTestSession>> call() throws Exception {
            List<List<SeleniumTestSession>> lglist = new ArrayList<List<SeleniumTestSession>>();
            for (int i = 0; i < tests.size(); i++) {
                SeleniumTest dyn = tests.get(i).copy(context, index + 1);
                List<SeleniumTestSession> result = null;
                try {
                    result = dyn.execute(lp);
                    lglist.add(result);
                } catch (Throwable e) {
                    dyn.pushResult(new SeleniumTestResult(dyn, e));
                    lglist.add(dyn.get__sessions__());
                }
                dyn.quitAll();
            }
            return lglist;
        }
    }


    private void doAsyncDistributedConcReporting(final ReportHandler reportHandler, final long suiteStartTime, final String fileurl, final TestExecutionPercentile testPercentiles,
            final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum, final List<LoadTestResource> loadTestResources, final int numberOfRuns,
            final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final int concrunNos, final String runPrefix, final DistributedAcceptanceContext dContext,
            final long suiteExecTime) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                for (int y = 0; y < concrunNos; y++) {
                    try {
                        if (dorep) {
                            reportHandler.doConcurrentRunReporting(context, suiteStartTime, fileurl, (y + 1), loadTestRunNum == 1, testPercentiles, runPercentiles, runPrefix);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (dorep) {
                    TestSuiteStats stats = reportHandler.doReportingIndex(context, suiteStartTime, fileurl, numberOfRuns, runPrefix + "-", true);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            stats.setGroupStats(null);
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, fileurl, stats));
                    }
                    reportHandler.addToLoadTestResources(null, loadTestRunNum, fileurl, loadTestResources);
                    reportHandler.clearForLoadTests(context);
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, null, stats));
                    }
                }
            }
        });
    }

    private void doAsyncDistributedReporting(final ReportHandler reportHandler, final long suiteStartTime, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles,
            final boolean dorep, final Integer loadTestRunNum, final List<LoadTestResource> loadTestResources, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool,
            final DistributedTestContext tContext, final String runPrefix, final DistributedAcceptanceContext dContext, final long suiteExecTime) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                if (dorep) {
                    long currentTime = System.currentTimeMillis();
                    String fileurl = "D" + tContext.getIndex() + "-" + currentTime + ".html";
                    TestSuiteStats stats = reportHandler.doReporting(context, suiteStartTime, fileurl, runPrefix + "-", true, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    reportHandler.addToLoadTestResources(null, loadTestRunNum, fileurl, loadTestResources);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            stats.setGroupStats(null);
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, fileurl, stats));
                    }
                    reportHandler.clearForLoadTests(context);
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, null, stats));
                    }
                }
            }
        });
    }

    public static GatfExecutorConfig getConfig(InputStream resource) {
        XStream xstream = new XStream(new DomDriver("UTF-8"));
        XStream.setupDefaultSecurity(xstream);
        xstream.allowTypes(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
        xstream.processAnnotations(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
        xstream.alias("gatf-testdata-source", GatfTestDataSource.class);
        xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
        xstream.alias("gatf-testdata-source-hook", GatfTestDataSourceHook.class);
        xstream.alias("gatfTestDataConfig", GatfTestDataConfig.class);
        xstream.alias("seleniumDriverConfigs", SeleniumDriverConfig[].class);
        xstream.alias("seleniumDriverConfig", SeleniumDriverConfig.class);
        xstream.alias("args", String[].class);
        xstream.alias("arg", String.class);
        xstream.alias("testCaseHooksPaths", String[].class);
        xstream.alias("testCaseHooksPath", String.class);
        xstream.alias("queryStrs", String[].class);
        xstream.alias("queryStr", String.class);
        xstream.alias("distributedNodes", String[].class);
        xstream.alias("distributedNode", String.class);
        xstream.alias("ignoreFiles", String[].class);
        xstream.alias("ignoreFile", String.class);
        xstream.alias("orderedFiles", String[].class);
        xstream.alias("orderedFile", String.class);
        xstream.alias("string", String.class);
        xstream.alias("seleniumScripts", String[].class);
        xstream.alias("seleniumScript", String.class);

        GatfExecutorConfig configuration = (GatfExecutorConfig) xstream.fromXML(resource);
        configuration.setJavaVersion(System.getProperty("java.version"));
        return configuration;
    }

    public static String getConfigStr(GatfExecutorConfig configuration) {
        XStream xstream = new XStream(new DomDriver("UTF-8"));
        XStream.setupDefaultSecurity(xstream);
        xstream.allowTypes(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
        xstream.processAnnotations(new Class[] {GatfExecutorConfig.class, GatfTestDataConfig.class, GatfTestDataProvider.class, 
                SeleniumDriverConfig.class, GatfTestDataSourceHook.class, GatfTestDataSource.class});
        xstream.alias("gatf-testdata-source", GatfTestDataSource.class);
        xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
        xstream.alias("gatf-testdata-source-hook", GatfTestDataSourceHook.class);
        xstream.alias("gatfTestDataConfig", GatfTestDataConfig.class);
        xstream.alias("seleniumDriverConfigs", SeleniumDriverConfig[].class);
        xstream.alias("seleniumDriverConfig", SeleniumDriverConfig.class);
        xstream.alias("args", String[].class);
        xstream.alias("arg", String.class);
        xstream.alias("testCaseHooksPaths", String[].class);
        xstream.alias("testCaseHooksPath", String.class);
        xstream.alias("queryStrs", String[].class);
        xstream.alias("queryStr", String.class);
        xstream.alias("distributedNodes", String[].class);
        xstream.alias("distributedNode", String.class);
        xstream.alias("ignoreFiles", String[].class);
        xstream.alias("orderedFiles", String[].class);
        xstream.alias("string", String.class);
        xstream.alias("seleniumScripts", String[].class);
        xstream.alias("seleniumScript", String.class);

        return xstream.toXML(configuration);
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
}
