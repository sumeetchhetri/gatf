package com.gatf.ui;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.maven.project.MavenProject;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.generator.core.GatfTestGeneratorMojo;

public class GatfPluginExecutionHandler extends HttpHandler {

	private AtomicBoolean isStarted = new AtomicBoolean(false);
	
	private AtomicBoolean isDone = new AtomicBoolean(false);
	
	private Thread _executorThread = null;
	
	private volatile String status = "";
	
	private GatfConfigToolMojo mojo;
	
	private MavenProject project;
	
	protected GatfPluginExecutionHandler(GatfConfigToolMojo mojo,
			MavenProject project) {
		super();
		this.mojo = mojo;
		this.project = project;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	GatfPluginConfig gatfConfig = null;
    	final String pluginType = request.getParameter("pluginType");
		try {
			gatfConfig = getGatfPluginConfig(pluginType, mojo);
			if(request.getMethod().equals(Method.GET) ) {
				if(isStarted.get()) {
					throw new RuntimeException("Execution already in progress..");
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					
					String temp = status;
					status = "";
					if(temp!=null)
						throw new RuntimeException("Execution failed with Error - " + temp);
					
					String text = "Execution completed, check Reports Section";
        			response.setContentType(MediaType.TEXT_PLAIN);
		            response.setContentLength(text.length());
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(!isStarted.get()) {
					throw new RuntimeException("Please Start the Execution....");
				} else {
					throw new RuntimeException("Unknown Error...");
				}
			}
			else if(request.getMethod().equals(Method.PUT) ) {
				if(!isStarted.get() && !isDone.get()) {
					isStarted.set(true);
					final GatfPluginConfig config = gatfConfig;
					_executorThread = new Thread(new Runnable() {
						public void run() {
							GatfPlugin executorMojo = getGatfPlugin(pluginType);
    						executorMojo.setProject(project);
    						try {
								executorMojo.doExecute(config);
								initializeMojoProps(executorMojo, mojo);
							} catch (Throwable e) {
								e.printStackTrace();
								if(e.getMessage()!=null)
									status = e.getMessage();
								else
									status = ExceptionUtils.getStackTrace(e);
							} finally { 
								executorMojo.shutdown();
							}
    						isDone.set(true);
							isStarted.set(false);
						}

						private void initializeMojoProps(GatfPlugin executorMojo, GatfConfigToolMojo mojo) {
							if(executorMojo instanceof GatfTestCaseExecutorMojo) {
								mojo.setContext(((GatfTestCaseExecutorMojo)executorMojo).getContext());
								mojo.setAuthTestCase(((GatfTestCaseExecutorMojo)executorMojo).getAuthTestCase());
							}
						}
					});
					_executorThread.start();
					String text = "Execution Started";
        			response.setContentType(MediaType.TEXT_PLAIN);
		            response.setContentLength(text.length());
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					
					String temp = status;
					status = "";
					if(temp!=null)
						throw new RuntimeException("Execution failed with Error - " + temp);
					
					String text = "Execution completed, check Reports Section";
        			response.setContentType(MediaType.TEXT_PLAIN);
		            response.setContentLength(text.length());
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else if(isStarted.get()) {
					throw new RuntimeException("Execution already in progress..");
				} else {
					throw new RuntimeException("Unknown Error...");
				}
        	} else if(request.getMethod().equals(Method.DELETE) ) {
        		if(isStarted.get() && _executorThread!=null) {
        			_executorThread.interrupt();
        			_executorThread = null;
				} else if(!isStarted.get()) {
					throw new RuntimeException("Testcase execution is not in progress...");
				} else if(isDone.get()) {
					isDone.set(false);
					isStarted.set(false);
					
					String temp = status;
					status = "";
					if(temp!=null)
						throw new RuntimeException("Execution failed with Error - " + temp);
					
					String text = "Execution completed, check Reports Section";
        			response.setContentType(MediaType.TEXT_PLAIN);
		            response.setContentLength(text.length());
		            response.getWriter().write(text);
        			response.setStatus(HttpStatus.OK_200);
				} else {
					throw new RuntimeException("Unknown Error...");
				}
        	}
		} catch (Exception e) {
			GatfConfigToolMojo.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			return;
		}
    }

	protected static GatfPlugin getGatfPlugin(String type)
	{
		if(type.equals("executor"))
		{
			return new GatfTestCaseExecutorMojo();
		}
		else
		{
			return new GatfTestGeneratorMojo();
		}
	}
	
	protected static GatfPluginConfig getGatfPluginConfig(String type, GatfConfigToolMojo mojo) throws Exception
	{
		if(type.equals("executor"))
		{
			GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
			return gatfConfig;
		}
		else
		{
			GatfConfiguration gatfConfig = GatfConfigToolMojo.getGatfConfiguration(mojo, null);
			return gatfConfig;
		}
	}
}
