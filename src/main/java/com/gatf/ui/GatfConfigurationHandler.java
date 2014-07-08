package com.gatf.ui;

import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.generator.core.GatfConfiguration;

public class GatfConfigurationHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	protected GatfConfigurationHandler(GatfConfigToolMojo mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	String configType = request.getParameter("configType");
    	if(request.getMethod().equals(Method.GET) ) {
    		try {
    			String configJson = null;
    			if(configType.equalsIgnoreCase("executor")) {
    				GatfConfigToolMojo.createConfigFileIfNotExists(mojo, true, null);
    				GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
        			configJson = new ObjectMapper().writeValueAsString(gatfConfig);
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfigToolMojo.createConfigFileIfNotExists(mojo, false, null);
    				GatfConfiguration gatfConfig = GatfConfigToolMojo.getGatfConfiguration(mojo, null);
        			configJson = new ObjectMapper().writeValueAsString(gatfConfig);
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
    				GatfExecutorConfig gatfConfig = new ObjectMapper().readValue(request.getInputStream(), 
        					GatfExecutorConfig.class);
    				GatfConfigToolMojo.getGatfExecutorConfig(mojo, gatfConfig);
    			} else if(configType.equalsIgnoreCase("generator")) {
    				GatfConfiguration gatfConfig = new ObjectMapper().readValue(request.getInputStream(), 
        					GatfConfiguration.class);
    				GatfConfigToolMojo.getGatfConfiguration(mojo, gatfConfig);
    			}
    			response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }
}
