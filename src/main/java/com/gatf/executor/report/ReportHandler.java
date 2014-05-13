package com.gatf.executor.report;

/*
Copyright 2013-2014, Sumeet Chhetri

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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.jackson.map.ObjectMapper;

import com.AlphanumComparator;
import com.gatf.distributed.DistributedTestStatus;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport.TestStatus;

/**
 * @author Sumeet Chhetri
 * The main report generation class which handles generation of all textual/graphical 
 * reporting after test suite execution
 */
public class ReportHandler {
	
	public ReportHandler(String node, String identifier) {
		if(node!=null && identifier!=null)
		{
			distributedTestStatus = new DistributedTestStatus();
			distributedTestStatus.setNode(node);
			distributedTestStatus.setIdentifier(identifier);
		}
	}
	
	private Logger logger = Logger.getLogger(ReportHandler.class.getSimpleName());
	
	private List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();
	
	private DistributedTestStatus distributedTestStatus = null;
	
	public DistributedTestStatus getDistributedTestStatus() {
		return distributedTestStatus;
	}

	private final StringBuffer reportLogsStr = new StringBuffer();
	
	public TestSuiteStats doReporting(AcceptanceTestContext acontext, long startTime, String reportFileName)
	{
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		TestSuiteStats testSuiteStats = new TestSuiteStats();
		
		int total = 0, failed = 0, totruns = 0, failruns = 0;
		List<TestCaseStats> testCaseStats = new ArrayList<TestCaseStats>();
		
		AlphanumComparator comparator = new AlphanumComparator();
		
		Map<String, List<TestCaseReport>> allTestCases = new TreeMap<String, List<TestCaseReport>>(comparator);
		Map<String, ConcurrentLinkedQueue<TestCaseReport>> tempMap = new TreeMap<String, ConcurrentLinkedQueue<TestCaseReport>>(comparator);
		tempMap.putAll(acontext.getFinalTestResults());
		
		Map<String, List<TestCaseCompareStatus>> compareStatuses = new TreeMap<String, List<TestCaseCompareStatus>>(comparator);
		
		Map<String, TestCaseReport> firstCompareCopy = null;
		if(acontext.getGatfExecutorConfig().getCompareBaseUrlsNum()!=null
				&& acontext.getGatfExecutorConfig().getCompareBaseUrlsNum()>1)
		{
			firstCompareCopy = new HashMap<String, TestCaseReport>();
		}
		
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  tempMap.entrySet()) {
			try {
				List<TestCaseReport> reports = Arrays.asList(entry.getValue().toArray(new TestCaseReport[entry.getValue().size()]));
				Collections.sort(reports, new Comparator<TestCaseReport>() {
					public int compare(TestCaseReport o1, TestCaseReport o2) {
						 return o1==null ?
						         (o2==null ? 0 : Integer.MIN_VALUE) :
						         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
					}
				});
				
				int grptot = 0, grpfld = 0, grpruns = 0, grpflruns = 0;
				long grpexecutionTime = 0;
				
				for (TestCaseReport testCaseReport : reports) {
					
					if(firstCompareCopy!=null) 
					{
						doCompare(testCaseReport, firstCompareCopy, compareStatuses, entry.getKey());
					}
					
					TestCaseStats tesStat = new TestCaseStats();
					tesStat.setIdentifier(entry.getKey());
					tesStat.setSourceFileName(testCaseReport.getTestCase().getSourcefileName());
					tesStat.setWorkflowName(testCaseReport.getWorkflowName());
					tesStat.setTestCaseName(testCaseReport.getTestCase().getName());
					if(testCaseReport.getAverageExecutionTime()!=null)
						tesStat.setExecutionTime(testCaseReport.getAverageExecutionTime());
					else
						tesStat.setExecutionTime(testCaseReport.getExecutionTime());
					tesStat.setStatus(TestStatus.Success.status);
					total ++;
					grptot++;
					if(testCaseReport.getNumberOfRuns()>1)
					{
						grpruns += testCaseReport.getNumberOfRuns();
						totruns += testCaseReport.getNumberOfRuns();
					}
					
					if(testCaseReport.getErrors()!=null && testCaseReport.getNumberOfRuns()>1)
					{
						grpflruns += testCaseReport.getErrors().size();
						failruns += testCaseReport.getErrors().size();
						if(testCaseReport.getError()!=null) {
							failruns += 1;
							grpflruns += 1;
						}
					}
					
					if(!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
						failed ++;
						grpfld ++;
						tesStat.setStatus(TestStatus.Failed.status);
					}
					testCaseStats.add(tesStat);
					if(testCaseReport.getTestCase().getHeaders()!=null)
					{
						StringBuilder build = new StringBuilder();
						for (Map.Entry<String, String> headerVal : testCaseReport.getTestCase().getHeaders().entrySet()) {
							build.append(headerVal.getKey());
							build.append(": ");
				            build.append(headerVal.getValue());
				            build.append("\n");
				            
				            if(headerVal.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
				            	testCaseReport.setRequestContentType(headerVal.getValue());
				            }
						}
						testCaseReport.setRequestHeaders(build.toString());
					}
					grpexecutionTime += testCaseReport.getExecutionTime();
				}
				
        		TestGroupStats testGroupStats = new TestGroupStats();
				testGroupStats.setSourceFile(entry.getKey());
				testGroupStats.setExecutionTime(grpexecutionTime);
				testGroupStats.setTotalTestCount(grptot);
				testGroupStats.setFailedTestCount(grpfld);
				testGroupStats.setTotalRuns(grpruns);
				testGroupStats.setFailedRuns(grpflruns);
				
				if(firstCompareCopy!=null)
				{
					testGroupStats.setBaseUrl(reports.get(0).getTestCase().getBaseUrl());
				}
				
				for (TestCaseReport testCaseReport : reports) {
					if(testCaseReport.getTestCase().getReportResponseContent()!=null
							&& !testCaseReport.getTestCase().getReportResponseContent())
						testCaseReport.setResponseContent(null);
					testCaseReport.setTestCase(null);
				}
				
				allTestCases.put(entry.getKey(), reports);
				
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
			
			context.put("userSimulation", false);
			if(config.getConcurrentUserSimulationNum()!=null && config.getConcurrentUserSimulationNum()>1) {
				context.put("userSimulation", true);
			}
			
			context.put("compareEnabled", false);
			if(firstCompareCopy!=null) {
				context.put("compareEnabled", true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testCaseStats);
			context.put("testcaseStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		long endTime = System.currentTimeMillis();
		testSuiteStats.setTotalTestCount(total);
		testSuiteStats.setFailedTestCount(failed);
		testSuiteStats.setTotalRuns(totruns);
		testSuiteStats.setFailedRuns(failruns);
		testSuiteStats.setExecutionTime(endTime - startTime);
		testSuiteStats.setTotalSuiteRuns(1);
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testSuiteStats);
			context.put("suiteStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(compareStatuses);
			context.put("compareStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			InputStream resourcesIS = GatfTestCaseExecutorMojo.class.getResourceAsStream("/gatf-resources.zip");
            if (resourcesIS != null)
            {
            	
            	File basePath = null;
            	if(config.getOutFilesBasePath()!=null)
            		basePath = new File(config.getOutFilesBasePath());
            	else
            	{
            		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
            		basePath = new File(url.getPath());
            	}
            	File resource = new File(basePath, config.getOutFilesDir());
                unzipZipFile(resourcesIS, resource.getAbsolutePath());
                
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index.vm", context, writer);

                if(reportFileName==null)
                	reportFileName = "index.html";
                
                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                        + SystemUtils.FILE_SEPARATOR + reportFileName)));
                fwriter.write(writer.toString());
                fwriter.close();
                
                if(distributedTestStatus!=null && distributedTestStatus.getReportFileContent().size()<5)
                {
                	distributedTestStatus.getReportFileContent().put(reportFileName, writer.toString());
                }
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
		acontext.clearTestResults();
		
		return testSuiteStats;
	}
	
	public void addToLoadTestResources(String prefix, int runNo, String url) {
		LoadTestResource resource = new LoadTestResource();
		if(prefix==null)
			prefix = "Run";
		resource.setTitle(prefix+"-"+runNo);
		resource.setUrl(url);
		loadTestResources.add(resource);
	}
	
	public void doFinalLoadTestReport(String prefix, TestSuiteStats testSuiteStats, AcceptanceTestContext acontext,
			List<String> nodes, List<String> nodeurls)
	{
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		VelocityContext context = new VelocityContext();
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testSuiteStats);
			context.put("suiteStats", reportingJson);
			
			if(nodes==null)
			{
				reportingJson = new ObjectMapper().writeValueAsString(loadTestResources);
				context.put("loadTestResources", reportingJson);
			}
			else
			{
				context.put("loadTestResources", "{}");
				context.put("nodes", nodes);
				context.put("nodeurls", nodeurls);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			File basePath = null;
        	if(config.getOutFilesBasePath()!=null)
        		basePath = new File(config.getOutFilesBasePath());
        	else
        	{
        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
        		basePath = new File(url.getPath());
        	}
        	File resource = new File(basePath, config.getOutFilesDir());
			
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/index-load.vm", context, writer);

            if(prefix==null)
            	prefix = "";
            
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                    + SystemUtils.FILE_SEPARATOR + prefix + "index.html")));
            fwriter.write(writer.toString());
            fwriter.close();
            
            if(distributedTestStatus!=null)
            {
            	distributedTestStatus.getReportFileContent().put(prefix + "index.html", writer.toString());
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void doFinalDsitributedLoadTestReport(AcceptanceTestContext acontext, List<TestSuiteStats> suiteStats, 
			List<List<LoadTestResource>> loadTestResources, List<String> nodes)
	{
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		VelocityContext context = new VelocityContext();
		context.put("suiteStats", suiteStats);
		context.put("loadTestResources", loadTestResources);
		context.put("nodes", nodes);
		
		try
		{
			File basePath = null;
        	if(config.getOutFilesBasePath()!=null)
        		basePath = new File(config.getOutFilesBasePath());
        	else
        	{
        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
        		basePath = new File(url.getPath());
        	}
        	File resource = new File(basePath, config.getOutFilesDir());
			
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/distributed-index-load.vm", context, writer);
            
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                    + SystemUtils.FILE_SEPARATOR + "index.html")));
            fwriter.write(writer.toString());
            fwriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public TestSuiteStats doLoadTestReporting(AcceptanceTestContext acontext, long startTime)
	{
		TestSuiteStats testSuiteStats = new TestSuiteStats();
		
		int total = 0, failed = 0, totruns = 0, failruns = 0;
		
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  acontext.getFinalTestResults().entrySet()) {
			try {
				List<TestCaseReport> reports = Arrays.asList(entry.getValue().toArray(new TestCaseReport[entry.getValue().size()]));
				
				for (TestCaseReport testCaseReport : reports) {
					
					total ++;
					if(testCaseReport.getNumberOfRuns()>1)
					{
						totruns += testCaseReport.getNumberOfRuns();
					}
					
					if(testCaseReport.getErrors()!=null && testCaseReport.getNumberOfRuns()>1)
					{
						failruns += testCaseReport.getErrors().size();
						if(testCaseReport.getError()!=null) {
							failruns += 1;
						}
					}
					
					if(!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
						failed ++;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		long endTime = System.currentTimeMillis();
		testSuiteStats.setTotalTestCount(total);
		testSuiteStats.setFailedTestCount(failed);
		testSuiteStats.setTotalRuns(totruns);
		testSuiteStats.setFailedRuns(failruns);
		testSuiteStats.setExecutionTime(endTime - startTime);
		testSuiteStats.setTotalSuiteRuns(1);
		
		acontext.clearTestResults();
		
		return testSuiteStats;
	}
	
	private void doCompare(TestCaseReport testCaseReport, 
			Map<String, TestCaseReport> firstCompareCopy, Map<String, List<TestCaseCompareStatus>> compareStatuses,
			String testCaseSuiteKey) 
	{
		List<TestCaseCompareStatus> compareStatusLst = null;
		TestCase tc = testCaseReport.getTestCase();
		if(tc.getSimulationNumber()==1 && !firstCompareCopy.containsKey("Run-1"+tc.getName()))
		{
			firstCompareCopy.put("Run-1"+tc.getName(), testCaseReport);
			compareStatusLst = new ArrayList<TestCaseCompareStatus>();
			compareStatuses.put(tc.getName(), compareStatusLst);
		}
		else if(tc.getSimulationNumber()>1)
		{
			compareStatusLst = compareStatuses.get(tc.getName());
			TestCaseReport testCaseReportRun1 = firstCompareCopy.get("Run-1"+tc.getName());
			TestCaseCompareStatus comStatus = new TestCaseCompareStatus();	
			comStatus.setIdentifer(testCaseReport.getTestIdentifier());
			comStatus.setTestCaseName(tc.getName());
			comStatus.setTestSuiteKey(testCaseSuiteKey);
			
			if(!testCaseReportRun1.getStatus().equals(testCaseReport.getStatus()))
			{
				comStatus.setCompareStatusError("FAILED_STATUS_CODE");
			}
			if(comStatus.getCompareStatusError()==null
					&& testCaseReportRun1.getError()!=null && !testCaseReportRun1.getError().trim().isEmpty()
					&& testCaseReport.getError()!=null && !testCaseReport.getError().trim().isEmpty()
					&& !testCaseReportRun1.getError().equals(testCaseReport.getError()))
			{
				comStatus.setCompareStatusError("FAILED_ERROR_DETAILS");
			}
			if(comStatus.getCompareStatusError()==null
					&& testCaseReportRun1.getErrorText()!=null && !testCaseReportRun1.getErrorText().trim().isEmpty()
					&& testCaseReport.getErrorText()!=null && !testCaseReport.getErrorText().trim().isEmpty()
					&& !testCaseReportRun1.getErrorText().equals(testCaseReport.getErrorText()))
			{
				comStatus.setCompareStatusError("FAILED_ERROR_CONTENT");
			}
			if(comStatus.getCompareStatusError()==null
					&& testCaseReportRun1.getResponseContentType()!=null 
					&& !testCaseReportRun1.getResponseContentType().trim().isEmpty()
					&& testCaseReport.getResponseContentType()!=null 
					&& !testCaseReport.getResponseContentType().trim().isEmpty()
					&& !testCaseReportRun1.getResponseContentType().equals(testCaseReport.getResponseContentType()))
			{
				comStatus.setCompareStatusError("FAILED_RESPONSE_TYPE");
			}
			if(comStatus.getCompareStatusError()==null
					&& testCaseReportRun1.getResponseContent()!=null && !testCaseReportRun1.getResponseContent().trim().isEmpty()
					&& testCaseReport.getResponseContent()!=null && !testCaseReport.getResponseContent().trim().isEmpty()
					&& !testCaseReportRun1.getResponseContent().equals(testCaseReport.getResponseContent()))
			{
				comStatus.setCompareStatusError("FAILED_RESPONSE_CONTENT");
			}
			
			if(comStatus.getCompareStatusError()==null)
			{
				comStatus.setCompareStatusError("SUCCESS");
			}
			
			compareStatusLst.add(comStatus);
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
                logger.info("Creating directory for Extraction...");
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
                	logger.severe(ExceptionUtils.getStackTrace(e));
                }
            }
        }
        catch (IOException ioe)
        {
        	logger.severe(ExceptionUtils.getStackTrace(ioe));
            return;
        }
    }
    
    public void doLog(String message)
    {
    	reportLogsStr.append(message);
    }
    
    public void doLog(String message, Throwable t)
    {
    	String cmsg = message + "\n" + ExceptionUtils.getStackTrace(t);
    	reportLogsStr.append(cmsg);
    }
    
    public void doLog(Throwable t)
    {
    	reportLogsStr.append(ExceptionUtils.getStackTrace(t));
    }
    
    public List<TestCaseReport> getReports(AcceptanceTestContext context)
    {
    	ConcurrentLinkedQueue<TestCaseReport> q = context.getFinalTestResults().values().iterator().next();
		List<TestCaseReport> reports = Arrays.asList(q.toArray(new TestCaseReport[q.size()]));
		Collections.sort(reports, new Comparator<TestCaseReport>() {
			public int compare(TestCaseReport o1, TestCaseReport o2) {
				 return o1==null ?
				         (o2==null ? 0 : Integer.MIN_VALUE) :
				         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
			}
		});
		return reports;
    }

	public List<LoadTestResource> getLoadTestResources() {
		return loadTestResources;
	}
	
	public void writeToReportFile(String fileName, String contents, GatfExecutorConfig config)
	{
		try
		{
			File basePath = null;
        	if(config.getOutFilesBasePath()!=null)
        		basePath = new File(config.getOutFilesBasePath());
        	else
        	{
        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
        		basePath = new File(url.getPath());
        	}
        	File resource = new File(basePath, config.getOutFilesDir());
			
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                    + SystemUtils.FILE_SEPARATOR + fileName)));
            fwriter.write(contents);
            fwriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
