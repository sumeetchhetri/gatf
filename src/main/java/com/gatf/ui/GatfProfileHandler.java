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

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.report.DataSourceProfiler;

/**
 * @author Sumeet Chhetri
 *
 */
public class GatfProfileHandler  extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	private MavenProject project;
	
	protected GatfProfileHandler(GatfConfigToolMojo mojo, MavenProject project) {
		super();
		this.mojo = mojo;
		this.project = project;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			final GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
    			String dsnames = request.getParameter("dsnames");
    			if(StringUtils.isBlank(dsnames) || dsnames.equalsIgnoreCase("all")) {
    				dsnames = null;
    			}
    			try {
					GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();
					executorMojo.setProject(project);
					executorMojo.initilaizeContext(gatfConfig, true);
					DataSourceProfiler profiler = new DataSourceProfiler(executorMojo.getContext());
					Map<String, List<List<String>>> retval = profiler.getProfileStats(dsnames);
					String configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(retval);
	    			response.setContentType(MediaType.APPLICATION_JSON);
		            response.setContentLength(configJson.length());
		            response.getWriter().write(configJson);
		            response.setStatus(HttpStatus.OK_200);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("Something went wrong...");
				}
    		} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, null);
			} 
    	}
	}
}
