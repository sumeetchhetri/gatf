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
import com.gatf.executor.core.GatfTestCaseExecutorUtil.ConcSeleniumTest;

/**
 * G8 — distributed-worker execution (a -listener node running API or Selenium test batches sent by a
 * controller), moved verbatim from GatfTestCaseExecutorUtil. Executor state it reads/writes (context, fcl,
 * logger, startTime, tclgenerator) and the helpers it calls stay on the executor, reached via {@code gtcu}.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
class DistributedExecution {

    private final GatfTestCaseExecutorUtil gtcu;

    DistributedExecution(GatfTestCaseExecutorUtil gtcu) {
        this.gtcu = gtcu;
    }

    @SuppressWarnings("rawtypes")
    public DistributedTestStatus handleDistributedTests(DistributedAcceptanceContext dContext, DistributedTestContext tContext) {
        gtcu.context = new AcceptanceTestContext(dContext, gtcu.fcl.apply(null));
        gtcu.context.handleTestDataSourcesAndHooks(gtcu.context.getGatfExecutorConfig().getGatfTestDataConfig());

        GatfExecutorConfig configuration = gtcu.context.getGatfExecutorConfig();

        if (configuration.isGenerateExecutionLogs()) {
            gtcu.tclgenerator = new Thread(new TestCaseExecutionLogGenerator(configuration));
            gtcu.tclgenerator.start();
        }

        final TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(gtcu.context);

        long concurrentUserRampUpTimeMs = 0;
        if (configuration.getConcurrentUserSimulationNum() > 1 && configuration.getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime() / configuration.getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;
        if (loadTestingReportSamplesNum > 100)
        	loadTestingReportSamplesNum = 100;

        int numberOfRuns = tContext.getNumberOfRuns();

        int loadTstReportsCount = 0;

        TestSuiteStats loadStats = new TestSuiteStats();

        int loadTestRunNum = 1;

        long reportSampleTimeMs = configuration.getLoadTestingTime() / (loadTestingReportSamplesNum - 1);

        ExecutorService threadPool = null;
        if (numberOfRuns > 1) {
            // Virtual-thread-per-task executor: run every configured simulated user
            // concurrently instead of capping the load at 100 platform threads. Each
            // user-simulation thread only parks on a CompletableFuture (the HTTP call is
            // dispatched asynchronously via OkHttp's own Dispatcher), so the vthreads park
            // cleanly with no carrier pinning and cost ~nothing while idle.
            threadPool = Executors.newVirtualThreadPerTaskExecutor();
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

        ExecutorService reportingThreadPool = Executors.newFixedThreadPool(Integer.getInteger("gatf.reportingThreads", 30));

        gtcu.context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

        gtcu.startTime = System.currentTimeMillis();

        List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();

        TestExecutionPercentile testPercentiles = new TestExecutionPercentile();

        TestExecutionPercentile runPercentiles = new TestExecutionPercentile();

        if (!isLoadTestingEnabled && gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;
        
        String path = gtcu.initOutFolderAndGetPath(gtcu.context, System.nanoTime(), 0, "api");

        try {
            while (tContext.getSimTestCases().size() > 0) {
                ReportHandler reportHandler = new ReportHandler();

                Integer runNums = gtcu.context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
                if (gtcu.context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                    runNums = gtcu.context.getGatfExecutorConfig().getCompareBaseUrlsNum();
                }

                for (String relativeFileName : tContext.getRelativeFileNames()) {
                    reportHandler.initializeResultsHolders(runNums, relativeFileName);
                }

                for (int i = 0; i < numberOfRuns; i++) {
                    reportHandler.getFinalTestResults().put(runPrefix + "-" + (i + 1), new ConcurrentLinkedQueue<TestCaseReport>());
                }

                long suiteStartTime = isLoadTestingEnabled ? System.currentTimeMillis() : gtcu.startTime;

                boolean dorep = false;

                boolean done = false;

                if (isLoadTestingEnabled) {
                    long currentTime = System.currentTimeMillis();

                    if (gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                        done = gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                        dorep = true;
                    } else {
                        done = (currentTime - gtcu.startTime) > configuration.getLoadTestingTime();

                        long elapsedFraction = (currentTime - gtcu.startTime);

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
                    Date time = new Date();
                    List<Future> userSimulations =
                            gtcu.doConcurrentRunExecution(false, numberOfRuns, tContext.getSimTestCases(), null, testCaseExecutorUtil, concurrentUserRampUpTimeMs, threadPool, dorep, reportHandler);

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

                    doAsyncDistributedConcReporting(path, reportHandler, suiteStartTime, fileurl, testPercentiles, runPercentiles, dorep, loadTestRunNum, loadTestResources, numberOfRuns, loadStats,
                            reportingThreadPool, userSimulations.size(), runPrefix, dContext, (System.currentTimeMillis() - suiteStartTime), time);

                    if (dorep) {
                        loadTestRunNum++;
                        loadTstReportsCount++;
                    }
                } else {
                    Date time = new Date();
                    gtcu.executeTestCases(tContext.getSimTestCases(), testCaseExecutorUtil, false, dorep, false, reportHandler);

                    doAsyncDistributedReporting(path, reportHandler, suiteStartTime, testPercentiles, runPercentiles, dorep, loadTestRunNum, loadTestResources, loadStats, reportingThreadPool, tContext,
                            runPrefix, dContext, (System.currentTimeMillis() - suiteStartTime), time);

                    if (dorep) {
                        loadTestRunNum++;
                        loadTstReportsCount++;
                    }
                }

                gtcu.context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

                if (done) {
                    break;
                }
            }
        } finally {
            if (numberOfRuns > 1) {
                threadPool.shutdown();
            }

            reportingThreadPool.shutdown();
            try {
                reportingThreadPool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (isLoadTestingEnabled) {
            loadStats.setExecutionTime(System.currentTimeMillis() - gtcu.startTime);
            ReportHandler.doFinalLoadTestReport(path, runPrefix + "-", loadStats, gtcu.context, null, null, loadTestResources);
        }

        if (loadStats != null) {
            gtcu.logger.info(loadStats.show());
        }

        DistributedTestStatus finalStats = new DistributedTestStatus();
        finalStats.setNode(dContext.getNode());
        finalStats.setIdentifier(runPrefix);
        finalStats.setTestPercentileTimes(testPercentiles.getPercentileTimes());
        finalStats.setRunPercentileTimes(runPercentiles.getPercentileTimes());
        finalStats.setSuiteStats(loadStats);
        return finalStats;
    }

    public DistributedTestStatus handleDistributedSeleniumTests(DistributedAcceptanceContext dContext, List<Class<SeleniumTest>> classes, DistributedTestContext tContext) throws Exception {
        gtcu.context = new AcceptanceTestContext(dContext, gtcu.fcl.apply(null));
        gtcu.context.handleTestDataSourcesAndHooks(dContext.getConfig().getGatfTestDataConfig());

        long concurrentUserRampUpTimeMs = 0;
        if (dContext.getConfig().getConcurrentUserSimulationNum() > 1 && dContext.getConfig().getConcurrentUserRampUpTime() > 0)
            concurrentUserRampUpTimeMs = dContext.getConfig().getConcurrentUserRampUpTime() / dContext.getConfig().getConcurrentUserSimulationNum();

        int loadTestingReportSamplesNum = dContext.getConfig().getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;
        if (loadTestingReportSamplesNum > 100)
        	loadTestingReportSamplesNum = 100;

        int numberOfRuns = tContext.getNumberOfRuns();

        int loadTstReportsCount = 0;

        String runPrefix = "DRun-" + tContext.getIndex();

        boolean isLoadTestingEnabled = true;

        gtcu.startTime = System.currentTimeMillis();

        if (!isLoadTestingEnabled && gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;

        int loadTestRunNum = 1;

        long reportSampleTimeMs = dContext.getConfig().getLoadTestingTime() / (loadTestingReportSamplesNum - 1);

        Map<String, Map<Integer, String>> indexes = new HashMap<String, Map<Integer, String>>();
        indexes.put(dContext.getNode(), new HashMap<Integer, String>());
        
        for (SeleniumDriverConfig selConf : dContext.getConfig().getSeleniumDriverConfigs()) {
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
        
        if(dContext.getConfig().getExtraProperties()!=null) {
        	if(dContext.getConfig().getExtraProperties().containsKey("jdk.tls.client.protocols")) {
        		System.setProperty("jdk.tls.client.protocols", dContext.getConfig().getExtraProperties().get("jdk.tls.client.protocols"));
        	} else if(dContext.getConfig().getExtraProperties().containsKey("crypto.policy")) {
        		System.setProperty("crypto.policy", dContext.getConfig().getExtraProperties().get("crypto.policy"));
        	} else if(dContext.getConfig().getExtraProperties().containsKey("webdriver.http.factory")) {
        		System.setProperty("webdriver.http.factory", dContext.getConfig().getExtraProperties().get("webdriver.http.factory"));
        	} else if(dContext.getConfig().getExtraProperties().containsKey("jna.library.path")) {
        		System.setProperty("jna.library.path", dContext.getConfig().getExtraProperties().get("jna.library.path"));
        	}
        }

        try {
            SeleniumCodeGeneratorAndUtil.clean();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if(dContext.getConfig().isSeleniumModuleTests()) {
        	File mdir = gtcu.context.getResourceFile(dContext.getConfig().getTestCaseDir());
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
        	dContext.getConfig().setSeleniumScripts(modules.toArray(new String[modules.size()]));
        }

        final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(dContext.getConfig());

        final List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
        for (Class<SeleniumTest> dynC : classes) {
            try {
                tests.add(dynC.getConstructor(new Class[] {AcceptanceTestContext.class, int.class}).newInstance(new Object[] {gtcu.context, 1}));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ExecutorService threadPool = null;
        boolean concExec = false;
        if (numberOfRuns > 1) {
            int threadNum = 100;
            if (numberOfRuns < 100)
                threadNum = numberOfRuns;

            threadPool = Executors.newFixedThreadPool(threadNum);
            gtcu.context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
        } else if(gtcu.context.getGatfExecutorConfig().getNumConcurrentExecutions()>1 && tests.size()>1) {
        	//Parallel selenium execution of multiple scripts
        	int threadNum = gtcu.context.getGatfExecutorConfig().getNumConcurrentExecutions();
        	if (threadNum > 10)
                threadNum = 10;
        	threadPool = Executors.newFixedThreadPool(threadNum);
        	numberOfRuns = 1;
        	concExec = true;
        	gtcu.context.getWorkflowContextHandler().initializeSuiteContext(tests.size());
        } else {
        	gtcu.context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
        }

        if (isLoadTestingEnabled) {
            reportSampleTimeMs = gtcu.context.getGatfExecutorConfig().getLoadTestingTime() / (loadTestingReportSamplesNum - 1);
        }

        if (!isLoadTestingEnabled && gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1)
            isLoadTestingEnabled = true;
        
        Long sessionId = System.nanoTime();
        String path = gtcu.initOutFolderAndGetPath(gtcu.context, sessionId, 0, "sel");

        List<GatfRunTimeError> allErrors = new ArrayList<>();
        int runNum = 0;
        while (tests.size() > 0) {
            // long suiteStartTime = isLoadTestingEnabled?System.currentTimeMillis():startTime;

            boolean dorep = false;

            boolean done = false;

            // ReportHandler reportHandler = new ReportHandler(null, null);

            // Integer runNums =
            // context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
            if (gtcu.context.getGatfExecutorConfig().getCompareBaseUrlsNum() != null) {
                // runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
            }

            if (isLoadTestingEnabled) {
                long currentTime = System.currentTimeMillis();

                if (gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() > 1) {
                    done = gtcu.context.getGatfExecutorConfig().getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                    dorep = true;
                } else {
                    done = (currentTime - gtcu.startTime) > gtcu.context.getGatfExecutorConfig().getLoadTestingTime();

                    long elapsedFraction = (currentTime - gtcu.startTime);

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
            Map<String, List<Map<String, Map<String, Integer[]>>>> sinfoMap = new LinkedHashMap<>();
            List<ImmutablePair<Method, Object[]>> reportLst = new ArrayList<>();
            List<String> failedFiles = Collections.synchronizedList(new ArrayList<>());
            summLstMap.put(dContext.getNode(), new ArrayList<>());
            sinfoMap.put(dContext.getNode(), new ArrayList<>());
            if (threadPool != null) {
            	List<FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>> ltasks = new ArrayList<>();
            	if(!concExec) {
                    for (int i = 0; i < numberOfRuns; i++) {
                        ltasks.add(new FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>(
                                new ConcSeleniumTest(sessionId, path, i, -1, gtcu.context, tests, dContext.getSelTestdata(), lp, dorep, isLoadTestingEnabled, runNum, runPrefix, dContext.getNode(), failedFiles)));
                        threadPool.execute(ltasks.get(i));
                        try {
                            Thread.sleep(concurrentUserRampUpTimeMs);
                        } catch (InterruptedException e) {
                        }
                    }
                    for (int i = 0; i < numberOfRuns; i++) {
                        try {
                        	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = ltasks.get(i).get();
                            summLstMap.get(dContext.getNode()).add(o.getLeft());
                            reportLst.addAll(o.getRight().getLeft());
                            sinfoMap.get(dContext.getNode()).add(o.getRight().getRight());
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
                		ctestdata.add(dContext.getSelTestdata().get(i));
                        ltasks.add(new FutureTask<ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>>>(
                        		new ConcSeleniumTest(sessionId, path, 0, i, gtcu.context, ctests, ctestdata, lp, dorep, isLoadTestingEnabled, runNum, runPrefix, dContext.getNode(), failedFiles)));
                        threadPool.execute(ltasks.get(i));
                    }
                    for (int i = 0; i < tests.size(); i++) {
                        try {
                        	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = ltasks.get(i).get();
                            summLstMap.get(dContext.getNode()).add(o.getLeft());
                            reportLst.addAll(o.getRight().getLeft());
                            sinfoMap.get(dContext.getNode()).add(o.getRight().getRight());
                            if(o.getMiddle()!=null && o.getMiddle().size()>0) {
                            	allErrors.addAll(o.getMiddle());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            } else {
                try {
                	ImmutableTriple<Map<String, Map<String, List<Object[]>>>, List<GatfRunTimeError>, ImmutablePair<List<ImmutablePair<Method, Object[]>>, Map<String, Map<String, Integer[]>>>> o = 
                			new ConcSeleniumTest(sessionId, path, 0, -1, gtcu.context, tests, dContext.getSelTestdata(), lp, dorep, isLoadTestingEnabled, runNum, runPrefix, dContext.getNode(), failedFiles).call();
                    summLstMap.get(dContext.getNode()).add(o.getLeft());
                    reportLst.addAll(o.getRight().getLeft());
                    sinfoMap.get(dContext.getNode()).add(o.getRight().getRight());
                    if(o.getMiddle()!=null && o.getMiddle().size()>0) {
                    	allErrors.addAll(o.getMiddle());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (dorep && isLoadTestingEnabled) {
                    loadTestRunNum++;
                    loadTstReportsCount++;
                }
            }

            if (dorep) {
            	if(!isLoadTestingEnabled) {
            		try {
		            	String pdfReport = gtcu.context.getOutDir().getAbsolutePath() + File.separator + path + File.separator + (runPrefix + "-" + (runNum) + "-report " + System.currentTimeMillis());
		                String csvReport = pdfReport + ".csv";
		                pdfReport += ".pdf";
		            	Document document = new Document(new PdfDocument(new PdfWriter(pdfReport)));
		                CSVWriter csvdoc = new CSVWriter(new FileWriter(csvReport), ',', '"', '\\', "\n");
		                Table table = null;
		                for (ImmutablePair<Method, Object[]> p : reportLst) {
							switch (p.getLeft().getName()) {
								case "start":
									p.getRight()[p.getRight().length-2] = document;
									p.getRight()[p.getRight().length-1] = csvdoc;
									table = (Table)p.getLeft().invoke(null, p.getRight());
									break;
								case "addSubTest":
									p.getRight()[p.getRight().length-3] = table;
									p.getRight()[p.getRight().length-2] = document;
									p.getRight()[p.getRight().length-1] = csvdoc;
									table = (Table)p.getLeft().invoke(null, p.getRight());
									break;
								case "addErrorDetails":
									if(table!=null) {
										table.setMarginBottom(30.0f);
						                document.add(table);
						                table = null;
									}
									p.getRight()[p.getRight().length-1] = document;
									table = (Table)p.getLeft().invoke(null, p.getRight());
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
            	
                ReportHandler.doSeleniumSummaryTestReport(sessionId, gtcu.context.getGatfExecutorConfig().getRetryCounter(), path, summLstMap, sinfoMap, gtcu.context, runNum, runPrefix);
                indexes.get(dContext.getNode()).put(runNum, runPrefix + "-" + runNum + "-selenium-index.html");
            }

            gtcu.context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);

            if (done) {
                break;
            }
        }

        DistributedTestStatus finalStats = new DistributedTestStatus();
        finalStats.setNode(dContext.getNode());
        finalStats.setIdentifier(runPrefix);
        finalStats.setIndexes(indexes);
        return finalStats;
    }

    private void doAsyncDistributedConcReporting(String path, final ReportHandler reportHandler, final long suiteStartTime, final String fileurl, final TestExecutionPercentile testPercentiles,
            final TestExecutionPercentile runPercentiles, final boolean dorep, final Integer loadTestRunNum, final List<LoadTestResource> loadTestResources, final int numberOfRuns,
            final TestSuiteStats loadStats, final ExecutorService reportingThreadPool, final int concrunNos, final String runPrefix, final DistributedAcceptanceContext dContext,
            final long suiteExecTime, Date time) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                for (int y = 0; y < concrunNos; y++) {
                    try {
                        if (dorep) {
                            reportHandler.doConcurrentRunReporting(path, gtcu.context, suiteStartTime, fileurl, (y + 1), loadTestRunNum == 1, testPercentiles, runPercentiles, runPrefix);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (dorep) {
                    TestSuiteStats stats = reportHandler.doReportingIndex(path, gtcu.context, suiteStartTime, fileurl, numberOfRuns, runPrefix + "-", true);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            stats.setGroupStats(null);
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, fileurl, stats, time));
                    }
                    reportHandler.addToLoadTestResources(null, loadTestRunNum, fileurl, loadTestResources);
                    reportHandler.clearForLoadTests(gtcu.context);
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(path, gtcu.context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, null, stats, time));
                    }
                }
            }
        });
    }

    private void doAsyncDistributedReporting(String path, final ReportHandler reportHandler, final long suiteStartTime, final TestExecutionPercentile testPercentiles, final TestExecutionPercentile runPercentiles,
            final boolean dorep, final Integer loadTestRunNum, final List<LoadTestResource> loadTestResources, final TestSuiteStats loadStats, final ExecutorService reportingThreadPool,
            final DistributedTestContext tContext, final String runPrefix, final DistributedAcceptanceContext dContext, final long suiteExecTime, Date time) {
        reportingThreadPool.execute(new Runnable() {
            public void run() {
                if (dorep) {
                    long currentTime = System.currentTimeMillis();
                    String fileurl = "D" + tContext.getIndex() + "-" + currentTime + ".html";
                    TestSuiteStats stats = reportHandler.doReporting(path, gtcu.context, suiteStartTime, fileurl, runPrefix + "-", true, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    reportHandler.addToLoadTestResources(null, loadTestRunNum, fileurl, loadTestResources);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            stats.setGroupStats(null);
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, fileurl, stats, time));
                    }
                    reportHandler.clearForLoadTests(gtcu.context);
                } else {
                    TestSuiteStats stats = reportHandler.doLoadTestReporting(path, gtcu.context, suiteStartTime, testPercentiles, runPercentiles);
                    stats.setExecutionTime(suiteExecTime);
                    synchronized (loadStats) {
                        if (loadStats.getExecutionTime() == 0) {
                            loadStats.copy(stats);
                        } else {
                            loadStats.updateStats(stats, false);
                        }
                        RuntimeReportUtil.addLEntry(new LoadTestEntry(dContext.getNode(), null, loadTestRunNum, null, stats, time));
                    }
                }
            }
        });
    }
}
