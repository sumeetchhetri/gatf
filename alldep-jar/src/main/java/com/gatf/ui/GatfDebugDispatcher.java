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
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.ReportHandler;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

/**
 * G8 — the online-debugger protocol, extracted verbatim from GatfReportsHandler (behaviour unchanged):
 * the PUT ?action=debug command dispatch (line codes 0, -1..-7, b&lt;n&gt;/r&lt;n&gt;) and the GET
 * ?action=debugStream SSE push loop. Sessions stay in {@link GatfReportsHandler#dbgSessions} (shared with
 * the /gatfjdb WebSocket).
 */
final class GatfDebugDispatcher {

    private GatfDebugDispatcher() {}

    /** GET ?action=debugStream — server-push of {s,r,p,n,v,t} frames (SSE). */
    static void streamDebug(Request request, Response response) throws Exception {
					// Server-push debug channel (delta-040): stream {s,r,p,n,v,t} over SSE, pushing on change.
					// Replaces the client's ~900ms heartbeat poll. Commands still arrive on PUT ?action=debug.
					String dsSessionId = request.getParameter("sessionId");
					response.setContentType("text/event-stream; charset=utf-8");
					response.setHeader("Cache-Control", "no-cache");
					response.setStatus(HttpStatus.OK_200);
					java.io.Writer dsW = response.getWriter();
					GatfSelDebugger dsDbg = GatfReportsHandler.dbgSessions.get(dsSessionId);
					for(int dsI=0; dsDbg==null && dsI<40; dsI++) { Thread.sleep(50); dsDbg = GatfReportsHandler.dbgSessions.get(dsSessionId); }
					if(dsDbg==null) {
						dsW.write("data: {\"s\":false,\"m\":\"No debug session\"}\n\n"); dsW.flush(); return;
					}
					int dsLp=Integer.MIN_VALUE, dsLn=Integer.MIN_VALUE, dsLv=Integer.MIN_VALUE; boolean dsLr=true, dsFirst=true; String dsLt=null; long dsVer=-1;
					try {
						while(true) {
							GatfSelDebugger dsS = GatfReportsHandler.dbgSessions.get(dsSessionId);
							if(dsS==null) { dsW.write("data: {\"s\":true,\"r\":false}\n\n"); dsW.flush(); break; }
							boolean dsR=dsS.getRunning(); int dsP=dsS.getPrevLine(); int dsN=dsS.getNextLine(); int dsV=dsS.getState(); String dsT=dsS.getCurrFile();
							boolean dsCh = dsFirst || dsP!=dsLp || dsN!=dsLn || dsV!=dsLv || dsR!=dsLr || (dsT==null?dsLt!=null:!dsT.equals(dsLt));
							if(dsCh) {
								Map<String, Object> dsOut = new HashMap<>();
								dsOut.put("s", true); dsOut.put("r", dsR); dsOut.put("p", dsP); dsOut.put("n", dsN); dsOut.put("v", dsV); dsOut.put("t", dsT);
								dsW.write("data: " + WorkflowContextHandler.OM.writeValueAsString(dsOut) + "\n\n"); dsW.flush();
								dsLp=dsP; dsLn=dsN; dsLv=dsV; dsLr=dsR; dsLt=dsT; dsFirst=false;
							}
							if(!dsR) { GatfReportsHandler.dbgSessions.remove(dsSessionId, dsS); break; }
							dsVer = dsS.awaitChange(dsVer, 1000); // delta-075: event-driven (was a 150 ms poll)
						}
					} catch (Exception dsE) { /* client disconnected / write failed - end stream */ }
					return;
    }

    /** PUT ?action=debug — returns {status, body, contentType, null} exactly as executeTest did. */
    static Object[] dispatch(GatfConfigToolMojoInt mojo, GatfPlugin executorMojo, GatfExecutorConfig gatfConfig, Request request,
            String testcaseFileName, String sessionId, int selDbgline, boolean selDbgBrkRemove) throws Exception {
        Map<String, Object> out = new HashMap<>();
                    	if(selDbgline<-8) {
                    		String cont = "Fail: Invalid debug command";
    	                    return new Object[]{HttpStatus.OK_200, cont.getBytes("UTF-8"), MediaType.TEXT_PLAIN, null};
                    	}
                    	if(StringUtils.isBlank(sessionId)) {
                    		String cont = "Fail: Invalid sessionId";
    	                    return new Object[]{HttpStatus.OK_200, cont.getBytes("UTF-8"), MediaType.TEXT_PLAIN, null};
                    	}
                    	GatfSelDebugger dbgSession = GatfReportsHandler.dbgSessions.get(sessionId);
                    	if(selDbgline==0) {
                    		if(dbgSession!=null) {
                    			GatfReportsHandler.dbgSessions.get(sessionId).destroy();
                    			GatfReportsHandler.dbgSessions.remove(sessionId);
                    			dbgSession = null;
                        	}
                    		String configPath = null;
    	                    synchronized (mojo) {
    	        				if(new File(mojo.getRootDir(), "gatf-config.json").exists()) {
    	        					configPath = new File(mojo.getRootDir(), "gatf-config.json").getAbsolutePath();
    	        				} else {
    	        					configPath = new File(mojo.getRootDir(), "gatf-config.xml").getAbsolutePath();
    	        				}
    	        			}
    	                    executorMojo.initilaizeContext(gatfConfig, true);
    	                    dbgSession = executorMojo.debugSeleniumTest(gatfConfig, testcaseFileName, configPath);
    	                    GatfReportsHandler.dbgSessions.put(sessionId, dbgSession);
    	                    out.put("s", true);
    	                    out.put("c", dbgSession.getSrcCode());
    	                    out.put("l", dbgSession.getDebuggableLines());
    	                    out.put("i", dbgSession.getPrevLine());
    	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
    	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
                    	} else {
                    		if(dbgSession==null) {
        	                    out.put("s", false);
        	                    out.put("m", "No Debug session running for script " + testcaseFileName);
        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
                        	}
                    		switch (selDbgline) {
								case -1: {
									String line = request.getParameter("sline");
									//F5 Step into
									if(!dbgSession.enableStepIntoRequest(line)) {
		        	                    out.put("s", false);
		        	                    out.put("m", "Please suspend the debugger first");
		        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
		        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
									}
									break;
								}
								case -2: {
									//F7 Step out
									if(!dbgSession.enableStepOutRequest()) {
		        	                    out.put("s", false);
		        	                    out.put("m", "Please suspend the debugger first");
		        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
		        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
									}
									break;
								}
								case -3: {
									//F6 Step over
									if(!dbgSession.enableStepOverRequest()) {
		        	                    out.put("s", false);
		        	                    out.put("m", "Please suspend the debugger first");
		        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
		        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
									}
									break;
								}
								case -4: {
									//F8 Continue
									dbgSession.resume();
									break;
								}
								case -5: {
									// Ctrl C Stop
									dbgSession.suspend();
									break;
								}
								case -6: {
									// Ctrl X Terminate — delta-075: also drop the session (only -7 used to, and
									// the push-channel clients no longer send -7)
									dbgSession.destroy();
									GatfReportsHandler.dbgSessions.remove(sessionId, dbgSession);
									break;
								}
								case -7: {
									//Status check call 1 - running, 0 - stopped
	        	                    out.put("s", true);
	        	                    out.put("r", dbgSession.getRunning());
	        	                    out.put("p", dbgSession.getPrevLine());
	        	                    out.put("n", dbgSession.getNextLine());
	        	                    out.put("v", dbgSession.getState());
	        	                    out.put("t", dbgSession.getCurrFile());
	        	                    if(!dbgSession.getRunning()) {
	        	                    	GatfReportsHandler.dbgSessions.remove(sessionId);
	        	                    }
	        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
	        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
								}
								default: {
									if(selDbgBrkRemove) {
										//Click on line number in script (remove breakpoint)
										if(!dbgSession.unsetBreakPoint(selDbgline+1)) {
			        	                    out.put("s", false);
			        	                    out.put("m", "No breakpoint found at line + " + (selDbgline+1));
			        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
			        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
										}
									} else {
										//Click on line number in script (add breakpoint)
										if(!dbgSession.setBreakPoint(selDbgline+1)) {
			        	                    out.put("s", false);
			        	                    out.put("m", "Breakpoint cannot be added on line " + (selDbgline+1));
			        	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
			        	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
										}
									}
									break;
								}
							}
    	                    out.put("s", true);
    	                    out.put("p", dbgSession.getPrevLine());
    	                    out.put("n", dbgSession.getNextLine());
    	                    ReportHandler.updatePaths("sel", out);
    	                    byte[] respo = WorkflowContextHandler.OM.writeValueAsBytes(out);
    	                    return new Object[]{HttpStatus.OK_200, respo, MediaType.APPLICATION_JSON, null};
                    	}
    }
}
