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

import java.io.File;
import java.util.function.Function;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.generator.core.GatfTestGeneratorMojo;

/**
 * @author Sumeet Chhetri
 *
 */
@Mojo(
		name = "gatf-config", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = true, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfConfigToolMojo extends AbstractMojo implements GatfConfigToolMojoInt {

    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;
	
	@Parameter(alias = "rootDir", defaultValue = "${project.build.testOutputDirectory}")
	protected String rootDir;
	
	@Parameter(alias = "ipAddress", defaultValue = "localhost")
	private String ipAddress;
	
	@Parameter(alias = "port", defaultValue = "9080")
	private int port;

	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	public GatfConfigToolMojo() {
	}
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		HttpServer server = new HttpServer();

		final String mainDir = rootDir + File.separator + "gatf-config-tool";
		WorkflowContextHandler.copyResourcesToDirectory("gatf-config-tool", mainDir);
        
        final GatfConfigToolMojo mojo = this;
        
        GatfConfigToolUtil.createConfigFileIfNotExists(mojo, true, null);
        
        GatfConfigToolUtil.createConfigFileIfNotExists(mojo, false, null);
        
        GatfConfigToolUtil.createServerApiAndIssueTrackingApiFilesIfNotExists(mojo);
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        
        GatfConfigToolUtil.handleRootContext(server, mainDir, mojo);
        
        Function<String, GatfPlugin> f = new Function<String, GatfPlugin>() {
			@Override
			public GatfPlugin apply(String type) {
				GatfPlugin gp = null;
				if(type.equals("executor"))
				{
					gp = new GatfTestCaseExecutorMojo();
				}
				else
				{
					gp = new GatfTestGeneratorMojo();
				}
				gp.setProject(project);
				return gp;
			}
		};
        
        server.getServerConfiguration().addHttpHandler(new GatfConfigurationHandler(mojo, f), "/configure");
        
        server.getServerConfiguration().addHttpHandler(new GatfReportsHandler(mojo, f), "/reports");
        
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseHandler(mojo), "/testcases");
		
        server.getServerConfiguration().addHttpHandler(new GatfPluginExecutionHandler(mojo, f), "/execute");
        
        server.getServerConfiguration().addHttpHandler(new GatfProfileHandler(mojo, f), "/profile");
		
		try {
		    server.start();
		    new File("gatf.ctrl").createNewFile();
		    while(new File("gatf.ctrl").exists()) {
				Thread.sleep(10000);
		    }
		} catch (Exception e) {
		    System.err.println(e);
		}
	}

	public AcceptanceTestContext getContext() {
		return context;
	}

	public void setContext(AcceptanceTestContext context) {
		this.context = context;
	}

	protected TestCase getAuthTestCase() {
		return authTestCase;
	}

	public void setAuthTestCase(TestCase authTestCase) {
		this.authTestCase = authTestCase;
	}
    
	public String getRootDir() {
	    return rootDir;
	}

	public void setRootDir(String rootDir) {
	    this.rootDir = rootDir;
	}

	@Override
	public boolean isAuthenticated(Request request) {
		// TODO Auto-generated method stub
		return false;
	}
}
