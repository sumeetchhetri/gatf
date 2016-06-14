/*
    Copyright 2013-2016, Sumeet Chhetri
    
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

import javax.ws.rs.core.MediaType;

import org.apache.maven.project.MavenProject;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.generator.core.GatfConfiguration;

public class GatfConfigurationHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	private MavenProject project;
	
	protected GatfConfigurationHandler(GatfConfigToolMojo mojo,
			MavenProject project) {
		super();
		this.mojo = mojo;
		this.project = project;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	String configType = request.getParameter("configType");
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			String configJson = null;
    			if(configType.equalsIgnoreCase("executor")) {
    				GatfConfigToolMojo.createConfigFileIfNotExists(mojo, true, null);
    				GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
        			configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(gatfConfig);
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfigToolMojo.createConfigFileIfNotExists(mojo, false, null);
    				GatfConfiguration gatfConfig = GatfConfigToolMojo.getGatfConfiguration(mojo, null);
        			configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(gatfConfig);
    			}
    			response.setContentType(MediaType.APPLICATION_JSON);
	            response.setContentLength(configJson.length());
	            response.getWriter().write(configJson);
	            response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.POST) ) {
    		try {
    			if(configType.equalsIgnoreCase("executor")) {
    				GatfExecutorConfig gatfConfig = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), 
        					GatfExecutorConfig.class);
    				GatfConfigToolMojo.getGatfExecutorConfig(mojo, gatfConfig);
    				GatfPlugin executorMojo = GatfPluginExecutionHandler.getGatfPlugin(configType);
        			if(executorMojo instanceof GatfTestCaseExecutorMojo) {
    					executorMojo.setProject(project);
    					try {
    						((GatfTestCaseExecutorMojo)executorMojo).initilaizeContext(gatfConfig, true);
    						initializeMojoProps(executorMojo, mojo);
    					} catch (Throwable e) {
    						e.printStackTrace();
    						throw e;
    					} finally { 
    						executorMojo.shutdown();
    					}
        			}
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfiguration gatfConfig = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), 
        					GatfConfiguration.class);
    				GatfConfigToolMojo.getGatfConfiguration(mojo, gatfConfig);
    			}
    			response.setStatus(HttpStatus.OK_200);
			} catch (Throwable e) {
				GatfConfigToolMojo.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }

	private void initializeMojoProps(GatfPlugin executorMojo, GatfConfigToolMojo mojo) {
		mojo.setContext(((GatfTestCaseExecutorMojo)executorMojo).getContext());
		mojo.setAuthTestCase(((GatfTestCaseExecutorMojo)executorMojo).getAuthTestCase());
	}
}
