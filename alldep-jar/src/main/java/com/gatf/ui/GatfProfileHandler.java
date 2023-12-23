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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.DataSourceProfiler;

/**
 * @author Sumeet Chhetri
 *
 */
public class GatfProfileHandler  extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	private Function<String, GatfPlugin> f = null;
	
	public GatfProfileHandler(GatfConfigToolMojoInt mojo, Function<String, GatfPlugin> f) {
		super();
		this.mojo = mojo;
		this.f = f;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			final GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
    			String dsnames = request.getParameter("dsnames");
    			if(StringUtils.isBlank(dsnames) || dsnames.equalsIgnoreCase("all")) {
    				dsnames = null;
    			}
    			try {
    				GatfPlugin executorMojo = f.apply("executor");
					executorMojo.initilaizeContext(gatfConfig, true);
					DataSourceProfiler profiler = new DataSourceProfiler(executorMojo.getContext());
					Map<String, List<List<String>>> retval = profiler.getProfileStats(dsnames);
					String configJson = WorkflowContextHandler.OM.writeValueAsString(retval);
	    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		            response.setContentLength(configJson.getBytes("UTF-8").length);
		            response.getWriter().write(configJson);
		            response.setStatus(HttpStatus.OK_200);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("Something went wrong...");
				}
    		} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, null);
			} 
    	}
	}
}
