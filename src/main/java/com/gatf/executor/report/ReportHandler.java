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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
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
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.jackson.map.ObjectMapper;
import org.custommonkey.xmlunit.XMLAssert;
import org.skyscreamer.jsonassert.JSONAssert;

import com.AlphanumComparator;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.distributed.DistributedTestStatus;
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
	
	private static Logger logger = Logger.getLogger(ReportHandler.class.getSimpleName());
	
	private List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();
	
	private DistributedTestStatus distributedTestStatus = null;
	
	private List<TestCaseStats> testCaseStats = new ArrayList<TestCaseStats>();
	
	private TestSuiteStats testSuiteStats = new TestSuiteStats();
	
	private TestExecutionPercentile testPercentiles = new TestExecutionPercentile();
	
	private TestExecutionPercentile runPercentiles = new TestExecutionPercentile();
	
	public DistributedTestStatus getDistributedTestStatus() {
		return distributedTestStatus;
	}

	public void clearForLoadTests(AcceptanceTestContext context)
	{
		testCaseStats = new ArrayList<TestCaseStats>();
		testSuiteStats = new TestSuiteStats();
		context.clearTestResults();
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
            	//distributedTestStatus.getReportFileContent().put(prefix + "index.html", writer.toString());
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void doFinalDistributedLoadTestReport(AcceptanceTestContext acontext, List<TestSuiteStats> suiteStats, 
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
		
		int total = 0, failed = 0, skipped = 0, totruns = 0, failruns = 0;
		long grpexecutionTime = 0L;
		
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
					
					if(testCaseReport.getStatus().equals(TestStatus.Skipped.status)) {
						skipped ++;
					} else if(!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
						failed ++;
					}
					
					grpexecutionTime += testCaseReport.getExecutionTime();
					
					testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		long endTime = System.currentTimeMillis();
		testSuiteStats.setTotalTestCount(total);
		testSuiteStats.setFailedTestCount(failed);
		testSuiteStats.setSkippedTestCount(skipped);
		testSuiteStats.setTotalRuns(totruns);
		testSuiteStats.setFailedRuns(failruns);
		testSuiteStats.setExecutionTime(endTime - startTime);
		testSuiteStats.setTotalSuiteRuns(1);
		testSuiteStats.setActualExecutionTime(grpexecutionTime);
		
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
				boolean eq = compareText(testCaseReport.getResponseContentType(), testCaseReport.getErrorText(), 
							testCaseReportRun1.getErrorText());
				if(!eq)
				{
					comStatus.setCompareStatusError("FAILED_ERROR_CONTENT");
				}
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
				boolean eq = compareText(testCaseReport.getResponseContentType(), testCaseReport.getResponseContent(), 
						testCaseReportRun1.getResponseContent());
				if(!eq)
				{
					comStatus.setCompareStatusError("FAILED_RESPONSE_CONTENT");
				}
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
    public static void unzipZipFile(InputStream zipFile, String directoryToExtractTo)
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
    
    /**
     * @param zipFile
     * @param directoryToExtractTo Provides file unzip functionality
     */
    public static void zipDirectory(File directory, final String fileFilter, String zipFileName)
    {
        try
        {
            if (!directory.exists() || !directory.isDirectory())
            {
                directory.mkdirs();
                logger.info("Invalid Directory provided for zipping...");
                return;
            }
            File zipFile = new File(directory, zipFileName);
            FileOutputStream fos = new FileOutputStream(zipFile);
        	ZipOutputStream zos = new ZipOutputStream (fos);
        	
        	File[] files = directory.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(fileFilter);
				}
			});
        	
        	for (File file : files) {
        		FileInputStream fis = new FileInputStream(file);
        		ZipEntry zipEntry = new ZipEntry(file.getName());
        		zos.putNextEntry(zipEntry);

        		byte[] bytes = new byte[1024];
        		int length;
        		while ((length = fis.read(bytes)) >= 0) {
        			zos.write(bytes, 0, length);
        		}

        		zos.closeEntry();
        		fis.close();
			}
        	
            zos.close();
			fos.close();
        }
        catch (IOException ioe)
        {
        	logger.severe(ExceptionUtils.getStackTrace(ioe));
            return;
        }
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
	
	private boolean compareText(String type, String lhs, String rhs)
	{
		if(MediaType.APPLICATION_JSON.equalsIgnoreCase(type))
		{
			try {
				JSONAssert.assertEquals(lhs, rhs,false);
				return true;
			} catch (Exception e) {
			} catch (AssertionError e) {
			}
		}
		else if(MediaType.APPLICATION_XML.equalsIgnoreCase(type) || MediaType.TEXT_XML.equalsIgnoreCase(type))
		{
			try {
				XMLAssert.assertXMLEqual(lhs, rhs);
				return true;
			} catch (Exception e) {
			} catch (AssertionError e) {
			}
		}
		return false;
	}

	public void doConcurrentRunReporting(AcceptanceTestContext acontext, long startTime, String reportFileName, int runNumber,
			int loadTestRunNum) {
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		int total = 0, failed = 0, totruns = 0, failruns = 0, skipped = 0;
		
		AlphanumComparator<String> comparator = new AlphanumComparator<String>();
		
		Map<String, List<TestCaseReport>> allTestCases = new TreeMap<String, List<TestCaseReport>>(comparator);
		Map<String, ConcurrentLinkedQueue<TestCaseReport>> tempMap = new TreeMap<String, ConcurrentLinkedQueue<TestCaseReport>>(comparator);
		
		tempMap.put("Run-"+ runNumber, acontext.getFinalTestResults().get("Run-"+ runNumber));
		
		Map<String, List<TestCaseCompareStatus>> compareStatuses = new TreeMap<String, List<TestCaseCompareStatus>>(comparator);
		
		Map<String, TestCaseReport> firstCompareCopy = null;
		if(acontext.getGatfExecutorConfig().getCompareBaseUrlsNum()!=null
				&& acontext.getGatfExecutorConfig().getCompareBaseUrlsNum()>1)
		{
			firstCompareCopy = new HashMap<String, TestCaseReport>();
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
		
		String orf = reportFileName;
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  tempMap.entrySet()) {
			reportFileName = orf;
			try {
				List<TestCaseReport> reports = new ArrayList<TestCaseReport>(entry.getValue());
				Collections.sort(reports, new Comparator<TestCaseReport>() {
					public int compare(TestCaseReport o1, TestCaseReport o2) {
						 return o1==null ?
						         (o2==null ? 0 : Integer.MIN_VALUE) :
						         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
					}
				});
				
				int grptot = 0, grpfld = 0, grpskp = 0, grpruns = 0, grpflruns = 0;
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
					
					if(testCaseReport.getStatus().equals(TestStatus.Skipped.status)) {
						skipped ++;
						grpskp ++;
						tesStat.setStatus(TestStatus.Skipped.status);
					} else if(!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
						failed ++;
						grpfld ++;
						tesStat.setStatus(TestStatus.Failed.status);
					}
					testCaseStats.add(tesStat);
					populateRequestResponseHeaders(testCaseReport);
					grpexecutionTime += testCaseReport.getExecutionTime();
					
					testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
				}
				
        		TestGroupStats testGroupStats = new TestGroupStats();
				testGroupStats.setSourceFile(entry.getKey());
				testGroupStats.setExecutionTime(grpexecutionTime);
				testGroupStats.setTotalTestCount(grptot);
				testGroupStats.setFailedTestCount(grpfld);
				testGroupStats.setSkippedTestCount(grpskp);
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
				testSuiteStats.setActualExecutionTime(testSuiteStats.getActualExecutionTime() + grpexecutionTime);
				
				if(firstCompareCopy==null) {
					try
					{
						String reportingJson = new ObjectMapper().writeValueAsString(allTestCases);
						context.put("testcaseReports", reportingJson);
						allTestCases.clear();
						context.put("compareStats", "{}");
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
			                if(runNumber==1 && loadTestRunNum==1)
			                	unzipZipFile(resourcesIS, resource.getAbsolutePath());
			                
			                VelocityEngine engine = new VelocityEngine();
			                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
			                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
			                engine.init();
			                
			                StringWriter writer = new StringWriter();
			                engine.mergeTemplate("/gatf-templates/index-run.vm", context, writer);

			                if(reportFileName==null)
			                	reportFileName = "index.html";
			                
			                if(entry.getKey().startsWith("Run") && !entry.getKey().endsWith(".xml")) {
			                	reportFileName = reportFileName.replaceFirst("\\.html", "");
			                	reportFileName += entry.getKey().substring(4);
			                	reportFileName += ".html";
			                } else if(!reportFileName.equals("index.html")) {
			                	reportFileName = reportFileName.replaceFirst("\\.html", "");
			                	reportFileName += "1.html";
			                } else {
			                	reportFileName = "index1.html";
			                }
			                
			                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
			                        + SystemUtils.FILE_SEPARATOR + reportFileName)));
			                fwriter.write(writer.toString());
			                fwriter.close();
			                
			                /*if(distributedTestStatus!=null && distributedTestStatus.getReportFileContent().size()<5)
			                {
			                	distributedTestStatus.getReportFileContent().put(reportFileName, writer.toString());
			                }*/
			            }
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
        		
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if(testSuiteStats.getTotalSuiteRuns()==0) {
			testSuiteStats.setTotalTestCount(total);
			testSuiteStats.setFailedTestCount(failed);
			testSuiteStats.setSkippedTestCount(skipped);
			testSuiteStats.setTotalRuns(totruns);
			testSuiteStats.setFailedRuns(failruns);
			testSuiteStats.setTotalUserSuiteRuns(1);
			testSuiteStats.setTotalSuiteRuns(1);
		} else {
			TestSuiteStats tempst = new TestSuiteStats();
			tempst.setTotalTestCount(total);
			tempst.setFailedTestCount(failed);
			tempst.setSkippedTestCount(skipped);
			tempst.setTotalRuns(totruns);
			tempst.setFailedRuns(failruns);
			tempst.setTotalSuiteRuns(1);
			testSuiteStats.updateStats(tempst, true);
		}
		
		acontext.getFinalTestResults().get("Run-"+ runNumber).clear();
	}

	public TestSuiteStats doReportingIndex(AcceptanceTestContext acontext, long suiteStartTime, String reportFileName, 
			int numberOfRuns, String prefix, boolean isLoadTestingEnabled) {
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		
		VelocityContext context = new VelocityContext();
		try
		{
			context.put("userSimulation", false);
			if(config.getConcurrentUserSimulationNum()!=null && config.getConcurrentUserSimulationNum()>1) {
				context.put("userSimulation", true);
			}
			
			context.put("compareEnabled", false);
		} catch (Exception e) {
			e.printStackTrace();
		}

		context.put("testcaseReports", "[]");
		context.put("isMultipleRuns", acontext.getGatfExecutorConfig().getConcurrentUserSimulationNum()>1);
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testCaseStats);
			context.put("testcaseStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			testSuiteStats.setTotalUserSuiteRuns(numberOfRuns);
			testSuiteStats.setExecutionTime(System.currentTimeMillis() - suiteStartTime);
			String reportingJson = new ObjectMapper().writeValueAsString(testSuiteStats);
			context.put("suiteStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			context.put("compareStats", "{}");
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
                
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                if(reportFileName==null)
                	reportFileName = "index.html";
                
                context.put("thisFile", reportFileName.replaceFirst(".html", ""));
                context.put("isShowTable", false);
                context.put("runPrefix", prefix==null?"":prefix);
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index.vm", context, writer);

                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                        + SystemUtils.FILE_SEPARATOR + reportFileName)));
                fwriter.write(writer.toString());
                fwriter.close();
                
                /*if(distributedTestStatus!=null && distributedTestStatus.getReportFileContent().size()<5)
                {
                	distributedTestStatus.getReportFileContent().put(reportFileName, writer.toString());
                }*/
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
		acontext.clearTestResults();
		
		return testSuiteStats;
	}

	public TestSuiteStats doReporting(AcceptanceTestContext acontext, long startTime, String reportFileName, String prefix,
			boolean isLoadTestingEnabled) {
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		int total = 0, failed = 0, totruns = 0, failruns = 0, skipped = 0;
		
		AlphanumComparator<String> comparator = new AlphanumComparator<String>();
		
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
		
		VelocityContext context = new VelocityContext();
		try
		{
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
		
		boolean unzipped = false;
		String orf = reportFileName;
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  tempMap.entrySet()) {
			reportFileName = orf;
			try {
				List<TestCaseReport> reports = new ArrayList<TestCaseReport>(entry.getValue());
				Collections.sort(reports, new Comparator<TestCaseReport>() {
					public int compare(TestCaseReport o1, TestCaseReport o2) {
						 return o1==null ?
						         (o2==null ? 0 : Integer.MIN_VALUE) :
						         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
					}
				});
				
				int grptot = 0, grpfld = 0, grpskp = 0, grpruns = 0, grpflruns = 0;
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
					
					if(testCaseReport.getStatus().equals(TestStatus.Skipped.status)) {
						skipped ++;
						grpskp ++;
						tesStat.setStatus(TestStatus.Skipped.status);
					} else if(!testCaseReport.getStatus().equals(TestStatus.Success.status)) {
						failed ++;
						grpfld ++;
						tesStat.setStatus(TestStatus.Failed.status);
					}
					testCaseStats.add(tesStat);
					populateRequestResponseHeaders(testCaseReport);
					grpexecutionTime += testCaseReport.getExecutionTime();
					
					testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
				}
				
        		TestGroupStats testGroupStats = new TestGroupStats();
				testGroupStats.setSourceFile(entry.getKey());
				testGroupStats.setExecutionTime(grpexecutionTime);
				testGroupStats.setTotalTestCount(grptot);
				testGroupStats.setFailedTestCount(grpfld);
				testGroupStats.setSkippedTestCount(grpskp);
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
				testSuiteStats.setActualExecutionTime(testSuiteStats.getActualExecutionTime() + grpexecutionTime);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		long endTime = System.currentTimeMillis();
		
		if(testSuiteStats.getTotalSuiteRuns()==0) {
			testSuiteStats.setTotalTestCount(total);
			testSuiteStats.setFailedTestCount(failed);
			testSuiteStats.setSkippedTestCount(skipped);
			testSuiteStats.setTotalRuns(totruns);
			testSuiteStats.setFailedRuns(failruns);
			testSuiteStats.setTotalUserSuiteRuns(1);
			testSuiteStats.setTotalSuiteRuns(1);
		} else {
			TestSuiteStats tempst = new TestSuiteStats();
			tempst.setTotalTestCount(total);
			tempst.setFailedTestCount(failed);
			tempst.setSkippedTestCount(skipped);
			tempst.setTotalRuns(totruns);
			tempst.setFailedRuns(failruns);
			tempst.setTotalSuiteRuns(1);
			testSuiteStats.updateStats(tempst, true);
		}
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(allTestCases);
			context.put("testcaseReports", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		context.put("isMultipleRuns", acontext.getGatfExecutorConfig().getConcurrentUserSimulationNum()>1);
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testCaseStats);
			context.put("testcaseStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			testSuiteStats.setExecutionTime(endTime - startTime);
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
                if(!unzipped)
                	unzipZipFile(resourcesIS, resource.getAbsolutePath());
                
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                if(orf==null)
                	orf = "index.html";
                
                context.put("thisFile", orf.replaceFirst(".html", ""));
                context.put("isShowTable", true);
                context.put("runPrefix", prefix==null?"":prefix);
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index.vm", context, writer);

                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                        + SystemUtils.FILE_SEPARATOR + orf)));
                fwriter.write(writer.toString());
                fwriter.close();
                
                /*if(distributedTestStatus!=null && distributedTestStatus.getReportFileContent().size()<5)
                {
                	distributedTestStatus.getReportFileContent().put(orf, writer.toString());
                }*/
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
		acontext.clearTestResults();
		
		return testSuiteStats;
	}

	public void doTAReporting(String prefix, AcceptanceTestContext acontext, boolean isLoadTestingEnabled) {
		
		Map<String, List<Long>> testPercentileValues = testPercentiles.getPercentileTimes();
		Map<String, List<Long>> runPercentileValues = runPercentiles.getPercentileTimes();
		
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		
		VelocityContext context = new VelocityContext();
		
		try
		{
			String reportingJson = new ObjectMapper().writeValueAsString(testPercentileValues);
			context.put("testcaseTAReports", reportingJson);
			
			if(testPercentileValues.size()>0)
				context.put("isShowTAWrapper", testPercentileValues.values().iterator().next().size()>0);
			else
				context.put("isShowTAWrapper", false);
			
			if(runPercentileValues.size()>0)
			{
				List<Long> times90 = new ArrayList<Long>();
				List<Long> times50 = new ArrayList<Long>();
				for (List<Long> times : runPercentileValues.values()) {
					times90.add(times.get(0));
					times50.add(times.get(1));
				}
				
				runPercentileValues.put("All", times90);
				
				Collections.sort(times90);
				int index = Math.round((float)0.9 * times90.size());
				long time = times90.get(index-1);
				times90.clear();
				times90.add(time);
				
				Collections.sort(times50);
				index = Math.round((float)0.5 * times50.size());
				time = times50.get(index-1);
				times90.add(time);
			}
			
			reportingJson = new ObjectMapper().writeValueAsString(runPercentileValues);
			context.put("runTAReports", reportingJson);
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
                
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index-ta.vm", context, writer);

                prefix = prefix==null?"":prefix;
                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(resource.getAbsolutePath()
                        + SystemUtils.FILE_SEPARATOR + prefix + "index-ta.html")));
                fwriter.write(writer.toString());
                fwriter.close();
                
                /*if(distributedTestStatus!=null && distributedTestStatus.getReportFileContent().size()<5)
                {
                	distributedTestStatus.getReportFileContent().put("", writer.toString());
                }*/
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void mergeTestPercentileTimes(Map<String, List<Long>> times)
	{
		testPercentiles.mergePercentileTimes(times);
	}
	
	public void mergeRunPercentileTimes(Map<String, List<Long>> times)
	{
		runPercentiles.mergePercentileTimes(times);
	}
	
	public Map<String, List<Long>> getTestPercentileTimes()
	{
		return testPercentiles.getPercentileTimes();
	}
	
	public Map<String, List<Long>> getRunPercentileTimes()
	{
		return runPercentiles.getPercentileTimes();
	}
	
	public static void populateRequestResponseHeaders(TestCaseReport testCaseReport)
	{
		StringBuilder build = new StringBuilder();
		if(testCaseReport.getTestCase()!=null && testCaseReport.getTestCase().getHeaders()!=null)
		{
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
		
		if(testCaseReport.getResHeaders()!=null)
		{
			build = new StringBuilder();
			for (Map.Entry<String, List<String>> entry : testCaseReport.getResHeaders().entrySet()) {
				build.append(entry.getKey());
				build.append(": ");
				
				boolean needsComma = false;
	            for (String value : entry.getValue()) {
	                if (needsComma) {
	                	build.append(", ");
	                } else {
	                    needsComma = true;
	                }
	                build.append(value);
	            }
	            build.append("\n");
			}
			testCaseReport.setResponseHeaders(build.toString());
		}
	}
}
