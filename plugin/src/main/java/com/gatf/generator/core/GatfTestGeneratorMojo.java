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
package com.gatf.generator.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;

/**
 * @author Sumeet Chhetri<br/>
 *         The gatf maven plugin main class which creates testcases for all the rest-ful/soap-wsdl services mentioned. <br/>
 * 
 * <pre>
 * {@code
 * 	<plugin>
		<groupId>com.github.sumeetchhetri.gatf</groupId>
		<artifactId>gatf-plugin</artifactId>
		<version>1.0.1</version>
		<configuration>
			<testPaths>
				<testPath>com.sample.services.*</testPath>
			</testPaths>
			<useSoapClient>true</useSoapClient>
			<soapWsdlKeyPairs>
				<soapWsdlKeyPair>AuthService,http://localhost:8081/soap/auth?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>ExampleService,http://localhost:8081/soap/example?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>MessageService,http://localhost:8081/soap/messages?wsdl</soapWsdlKeyPair>
				<soapWsdlKeyPair>UserService,http://localhost:8081/soap/user?wsdl</soapWsdlKeyPair>
			</soapWsdlKeyPairs>
			<urlPrefix>rest</urlPrefix>
			<urlSuffix>_param=value</urlSuffix>
			<requestDataType>json</requestDataType>
			<responseDataType>json</responseDataType>
			<overrideSecure>true</overrideSecure>
			<resourcepath>src/test/resources/generated</resourcepath>
			<postmanCollectionVersion>2</postmanCollectionVersion>
			<testCaseFormat>xml</testCaseFormat>
			<enabled>true</enabled>
		</configuration>
		<executions>
			<execution>
				<goals>
					<goal>gatf</goal>
				</goals>
			</execution>
		</executions>
	</plugin>
 *   }
 * </pre>
 */
@Mojo(
		name = "gatf-generator", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = false, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfTestGeneratorMojo extends AbstractMojo implements GatfPlugin {
	
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    @Parameter(alias = "testPaths")
    private String[] testPaths;

    @Parameter(alias = "soapWsdlKeyPairs")
    private String[] soapWsdlKeyPairs;
    
	@Parameter(alias = "urlPrefix")
    private String urlPrefix;

    @Parameter(alias = "urlSuffix")
    private String urlSuffix;

    @Parameter(alias = "resourcepath")
    private String resourcepath;

    @Parameter(alias = "debug-enabled")
    private boolean debugEnabled;

    @Parameter(alias = "requestDataType")
    private String requestDataType;
    
	@Parameter(alias = "responseDataType")
	private String outDataType;

	@Parameter(alias = "overrideSecure")
	private boolean overrideSecure;
	
	@Parameter(alias = "enabled")
	private boolean enabled;
	
	@Parameter(alias = "useSoapClient")
	private boolean useSoapClient;
	
	@Parameter(alias = "postmanCollectionVersion")
	private int postmanCollectionVersion;
	
	@Parameter(alias = "testCaseFormat")
	private String testCaseFormat;
	
	@Parameter(alias = "configFile")
	private String configFile;
	
	public MavenProject getProject() {
		return project;
	}
	
	public void setProject(Object project) {
		this.project = (MavenProject)project;
	}
	
	public String[] getTestPaths() {
		return testPaths;
	}
	
	public void setTestPaths(String[] testPaths) {
		this.testPaths = testPaths;
	}
	
	public String[] getSoapWsdlKeyPairs() {
		return soapWsdlKeyPairs;
	}
	
	public void setSoapWsdlKeyPairs(String[] soapWsdlKeyPairs) {
		this.soapWsdlKeyPairs = soapWsdlKeyPairs;
	}
	
	public String getUrlPrefix() {
		return urlPrefix;
	}
	
	public void setUrlPrefix(String urlPrefix) {
		this.urlPrefix = urlPrefix;
	}
	
	public String getUrlSuffix() {
		return urlSuffix;
	}
	
	public void setUrlSuffix(String urlSuffix) {
		this.urlSuffix = urlSuffix;
	}
	
	public String getResourcepath() {
		return resourcepath;
	}
	
	public void setResourcepath(String resourcepath) {
		this.resourcepath = resourcepath;
	}
	
	public boolean isDebugEnabled() {
		return debugEnabled;
	}
	
	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
	}
	
	public String getRequestDataType() {
		return requestDataType;
	}
	
	public void setRequestDataType(String requestDataType) {
		this.requestDataType = requestDataType;
	}
	
	public String getOutDataType() {
		return outDataType;
	}
	
	public void setOutDataType(String outDataType) {
		this.outDataType = outDataType;
	}
	
	public boolean isOverrideSecure() {
		return overrideSecure;
	}
	
	public void setOverrideSecure(boolean overrideSecure) {
		this.overrideSecure = overrideSecure;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public boolean isUseSoapClient() {
		return useSoapClient;
	}
	
	public void setUseSoapClient(boolean useSoapClient) {
		this.useSoapClient = useSoapClient;
	}
	
	public int getPostmanCollectionVersion() {
		return postmanCollectionVersion;
	}
	
	public void setPostmanCollectionVersion(int postmanCollectionVersion) {
		this.postmanCollectionVersion = postmanCollectionVersion;
	}
	
	public String getTestCaseFormat() {
		return testCaseFormat;
	}
	
	public void setTestCaseFormat(String testCaseFormat) {
		this.testCaseFormat = testCaseFormat;
	}
	
	public String getConfigFile() {
		return configFile;
	}
	
	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}
	
	public static String getFileExtension(String fileName)
    {
        if (fileName == null || fileName.indexOf(".") == -1)
            return null;
        return fileName.substring(fileName.lastIndexOf("."));
    }
	
    private ClassLoader getClassLoader()
    {
		if(project!=null)
		{
	        try
	        {
	            /*List classpathElements = project.getCompileClasspathElements();
	            classpathElements.add(project.getBuild().getOutputDirectory());
	            classpathElements.add(project.getBuild().getTestOutputDirectory());
	            URL[] urls = new URL[classpathElements.size()];
	            for (int i = 0; i < classpathElements.size(); i++)
	            {
	                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
	            }
	            return new URLClassLoader(urls, getClass().getClassLoader());*/
	            ClassWorld world = new ClassWorld();
	            ClassRealm realm;
	            realm = world.newRealm("gatf", null);
	            for (String elt : project.getCompileSourceRoots()) {
	                URL url = new File(elt).toURI().toURL();
	                realm.addURL(url);
	                if (getLog().isDebugEnabled()) {
	                    getLog().debug("Source root: " + url);
	                }
	            }
	            for (String elt : project.getCompileClasspathElements()) {
	                URL url = new File(elt).toURI().toURL();
	                realm.addURL(url);
	                if (getLog().isDebugEnabled()) {
	                    getLog().debug("Compile classpath: " + url);
	                }
	            }
	            return realm;
	        }
	        catch (Exception e)
	        {
	            getLog().error("Couldn't get the classloader.");
	        }
		}
        return getClass().getClassLoader();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @SuppressWarnings("rawtypes")
    public void execute()
    {
    	if(!isEnabled())
    	{
    		getLog().info("Skipping gatf-plugin execution....");
    		return;
    	}
    	
    	if(configFile!=null) {
			try {
				GatfConfiguration config = null;
    			
    			if(configFile.trim().endsWith(".xml")) {
					InputStream io = new FileInputStream(configFile);
		    		config = WorkflowContextHandler.XOM.readValue(io, GatfConfiguration.class);
    			} else if(configFile.trim().endsWith(".json")) {
    				InputStream io = new FileInputStream(configFile);
    				config = new ObjectMapper().readValue(io, GatfConfiguration.class);
    			}
	    		
	    		setDebugEnabled(config.isDebugEnabled());
	    		setEnabled(config.isEnabled());
	    		setRequestDataType(config.getRequestDataType());
	    		setTestPaths(config.getTestPaths());
	    		setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
	    		setUrlPrefix(config.getUrlPrefix());
	    		setResourcepath(config.getResourcepath());
	    		setOutDataType(config.getResponseDataType()); 
	    		setOverrideSecure(config.isOverrideSecure());
	    		setUrlSuffix(config.getUrlSuffix());
	    		setUseSoapClient(config.isUseSoapClient());
	    		setTestCaseFormat(config.getTestCaseFormat());
	    		setPostmanCollectionVersion(config.getPostmanCollectionVersion());
	    		setOverrideSecure(config.isOverrideSecure());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
    	}
    	
    	if(getResourcepath()==null) {
    		setResourcepath(".");
    		getLog().info("No resource path specified, using the current working directory to generate testcases...");
    	} else {
    		File dir = new File(getResourcepath());
    		if(!dir.exists())
            {
    			dir.mkdirs();
            }
    	}
    	
    	GatfTestGeneratorUtil testGenerator = new GatfTestGeneratorUtil();
		testGenerator.setDebugEnabled(isDebugEnabled());
		testGenerator.setEnabled(isEnabled());
		testGenerator.setRequestDataType(getRequestDataType());
		testGenerator.setTestPaths(getTestPaths());
		testGenerator.setSoapWsdlKeyPairs(getSoapWsdlKeyPairs());
		testGenerator.setUrlPrefix(getUrlPrefix());
		testGenerator.setResourcepath(getResourcepath());
		testGenerator.setOutDataType(getOutDataType()); 
		testGenerator.setOverrideSecure(isOverrideSecure());
		testGenerator.setUrlSuffix(getUrlSuffix());
		testGenerator.setUseSoapClient(isUseSoapClient());
		testGenerator.setTestCaseFormat(getTestCaseFormat());
		testGenerator.setPostmanCollectionVersion(getPostmanCollectionVersion());
		testGenerator.setOverrideSecure(isOverrideSecure());
    	
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try
        {
            currentThread.setContextClassLoader(getClassLoader());
            getLog().info("Inside execute");
            List<Class> allClasses = new ArrayList<Class>();
            if (getTestPaths() != null)
            {
                for (String item : getTestPaths())
                {
                    if (item.endsWith(".*"))
                    {
                        List<Class> classes = ClassLoaderUtils.getClasses(item.substring(0, item.indexOf(".*")));
                        if (classes != null && classes.size() > 0)
                        {
                            allClasses.addAll(classes);
                            getLog().info("Adding package " + item);
                        }
                        else
                        {
                            getLog().error("Error:package not found - " + item);
                        }
                    }
                    else
                    {
                        try
                        {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            getLog().info("Adding class " + item);
                        }
                        catch (Exception e)
                        {
                            getLog().error("Error:class not found - " + item);
                        }
                    }
                }
                if(!allClasses.isEmpty())
                {
                	testGenerator.generateRestTestCases(allClasses);
                }
            }
            else
            {
            	getLog().info("Nothing to generate..");
            }
            testGenerator.generateSoapTestCases();
            
            getLog().info("Done execute");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }

	public void doExecute(GatfPluginConfig configuration, List<String> files) throws MojoFailureException {
		GatfConfiguration config = (GatfConfiguration)configuration;
		
		setDebugEnabled(false);
		setEnabled(config.isEnabled());
		setRequestDataType(config.getRequestDataType());
		setTestPaths(config.getTestPaths());
		setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
		setUrlPrefix(config.getUrlPrefix());
		setResourcepath(config.getResourcepath());
		setOutDataType(config.getResponseDataType()); 
		setOverrideSecure(config.isOverrideSecure());
		setUrlSuffix(config.getUrlSuffix());
		setUseSoapClient(config.isUseSoapClient());
		setTestCaseFormat(config.getTestCaseFormat());
		setPostmanCollectionVersion(config.getPostmanCollectionVersion());
		setOverrideSecure(config.isOverrideSecure());
		execute();
	}

	public void shutdown() {
	}

	@Override
	public void initilaizeContext(GatfExecutorConfig configuration, boolean flag) throws Exception {
	}

	@Override
	public AcceptanceTestContext getContext() {
		return null;
	}

	@Override
	public void setContext(AcceptanceTestContext context) {
	}

	@Override
	public TestCase getAuthTestCase() {
		return null;
	}

	@Override
	public void invokeServerLogApi(boolean b, TestCaseReport testCaseReport, TestCaseExecutorUtil testCaseExecutorUtil,
			boolean serverLogsApiAuthEnabled) {
	}

	@Override
	public List<TestCase> getAllTestCases(AcceptanceTestContext context, Set<String> relativeFileNames,
			List<String> targetFileNames) {
		return null;
	}

	@Override
	public void doSeleniumTest(GatfPluginConfig configuration, List<String> files) {
	}

	@Override
	public GatfSelDebugger debugSeleniumTest(GatfExecutorConfig configuration, String selscript, String configPath) {
		return null;
	}
}
