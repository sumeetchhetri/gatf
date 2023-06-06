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

import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.generator.core.GatfConfiguration;

public class GatfConfigurationHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	private Function<String, GatfPlugin> f = null;
	
	public GatfConfigurationHandler(GatfConfigToolMojoInt mojo, Function<String, GatfPlugin> f) {
		super();
		this.mojo = mojo;
		this.f = f;
	}

	
	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	String configType = request.getParameter("configType");
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			String configJson = null;
    			if(configType.equalsIgnoreCase("executor")) {
    				GatfConfigToolUtil.createConfigFileIfNotExists(mojo, true, null);
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        			configJson = WorkflowContextHandler.OM.writeValueAsString(gatfConfig);
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfigToolUtil.createConfigFileIfNotExists(mojo, false, null);
    				GatfConfiguration gatfConfig = GatfConfigToolUtil.getGatfConfiguration(mojo, null);
        			configJson = WorkflowContextHandler.OM.writeValueAsString(gatfConfig);
    			}
    			response.setContentType(MediaType.APPLICATION_JSON);
	            response.setContentLength(configJson.length());
	            response.getWriter().write(configJson);
	            response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.POST) ) {
    		try {
    			if(configType.equalsIgnoreCase("executor")) {
    				GatfExecutorConfig gatfConfig = WorkflowContextHandler.OM.readValue(request.getInputStream(), 
        					GatfExecutorConfig.class);
    				GatfConfigToolUtil.getGatfExecutorConfig(mojo, gatfConfig);
    				GatfPlugin executorMojo = f.apply(configType);
        			if(executorMojo instanceof GatfTestCaseExecutorUtil) {
    					try {
    						((GatfTestCaseExecutorUtil)executorMojo).initilaizeContext(gatfConfig, true);
    						initializeMojoProps(executorMojo, mojo);
    					} catch (Throwable e) {
    						e.printStackTrace();
    						throw e;
    					} finally { 
    						executorMojo.shutdown();
    					}
        			}
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfiguration gatfConfig = WorkflowContextHandler.OM.readValue(request.getInputStream(), 
        					GatfConfiguration.class);
    				GatfConfigToolUtil.getGatfConfiguration(mojo, gatfConfig);
    			}
    			response.setStatus(HttpStatus.OK_200);
			} catch (Throwable e) {
				GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }

	private void initializeMojoProps(GatfPlugin executorMojo, GatfConfigToolMojoInt mojo) {
		mojo.setContext(((GatfTestCaseExecutorUtil)executorMojo).getContext());
		mojo.setAuthTestCase(((GatfTestCaseExecutorUtil)executorMojo).getAuthTestCase());
	}
}
