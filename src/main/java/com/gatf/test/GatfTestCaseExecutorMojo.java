package com.gatf.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.jackson.map.ObjectMapper;

import com.gatf.core.GatfExecutorConfig;
import com.gatf.report.TestCaseReport;
import com.gatf.report.TestCaseStats;
import com.gatf.report.TestGroupStats;
import com.gatf.report.TestSuiteStats;

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
	
	private Long startTime = 0L, endTime = 0L;
	
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

	public boolean isAuthEnabled() {
		return authEnabled;
	}

	public boolean isSoapAuthEnabled() {
		return soapAuthEnabled;
	}

	private void sortAndOrderTestCases(List<TestCase> allTestCases, GatfExecutorConfig configuration) {
		List<TestCase> pretestcases = new ArrayList<TestCase>();
		List<TestCase> posttestcases = new ArrayList<TestCase>();
		for (TestCase testCase : allTestCases) {
			if(testCase.getUrl().equalsIgnoreCase(getAuthUrl()) || configuration.isSoapAuthTestCase(testCase)) {
				pretestcases.add(testCase);
			}
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
	
	public void execute() throws MojoFailureException {
		
		GatfExecutorConfig configuration = new GatfExecutorConfig();
		configuration.setAuthEnabled(isAuthEnabled());
		configuration.setAuthExtractAuth(getAuthExtractAuth());
		configuration.setAuthUrl(getAuthUrl());
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
		
		AcceptanceTestContext context = new AcceptanceTestContext(configuration);
		try {
			context.validateAndInit();
		} catch (Exception e) {
			getLog().error(e);
			throw new MojoFailureException("Configuration is invalid", e);
		}
		
		TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(context);
		
		List<TestCase> allTestCases = getAllTestCases(context);
		sortAndOrderTestCases(allTestCases, configuration);
		
		startTime = System.currentTimeMillis();
		
		executeTestCases(allTestCases, testCaseExecutorUtil);
		
		doReporting(context);
	}
	
	private void executeTestCase(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) throws Exception
	{
		if(testCase.getNumberOfExecutions()!=null && testCase.getNumberOfExecutions()>1)
		{
			testCaseExecutorUtil.getContext().getPerformanceTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else if(testCase.getRepeatScenarios()==null || testCase.getRepeatScenarios().isEmpty())
		{
			testCaseExecutorUtil.getContext().getSingleTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else
		{
			testCaseExecutorUtil.getContext().getScenarioTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
	}
	
	private void executeTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil) {
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
				
				executeTestCase(testCase, testCaseExecutorUtil);
				
				getLog().info("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
				getLog().info("============================================================\n");
				
			} catch (Exception e) {
				getLog().error(e);
			} catch (Error e) {
				getLog().error(e);
			}
		}
	}
	
	public void doReporting(AcceptanceTestContext acontext)
	{
		if(isReportingEnabled())
		{
			TestSuiteStats testSuiteStats = new TestSuiteStats();
			
			int total = 0, failed = 0, totruns = 0;
			List<TestCaseStats> testCaseStats = new ArrayList<TestCaseStats>();
			Map<String, List<TestCaseReport>> allTestCases = new HashMap<String, List<TestCaseReport>>();
			for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry : acontext.getFinalTestResults().entrySet()) {
				try {
					List<TestCaseReport> reports = Arrays.asList(entry.getValue().toArray(new TestCaseReport[entry.getValue().size()]));
					Collections.sort(reports, new Comparator<TestCaseReport>() {
						public int compare(TestCaseReport o1, TestCaseReport o2) {
							if(o1.getStatus().equals("Failed") && o1.getStatus().equals(o2.getStatus())) {
								return 0;
							} else if(o1.getStatus().equals("Failed")) {
								return 1;
							} else {
								return -1;
							}
						}
					});
					
					int grptot = 0, grpfld = 0, grpruns = 0;
					long grpexecutionTime = 0;
					for (TestCaseReport testCaseReport : reports) {
						TestCaseStats tesStat = new TestCaseStats();
						tesStat.setSourceFileName(entry.getKey());
						tesStat.setWorkflowName(testCaseReport.getWorkflowName());
						tesStat.setTestCaseName(testCaseReport.getTestCase().getName());
						tesStat.setExecutionTime(testCaseReport.getExecutionTime());
						tesStat.setStatus("Success");
						total ++;
						grptot++;
						grpruns += testCaseReport.getNumberOfRuns();
						totruns += testCaseReport.getNumberOfRuns();
						if(!testCaseReport.getStatus().equals("Success")) {
							failed ++;
							grpfld ++;
							tesStat.setStatus("Failed");
						}
						testCaseStats.add(tesStat);
						grpexecutionTime += testCaseReport.getExecutionTime();
					}
					
					allTestCases.put(entry.getKey(), reports);
	        		
	        		TestGroupStats testGroupStats = new TestGroupStats();
					testGroupStats.setSourceFile(entry.getKey());
					testGroupStats.setExecutionTime(grpexecutionTime);
					testGroupStats.setTotalTestCount(grptot);
					testGroupStats.setFailedTestCount(grpfld);
					testGroupStats.setTotalRuns(grpruns);
					
					testSuiteStats.getGroupStats().add(testGroupStats);
	        		
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			VelocityContext context = new VelocityContext();
			try
			{
				String reportingJson = new ObjectMapper().writeValueAsString(allTestCases);
				context.put("testcaseReports", reportingJson);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			Collections.sort(testCaseStats, new Comparator<TestCaseStats>() {
				public int compare(TestCaseStats o1, TestCaseStats o2) {
					 return o1==null ?
					         (o2==null ? 0 : Integer.MAX_VALUE) :
					         (o2==null ? Integer.MIN_VALUE : o2.getExecutionTime().compareTo(o1.getExecutionTime()));
				}
			});
			
			try
			{
				String reportingJson = new ObjectMapper().writeValueAsString(testCaseStats);
				context.put("testcaseStats", reportingJson);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			endTime = System.currentTimeMillis();
			testSuiteStats.setTotalTestCount(total);
			testSuiteStats.setFailedTestCount(failed);
			testSuiteStats.setTotalRuns(totruns);
			testSuiteStats.setExecutionTime(endTime - startTime);
			
			try
			{
				String reportingJson = new ObjectMapper().writeValueAsString(testSuiteStats);
				context.put("suiteStats", reportingJson);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			try
			{
				InputStream resourcesIS = GatfTestCaseExecutorMojo.class.getResourceAsStream("/gatf-resources.zip");
	            if (resourcesIS != null)
	            {
	            	
	            	File basePath = null;
	            	if(getOutFilesBasePath()!=null)
	            		basePath = new File(getOutFilesBasePath());
	            	else
	            	{
	            		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
	            		basePath = new File(url.getPath());
	            	}
	            	File resource = new File(basePath, getOutFilesDir());
	                unzipZipFile(resourcesIS, resource.getAbsolutePath());
	                
	                VelocityEngine engine = new VelocityEngine();
	                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
	                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
	                engine.init();
	                
	                StringWriter writer = new StringWriter();
	                engine.mergeTemplate("/gatf-templates/index.vm", context, writer);

                    BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                            + SystemUtils.FILE_SEPARATOR + "index.html")));
                    fwriter.write(writer.toString());
                    fwriter.close();

	            }
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
     * @param zipFile
     * @param directoryToExtractTo Provides file unzip functionality
     */
    public void unzipZipFile(InputStream zipFile, String directoryToExtractTo)
    {
        ZipInputStream in = new ZipInputStream(zipFile);
        try
        {
            File directory = new File(directoryToExtractTo);
            if (!directory.exists())
            {
                directory.mkdirs();
                getLog().info("Creating directory for Extraction...");
            }
            ZipEntry entry = in.getNextEntry();
            while (entry != null)
            {
                try
                {
                    File file = new File(directory, entry.getName());
                    if (entry.isDirectory())
                    {
                        file.mkdirs();
                    }
                    else
                    {
                        FileOutputStream out = new FileOutputStream(file);
                        byte[] buffer = new byte[2048];
                        int len;
                        while ((len = in.read(buffer)) > 0)
                        {
                            out.write(buffer, 0, len);
                        }
                        out.close();
                    }
                    in.closeEntry();
                    entry = in.getNextEntry();
                }
                catch (Exception e)
                {
                	getLog().error(e);
                }
            }
        }
        catch (IOException ioe)
        {
        	getLog().error(ioe);
            return;
        }
    }
}
