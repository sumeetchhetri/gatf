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
package com.gatf.executor.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.custommonkey.xmlunit.XMLAssert;
import org.joda.time.DateTime;
import org.skyscreamer.jsonassert.JSONAssert;

import com.AlphanumComparator;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil.TestCaseResponseHandler;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;

import au.com.bytecode.opencsv.CSVWriter;
import net.lingala.zip4j.ZipFile;

/**
 * @author Sumeet Chhetri
 * The main report generation class which handles generation of all textual/graphical 
 * reporting after test suite execution
 */
public class ReportHandler {
	
	public static String MY_URL = "http://localhost:9080";
	
	private static final Map<String, Map<Long, Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>>>> allRuns = new ConcurrentHashMap<>();
	
	static {
		allRuns.put("api", new ConcurrentHashMap<>());
		allRuns.put("sel", new ConcurrentHashMap<>());
	}
	
	public static void pushPath(Long sessionId, int attempt, String path, String type) {
		allRuns.get(type).putIfAbsent(sessionId, new ConcurrentHashMap<>());
		allRuns.get(type).get(sessionId).put(path, new ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>(attempt, new AtomicInteger(), new ConcurrentHashMap<>()));
	}
	
	public static void addPathFailures(Long sessionId, String path, String type, int failures, Map<String, Map<String, Integer[]>> sinfos) {
		allRuns.get(type).get(sessionId).get(path).getMiddle().addAndGet(failures);
		allRuns.get(type).get(sessionId).get(path).getRight().putAll(sinfos);
		for(String key: sinfos.keySet()) {
			if(allRuns.get(type).get(sessionId).get(path).getRight().containsKey(key)) {
				allRuns.get(type).get(sessionId).get(path).getRight().get(key).putAll(sinfos.get(key));
			} else {
				allRuns.get(type).get(sessionId).get(path).getRight().put(key, sinfos.get(key));
			}
		}
	}
	
	public static void updatePaths(String type, Map<String, Object> out) {
		List<ImmutablePair<Long, List<ImmutableTriple<String, Integer, Integer>>>> spaths = new ArrayList<>();
		Map<Long, Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>>> sessions = allRuns.get(type);
		List<Long> sessIds = new ArrayList<>(sessions.keySet());
		Collections.sort(sessIds);
		Collections.reverse(sessIds);
		for(Long sid : sessIds) {
			ImmutablePair<Long, List<ImmutableTriple<String, Integer, Integer>>> spth = new ImmutablePair<>(sid, new ArrayList<>());
			if(sessions.get(sid).size()==1) {
				Map.Entry<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>> e = sessions.get(sid).entrySet().iterator().next();
				spth.getRight().add(new ImmutableTriple<String, Integer, Integer>(e.getKey(), e.getValue().getLeft(), e.getValue().getMiddle().get()));
			} else {
				Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>> sortedMap = sessions.get(sid).entrySet().stream().sorted(Comparator.comparingInt(a -> a.getValue().getMiddle().get()))
						.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
				sortedMap.entrySet().stream().forEach(e -> {
					spth.getRight().add(new ImmutableTriple<String, Integer, Integer>(e.getKey(), e.getValue().getLeft(), e.getValue().getMiddle().get()));
				});
			}
			spaths.add(spth);
		}
		out.put("paths", spaths);
	}
	
	public static List<Object[]> getSessPaths(String type, Long sessionId) {
		List<Object[]> out = new ArrayList<>();
		Map<Long, Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>>> sessions = allRuns.get(type);
		List<Long> sessIds = new ArrayList<>(sessions.keySet());
		if(sessIds.contains(sessionId)) {
			if(sessions.get(sessionId).size()==1) {
				Map.Entry<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>> e = sessions.get(sessionId).entrySet().iterator().next();
				out.add(new Object[] {"First Attempt", e.getKey(), e.getValue().getLeft(), e.getValue().getMiddle().get(), e.getValue().getRight()});
			} else {
				Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>> sortedMap = sessions.get(sessionId).entrySet().stream().sorted(Comparator.comparingInt(a -> a.getValue().getLeft()))
						.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
				sortedMap.entrySet().stream().forEach(e -> {
					out.add(new Object[] {(out.size()==0?"First Attempt":"Retry Attempt - " + (e.getValue().getLeft())) , e.getKey(), e.getValue().getLeft(), e.getValue().getMiddle().get(), e.getValue().getRight()});
				});
			}
		}
		return out;
	}
	
	public static List<String> getRDirs(String type) {
		List<String> out = new ArrayList<>();
		Map<Long, Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>>> sessions = allRuns.get(type);
		List<Long> sessIds = new ArrayList<>(sessions.keySet());
		Collections.sort(sessIds);
		Collections.reverse(sessIds);
		for(Long sid : sessIds) {
			if(sessions.get(sid).size()==1) {
				out.add(sessions.get(sid).keySet().iterator().next());
			} else {
				Map<String, ImmutableTriple<Integer, AtomicInteger, Map<String, Map<String, Integer[]>>>> sortedMap = sessions.get(sid).entrySet().stream().sorted(Comparator.comparingInt(a -> a.getValue().getMiddle().get()))
						.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
				sortedMap.keySet().stream().forEach(e -> out.add(e));
			}
		}
		return out;
	}
	
	private static Logger logger = LogManager.getLogger(ReportHandler.class.getSimpleName());
	
	private final Map<String, ConcurrentLinkedQueue<TestCaseReport>> finalTestResults = 
			new ConcurrentHashMap<String, ConcurrentLinkedQueue<TestCaseReport>>();
	
	private final Map<String, Integer> finalTestReportsDups = new ConcurrentHashMap<String, Integer>();
	
	//private DistributedTestStatus distributedTestStatus = null;
	
	private List<TestCaseStats> testCaseStats = new ArrayList<TestCaseStats>();
	
	private TestSuiteStats testSuiteStats = new TestSuiteStats();
	
	/*public DistributedTestStatus getDistributedTestStatus() {
		return distributedTestStatus;
	}*/

	public void clearForLoadTests(AcceptanceTestContext context)
	{
		testCaseStats = new ArrayList<TestCaseStats>();
		testSuiteStats = new TestSuiteStats();
		clearTestResults();
	}
	
	
	
	public void addToLoadTestResources(String prefix, int runNo, String url, List<LoadTestResource> loadTestResources) {
		LoadTestResource resource = new LoadTestResource();
		if(prefix==null)
			prefix = "Run";
		resource.setTitle(prefix+"-"+runNo);
		resource.setUrl(url);
		synchronized (loadTestResources) {
			loadTestResources.add(resource);
		}
	}
	
	private static String getPath(File resource, String path) {
		return resource.getAbsolutePath() + File.separator + path;
	}
	
	public static void doFinalLoadTestReport(String path, String prefix, TestSuiteStats testSuiteStats, AcceptanceTestContext acontext,
			List<String> nodes, List<String> nodeurls, List<LoadTestResource> loadTestResources)
	{
		VelocityContext context = new VelocityContext();
		try
		{
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testSuiteStats);
			context.put("suiteStats", reportingJson);
			
			if(nodes==null)
			{
				reportingJson = WorkflowContextHandler.OM.writeValueAsString(loadTestResources);
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
        	File resource = acontext.getOutDir();
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/index-load.vm", "UTF-8", context, writer);

            if(prefix==null)
            	prefix = "";
            
            String filenm = getPath(resource, path) + File.separator + prefix.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "index.html";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write(writer.toString());
            fwriter.close();
            
            //if(distributedTestStatus!=null)
            {
            	//distributedTestStatus.getReportFileContent().put(prefix + "index.html", writer.toString());
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void doFinalDistributedLoadTestReport(String path, AcceptanceTestContext acontext, List<TestSuiteStats> suiteStats, 
			List<List<LoadTestResource>> loadTestResources, List<String> nodes)
	{
		VelocityContext context = new VelocityContext();
		context.put("suiteStats", suiteStats);
		context.put("loadTestResources", loadTestResources);
		context.put("nodes", nodes);
		
		try
		{
			File resource = acontext.getOutDir();
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/distributed-index-load.vm", "UTF-8", context, writer);
            
            String filenm = getPath(resource, path) + File.separator + "index.html";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write(writer.toString());
            fwriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public TestSuiteStats doLoadTestReporting(String path, AcceptanceTestContext acontext, long startTime, TestExecutionPercentile testPercentiles,
			TestExecutionPercentile runPercentiles)
	{
		TestSuiteStats testSuiteStats = new TestSuiteStats();
		
		int total = 0, failed = 0, skipped = 0, totruns = 0, failruns = 0;
		long grpexecutionTime = 0L;
		
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  getFinalTestResults().entrySet()) {
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
					
					synchronized (testPercentiles) {
						testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					}
					synchronized (runPercentiles) {
						runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
					}
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
		
		clearTestResults();
		
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
    public static void unzipZipFile(File zipFile, String directoryToExtractTo)
    {
        try
        {
        	ZipFile zf = new ZipFile(zipFile);
        	zf.extractAll(directoryToExtractTo);
        	zf.close();
        }
        catch (IOException ioe)
        {
        	logger.error(ExceptionUtils.getStackTrace(ioe));
            return;
        }
    }
    
    /**
     * @param zipFile
     * @param directoryToExtractTo Provides file unzip functionality
     */
    public static void zipDirectory(File directory, final String[] fileFilters, String zipFileName, boolean addFolders, boolean suffixOrPrefix)
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
        	
        	Collection<File> files = null;
        	if(addFolders) {
        	    if(suffixOrPrefix) {
        	        files = FileUtils.listFilesAndDirs(directory, new SuffixFileFilter(Arrays.asList(fileFilters)), FileFilterUtils.trueFileFilter());
        	    } else {
        	        files = FileUtils.listFilesAndDirs(directory, new PrefixFileFilter(Arrays.asList(fileFilters)), FileFilterUtils.trueFileFilter());
        	    }
        	} else {
        	    if(suffixOrPrefix) {
                    files = FileUtils.listFiles(directory, new SuffixFileFilter(Arrays.asList(fileFilters)), null);
        	    } else {
        	        files = FileUtils.listFiles(directory, new PrefixFileFilter(Arrays.asList(fileFilters)), null);
                }
        	}
        	
        	for (File file : files) {
        		if(file.isDirectory()) {
        			String dp = file.getAbsolutePath();
        			dp = dp.replace(directory.getAbsolutePath(), "");
        			dp = dp.replace(File.separator, "/");
        			if(dp.isEmpty())continue;
        			if(dp.startsWith("/")) {
        				dp = dp.substring(1);
        			}
        			ZipEntry zipEntry = new ZipEntry(dp + "/");
        			zos.putNextEntry(zipEntry);
        			zos.closeEntry();
        		} else {
        			String dp = file.getAbsolutePath();
        			dp = dp.replace(directory.getAbsolutePath(), "");
        			dp = dp.replace(File.separator, "/");
        			if(dp.isEmpty())continue;
        			if(dp.startsWith("/")) {
        				dp = dp.substring(1);
        			}
        			FileInputStream fis = new FileInputStream(file);
            		ZipEntry zipEntry = new ZipEntry(dp);
            		zos.putNextEntry(zipEntry);

            		byte[] bytes = new byte[1024];
            		int length;
            		while ((length = fis.read(bytes)) >= 0) {
            			zos.write(bytes, 0, length);
            		}

            		zos.closeEntry();
            		fis.close();
        		}
			}
        	
            zos.close();
			fos.close();
        }
        catch (IOException ioe)
        {
        	logger.error(ExceptionUtils.getStackTrace(ioe));
            return;
        }
    }
	
	private boolean compareText(String type, String lhs, String rhs)
	{
		if(TestCaseResponseHandler.isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, type))
		{
			try {
				JSONAssert.assertEquals(lhs, rhs,false);
				return true;
			} catch (Exception e) {
			} catch (AssertionError e) {
			}
		}
		else if(TestCaseResponseHandler.isMatchesContentType(MediaType.APPLICATION_XML_TYPE, type) 
		        || TestCaseResponseHandler.isMatchesContentType(MediaType.TEXT_XML_TYPE, type))
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

	public void doConcurrentRunReporting(String path, AcceptanceTestContext acontext, long startTime, String reportFileName, int runNumber,
			boolean unzipFile, TestExecutionPercentile testPercentiles, TestExecutionPercentile runPercentiles, 
			String distRunPrefix) {
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		int total = 0, failed = 0, totruns = 0, failruns = 0, skipped = 0;
		
		AlphanumComparator<String> comparator = new AlphanumComparator<String>();
		
		Map<String, List<TestCaseReport>> allTestCases = new TreeMap<String, List<TestCaseReport>>(comparator);
		Map<String, ConcurrentLinkedQueue<TestCaseReport>> tempMap = new TreeMap<String, ConcurrentLinkedQueue<TestCaseReport>>(comparator);
		
		if(distRunPrefix==null)
			tempMap.put("Run-"+ runNumber, getFinalTestResults().get("Run-"+ runNumber));
		else
			tempMap.put("Run-"+ runNumber, getFinalTestResults().get(distRunPrefix + "-" + runNumber));
		
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
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(allTestCases);
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
						         (o2==null ? Integer.MAX_VALUE : Integer.valueOf(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
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
					
					synchronized (testPercentiles) {
						testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					}
					synchronized (runPercentiles) {
						runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
					}
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
						String reportingJson = WorkflowContextHandler.OM.writeValueAsString(allTestCases);
						context.put("testcaseReports", reportingJson);
						allTestCases.clear();
						context.put("compareStats", "{}");
						
						InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
			            if (resourcesIS != null)
			            {
			            	
			            	File resource = acontext.getOutDir();
			                /*if(runNumber==1 && unzipFile) {
			                	WorkflowContextHandler.copyResourcesToDirectory("gatf-resources", resource.getAbsolutePath());
			                	//unzipGatfResources(resourcesIS, resource.getAbsolutePath());
			                }*/
			                
			                VelocityEngine engine = new VelocityEngine();
			                engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
			                engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
			                engine.init();
			                
			                StringWriter writer = new StringWriter();
			                engine.mergeTemplate("/gatf-templates/index-run.vm", "UTF-8", context, writer);

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
			                
			                String filenm = getPath(resource, path) + File.separator + reportFileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
			                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
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
		
		if(distRunPrefix==null)
			getFinalTestResults().get("Run-"+ runNumber).clear();
		else
			getFinalTestResults().get(distRunPrefix + "-" + runNumber).clear();
	}

	public TestSuiteStats doReportingIndex(String path, AcceptanceTestContext acontext, long suiteStartTime, String reportFileName, 
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
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testCaseStats);
			context.put("testcaseStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			testSuiteStats.setTotalUserSuiteRuns(numberOfRuns);
			testSuiteStats.setExecutionTime(System.currentTimeMillis() - suiteStartTime);
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testSuiteStats);
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
			InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
            if (resourcesIS != null)
            {
            	File resource = acontext.getOutDir();
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
                engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                if(reportFileName==null)
                	reportFileName = "index.html";
                
                context.put("thisFile", reportFileName.replaceFirst(".html", ""));
                context.put("isShowTable", false);
                context.put("runPrefix", prefix==null?"":prefix);
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index.vm", "UTF-8", context, writer);

                String filenm = getPath(resource, path) + File.separator + reportFileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
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
		clearTestResults();
		
		return testSuiteStats;
	}

	public TestSuiteStats doReporting(String path, AcceptanceTestContext acontext, long startTime, String reportFileName, String prefix,
			boolean isLoadTestingEnabled, TestExecutionPercentile testPercentiles, TestExecutionPercentile runPercentiles) {
		GatfExecutorConfig config = acontext.getGatfExecutorConfig();
		int total = 0, failed = 0, totruns = 0, failruns = 0, skipped = 0;
		
		AlphanumComparator<String> comparator = new AlphanumComparator<String>();
		
		Map<String, List<TestCaseReport>> allTestCases = new TreeMap<String, List<TestCaseReport>>(comparator);
		Map<String, ConcurrentLinkedQueue<TestCaseReport>> tempMap = new TreeMap<String, ConcurrentLinkedQueue<TestCaseReport>>(comparator);
		tempMap.putAll(getFinalTestResults());
		
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
		
		//boolean unzipped = false;
		String orf = reportFileName;
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  tempMap.entrySet()) {
			reportFileName = orf;
			try {
				List<TestCaseReport> reports = new ArrayList<TestCaseReport>(entry.getValue());
				Collections.sort(reports, new Comparator<TestCaseReport>() {
					public int compare(TestCaseReport o1, TestCaseReport o2) {
						 return o1==null ?
						         (o2==null ? 0 : Integer.MIN_VALUE) :
						         (o2==null ? Integer.MAX_VALUE : Integer.valueOf(o1.getTestCase().getSequence()).compareTo(o2.getTestCase().getSequence()));
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
					
					synchronized (testPercentiles) {
						testPercentiles.addExecutionTime(testCaseReport.getTestCase().getName(), testCaseReport.getExecutionTime());
					}
					synchronized (runPercentiles) {
						runPercentiles.addExecutionTime(testCaseReport.getTestCase().getIdentifier(), testCaseReport.getExecutionTime());
					}
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
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(allTestCases);
			context.put("testcaseReports", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		context.put("isMultipleRuns", acontext.getGatfExecutorConfig().getConcurrentUserSimulationNum()>1);
		
		try
		{
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testCaseStats);
			context.put("testcaseStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			testSuiteStats.setExecutionTime(endTime - startTime);
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testSuiteStats);
			context.put("suiteStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(compareStatuses);
			context.put("compareStats", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
            if (resourcesIS != null)
            {
            	File resource = acontext.getOutDir();
                /*if(!unzipped) {
                	WorkflowContextHandler.copyResourcesToDirectory("gatf-resources", resource.getAbsolutePath());
                	//unzipGatfResources(resourcesIS, resource.getAbsolutePath());
                }*/
                
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
                engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                if(orf==null)
                	orf = "index.html";
                
                context.put("thisFile", orf.replaceFirst(".html", ""));
                context.put("isShowTable", true);
                context.put("runPrefix", prefix==null?"":prefix);
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index.vm", "UTF-8", context, writer);

                String filenm = getPath(resource, path) + File.separator + orf.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
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
		clearTestResults();
		
		return testSuiteStats;
	}

	public static void doTAReporting(String path, String prefix, AcceptanceTestContext acontext, boolean isLoadTestingEnabled,
			TestExecutionPercentile testPercentiles, TestExecutionPercentile runPercentiles) {
		Map<String, List<Long>> testPercentileValues = testPercentiles.getPercentileTimes();
		Map<String, List<Long>> runPercentileValues = runPercentiles.getPercentileTimes();
		VelocityContext context = new VelocityContext();
		
		try
		{
			String reportingJson = WorkflowContextHandler.OM.writeValueAsString(testPercentileValues);
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
			
			reportingJson = WorkflowContextHandler.OM.writeValueAsString(runPercentileValues);
			context.put("runTAReports", reportingJson);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try
		{
			InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
            if (resourcesIS != null)
            {
            	File resource = acontext.getOutDir();
                VelocityEngine engine = new VelocityEngine();
                engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
                engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
                engine.init();
                
                context.put("isLoadTestingEnabled", isLoadTestingEnabled);
                
                StringWriter writer = new StringWriter();
                engine.mergeTemplate("/gatf-templates/index-ta.vm", "UTF-8", context, writer);

                prefix = prefix==null?"":prefix;
                
                String filenm = getPath(resource, path) + File.separator + prefix.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "index-ta.html";
                BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
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
	
	public static void doSeleniumSummaryTestReport(Long sessionId, Integer attempt, String path, Map<String, List<Map<String, Map<String, List<Object[]>>>>> summLstMap, 
			Map<String, List<Map<String, Map<String, Integer[]>>>> sinfoMap, AcceptanceTestContext acontext, int loadTestRunNum, String runPrefix)
    {
        VelocityContext context = new VelocityContext();
        try
        {
            context.put("testsMap", summLstMap);
            context.put("sessionId", sessionId);
            context.put("attempt", attempt);
            context.put("sinfoMap", sinfoMap);
            
            File resource = acontext.getOutDir();
            //InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
            /*if (resourcesIS != null)
            {
            	WorkflowContextHandler.copyResourcesToDirectory("gatf-resources", resource.getAbsolutePath());
            	//unzipGatfResources(resourcesIS, resource.getAbsolutePath());
            }*/
            
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/index-selenium-summ.vm", "UTF-8", context, writer);
            
            //String rtid = (acontext.getGatfExecutorConfig().getSeleniumScriptRetryCount()>0 && acontext.getGatfExecutorConfig().getRetryCounter()>0)?
            //		("-"+acontext.getGatfExecutorConfig().getRetryCounter()):"";
            String filenm = getPath(resource, path) + File.separator + runPrefix + "-" + loadTestRunNum + "-selenium-index.html";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write(writer.toString());
            fwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	public static void doSeleniumFinalTestReport(Long sessionId, Integer attempt, String path, Map<String, Map<Integer, String>> indexes, AcceptanceTestContext acontext, GatfExecutorConfig config, int failed)
    {
        VelocityContext context = new VelocityContext();
        try
        {
        	/*int prc = 0;
        	if(acontext.getGatfExecutorConfig().getSeleniumScriptRetryCount()>0) {
        		prc = acontext.getGatfExecutorConfig().getSeleniumScriptRetryCount() + acontext.getGatfExecutorConfig().getRetryCounter();
        	} else if(acontext.getGatfExecutorConfig().getRetryCounter()>0) {
        		prc = acontext.getGatfExecutorConfig().getRetryCounter();
        	}
        	
        	Map<Integer, String> retries = new HashMap<>();
        	
        	if(prc>0 && failed>0) {
	        	for (int rc=1;rc<prc;rc++) {
	        		retries.put(rc, config.getSessionId()+"-"+rc);
				}
        	}*/
        	
            context.put("indexes", indexes);
            context.put("sessionId", sessionId);
            context.put("attempt", attempt);
            //List<Object[]> out = getSessPaths("sel", sessionId);
            //context.put("retries", out);
            //context.put("at1", config.getSessionId());
            context.put("dt", new DateTime().toString("dd/MM/yyyy hh:mm:ss"));
            File resource = acontext.getOutDir();
            
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/selenium-final-index.vm", "UTF-8", context, writer);
            
            //String rtid = (acontext.getGatfExecutorConfig().getSeleniumScriptRetryCount()>0 && acontext.getGatfExecutorConfig().getRetryCounter()>0)?
            //		("-"+acontext.getGatfExecutorConfig().getRetryCounter()):"";
            String filenm = getPath(resource, path) + File.separator + "selenium-index.html";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write(writer.toString());
            fwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	public static void writeSessInfo(Long sessionId, AcceptanceTestContext acontext) {
		File resource = acontext.getOutDir();
		try {
			List<Object[]> out = getSessPaths("sel", sessionId);
			String filenm = resource.getAbsolutePath() + File.separator + sessionId + ".js";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write("var sessInfo_ = " + WorkflowContextHandler.OM.writeValueAsString(out) + ";\n");
            fwriter.close();
		} catch (Exception e) {
		}
		
	}
	
	/*private static void unzipGatfResources(InputStream resourcesIS, String path) throws IOException {
		File gctzip = File.createTempFile("gatf-resources-"+UUID.randomUUID(), ".zip");
		IOUtils.copy(resourcesIS, new FileOutputStream(gctzip));
    	unzipZipFile(gctzip, path);
	}*/
    
    public static void doSeleniumTestReport(String path, String prefix, Object[] retvals, SeleniumTestResult result, AcceptanceTestContext acontext)
    {
        VelocityContext context = new VelocityContext();
        File resource = acontext.getOutDir();
        
        //InputStream resourcesIS = GatfTestCaseExecutorUtil.class.getResourceAsStream("/gatf-resources");
        /*if (resourcesIS != null)
        {
        	WorkflowContextHandler.copyResourcesToDirectory("gatf-resources", resource.getAbsolutePath());
        	//unzipGatfResources(resourcesIS, resource.getAbsolutePath());
        }*/
        
        try
        {
            context.put("selFileName", StringEscapeUtils.escapeHtml4((String)retvals[0]));
            context.put("selCode", StringEscapeUtils.escapeHtml4((String)retvals[1]));
            context.put("javaFileName", StringEscapeUtils.escapeHtml4((String)retvals[2]));
            context.put("javaCode", StringEscapeUtils.escapeHtml4((String)retvals[3]));
            context.put("selLogs", result.getLogs());
            context.put("succFail", result.isStatus()?"SUCCESS":"FAILED");
            context.put("StringEscapeUtils", StringEscapeUtils.class);
            
            VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            engine.init();
            
            StringWriter writer = new StringWriter();
            engine.mergeTemplate("/gatf-templates/index-selenium-tr.vm", "UTF-8", context, writer);
            
            //String rtid = (acontext.getGatfExecutorConfig().getSeleniumScriptRetryCount()>0 && acontext.getGatfExecutorConfig().getRetryCounter()>0)?
            //		("-"+acontext.getGatfExecutorConfig().getRetryCounter()):"";
            String filenm = getPath(resource, path) + File.separator + prefix + ".html";
            BufferedWriter fwriter = new BufferedWriter(new FileWriter(new File(filenm)));
            fwriter.write(writer.toString());
            fwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

	public Map<String, ConcurrentLinkedQueue<TestCaseReport>> getFinalTestResults() {
		return finalTestResults;
	}
	
	public void addTestCaseReport(TestCaseReport testCaseReport) {
		String key = testCaseReport.getTestCase().getIdentifier() + testCaseReport.getTestCase().getName();
		getFinalTestResults().get(testCaseReport.getTestCase().getIdentifier()).add(testCaseReport);
		if(!finalTestReportsDups.containsKey(key)) {
			finalTestReportsDups.put(key, 1);
		} else {
			Integer ncount = finalTestReportsDups.get(key) + 1;
			String ext = "-" + ncount;
			finalTestReportsDups.put(key, ncount);
			testCaseReport.setTestIdentifier(testCaseReport.getTestIdentifier()+ext);
		}
	}
	
	public void clearTestResults() {
		
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  getFinalTestResults().entrySet()) {
			entry.getValue().clear();
		}
		finalTestReportsDups.clear();
	}
	
	public void initializeResultsHolders(int runNums, String fileName)
	{
		if(runNums>1)
		{
			for (int i = 0; i < runNums; i++)
			{
				finalTestResults.put("Run-" + (i+1), new ConcurrentLinkedQueue<TestCaseReport>());
			}
		}
		else
		{
			finalTestResults.put(fileName, new ConcurrentLinkedQueue<TestCaseReport>());
		}
	}
	
	public static class TestFileReporter {
		public static ImmutablePair<Method, Object[]> startO(Object...args) {
			try {
				Method m = TestFileReporter.class.getMethod("start", new Class[] {String.class, boolean.class, Document.class, CSVWriter.class});
				return new ImmutablePair<Method, Object[]>(m , args);
			} catch (Exception e) {
			}
			return null;
		}
		public static Table start(String name, boolean isNew, Document document, CSVWriter csvdoc) {
			csvdoc.writeNext(new String[] {});
			csvdoc.writeNext(new String[] {});
			csvdoc.writeNext(new String[] {name});
			csvdoc.writeNext(new String[] {});
			
			String[] headerNames = new String[] {"S.No", "Node", "Run.No", "Session", "Sub Test Name", "Execution Time", "Status", "Message"};
			if(headerNames.length<=5) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A4);
			} else if(headerNames.length<=8) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A4.rotate());
			} else if(headerNames.length<=10) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A3);
			} else if(headerNames.length<=12) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A3.rotate());
			} else if(headerNames.length<=14) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A2);
			} else if(headerNames.length<=16) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A2.rotate());
			} else if(headerNames.length<=18) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A1);
			} else if(headerNames.length<=22) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A1.rotate());
			} else if(headerNames.length<=32) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A0);
			} else {
				document.getPdfDocument().setDefaultPageSize(PageSize.A0.rotate());
			}
			
			if(!isNew) {
				document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
			} else {
				TextFooterEventHandler evt = new TextFooterEventHandler("Gatf Test Report", null, "== End of Report ==", document);
				document.getPdfDocument().addEventHandler(PdfDocumentEvent.START_PAGE, evt);
				document.getPdfDocument().addEventHandler(PdfDocumentEvent.END_PAGE, evt);
			}
			
			Paragraph hdr = new Paragraph();
			hdr.setTextAlignment(TextAlignment.CENTER);
			hdr.setHorizontalAlignment(HorizontalAlignment.CENTER);
			Paragraph chunk = new Paragraph(name);
			try {
				chunk.setFont(PdfFontFactory.createFont(StandardFonts.COURIER));
			} catch (IOException e) {
			}
			chunk.setFontSize(10);
			chunk.setUnderline(0.1f, -2f);
			hdr.add(chunk);
			hdr.setMarginBottom(30.0f);
			document.add(hdr);
			
			Table table = new Table(8);
			csvdoc.writeNext(headerNames);
			for(String str: headerNames) {
				com.itextpdf.layout.element.Cell header = new com.itextpdf.layout.element.Cell();
		        header.setBackgroundColor(new DeviceGray(0.75f));
		        header.setBorder(new SolidBorder(2));
		        header.add(new Paragraph(str));
		        table.addHeaderCell(header);
			}
			table.setHorizontalAlignment(HorizontalAlignment.CENTER);
			return table;
		}
		
		public static ImmutablePair<Method, Object[]> addSubTestO(Object...args) {
			try {
				Method m = TestFileReporter.class.getMethod("addSubTest", new Class[] {int.class, String.class, int.class, String.class, String.class, String.class, 
						boolean.class, String.class, String.class, String.class, Table.class, Document.class, CSVWriter.class});
				return new ImmutablePair<Method, Object[]>(m , args);
			} catch (Exception e) {
			}
			return null;
		}
		public static void addSubTest(int sno, String node, int runNo, String sess, String stname, String stTime, boolean status, String result, String fileName, String prefix, Table table, Document document, CSVWriter csvdoc) {
			if(fileName!=null) {
				/*PdfFileSpec spec = PdfFileSpec.createExternalFileSpec(document.getPdfDocument(), fileName, null);
				PdfAction action = PdfAction.createLaunch(spec);
				Paragraph rnl = new Paragraph(new Link(sno+"", action));
				table.addCell(rnl);*/
				Paragraph fp = new Paragraph(new Link(sno+"", PdfAction.createGoTo("H_"+prefix)));
				table.addCell(fp);
			} else {
				table.addCell(sno+"");
			}
			table.addCell(node);
			table.addCell(runNo+"");
			table.addCell(sess);
			table.addCell(stname!=null?stname:"");
			table.addCell(stTime);
			if(!status) {
				Paragraph fp = new Paragraph(new Link(status+"", PdfAction.createGoTo("S_"+prefix)));
				table.addCell(fp);
			} else {
				table.addCell(status+"");
			}
			table.addCell(result==null?"":result);
			csvdoc.writeNext(new String[] {sno+"", node, runNo+"", sess, stname!=null?stname:"", stTime, status+"", result});
		}
		
		public static ImmutablePair<Method, Object[]> addErrorDetailsO(Object...args) {
			try {
				Method m = TestFileReporter.class.getMethod("addErrorDetails", new Class[] {ImmutableTriple.class, Document.class});
				return new ImmutablePair<Method, Object[]>(m , args);
			} catch (Exception e) {
			}
			return null;
		}
		public static void addErrorDetails(ImmutableTriple<SeleniumTestResult, String, String> res, Document document) {
			if(!res.getLeft().isStatus()) {
				document.getPdfDocument().setDefaultPageSize(PageSize.A4);
				document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
				Paragraph hdr = new Paragraph();
				hdr.setTextAlignment(TextAlignment.CENTER);
				hdr.setHorizontalAlignment(HorizontalAlignment.CENTER);
				String sno = res.getRight().substring(res.getRight().lastIndexOf("-")+1);
				Paragraph chunk = new Paragraph("Error Details for Test #" + sno);
				try {
					chunk.setFont(PdfFontFactory.createFont(StandardFonts.COURIER));
				} catch (IOException e) {
				}
				chunk.setFontSize(10);
				chunk.setUnderline(0.1f, -2f);
				hdr.add(chunk);
				hdr.setMarginBottom(30.0f);
				hdr.setProperty(Property.DESTINATION, "S_"+res.getRight());
				document.add(hdr);
				
				String errTrace = res.getLeft().getLogs().get("gatf").getAll().size()>1?res.getLeft().getLogs().get("gatf").getAll().get(1).getMessage():null;
				if(errTrace!=null) {
					Paragraph p1 = new Paragraph();
					p1.setTextAlignment(TextAlignment.LEFT);
					p1.setHorizontalAlignment(HorizontalAlignment.LEFT);
					Paragraph c1 = new Paragraph(errTrace);
					c1.setFontSize(8);
					p1.add(c1);
					p1.setMarginBottom(30.0f);
					document.add(p1);
				}
				
				if(res.getLeft().getSubtestImg()!=null && new File(res.getLeft().getSubtestImg()).exists()) {
					try {
						Image img = new Image(ImageDataFactory.create(res.getLeft().getSubtestImg()));
						//img.scale(50, 50);
						img.setWidth(document.getPdfDocument().getDefaultPageSize().getWidth()-50);
						//img.setHeight(400);
						//img.setMarginBottom(30.0f);
						img.setAutoScale(true);
						img.setHorizontalAlignment(HorizontalAlignment.CENTER);
						document.add(img);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			/*if(res.getMiddle()!=null) {
				ConverterProperties properties = new ConverterProperties();
			    properties.setBaseUri(MY_URL);
			    try {
			    	document.getPdfDocument().setDefaultPageSize(PageSize.A4);
					document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
					List<IElement> elements = HtmlConverter.convertToElements(new FileInputStream(res.getMiddle()), properties);
					elements.get(0).setProperty(Property.DESTINATION, "H_"+res.getRight());
					for (IElement element : elements) {
						document.add((IBlockElement)element);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}*/
		}
		
		private static class TextFooterEventHandler implements IEventHandler {
	        protected Document doc;
	        private String header, footer, lastFooter;
	        public TextFooterEventHandler(String header, String footer, String lastFooter, Document doc) {
	        	this.header = header;
	        	this.footer = footer;
	        	this.lastFooter = lastFooter;
	            this.doc = doc;
	        }
	        @Override
	        public void handleEvent(Event currentEvent) {
	            PdfDocumentEvent docEvent = (PdfDocumentEvent) currentEvent;
	            Rectangle pageSize = docEvent.getPage().getPageSize();
	            PdfFont font = null;
	            try {
	                font = PdfFontFactory.createFont(StandardFonts.COURIER);
	            } catch (IOException e) {
	            }
	            float coordX = ((pageSize.getLeft() + doc.getLeftMargin())
	                    + (pageSize.getRight() - doc.getRightMargin())) / 2;
	            float coordP = (pageSize.getLeft() + doc.getLeftMargin())
	                    + (pageSize.getRight() - doc.getRightMargin()) - 5;
	            float headerY = pageSize.getTop() - doc.getTopMargin() + 10;
	            float footerY = doc.getBottomMargin()/3;
	            Canvas canvas = new Canvas(docEvent.getPage(), pageSize);
	            if(currentEvent.getType().equals(PdfDocumentEvent.START_PAGE) && header!=null) {
	            	canvas.setFont(font)
	                    .setFontSize(5)
	                    .showTextAligned(header, coordX, headerY, TextAlignment.CENTER)
	                    .close();
	            } else if(currentEvent.getType().equals(PdfDocumentEvent.END_PAGE)) {
	            	int totalPages = docEvent.getDocument().getNumberOfPages();
	            	int pageNum = docEvent.getDocument().getPageNumber(docEvent.getPage());
	            	String foot = footer;
	            	if(totalPages==pageNum) {
	            		foot = lastFooter;
	            	}
	            	if(foot!=null) {
	            		canvas.setFont(font)
		                    .setFontSize(5)
		                    .showTextAligned(foot, coordX, footerY, TextAlignment.CENTER);
	            	}
	            	canvas.setFont(font)
	                    .setFontSize(5)
	                    .showTextAligned("Page " +  pageNum, coordP, footerY, TextAlignment.RIGHT)
	                    .close();
	            }
	            canvas.close();
	        }
	    }
	}
}
