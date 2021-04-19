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
package com.gatf.ui;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.selenium.Command;

public class GatfReportsHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	private Function<String, GatfPlugin> f;
	
	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	private ReentrantLock lock = new ReentrantLock();
	
	public GatfReportsHandler(GatfConfigToolMojoInt mojo, Function<String, GatfPlugin> f) {
		super();
		this.mojo = mojo;
		this.f = f;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	try {
    		final GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
			String basepath = gatfConfig.getOutFilesBasePath()==null?mojo.getRootDir():gatfConfig.getOutFilesBasePath();
			String dirPath = basepath + File.separator + gatfConfig.getOutFilesDir();
			if(!new File(dirPath).exists()) {
				new File(dirPath).mkdir();
			}
			if(request.getMethod().equals(Method.GET) ) {
			    new CacheLessStaticHttpHandler(dirPath).service(request, response);
			} else if(request.getMethod().equals(Method.PUT) ) {
			    String action = request.getParameter("action");
			    String testcaseFileName = request.getParameter("testcaseFileName");
			    String testCaseName = request.getParameter("testCaseName");
			    boolean isServerLogsApi = request.getParameter("isServerLogsApi")!=null;
			    boolean isExternalLogsApi = request.getParameter("isExternalLogsApi")!=null;
			    TestCaseReport tcReport = null;
			    if(action.equals("validateTest") && testcaseFileName.endsWith(".sel"))
			    {
			    	String status = Command.validateSel(new String[] {"-validate-sel", testcaseFileName, "gatf-config.xml", gatfConfig.getTestCasesBasePath()});
			    	response.setContentType(MediaType.APPLICATION_JSON);
			        response.setContentLength(status.length());
			        response.getWriter().write(status);
			        response.setStatus(HttpStatus.OK_200);
			        return;
			    }
			    else if(action.equals("replayTest"))
			    { 
			        tcReport = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), TestCaseReport.class);
			        if(tcReport == null) {
			            throw new RuntimeException("Invalid testcase report details provided");
			        }
			    } 
			    else if(isExternalLogsApi && !action.equals("playTest"))
			    {
			        tcReport = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), TestCaseReport.class);
			    }
			    else if(!action.equals("playTest"))
			    {
			    	throw new RuntimeException("Invalid action specified..");
			    }
			    Object[] out = executeTest(gatfConfig, tcReport, action, testcaseFileName, testCaseName, isServerLogsApi, isExternalLogsApi, 0, false);
			    if(out[1]!=null) {
			        response.setContentType(out[2].toString());
			        response.setContentLength(((String)out[1]).length());
			        response.getWriter().write((String)out[1]);
			    }
			    response.setStatus((HttpStatus)out[0]);
			}
    	} catch (Exception e) {
    		GatfConfigToolUtil.handleError(e, response, null);
		} finally {
		    if(lock.isLocked()) {
		        lock.unlock();
		    }
		}
    }

	public Object[] executeTest(GatfExecutorConfig gatfConfig, TestCaseReport tcReport, String action, String testcaseFileName, 
	        String testCaseName, boolean isServerLogsApi, boolean isExternalLogsApi, int index, boolean fromApiPlugin) throws Exception {
	    String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
	    if(action.equals("replayTest") || action.equals("playTest") || action.equals("createIssue") || action.equals("getContent"))
        {
            boolean isReplay = action.equals("replayTest");
            List<TestCaseReport> reports = null;
            
            if(isReplay)
            {
                TestCase origfound = null;
                String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator
                        + testcaseFileName;
                if(!new File(filePath).exists()) {
                    throw new RuntimeException("Test case file does not exist");
                }
                
                List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
                for (TestCase tc : tcs) {
                    if(tc.getName().equals(testCaseName)) {
                        origfound = tc;
                        break;
                    }
                }
                if(origfound==null) {
                    throw new RuntimeException("Testcase does not exist");
                }
                
                GatfPlugin executorMojo = f.apply("executor");

                lock.lock();
                
                if(mojo!=null && mojo.getContext()!=null) {
                    context = mojo.getContext();
                } else if(context==null) {
                    try {
                        executorMojo.initilaizeContext(gatfConfig, true);
                        context = executorMojo.getContext();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Please Execute the GATF Suite first..");
                    }
                }
                
                context.setGatfExecutorConfig(gatfConfig);
                executorMojo.setContext(context);
                executorMojo.getAllTestCases(context, null, null);
                authTestCase = executorMojo.getAuthTestCase();
                
                TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
                
                boolean isAuthExec = false;
                
                TestCase found = new TestCase(origfound);
                found.setSimulationNumber(index);
                found.setSourcefileName(testcaseFileName);
                found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
                found.setLogicalValidations(null);
                found.setExecuteOnCondition(null);
                found.setPreWaitMs(0L);
                found.setPostWaitMs(0L);
                found.setPreExecutionDataSourceHookName(null);
                found.setPostExecutionDataSourceHookName(null);
                
                if(found.getHeaders()!=null)
                {
                    found.getHeaders().clear();
                }
                else
                {
                    found.setHeaders(new HashMap<String, String>());
                }
                
                found.setAurl(tcReport.getActualUrl());
                found.setAcontent(tcReport.getRequestContent());
                found.setAexpectedNodes(tcReport.getAexpectedNodes());
                
                if(found.getAurl().startsWith(found.getBaseUrl().trim())) {
                    String turl = found.getAurl().replace(found.getBaseUrl().trim(), "");
                    found.setAurl(turl);
                }
                
                if(StringUtils.isNotBlank(tcReport.getRequestHeaders()))
                {
                    String[] headers = tcReport.getRequestHeaders().split("\n");
                    for(String header: headers)
                    {
                        if(header.indexOf(": ")!=-1)
                        {
                            found.getHeaders().put(header.substring(0, header.indexOf(": ")),
                                    header.substring(header.indexOf(": ")+2));
                        }
                    }
                }
                isAuthExec = found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null;
                context.getWorkflowContextHandler().initializeSuiteContextWithnum(index);
                
                TestCase authTestCaseT = authTestCase;
                
                boolean complete = false;
                
                if(isAuthExec)
                {
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
                            testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        //Server logs api
                        context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                        executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
                                gatfConfig.isServerLogsApiAuthEnabled());
                        complete = true;
                    }
                }
                
                if(reports!=null && authTestCaseT!=null && found.getName().equals(authTestCaseT.getName()))
                {
                    complete = true;
                }
                
                if(!complete)
                {
                    if(found.isSecure() && authTestCaseT==null)
                    {
                        throw new RuntimeException("No Authentication testcase found, please add one" +
                                "or change the testcase to unsecure mode");
                    }
                    
                    List<Map<String, String>> sceanrios = found.getRepeatScenariosOrig();
                    if (found.getRepeatScenarioProviderName() != null) {
                        // actual repeat scenario map
                        List<Map<String, String>> sceanriosp = context.getProviderTestDataMap(found.getRepeatScenarioProviderName());
                        if (sceanriosp != null) {
                            if (sceanrios != null) {
                                sceanrios.addAll(sceanriosp);
                            } else {
                                sceanrios = sceanriosp;
                            }
                        }
                        // repeat scenario map obtained from #responseMapped functions
                        else {
                            List<Map<String, String>> sceanriowk = context.getWorkflowContextHandler().getSuiteWorkflowScenarioContextValues(found, found.getRepeatScenarioProviderName());
                            if (sceanriowk != null) {
                                if (sceanrios != null) {
                                    sceanrios.addAll(sceanriowk);
                                } else {
                                    sceanrios = sceanriowk;
                                }
                            }
                        }
                        found.setRepeatScenarios(sceanrios);
                    } else {
                        found.setRepeatScenarios(null);
                        found.setNumberOfExecutions(1);
                    }
                    
                    reports = context.getSingleTestCaseExecutor().executeDirectTestCase(found, 
                            testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        //Server logs api
                        context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                        executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
                                gatfConfig.isServerLogsApiAuthEnabled());
                    }
                }
                
                lock.unlock();
                
                testCaseExecutorUtil.shutdown();
            }
            else if(isServerLogsApi)
            {
                AcceptanceTestContext context = null;
                try {
                	GatfPlugin executorMojo = f.apply("executor");
                    executorMojo.initilaizeContext(gatfConfig, false);
                    context = executorMojo.getContext();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Please Execute the GATF Suite first..");
                }
                
                TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
                
                boolean isAuthExec = false;
                TestCase authTestCaseT = context.getServerLogApi(true);
                isAuthExec = gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT!=null;
                //Server logs api
                context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                
                boolean complete = false;
                
                if(isAuthExec)
                {
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
                            testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        complete = true;
                    }
                }
                
                if(!complete)
                {
                    TestCase found = context.getServerLogApi(false);
                    if(gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT==null)
                    {
                        throw new RuntimeException("No Authentication testcase found, please add one" +
                                "or change the testcase to unsecure mode");
                    }
                    if(testCaseName.equals(found.getName()))
                    {
                        reports = context.getSingleTestCaseExecutor().execute(found, 
                                testCaseExecutorUtil);
                    }
                }
                
                testCaseExecutorUtil.shutdown();
            }
            else if(isExternalLogsApi)
            {
                TestCase origfound = null;
                String filePath = basepath + File.separator + testcaseFileName;
                if(!new File(filePath).exists()) {
                    throw new RuntimeException("External API Test case file does not exist");
                }
                
                TestCase authTestCaseT = null;
                List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
                for (TestCase tc : tcs) {
                    if(tc.getName().equals(testCaseName)) {
                        origfound = tc;
                    } else if(tc.getName().equals("authapi")) {
                        authTestCaseT = tc;
                    }
                }
                if(origfound==null) {
                    throw new RuntimeException("External API Testcase does not exist");
                }
                
                AcceptanceTestContext context = null;
                try {
                	GatfPlugin executorMojo = f.apply("executor");
                    executorMojo.initilaizeContext(gatfConfig, false);
                    context = executorMojo.getContext();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Please Execute the GATF Suite first..");
                }
                
                TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
                
                boolean isAuthExec = false;
                
                TestCase found = new TestCase(origfound);
                found.setSimulationNumber(0);
                found.setSourcefileName(testcaseFileName);
                found.setExternalApi(true);
                
                if(found.getHeaders()==null)
                {
                    found.setHeaders(new HashMap<String, String>());
                }
                
                if(!action.equals("playTest") && tcReport!=null)
                {
                    tcReport.setTestCaseExternalApi(found);
                }
                
                if(action.equals("getContent"))
                {
                    if(tcReport == null) {
                        throw new RuntimeException("Invalid testcase report details provided");
                    }
                    
                    if(!StringUtils.isBlank(found.getContent())) 
                    {
                        VelocityContext vcontext = new VelocityContext();
                        vcontext.put("report", tcReport);
                        
                        StringWriter writer = new StringWriter();
                        context.getWorkflowContextHandler().getEngine()
                            .evaluate(vcontext, writer, "ERROR", found.getContent());
                        String content = writer.toString();
                        
                        writer = new StringWriter();
                        context.getWorkflowContextHandler().getEngine()
                            .evaluate(vcontext, writer, "ERROR", found.getUrl());
                        String url = writer.toString();
                        
                        TestCaseReport nrep = new TestCaseReport();
                        nrep.setUrl(url);
                        nrep.setRequestContent(content);
                        
                        String configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(nrep);
                        return new Object[]{HttpStatus.OK_200, configJson, MediaType.APPLICATION_JSON, nrep};
                    }
                }
                else if(action.equals("createIssue"))
                {
                    if(tcReport == null) {
                        throw new RuntimeException("Invalid Issue details provided");
                    }
                    
                    found.setUrl(tcReport.getUrl());
                    found.setContent(tcReport.getRequestContent());
                }
                
                isAuthExec = gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT!=null;
                //Exrternal issue api
                context.getWorkflowContextHandler().initializeSuiteContextWithnum(-2);
                
                boolean complete = false;
                
                if(isAuthExec)
                {
                    authTestCaseT = new TestCase(authTestCaseT);
                    authTestCaseT.setSimulationNumber(0);
                    authTestCaseT.setSourcefileName(testcaseFileName);
                    authTestCaseT.setExternalApi(true);
                    
                    if(authTestCaseT.getHeaders()==null)
                    {
                        authTestCaseT.setHeaders(new HashMap<String, String>());
                    }
                    authTestCaseT.setExternalApi(true);
                    authTestCaseT.validate(context.getHttpHeaders(), null);
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
                            testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        complete = true;
                    }
                }

                if(!complete)
                {
                    if(found.isSecure() && authTestCaseT==null)
                    {
                        throw new RuntimeException("No Authentication testcase found, please add one" +
                                "or change the testcase to unsecure mode");
                    }
                    found.validate(context.getHttpHeaders(), null);
                    reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
                }
                
                testCaseExecutorUtil.shutdown();
            }
            else
            {
                TestCase origfound = null;
                String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator
                        + testcaseFileName;
                if(!new File(filePath).exists()) {
                    throw new RuntimeException("Test case file does not exist");
                }
                
                if(!testcaseFileName.toLowerCase().endsWith(".sel")) {
                    List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
                    for (TestCase tc : tcs) {
                        if(tc.getName().equals(testCaseName)) {
                            origfound = tc;
                            break;
                        }
                    }
                    if(origfound==null) {
                        throw new RuntimeException("Testcase does not exist");
                    }
                }

                GatfPlugin executorMojo = f.apply("executor");
                
                lock.lock();
                
                if(testcaseFileName.toLowerCase().endsWith(".sel")) {
                    executorMojo.initilaizeContext(gatfConfig, true);
                    gatfConfig.setSeleniumScripts(new String[]{filePath});
                    executorMojo.doSeleniumTest(gatfConfig, null);
                    String cont = "Please check Reports section for the selenium test results";
                    return new Object[]{HttpStatus.OK_200, cont, MediaType.TEXT_PLAIN, null};
                }
                
                if(!testcaseFileName.toLowerCase().endsWith(".sel")) {
                    
                    if(mojo!=null && mojo.getContext()!=null) {
                        context = mojo.getContext();
                    } else if(context==null) {
                        try {
                            executorMojo.initilaizeContext(gatfConfig, true);
                            context = executorMojo.getContext();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException("Please Execute the GATF Suite first..");
                        }
                    }
                    
                    context.setGatfExecutorConfig(gatfConfig);
                    executorMojo.setContext(context);
                    executorMojo.getAllTestCases(context, null, null);
                    authTestCase = executorMojo.getAuthTestCase();
                    
                    TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
                    
                    boolean isAuthExec = false;
                    TestCase found = new TestCase(origfound);
                    found.setSimulationNumber(0);
                    found.setSourcefileName(testcaseFileName);
                    found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
                    found.setLogicalValidations(null);
                    found.setExecuteOnCondition(null);
                    
                    if(found.getHeaders()==null)
                    {
                        found.setHeaders(new HashMap<String, String>());
                    }
                    
                    isAuthExec = found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null;
                    if(!fromApiPlugin) {
                        context.getWorkflowContextHandler().initializeSuiteContextWithnum(index);
                    }
                    
                    TestCase authTestCaseT = authTestCase;
                    
                    boolean complete = false;
                    
                    if(isAuthExec)
                    {
                        reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, testCaseExecutorUtil);
                        if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                        {
                          //Server logs api
                            context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                            executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
                                    gatfConfig.isServerLogsApiAuthEnabled());
                            complete = true;
                        }
                    }
                    
                    if(reports!=null && authTestCaseT!=null && found.getName().equals(authTestCaseT.getName()))
                    {
                        complete = true;
                    }
                    
                    if(!complete)
                    {
                        if(found.isSecure() && authTestCaseT==null)
                        {
                            throw new RuntimeException("No Authentication testcase found, please add one" +
                                    "or change the testcase to unsecure mode");
                        }
                        
                        List<Map<String, String>> sceanrios = found.getRepeatScenariosOrig();
                        if (found.getRepeatScenarioProviderName() != null) {
                            // actual repeat scenario map
                            List<Map<String, String>> sceanriosp = context.getProviderTestDataMap(found.getRepeatScenarioProviderName());
                            if (sceanriosp != null) {
                                if (sceanrios != null) {
                                    sceanrios.addAll(sceanriosp);
                                } else {
                                    sceanrios = sceanriosp;
                                }
                            }
                            // repeat scenario map obtained from #responseMapped functions
                            else {
                                List<Map<String, String>> sceanriowk = context.getWorkflowContextHandler().getSuiteWorkflowScenarioContextValues(found, found.getRepeatScenarioProviderName());
                                if (sceanriowk != null) {
                                    if (sceanrios != null) {
                                        sceanrios.addAll(sceanriowk);
                                    } else {
                                        sceanrios = sceanriowk;
                                    }
                                }
                            }
                            found.setRepeatScenarios(sceanrios);
                        } else {
                            found.setRepeatScenarios(null);
                            found.setNumberOfExecutions(1);
                        }
                        
                        reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
                        if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                        {
                            //Server logs api
                            context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                            executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
                                    gatfConfig.isServerLogsApiAuthEnabled());
                        }
                    }
                    
                    testCaseExecutorUtil.shutdown();
                }
                
                lock.unlock();
            }
            
            if(!testcaseFileName.toLowerCase().endsWith(".sel")) {
                if(reports==null || reports.size()==0)
                {
                    throw new RuntimeException("Could not execute Testcase");
                }
                
                TestCaseReport report = reports.get(0);
                ReportHandler.populateRequestResponseHeaders(report);
                String configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(report);
                return new Object[]{HttpStatus.OK_200, configJson, MediaType.APPLICATION_JSON, report};
            }
        }
	    return new Object[]{HttpStatus.NOT_FOUND_404, null, null, null};
	}
}
