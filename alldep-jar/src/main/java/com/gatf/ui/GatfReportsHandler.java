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
package com.gatf.ui;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.selenium.Command;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

public class GatfReportsHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	private Function<String, GatfPlugin> f;
	
	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	private ReentrantLock lock = new ReentrantLock();
	
	//Only one debug session per instance allowed for now, later we can support multiple as well
	//but for now lets keep it simple
	// Shared (static) so the WebSocket push app (GatfDebugWebSocket) and this handler use the same
	// sessions; ConcurrentHashMap since the WS pusher thread reads while HTTP threads mutate.
	static final Map<String, GatfSelDebugger> dbgSessions = new java.util.concurrent.ConcurrentHashMap<String, GatfSelDebugger>();
	
	public GatfReportsHandler(GatfConfigToolMojoInt mojo, Function<String, GatfPlugin> f) {
		super();
		this.mojo = mojo;
		this.f = f;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	try {
    		final GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
			String basepath = gatfConfig.getOutFilesBasePath()==null?mojo.getRootDir():gatfConfig.getOutFilesBasePath();
			String dirPath = basepath + File.separator + gatfConfig.getOutFilesDir();
			if(!new File(dirPath).exists()) {
				new File(dirPath).mkdir();
			}
			if(request.getMethod().equals(Method.GET) ) {
				String action = request.getParameter("action");
				String type = request.getParameter("type");
				if(StringUtils.isNotBlank(action) && action.equals("paths") && StringUtils.isNotBlank(type)) {
					Map<String, Object> out = new HashMap<>();
					ReportHandler.updatePaths(type, out);
					String text = WorkflowContextHandler.OM.writeValueAsString(out);
			    	writeJson(response, text);
			        return;
				}
				else if(StringUtils.isNotBlank(action) && action.equals("debugStream")) {
					GatfDebugDispatcher.streamDebug(request, response);
					return;
				}
			    new CacheLessStaticHttpHandler(GatfConfigToolUtil.authSrc!=null, dirPath).service(request, response);
			} else if(request.getMethod().equals(Method.PUT) ) {
			    String action = request.getParameter("action");
			    String sessionId = request.getParameter("sessionId");
			    String testcaseFileName = request.getParameter("testcaseFileName");
			    String testCaseName = request.getParameter("testCaseName");
			    boolean isServerLogsApi = request.getParameter("isServerLogsApi")!=null;
			    boolean isExternalLogsApi = request.getParameter("isExternalLogsApi")!=null;
			    int selDbgline = -1;
			    boolean selDbgBrkRemove = false, setBreakpoint = false;
			    TestCaseReport tcReport = null;
			    if(isRunControl(action))
			    {
			    	writeJson(response, runControl(action));
			    	return;
			    }
                else if(action.equals("validateTest") && testcaseFileName.endsWith(".sel"))
			    {
			    	String status = Command.validateSel(new String[] {"-validate-sel", testcaseFileName, new File(gatfConfig.getTestCasesBasePath(), "gatf-config.json").exists() ? "gatf-config.json" : "gatf-config.xml", gatfConfig.getTestCasesBasePath()}, null, false);
			    	writeJson(response, status);
			        return;
			    }
			    else if(action.equals("replayTest"))
			    { 
			        tcReport = WorkflowContextHandler.OM.readValue(request.getInputStream(), TestCaseReport.class);
			        if(tcReport == null) {
			            throw new RuntimeException("Invalid testcase report details provided");
			        }
			    } 
			    else if(isExternalLogsApi && !action.equals("playTest"))
			    {
			        tcReport = WorkflowContextHandler.OM.readValue(request.getInputStream(), TestCaseReport.class);
			    }
			    else if(action.equals("debug") && testcaseFileName.endsWith(".sel"))
			    {
			    	String linenum = request.getParameter("line");
			    	if(linenum.startsWith("r")) {
			    		selDbgBrkRemove = true;
			    		linenum = linenum.substring(1);
			    		setBreakpoint = true;
			    	} else if(linenum.startsWith("b")) {
			    		selDbgBrkRemove = false;
			    		linenum = linenum.substring(1);
			    		setBreakpoint = true;
			    	}
			    	selDbgline = Integer.parseInt(linenum);
			    }
			    else if(!action.equals("playTest"))
			    {
			    	throw new RuntimeException("Invalid action specified..");
			    }
			    Object[] out = executeTest(request, gatfConfig, tcReport, action, testcaseFileName, testCaseName, isServerLogsApi, isExternalLogsApi, 
			    		0, false, selDbgline, setBreakpoint, selDbgBrkRemove, sessionId);
			    if(out[1]!=null) {
			        response.setContentType(out[2].toString() + "; charset=utf-8");
			        response.setContentLength(((byte[])out[1]).length);
			        response.getOutputStream().write((byte[])out[1]);
			    }
			    response.setStatus((HttpStatus)out[0]);
			}
    	} catch (Exception e) {
    		GatfConfigToolUtil.handleError(e, response, null);
		} finally {
		    if(lock.isLocked()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                }
		    }
		}
    }

	public Object[] executeTest(Request request, GatfExecutorConfig gatfConfig, TestCaseReport tcReport, String action, String testcaseFileName, 
	        String testCaseName, boolean isServerLogsApi, boolean isExternalLogsApi, int index, boolean fromApiPlugin, int selDbgline, 
	        boolean setBreakpoint, boolean selDbgBrkRemove, String sessionId) throws Exception {
	    String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
	    if(action.equals("replayTest") || action.equals("playTest") || action.equals("createIssue") || action.equals("getContent") || action.equals("debug"))
        {
            boolean isReplay = action.equals("replayTest");
            // Play a regular (.xml) API test: re-run it AS DEFINED (no captured-request override). Mirrors the
            // external-API branch's playTest vs replayTest split. replayTest still re-sends the captured request.
            boolean isPlayApi = action.equals("playTest") && !isServerLogsApi && !isExternalLogsApi
                    && testcaseFileName!=null && !testcaseFileName.toLowerCase().endsWith(".sel");
            boolean applyCaptured = isReplay; // replay = captured request; play = the definition
            List<TestCaseReport> reports = null;
            
            if(isReplay || isPlayApi)
            {
                TestCase origfound = null;
                String filePath = null;
                if(gatfConfig.getTestCaseDir()!=null) {
	                filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator
	                        + testcaseFileName;
                } else {
	                filePath = basepath + File.separator + testcaseFileName;
                }
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
                
                GatfPlugin executorMojo = f.apply("executor-api");

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
                testCaseExecutorUtil.setSingleExecutionContext(true);
                
                boolean isAuthExec = false;
                
                TestCase found = new TestCase(origfound);
                found.setSimulationNumber(index);
                found.setSourcefileName(testcaseFileName);
                if(StringUtils.isBlank(found.getBaseUrl())) {
                	found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
                }
                found.setLogicalValidations(null);
                found.setExecuteOnCondition(null);
                found.setPreWaitMs(0L);
                found.setPostWaitMs(0L);
                found.setPreExecutionDataSourceHookName(null);
                found.setPostExecutionDataSourceHookName(null);
                
                if(found.getHeaders()==null)
                {
                    found.setHeaders(new HashMap<String, String>());
                }
                
                // Replay re-sends the captured request (actual URL/body/headers as sent, editable in the report).
                // Play leaves the definition untouched — no header clear, no actual-url/content override.
                if(applyCaptured && tcReport!=null)
                {
                    found.getHeaders().clear();
                    found.setAurl(tcReport.getActualUrl());
                    found.setAcontent(tcReport.getRequestContent());
                    found.setAexpectedNodes(tcReport.getAexpectedNodes());
                    
                    if(found.getAurl()!=null && found.getBaseUrl()!=null && found.getAurl().startsWith(found.getBaseUrl().trim())) {
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
                                found.getHeaders().put(header.substring(0, header.indexOf(": ")), header.substring(header.indexOf(": ")+2));
                            }
                        }
                    }
                }
                isAuthExec = found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null;
                context.getWorkflowContextHandler().initializeSuiteContextWithnum(index);
                
                TestCase authTestCaseT = authTestCase;
                
                boolean complete = false;
                
                if(isAuthExec)
                {
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        //Server logs api
                        context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                        executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil, gatfConfig.isServerLogsApiAuthEnabled());
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
                        throw new RuntimeException("No Authentication testcase found, please add one or change the testcase to unsecure mode");
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
                    
                    reports = context.getSingleTestCaseExecutor().executeDirectTestCase(found, testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        //Server logs api
                        context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
                        executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil, gatfConfig.isServerLogsApiAuthEnabled());
                    }
                }
                
                lock.unlock();
                
                testCaseExecutorUtil.shutdown();
            }
            else if(isServerLogsApi)
            {
                AcceptanceTestContext context = null;
                try {
                	GatfPlugin executorMojo = f.apply("executor-api");
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
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, testCaseExecutorUtil);
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
                        reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
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
                	GatfPlugin executorMojo = f.apply("executor-api");
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
                        context.getWorkflowContextHandler().getEngine().evaluate(vcontext, writer, "ERROR", found.getContent());
                        String content = writer.toString();
                        
                        writer = new StringWriter();
                        context.getWorkflowContextHandler().getEngine().evaluate(vcontext, writer, "ERROR", found.getUrl());
                        String url = writer.toString();
                        
                        TestCaseReport nrep = new TestCaseReport();
                        nrep.setUrl(url);
                        nrep.setRequestContent(content);
                        
                        byte[] configJson = WorkflowContextHandler.OM.writeValueAsBytes(nrep);
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
                    reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, testCaseExecutorUtil);
                    if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
                    {
                        complete = true;
                    }
                }

                if(!complete)
                {
                    if(found.isSecure() && authTestCaseT==null)
                    {
                        throw new RuntimeException("No Authentication testcase found, please add one or change the testcase to unsecure mode");
                    }
                    found.validate(context.getHttpHeaders(), null);
                    reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
                }
                
                testCaseExecutorUtil.shutdown();
            }
            else
            {
                TestCase origfound = null;
                String filePath = null;
                if(gatfConfig.getTestCaseDir()!=null) {
	                filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator
	                        + testcaseFileName;
                } else {
	                filePath = basepath + File.separator + testcaseFileName;
                }
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

                GatfPlugin executorMojo = f.apply("executor-"+(testcaseFileName.toLowerCase().endsWith(".sel")?"sel":"api"));
                
                lock.lock();
                
                if(testcaseFileName.toLowerCase().endsWith(".sel")) {
                    gatfConfig.setSeleniumScripts(new String[]{testcaseFileName});
                    Map<String, Object> out = new HashMap<>();
                    if(action.equals("debug")) {
                    	return GatfDebugDispatcher.dispatch(mojo, executorMojo, gatfConfig, request, testcaseFileName, sessionId, selDbgline, selDbgBrkRemove);
                    } else {
                        context = new AcceptanceTestContext(gatfConfig, null);
                        executorMojo.setContext(context);
                        try {
                    	    executorMojo.doSeleniumTest(gatfConfig, null);
                        } finally {
                            context = null;
                        }
                    	String cont = "Please check Reports section for the selenium test results";
	                    out.put("msg", cont);
	                    ReportHandler.updatePaths("sel", out);
	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
                        return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
                    }
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
                    testCaseExecutorUtil.setSingleExecutionContext(true);
                    
                    boolean isAuthExec = false;
                    TestCase found = new TestCase(origfound);
                    found.setSimulationNumber(0);
                    found.setSourcefileName(testcaseFileName);
                    if(StringUtils.isBlank(found.getBaseUrl())) {
                    	found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
                    }
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
                            executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil, gatfConfig.isServerLogsApiAuthEnabled());
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
                            executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil, gatfConfig.isServerLogsApiAuthEnabled());
                        }
                    }
                    
                    testCaseExecutorUtil.shutdown();
                    context = null;
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
                report.setPaths(ReportHandler.getRDirs("api"));
                byte[] configJson = WorkflowContextHandler.OM.writeValueAsBytes(report);
                return new Object[]{HttpStatus.OK_200, configJson, MediaType.APPLICATION_JSON, report};
            }
        }
	    return new Object[]{HttpStatus.NOT_FOUND_404, null, null, null};
	}

	// ---- G8: extracted from service() (behaviour unchanged) ----

	private static void writeJson(Response response, String json) throws Exception {
		response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		response.setContentLength(json.getBytes("UTF-8").length);
		response.getWriter().write(json);
		response.setStatus(HttpStatus.OK_200);
	}

	private static boolean isRunControl(String action) {
		return action.equals("pauseElSearch") || action.equals("unPauseElSearch") || action.equals("restartSubtest")
				|| action.equals("stop") || action.equals("pausedLineNo");
	}

	// pauseElSearch / unPauseElSearch / pausedLineNo report the pause position; restartSubtest / stop act.
	// Same JSON as the four former inline blocks.
	private String runControl(String action) throws Exception {
		AcceptanceTestContext ctx = context;
		if(action.equals("restartSubtest") || action.equals("stop")) {
			if(ctx==null) return "{\"status\": false}";
			if(action.equals("restartSubtest")) ctx.restartSubtest(); else ctx.stopExec();
			Map<String, Object> out = new HashMap<>();
			out.put("status", true);
			return WorkflowContextHandler.OM.writeValueAsString(out);
		}
		if(ctx==null) {
			return action.equals("pausedLineNo") ? "{\"line\": -1, \"running\": false}" : "{\"status\": false, \"running\": false}";
		}
		if(action.equals("pauseElSearch")) ctx.pauseElSearch();
		else if(action.equals("unPauseElSearch")) ctx.unPauseElSearch();
		Map<String, Object> out = new HashMap<>();
		out.put("file", ctx.getPausedFile());
		out.put("line", ctx.getPausedLineNo());
		out.put("is_paused", ctx.isPauseElSearch(false));
		out.put("running", true);
		return WorkflowContextHandler.OM.writeValueAsString(out);
	}

	public void clearInactiveDebugSessions() {
		for (Map.Entry<String, GatfSelDebugger> e : dbgSessions.entrySet()) {
			GatfSelDebugger dbgSession = e.getValue();
			if(!dbgSession.getRunning() || !dbgSession.isActivityObserved()) {
				if(dbgSessions.remove(e.getKey(), dbgSession)) {
					try {
						dbgSession.destroy();
					} catch (Exception ignore) {
						// already gone
					}
				}
			}
		}
	}
}
