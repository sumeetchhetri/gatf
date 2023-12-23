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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.selenium.SeleniumTest.GatfRunTimeError;
import com.gatf.selenium.SeleniumTest.GatfRunTimeErrors;

public class GatfPluginExecutionHandler extends HttpHandler {

	private AtomicBoolean isStarted = new AtomicBoolean(false);
	
	private AtomicBoolean isJsonErr = new AtomicBoolean(false);
	
	private AtomicBoolean isDone = new AtomicBoolean(false);
	
	private AtomicReference<byte[]> leftOver = new AtomicReference<byte[]>();
	
	private Thread _executorThread = null;
	
	private volatile String status = "";
	
	private GatfConfigToolMojoInt mojo;
	
	private Function<String, GatfPlugin> f;
	
	public GatfPluginExecutionHandler(GatfConfigToolMojoInt mojo, Function<String, GatfPlugin> f) {
		super();
		this.mojo = mojo;
		this.f = f;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	GatfPluginConfig gatfConfig = null;
    	final String pluginType = request.getParameter("pluginType");
		try {
			gatfConfig = getGatfPluginConfig(pluginType, mojo);
			if(request.getMethod().equals(Method.GET) ) {
				if(isStarted.get()) {
					if(pluginType.equals("executor")) {
						byte[] status = RuntimeReportUtil.getEntry();
						if(status==null)
							throw new RuntimeException("{\"error\": \"Execution already in progress..\"}");
						else
							throw new RuntimeException(new String(status, "UTF-8"));
					}
					throw new RuntimeException("{\"error\": \"Execution already in progress..\"}");
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					
					String temp = status;
					status = "";
					if(isJsonErr.get()) {
						isJsonErr.set(false);
						throw new RuntimeException(temp);
					}
					if(StringUtils.isNotBlank(temp))
						throw new RuntimeException("{\"error\": \"Execution failed with Error - " + temp + "\"}");
					
					String text = "{\"error\": \"Execution completed, check Reports Section\"}";
        			response.setContentType(MediaType.APPLICATION_XML + "; charset=utf-8");
		            response.setContentLength(text.getBytes("UTF-8").length);
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(!isStarted.get()) {
					throw new RuntimeException("{\"error\": \"Please Start the Execution....\"}");
				} else {
					throw new RuntimeException("{\"error\": \"Unknown Error...\"}");
				}
			}
			else if(request.getMethod().equals(Method.PUT)) {
				final List<String> files = WorkflowContextHandler.OM.readValue(request.getInputStream(), 
						TypeFactory.defaultInstance().constructCollectionType(List.class, String.class));
				
				if(!isStarted.get() && !isDone.get()) {
					leftOver.set(null);
					isStarted.set(true);
					isJsonErr.set(false);
					if(pluginType.equals("executor")) {
						RuntimeReportUtil.registerConfigUI();
					}
					final GatfPluginConfig config = gatfConfig;
					_executorThread = new Thread(new Runnable() {
						public void run() {
							GatfPlugin executorMojo = f.apply(pluginType);
    						try {
								executorMojo.doExecute(config, files);
								initializeMojoProps(executorMojo, mojo);
							} catch (Throwable e) {
								e.printStackTrace();
								if(e instanceof GatfRunTimeError) {
									Map<String, Object> h = new HashMap<>();
									h.put("error", ((GatfRunTimeError)e).getDetails());
									try {
										status = WorkflowContextHandler.OM.writeValueAsString(h);
										isJsonErr.set(true);
									} catch (JsonProcessingException e1) {
									}
								} else if(e instanceof GatfRunTimeErrors) {
									GatfRunTimeErrors errs = (GatfRunTimeErrors)e;
									Map<String, Object> h = new HashMap<>();
									h.put("error", errs.getAll().get(0).getDetails());
									List<Object[]> others = new ArrayList<>();
									for (int i=1;i<errs.getAll().size();i++) {
										others.add(errs.getAll().get(i).getDetails());
									}
									h.put("others", others);
									try {
										status = WorkflowContextHandler.OM.writeValueAsString(h);
										isJsonErr.set(true);
									} catch (JsonProcessingException e1) {
									}
								}
								if(!isJsonErr.get()) {
									if(e.getMessage()!=null)
										status = e.getMessage();
									else
										status = ExceptionUtils.getStackTrace(e);
								}
							} finally { 
								executorMojo.shutdown();
							}
    						
    						//This while loop makes the UI wait to finish loading all sample summary
    						//while(!RuntimeReportUtil.isDone()) {
    						    try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                } 
    						//}
    						
    						isDone.set(true);
							isStarted.set(false);

							if(pluginType.equals("executor")) {
								byte[] status = RuntimeReportUtil.getEntry();
								if(status!=null && status.length>0) {
									leftOver.set(status);
								}
								RuntimeReportUtil.unRegisterConfigUI();
							}
						}

						private void initializeMojoProps(GatfPlugin executorMojo, GatfConfigToolMojoInt mojo) {
							if(executorMojo instanceof GatfTestCaseExecutorUtil) {
								mojo.setContext(((GatfTestCaseExecutorUtil)executorMojo).getContext());
								mojo.setAuthTestCase(((GatfTestCaseExecutorUtil)executorMojo).getAuthTestCase());
							}
						}
					});
					boolean isLoadTestingEnabled = false;
					if(config instanceof GatfExecutorConfig) {
						GatfExecutorConfig econfig = (GatfExecutorConfig)config;
						isLoadTestingEnabled = econfig.isLoadTestingEnabled() && econfig.getLoadTestingTime() > 10000 && !econfig.isCompareEnabled();
					}
					_executorThread.start();
					String text = "{\"error\": \"Execution Started\", \"loadTestingEnabled\": "+isLoadTestingEnabled+"}";
        			response.setContentType(MediaType.APPLICATION_XML + "; charset=utf-8");
		            response.setContentLength(text.getBytes("UTF-8").length);
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					
					String temp = status;
					status = "";
					if(isJsonErr.get()) {
						isJsonErr.set(false);
						throw new RuntimeException(temp);
					}
					if(StringUtils.isNotBlank(temp))
						throw new RuntimeException("{\"error\": \"Execution failed with Error - " + temp + "\"}");
					
					byte[] text = "{\"error\": \"Execution completed, check Reports Section\"}".getBytes("UTF-8");
					if(leftOver.get()!=null) {
						text = new String(leftOver.get(), "UTF-8").replaceFirst("Execution already in progress..", "Execution completed, check Reports Section").getBytes("UTF-8");
					}
        			response.setContentType(MediaType.APPLICATION_XML + "; charset=utf-8");
		            response.setContentLength(text.length);
		            response.getOutputStream().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(isStarted.get()) {
					throw new RuntimeException("{\"error\": \"Execution already in progress..\"}");
				} else {
					throw new RuntimeException("{\"error\": \"Unknown Error...\"}");
				}
        	} else if(request.getMethod().equals(Method.DELETE) ) {
        		if(pluginType.equals("executor")) {
					RuntimeReportUtil.unRegisterConfigUI();
				}
        		if(isStarted.get() && _executorThread!=null) {
        			_executorThread.interrupt();
        			_executorThread = null;
				} else if(!isStarted.get()) {
					throw new RuntimeException("{\"error\": \"Testcase execution is not in progress...\"}");
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					isJsonErr.set(false);
					
					String temp = status;
					status = "";
					if(isJsonErr.get()) {
						throw new RuntimeException(temp);
					}
					if(StringUtils.isNotBlank(temp))
						throw new RuntimeException("{\"error\": \"Execution failed with Error - " + temp + "\"}");
					
					String text = "{\"error\": \"Execution completed, check Reports Section\"}";
        			response.setContentType(MediaType.APPLICATION_XML + "; charset=utf-8");
		            response.setContentLength(text.getBytes("UTF-8").length);
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else {
					throw new RuntimeException("{\"error\": \"Unknown Error...\"}");
				}
        	}
		} catch (Exception e) {
			GatfConfigToolUtil.handleErrorJson(e, response, HttpStatus.BAD_REQUEST_400);
			return;
		}
    }
	
	protected static GatfPluginConfig getGatfPluginConfig(String type, GatfConfigToolMojoInt mojo) throws Exception
	{
		if(type.equals("executor"))
		{
			GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
			return gatfConfig;
		}
		else
		{
			GatfConfiguration gatfConfig = GatfConfigToolUtil.getGatfConfiguration(mojo, null);
			return gatfConfig;
		}
	}
}
