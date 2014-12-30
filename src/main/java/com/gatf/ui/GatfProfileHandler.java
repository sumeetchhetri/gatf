package com.gatf.ui;

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.jackson.map.ObjectMapper;
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
					String configJson = new ObjectMapper().writeValueAsString(retval);
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
