package com.gatf.test.core;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.gatf.core.GatfExecutorConfig;
import com.gatf.report.ReportHandler;
import com.gatf.test.executor.TestCaseExecutorUtil;
import com.gatf.test.finder.CSVTestCaseFinder;
import com.gatf.test.finder.JSONTestCaseFinder;
import com.gatf.test.finder.TestCaseFinder;
import com.gatf.test.finder.XMLTestCaseFinder;

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
public class GatfTestCaseExecutorMojo extends AbstractMojo {

	@Component
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
	
	@Parameter(alias = "reportingEnabled")
	private boolean reportingEnabled;
	
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
	
	@Parameter(alias = "simulationUsersProviderName")
	private String simulationUsersProviderName;
	
	@Parameter(alias = "compareEnabled")
	private boolean compareEnabled;
	
	private Long startTime = 0L;
	
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getTestCasesBasePath() {
		return testCasesBasePath;
	}

	public void setTestCasesBasePath(String testCasesBasePath) {
		this.testCasesBasePath = testCasesBasePath;
	}

	public String getTestCaseDir() {
		return testCaseDir;
	}

	public void setTestCaseDir(String testCaseDir) {
		this.testCaseDir = testCaseDir;
	}

	public String getOutFilesBasePath() {
		return outFilesBasePath;
	}

	public void setOutFilesBasePath(String outFilesBasePath) {
		this.outFilesBasePath = outFilesBasePath;
	}

	public String getOutFilesDir() {
		return outFilesDir;
	}

	public void setOutFilesDir(String outFilesDir) {
		this.outFilesDir = outFilesDir;
	}

	public void setAuthEnabled(boolean authEnabled) {
		this.authEnabled = authEnabled;
	}

	public String getAuthUrl() {
		return authUrl;
	}

	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}

	public String getAuthExtractAuth() {
		return authExtractAuth;
	}

	public void setAuthExtractAuth(String authExtractAuth) {
		this.authExtractAuth = authExtractAuth;
	}

	public String getWsdlLocFile() {
		return wsdlLocFile;
	}

	public void setWsdlLocFile(String wsdlLocFile) {
		this.wsdlLocFile = wsdlLocFile;
	}

	public void setSoapAuthEnabled(boolean soapAuthEnabled) {
		this.soapAuthEnabled = soapAuthEnabled;
	}

	public String getSoapAuthWsdlKey() {
		return soapAuthWsdlKey;
	}

	public void setSoapAuthWsdlKey(String soapAuthWsdlKey) {
		this.soapAuthWsdlKey = soapAuthWsdlKey;
	}

	public String getSoapAuthOperation() {
		return soapAuthOperation;
	}

	public void setSoapAuthOperation(String soapAuthOperation) {
		this.soapAuthOperation = soapAuthOperation;
	}

	public String getSoapAuthExtractAuth() {
		return soapAuthExtractAuth;
	}

	public void setSoapAuthExtractAuth(String soapAuthExtractAuth) {
		this.soapAuthExtractAuth = soapAuthExtractAuth;
	}

	public boolean isReportingEnabled() {
		return reportingEnabled;
	}

	public void setReportingEnabled(boolean reportingEnabled) {
		this.reportingEnabled = reportingEnabled;
	}
	
	public Integer getNumConcurrentExecutions() {
		return numConcurrentExecutions;
	}

	public void setNumConcurrentExecutions(Integer numConcurrentExecutions) {
		this.numConcurrentExecutions = numConcurrentExecutions;
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}

	public boolean isHttpCompressionEnabled() {
		return httpCompressionEnabled;
	}

	public void setHttpCompressionEnabled(boolean httpCompressionEnabled) {
		this.httpCompressionEnabled = httpCompressionEnabled;
	}

	public Integer getHttpConnectionTimeout() {
		return httpConnectionTimeout;
	}

	public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
		this.httpConnectionTimeout = httpConnectionTimeout;
	}

	public Integer getHttpRequestTimeout() {
		return httpRequestTimeout;
	}

	public void setHttpRequestTimeout(Integer httpRequestTimeout) {
		this.httpRequestTimeout = httpRequestTimeout;
	}

	public Integer getConcurrentUserSimulationNum() {
		return concurrentUserSimulationNum;
	}

	public void setConcurrentUserSimulationNum(Integer concurrentUserSimulationNum) {
		this.concurrentUserSimulationNum = concurrentUserSimulationNum;
	}

	public String getTestDataConfigFile() {
		return testDataConfigFile;
	}

	public void setTestDataConfigFile(String testDataConfigFile) {
		this.testDataConfigFile = testDataConfigFile;
	}

	public String getSimulationUsersProviderName() {
		return simulationUsersProviderName;
	}

	public void setSimulationUsersProviderName(String simulationUsersProviderName) {
		this.simulationUsersProviderName = simulationUsersProviderName;
	}
	
	public String getAuthParamsDetails() {
		return authParamsDetails;
	}

	public void setAuthParamsDetails(String authParamsDetails) {
		this.authParamsDetails = authParamsDetails;
	}

	public boolean isAuthEnabled() {
		return authEnabled;
	}

	public boolean isSoapAuthEnabled() {
		return soapAuthEnabled;
	}
	
	public boolean isCompareEnabled() {
		return compareEnabled;
	}

	public void setCompareEnabled(boolean compareEnabled) {
		this.compareEnabled = compareEnabled;
	}

	private void sortAndOrderTestCases(List<TestCase> allTestCases, GatfExecutorConfig configuration) {
		List<TestCase> pretestcases = new ArrayList<TestCase>();
		List<TestCase> posttestcases = new ArrayList<TestCase>();
		for (TestCase testCase : allTestCases) {
			if(testCase.getUrl().equalsIgnoreCase(getAuthUrl()) || configuration.isSoapAuthTestCase(testCase)) {
				pretestcases.add(testCase);
			}
			testCase.setSimulationNumber(0);
		}
		for (TestCase testCase : allTestCases) {
			if(!testCase.getUrl().equalsIgnoreCase(getAuthUrl()) && !configuration.isSoapAuthTestCase(testCase)) {
				posttestcases.add(testCase);
			}
		}
		
		Collections.sort(posttestcases, new Comparator<TestCase>() {
			public int compare(TestCase o1, TestCase o2) {
				 return o1==null ?
				         (o2==null ? 0 : Integer.MIN_VALUE) :
				         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getSequence()).compareTo(o2.getSequence()));
			}
		});
		
		allTestCases.clear();
		allTestCases.addAll(pretestcases);
		allTestCases.addAll(posttestcases);
		
		int sequence = 0;
		for (TestCase testCase : allTestCases) {
			if(testCase.getSequence()<=0)
				testCase.setSequence(sequence++);
			else
				sequence = testCase.getSequence()+1;
		}
	}
	
	private List<TestCase> getAllTestCases(AcceptanceTestContext context)
	{
		List<TestCase> allTestCases = new ArrayList<TestCase>();
		File testCaseDirectory = context.getResourceFile(getTestCaseDir());
		
		TestCaseFinder finder = new XMLTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		finder = new JSONTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		finder = new CSVTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		return allTestCases;
	}
	
	public List<TestCase> copyTestCases(List<TestCase> testCases, Integer runNumber, String baseUrl)
	{
		List<TestCase> allTestCases = new ArrayList<TestCase>();
		for (TestCase testCase : testCases) {
			TestCase testCaseCopy = new TestCase(testCase);
			testCaseCopy.setSimulationNumber(runNumber);
			if(baseUrl!=null) {
				testCaseCopy.setBaseUrl(baseUrl);
			}
			allTestCases.add(testCaseCopy);
		}
		return allTestCases;
	}
	
	public void execute() throws MojoFailureException {
			
		GatfExecutorConfig configuration = new GatfExecutorConfig();
		configuration.setAuthEnabled(isAuthEnabled());
		configuration.setAuthExtractAuth(getAuthExtractAuth());
		configuration.setAuthUrl(getAuthUrl());
		configuration.setAuthParamsDetails(getAuthParamsDetails());
		configuration.setBaseUrl(getBaseUrl());
		configuration.setNumConcurrentExecutions(getNumConcurrentExecutions());
		configuration.setOutFilesBasePath(getOutFilesBasePath());
		configuration.setOutFilesDir(getOutFilesDir());
		configuration.setReportingEnabled(isReportingEnabled());
		configuration.setSoapAuthEnabled(isSoapAuthEnabled());
		configuration.setSoapAuthExtractAuth(getSoapAuthExtractAuth());
		configuration.setSoapAuthOperation(getSoapAuthOperation());
		configuration.setSoapAuthWsdlKey(getSoapAuthWsdlKey());
		configuration.setTestCaseDir(getTestCaseDir());
		configuration.setTestCasesBasePath(getTestCasesBasePath());
		configuration.setWsdlLocFile(getWsdlLocFile());
		configuration.setHttpCompressionEnabled(isHttpCompressionEnabled());
		configuration.setHttpConnectionTimeout(getHttpConnectionTimeout());
		configuration.setHttpRequestTimeout(getHttpRequestTimeout());
		configuration.setConcurrentUserSimulationNum(getConcurrentUserSimulationNum());
		configuration.setTestDataConfigFile(getTestDataConfigFile());
		configuration.setSimulationUsersProviderName(getSimulationUsersProviderName());
		configuration.setCompareEnabled(isCompareEnabled());
		
		AcceptanceTestContext context = new AcceptanceTestContext(configuration, getClassLoader());
		try {
			context.validateAndInit();
		} catch (Throwable e) {
			getLog().error(e);
			throw new MojoFailureException("Configuration is invalid", e);
		}
		
		final TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(context);
		
		boolean compareEnabledOnlySingleTestCaseExec = isCompareEnabled() && configuration.getGatfTestDataConfig()!=null 
				&& configuration.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null
				&& !configuration.getGatfTestDataConfig().getCompareEnvBaseUrls().isEmpty(); 
		
		Set<String> baseUrls = null;
		if(compareEnabledOnlySingleTestCaseExec) {
			baseUrls = new LinkedHashSet<String>();
			baseUrls.add(configuration.getBaseUrl());
			baseUrls.addAll(configuration.getGatfTestDataConfig().getCompareEnvBaseUrls());
			
			if(baseUrls.size()==1)
			{
				baseUrls = null;
				compareEnabledOnlySingleTestCaseExec = false;
			}
			else
			{
				configuration.setCompareBaseUrlsNum(baseUrls.size());
			}
		}
		
		List<TestCase> allTestCases = getAllTestCases(context);
		sortAndOrderTestCases(allTestCases, configuration);
		
		startTime = System.currentTimeMillis();
		
		int numberOfRuns = 0;
		if(compareEnabledOnlySingleTestCaseExec)
		{
			numberOfRuns = baseUrls.size();
		}
		else if(getConcurrentUserSimulationNum()!=null && getConcurrentUserSimulationNum()>1)
		{
			numberOfRuns = getConcurrentUserSimulationNum();
		}
		
		if(numberOfRuns>0)
		{
			final boolean onlySingleTestCaseExecl = compareEnabledOnlySingleTestCaseExec;
			ExecutorService threadPool = Executors.newFixedThreadPool(numberOfRuns);
			
			List<Future<Void>> userSimulations = new ArrayList<Future<Void>>();
			Iterator<String> baseUrlIter = null;
			if(baseUrls!=null)
			{
				baseUrlIter = baseUrls.iterator();
			}
			for (int i = 0; i < numberOfRuns; i++) {
				List<TestCase> simTestCases = null;
				if(onlySingleTestCaseExecl)
					simTestCases = copyTestCases(allTestCases, i+1, baseUrlIter.next());
				else
					simTestCases = copyTestCases(allTestCases, i+1, null);
					
				final List<TestCase> simTestCasesCopy = simTestCases;
				userSimulations.add(
						threadPool.submit(new Callable<Void>() {
							public Void call() throws Exception {
								executeTestCases(simTestCasesCopy, testCaseExecutorUtil, onlySingleTestCaseExecl);
								return null;
							}
						})
				);
			}
			
			boolean doneSimulation = false;
			while(!doneSimulation)
			{
				for (Future<Void> future : userSimulations) {
					if(future.isCancelled() || future.isDone()) {
						doneSimulation = true;
					} else {
						doneSimulation = false;
					}
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			executeTestCases(allTestCases, testCaseExecutorUtil, compareEnabledOnlySingleTestCaseExec);
		}
		
		new ReportHandler().doReporting(context, startTime);
	}
	
	private void executeTestCase(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil, 
			boolean onlySingleTestCaseExec) throws Exception
	{
		List<Map<String, String>> sceanrios = testCase.getRepeatScenarios();
		if(!onlySingleTestCaseExec) {
			if((sceanrios==null || sceanrios.isEmpty()) && testCase.getRepeatScenarioProviderName()!=null) {
				sceanrios = testCaseExecutorUtil.getContext().getProviderTestDataMap().get(testCase.getRepeatScenarioProviderName());
			}
			testCase.setRepeatScenarios(sceanrios);
		} else {
			testCase.setRepeatScenarios(null);
			testCase.setNumberOfExecutions(1);
		}
		
		if(testCase.getNumberOfExecutions()!=null && testCase.getNumberOfExecutions()>1)
		{
			testCaseExecutorUtil.getContext().getPerformanceTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else if(sceanrios==null || sceanrios.isEmpty())
		{
			testCaseExecutorUtil.getContext().getSingleTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else
		{
			testCaseExecutorUtil.getContext().getScenarioTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
	}
	
	private void executeTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil,
			boolean onlySingleTestCaseExec) {
		for (TestCase testCase : allTestCases) {
			try {
				
				getLog().info("Running acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
				if(testCase.isSkipTest())
				{
					getLog().info("Skipping acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
					return;
				}
				if(testCase.isDetailedLog())
				{
					getLog().info(testCase.toString());
				}
				try {
					testCase.validate(testCaseExecutorUtil.getContext().getHttpHeaders());
				} catch (RuntimeException e) {
					getLog().error("Got exception while running acceptance test " + testCase.getName()+"/"+testCase.getDescription(), e);
					throw e;
				}
				
				executeTestCase(testCase, testCaseExecutorUtil, onlySingleTestCaseExec);
				
				getLog().info("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
				getLog().info("============================================================\n");
				
			} catch (Exception e) {
				getLog().error(e);
			} catch (Error e) {
				getLog().error(e);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
    private ClassLoader getClassLoader()
    {
        try
        {
            List classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); i++)
            {
                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        }
        catch (Exception e)
        {
            getLog().error("Couldn't get the classloader.");
        }
        return getClass().getClassLoader();
    }
}