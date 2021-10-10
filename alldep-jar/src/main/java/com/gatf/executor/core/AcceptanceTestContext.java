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
package com.gatf.executor.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.glassfish.grizzly.http.server.Response;
import org.junit.Assert;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.dataprovider.InlineValueTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.RandomValueTestDataProvider;
import com.gatf.executor.dataprovider.SQLDatabaseTestDataSource;
import com.gatf.executor.dataprovider.TestDataHook;
import com.gatf.executor.dataprovider.TestDataProvider;
import com.gatf.executor.dataprovider.TestDataSource;
import com.gatf.executor.distributed.DistributedAcceptanceContext;
import com.gatf.executor.executor.PerformanceTestCaseExecutor;
import com.gatf.executor.executor.ScenarioTestCaseExecutor;
import com.gatf.executor.executor.SingleTestCaseExecutor;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.TestCaseReport;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * @author Sumeet Chhetri
 * The Executor context, holds context information for the execution of testcases and also holds
 * test case report information
 */
public class AcceptanceTestContext {

	private Logger logger = Logger.getLogger(AcceptanceTestContext.class.getSimpleName());
	
	public final static String
	    PROP_SOAP_ACTION_11 = "SOAPAction",
	    PROP_SOAP_ACTION_12 = "action=",
	    PROP_CONTENT_TYPE = "Content-Type",
	    PROP_CONTENT_LENGTH = "Content-Length",
	    PROP_AUTH = "Authorization",
	    PROP_PROXY_AUTH = "Proxy-Authorization",
	    PROP_PROXY_CONN = "Proxy-Connection",
	    PROP_KEEP_ALIVE = "Keep-Alive",
	    PROP_BASIC_AUTH = "Basic",
	    PROP_DELIMITER = "; ";
	
	public final static String
	    SOAP_1_1_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/",
	    SOAP_1_2_NAMESPACE = "http://www.w3.org/2003/05/soap-envelope";
	
	public final static String
	    MIMETYPE_TEXT_HTML = "text/html",
	    MIMETYPE_TEXT_PLAIN = "text/plain",
	    MIMETYPE_TEXT_XML = "text/xml",
	    MIMETYPE_APPLICATION_XML = "application/soap+xml";
	
	public static final String GATF_SERVER_LOGS_API_FILE_NM = "gatf-logging-api-int.xml";
	
	private final Map<String, List<TestCase>> relatedTestCases = new HashMap<String, List<TestCase>>();
	
	private final Map<String, String> httpHeaders = new HashMap<String, String>();

	private ConcurrentHashMap<Integer, String> sessionIdentifiers = new ConcurrentHashMap<Integer, String>();
	
	private final Map<String, String> soapEndpoints = new HashMap<String, String>();
	
	private final Map<String, Document> soapMessages = new HashMap<String, Document>();
	
	private final Map<String, String> soapStrMessages = new HashMap<String, String>();
	
	private final Map<String, String> soapActions = new HashMap<String, String>();
	
	private final WorkflowContextHandler workflowContextHandler = new WorkflowContextHandler();
	
	private List<TestCase> serverLogsApiLst = new ArrayList<TestCase>();
	
	private GatfExecutorConfig gatfExecutorConfig;
	
	private ClassLoader projectClassLoader;
	
	private Map<String, Method> prePostTestCaseExecHooks = new HashMap<String, Method>();
	
	public static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http","https"}, UrlValidator.ALLOW_LOCAL_URLS);
	
	public static void setCorsHeaders(Response response) {
		if(Boolean.TRUE.toString().equalsIgnoreCase(System.getenv("cor.enabled")) || Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("cor.enabled"))) {
			response.setHeader("Access-Control-Allow-Origin", "*");
			response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
			response.setHeader("Access-Control-Allow-Headers", "*");
		}
	}
	
	public ClassLoader getProjectClassLoader() {
		return projectClassLoader;
	}

	private final Map<String, List<Map<String, String>>> providerTestDataMap = new HashMap<String, List<Map<String,String>>>();
	private final Map<String, String> fileProviderStateMap = new HashMap<String, String>();
	
	public List<Map<String, String>> getProviderTestDataMap(String providerName) {
	    if(liveProviders.containsKey(providerName))
        {
            GatfTestDataProvider provider = liveProviders.get(providerName);
            return getProviderData(provider, null);
        }
	    else 
	    {
	        return providerTestDataMap.get(providerName);
	    }
	}
	
	public String getFileProviderHash(String name) {
	    if(!fileProviderStateMap.containsKey(name)) {
	        return fileProviderStateMap.get(name);
	    }
	    return null;
	}
	
	public void newProvider(String name) {
	    if(!providerTestDataMap.containsKey(name)) {
	        providerTestDataMap.put(name, new ArrayList<Map<String,String>>());
	    }
	}
	
	private final Map<String, TestDataSource> dataSourceMap = new HashMap<String, TestDataSource>();
	
	private final Map<String, TestDataSource> dataSourceMapForProfiling = new HashMap<String, TestDataSource>();
	
	private final Map<String, GatfTestDataSourceHook> dataSourceHooksMap = new HashMap<String, GatfTestDataSourceHook>();

	private final SingleTestCaseExecutor singleTestCaseExecutor = new SingleTestCaseExecutor();
	
	private final ScenarioTestCaseExecutor scenarioTestCaseExecutor = new ScenarioTestCaseExecutor();
	
	private final PerformanceTestCaseExecutor performanceTestCaseExecutor = new PerformanceTestCaseExecutor();
	
	private final Map<String, GatfTestDataProvider> liveProviders = new HashMap<String, GatfTestDataProvider>();

	public SingleTestCaseExecutor getSingleTestCaseExecutor() {
		return singleTestCaseExecutor;
	}

	public ScenarioTestCaseExecutor getScenarioTestCaseExecutor() {
		return scenarioTestCaseExecutor;
	}

	public PerformanceTestCaseExecutor getPerformanceTestCaseExecutor() {
		return performanceTestCaseExecutor;
	}

	public AcceptanceTestContext(){}
	
	public AcceptanceTestContext(GatfExecutorConfig gatfExecutorConfig, ClassLoader projectClassLoader)
	{
		this.gatfExecutorConfig = gatfExecutorConfig;
		this.projectClassLoader = projectClassLoader;
		getWorkflowContextHandler().init();
	}
	
	public AcceptanceTestContext(DistributedAcceptanceContext dContext, ClassLoader projectClassLoader)
	{
        this.projectClassLoader = projectClassLoader;
		this.gatfExecutorConfig = dContext.getConfig();
		this.httpHeaders.putAll(dContext.getHttpHeaders());
		this.soapEndpoints.putAll(dContext.getSoapEndpoints());
		this.soapActions.putAll(dContext.getSoapActions());
		try {
			if(dContext.getSoapMessages()!=null) {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				for (Map.Entry<String, String> soapMsg : dContext.getSoapMessages().entrySet()) {
					Document soapMessage = db.parse(new ByteArrayInputStream(soapMsg.getValue().getBytes()));
					this.soapMessages.put(soapMsg.getKey(), soapMessage);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
		getWorkflowContextHandler().init();
	}
	
	public GatfExecutorConfig getGatfExecutorConfig() {
		return gatfExecutorConfig;
	}

	public void setGatfExecutorConfig(GatfExecutorConfig gatfExecutorConfig) {
		this.gatfExecutorConfig = gatfExecutorConfig;
	}

	public ConcurrentHashMap<Integer, String> getSessionIdentifiers() {
		return sessionIdentifiers;
	}

	public void setSessionIdentifiers(
			ConcurrentHashMap<Integer, String> sessionIdentifiers) {
		this.sessionIdentifiers = sessionIdentifiers;
	}

	public void setSessionIdentifier(String identifier, TestCase testCase) {
		Integer simulationNumber = testCase.getSimulationNumber();
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			simulationNumber = -1;
		} else if(testCase.isExternalApi()) {
			simulationNumber = -2;
		} else if(simulationNumber==null) {
			simulationNumber = 0;
		}
		sessionIdentifiers.put(simulationNumber, identifier);
	}
	
	public String getSessionIdentifier(TestCase testCase) {
		if(testCase.isServerApiAuth() || testCase.isServerApiTarget()) {
			return sessionIdentifiers.get(-1);
		}
		if(testCase.isExternalApi()) {
			return sessionIdentifiers.get(-2);
		}
		if(testCase.getSimulationNumber()!=null)
			return sessionIdentifiers.get(testCase.getSimulationNumber());
		
		return sessionIdentifiers.get(0);
	}
	
	public Map<String, String> getHttpHeaders() {
		return httpHeaders;
	}

	public Map<String, String> getSoapEndpoints() {
		return soapEndpoints;
	}

	public Map<String, Document> getSoapMessages() {
		return soapMessages;
	}

	public Map<String, String> getSoapActions() {
		return soapActions;
	}

	public WorkflowContextHandler getWorkflowContextHandler() {
		return workflowContextHandler;
	}
	
	@SuppressWarnings("rawtypes")
	public Class addTestCaseHooks(Method method) {
		Class claz = null;
		if(method!=null && Modifier.isStatic(method.getModifiers())) {
			
			Annotation preHook = method.getAnnotation(PreTestCaseExecutionHook.class);
            Annotation postHook = method.getAnnotation(PostTestCaseExecutionHook.class);
			
			if(preHook!=null)
			{
				PreTestCaseExecutionHook hook = (PreTestCaseExecutionHook)preHook;
				
				if(method.getParameterTypes().length!=1 || !method.getParameterTypes()[0].equals(TestCase.class))
				{
					logger.severe("PreTestCaseExecutionHook annotated methods should " +
							"confirm to the method signature - `public static void {methodName} (" +
							"TestCase testCase)`");
					return claz;
				}
				
				if(hook.value()!=null && hook.value().length>0)
				{
					for (String testCaseName : hook.value()) {
						if(testCaseName!=null && !testCaseName.trim().isEmpty())  {
							prePostTestCaseExecHooks.put("pre"+testCaseName, method);
						}
					}
				}
				else
				{
					prePostTestCaseExecHooks.put("preAll", method);
				}
				claz = method.getDeclaringClass();
			}
			if(postHook!=null)
			{
				PostTestCaseExecutionHook hook = (PostTestCaseExecutionHook)postHook;
				
				if(method.getParameterTypes().length!=1 || !method.getParameterTypes()[0].equals(TestCaseReport.class))
				{
					logger.severe("PostTestCaseExecutionHook annotated methods should " +
							"confirm to the method signature - `public static void {methodName} (" +
							"TestCaseReport testCaseReport)`");
					return claz;
				}
				
				if(hook.value()!=null && hook.value().length>0)
				{
					for (String testCaseName : hook.value()) {
						if(testCaseName!=null && !testCaseName.trim().isEmpty())  {
							prePostTestCaseExecHooks.put("post"+testCaseName, method);
						}
					}
				}
				else
				{
					prePostTestCaseExecHooks.put("postAll", method);
				}
				claz = method.getDeclaringClass();
			}
		}
		return claz;
	}
	
	public List<Method> getPrePostHook(TestCase testCase, boolean isPreHook) {
		List<Method> methods = new ArrayList<Method>();
		String hookKey = (isPreHook?"pre":"post") + testCase.getName();
		Method meth = prePostTestCaseExecHooks.get(hookKey);
		if(meth!=null)
			methods.add(meth);
		meth = prePostTestCaseExecHooks.get((isPreHook?"preAll":"postAll"));
		if(meth!=null)
			methods.add(meth);
		return methods;
	}
	
	public File getResourceFile(String filename) {
		File basePath = new File(gatfExecutorConfig.getTestCasesBasePath());
	    File testPath = new File(basePath, gatfExecutorConfig.getTestCaseDir());
	    File resource = new File(testPath, filename);
        if(!resource.exists()) {
            resource = new File(basePath, filename);
        }
		return resource;
	}
	
	public File getNewOutResourceFile(String filename) {
		File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
		File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
		File file = new File(resource, filename);
		if(!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
			}
		}
		return file;
	}
	
	public File getOutDir() {
		File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
		File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
		return resource;
	}
	
	public String getOutDirPath() {
		File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
		File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
		return resource.getAbsolutePath();
	}
	
	public Map<String, List<TestCase>> getRelatedTestCases() {
		return relatedTestCases;
	}

	public static void removeFolder(File folder)
	{
		if(folder==null || !folder.exists())return;
		try {
			FileUtils.deleteDirectory(folder);
		} catch (IOException e) {
		}
	}
	
	public void validateAndInit(boolean flag) throws Exception
	{
		gatfExecutorConfig.validate();
		
		if(gatfExecutorConfig.getOutFilesBasePath()!=null)
		{
			File basePath = new File(gatfExecutorConfig.getOutFilesBasePath().trim());
			Assert.assertTrue("Invalid out files base path..", basePath.exists());
			gatfExecutorConfig.setOutFilesBasePath(basePath.getAbsolutePath());
		}
		else
		{
			File basePath = new File(System.getProperty("user.dir"));
			gatfExecutorConfig.setOutFilesBasePath(basePath.getAbsolutePath());
		}
		
		if(gatfExecutorConfig.getTestCasesBasePath()!=null)
		{
			File basePath = new File(gatfExecutorConfig.getTestCasesBasePath());
			Assert.assertTrue("Invalid test cases base path..", basePath.exists());
			gatfExecutorConfig.setTestCasesBasePath(basePath.getAbsolutePath());
		}
		else
		{
			File basePath = new File(System.getProperty("user.dir"));
			gatfExecutorConfig.setTestCasesBasePath(basePath.getAbsolutePath());
		}
		
		Assert.assertEquals("Testcase directory not found...", getResourceFile(gatfExecutorConfig.getTestCaseDir()).exists(), true);
		
		if(StringUtils.isNotBlank(gatfExecutorConfig.getBaseUrl()))
		{
			Assert.assertTrue("Base URL is not valid", URL_VALIDATOR.isValid(gatfExecutorConfig.getBaseUrl()));
		}
		
		if(gatfExecutorConfig.getOutFilesDir()!=null && !gatfExecutorConfig.getOutFilesDir().trim().isEmpty())
		{
			try
			{
				if(gatfExecutorConfig.getOutFilesBasePath()!=null)
				{
					File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
					File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
					if(flag)
					{
						removeFolder(resource);
						File nresource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
						nresource.mkdirs();
						Assert.assertTrue("Out files directory could not be created...", nresource.exists());
					}
				}
				else
				{
					File resource = new File(System.getProperty("user.dir"));
					File file = new File(resource, gatfExecutorConfig.getOutFilesDir());
					if(flag)
					{
						removeFolder(file);
						File nresource = new File(resource, gatfExecutorConfig.getOutFilesDir());
						nresource.mkdir();
						Assert.assertTrue("Out files directory could not be created...", nresource.exists());
					}
				}
			} catch (Exception e) {
				gatfExecutorConfig.setOutFilesDir(null);
			}
			Assert.assertNotNull("Testcase out file directory not found...", gatfExecutorConfig.getOutFilesDir());
		}
		else
		{
			File resource = new File(System.getProperty("user.dir"));
			if(flag)
			{
				removeFolder(resource);
				File nresource = new File(System.getProperty("user.dir"), "out");
				nresource.mkdir();
			}
			gatfExecutorConfig.setOutFilesDir("out");
			gatfExecutorConfig.setOutFilesBasePath(System.getProperty("user.dir"));
		}
		
		if(flag)
		{
			initSoapContextAndHttpHeaders();
			
			initTestDataProviderAndGlobalVariables();
		}
		
		initServerLogsApis();
	}
	
	public void initServerLogsApis() throws Exception {
		File basePath = new File(gatfExecutorConfig.getTestCasesBasePath());
		File resource = new File(basePath, GATF_SERVER_LOGS_API_FILE_NM);
		if(resource.exists() && gatfExecutorConfig.isFetchFailureLogs()) {
			TestCaseFinder finder = new XMLTestCaseFinder();
			serverLogsApiLst.clear();
			serverLogsApiLst.addAll(finder.resolveTestCases(resource));
			for (TestCase testCase : serverLogsApiLst) {
				testCase.setSourcefileName(GATF_SERVER_LOGS_API_FILE_NM);
				if(testCase.getSimulationNumber()==null)
				{
					testCase.setSimulationNumber(0);
				}
				testCase.setExternalApi(true);
				testCase.validate(getHttpHeaders(), null);
			}
		}
	}

	private void initTestDataProviderAndGlobalVariables() throws Exception {
		GatfTestDataConfig gatfTestDataConfig = null;
		if(gatfExecutorConfig.getTestDataConfigFile()!=null) {
			File file = getResourceFile(gatfExecutorConfig.getTestDataConfigFile());
			Assert.assertNotNull("Testdata configuration file not found...", file);
			Assert.assertEquals("Testdata configuration file not found...", file.exists(), true);
			
			if(gatfExecutorConfig.getTestDataConfigFile().trim().endsWith(".xml")) {
				XStream xstream = new XStream(new DomDriver("UTF-8"));
		       
		        xstream.allowTypes(new Class[]{GatfTestDataConfig.class, GatfTestDataProvider.class});
				xstream.processAnnotations(new Class[]{GatfTestDataConfig.class, GatfTestDataProvider.class});
				xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
				xstream.alias("args", String[].class);
				xstream.alias("arg", String.class);
				gatfTestDataConfig = (GatfTestDataConfig)xstream.fromXML(file);
			} else  {
				gatfTestDataConfig = new ObjectMapper().readValue(file, GatfTestDataConfig.class);
			}
			gatfExecutorConfig.setGatfTestDataConfig(gatfTestDataConfig);
		} else {
			gatfTestDataConfig = gatfExecutorConfig.getGatfTestDataConfig();
		}
		
		handleTestDataSourcesAndHooks(gatfTestDataConfig);
	}

	public void handleTestDataSourcesAndHooks(GatfTestDataConfig gatfTestDataConfig) {
		if(gatfTestDataConfig!=null) {
			getWorkflowContextHandler().addGlobalVariables(gatfTestDataConfig.getGlobalVariables());
			
			if(gatfTestDataConfig.getDataSourceList()!=null)
			{
				handleDataSources(gatfTestDataConfig.getDataSourceList(), false);
			}
			
			if(gatfTestDataConfig.getDataSourceListForProfiling()!=null)
			{
				handleDataSources(gatfTestDataConfig.getDataSourceListForProfiling(), true);
			}
			
			if(gatfTestDataConfig.getDataSourceHooks()!=null)
			{
				handleHooks(gatfTestDataConfig.getDataSourceHooks());
			}
				
			if(gatfTestDataConfig.getProviderTestDataList()!=null)
			{
				handleProviders(gatfTestDataConfig.getProviderTestDataList());
			}
		}
	}

	public static Map<String, String> getHttpHeadersMap() throws Exception
	{
		Map<String, String> headers = new HashMap<String, String>();
		Field[] declaredFields = HttpHeaders.class.getDeclaredFields();
		for (Field field : declaredFields) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
					&& field.getType().equals(String.class)) {
				headers.put(field.get(null).toString().toLowerCase(), field.get(null).toString());
			}
		}
		return headers;
	}
	
	private void initSoapContextAndHttpHeaders() throws Exception
	{
		Field[] declaredFields = HttpHeaders.class.getDeclaredFields();
		for (Field field : declaredFields) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
					&& field.getType().equals(String.class)) {
				httpHeaders.put(field.get(null).toString().toLowerCase(), field.get(null).toString());
			}
		}
		
		File file = null;
		if(gatfExecutorConfig.getWsdlLocFile()!=null && !gatfExecutorConfig.getWsdlLocFile().trim().isEmpty())
			file = getResourceFile(gatfExecutorConfig.getWsdlLocFile());
		
		if(file!=null)
		{
			Scanner s = new Scanner(file);
			s.useDelimiter("\n");
			List<String> list = new ArrayList<String>();
			while (s.hasNext()) {
				list.add(s.next().replace("\r", ""));
			}
			s.close();
	
			for (String wsdlLoc : list) {
				if(!wsdlLoc.trim().isEmpty())
				{
					String[] wsdlLocParts = wsdlLoc.split(",");
					logger.info("Started Parsing WSDL location - " + wsdlLocParts[1]);
					Wsdl wsdl = Wsdl.parse(wsdlLocParts[1]);
					for (QName bindingName : wsdl.getBindings()) {
						SoapBuilder builder = wsdl.getBuilder(bindingName);
						for (SoapOperation operation : builder.getOperations()) {
							String request = builder.buildInputMessage(operation);
							DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
							DocumentBuilder db = dbf.newDocumentBuilder();
							Document soapMessage = db.parse(new ByteArrayInputStream(request.getBytes()));
							
							if(gatfExecutorConfig.isDistributedLoadTests()) {
								soapStrMessages.put(wsdlLocParts[0]+operation.getOperationName(), request);
							}
							
							soapMessages.put(wsdlLocParts[0]+operation.getOperationName(), soapMessage);
							if(operation.getSoapAction()!=null) {
								soapActions.put(wsdlLocParts[0]+operation.getOperationName(), operation.getSoapAction());
							}
							logger.info("Adding message for SOAP operation - " + operation.getOperationName());
						}
						soapEndpoints.put(wsdlLocParts[0], builder.getServiceUrls().get(0));
						logger.info("Adding SOAP Service endpoint - " + builder.getServiceUrls().get(0));
					}
					logger.info("Done Parsing WSDL location - " + wsdlLocParts[1]);
				}
			}
		}
	}

	public void shutdown() {
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooksMap.values()) {
			TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
			
			Assert.assertNotNull("No DataSource found", dataSource);
			
			if(dataSourceHook.isExecuteOnShutdown() && dataSourceHook.getQueryStrs()!=null) {
				for (String query : dataSourceHook.getQueryStrs()) {
					boolean flag = false;
					try {
						flag = dataSource.execute(query);
					} catch (Throwable e) {
					}
					if(!flag) {
						logger.severe("Shutdown DataSourceHook execution for " + dataSourceHook.getHookName()
								+ " failed, queryString = " + query);
					}
				}
			}
		}
		
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooksMap.values()) {
			TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
			
			Assert.assertNotNull("No DataSource found", dataSource);
			
			dataSource.destroy();
		}
	}
	
	public void executeDataSourceHook(String hookName) {
		GatfTestDataSourceHook dataSourceHook = dataSourceHooksMap.get(hookName);
		Assert.assertNotNull("No DataSourceHook found", dataSourceHook);
		
		TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
		Assert.assertNotNull("No DataSource found", dataSource);
		
		for (String query : dataSourceHook.getQueryStrs()) {
			boolean flag = dataSource.execute(query);
			if(!flag) {
				Assert.assertNotNull("DataSourceHook execution for " + dataSourceHook.getHookName()
						+ " failed...", null);
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	private void handleDataSources(List<GatfTestDataSource> dataSourceList, boolean forProfiling)
	{
		for (GatfTestDataSource dataSource : dataSourceList) {
			Assert.assertNotNull("DataSource name is not defined", dataSource.getDataSourceName());
			Assert.assertNotNull("DataSource class is not defined", dataSource.getDataSourceClass());
			Assert.assertNotNull("DataSource args not defined", dataSource.getArgs());
			Assert.assertTrue("DataSource args empty", dataSource.getArgs().length>0);
			Assert.assertNull("Duplicate DataSource name found", dataSourceMap.get(dataSource.getDataSourceName()));
			
			TestDataSource testDataSource = null;
			if(SQLDatabaseTestDataSource.class.getCanonicalName().equals(dataSource.getDataSourceClass())) {
				testDataSource = new SQLDatabaseTestDataSource();
			} else if(MongoDBTestDataSource.class.getCanonicalName().equals(dataSource.getDataSourceClass())) {
				testDataSource = new MongoDBTestDataSource();
			} else {
				try {
					Class claz = loadCustomClass(dataSource.getDataSourceClass());
					Class[] classes = claz.getInterfaces();
					boolean validProvider = false;
					if(classes!=null) {
						for (Class class1 : classes) {
							if(class1.equals(TestDataSource.class)) {
								validProvider = true;
								break;
							}
						}
					}
					Assert.assertTrue("DataSource class should extend the TestDataSource class", validProvider);
					Object providerInstance = claz.newInstance();
					testDataSource = (TestDataSource)providerInstance;
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
			}
			
			testDataSource.setClassName(dataSource.getDataSourceClass());
			testDataSource.setArgs(dataSource.getArgs());
			testDataSource.setContext(this);
			testDataSource.setDataSourceName(dataSource.getDataSourceName());
			if(dataSource.getPoolSize()>1)
			{
				testDataSource.setPoolSize(dataSource.getPoolSize());
			}
			
			if(forProfiling)
			{
				testDataSource.setPoolSize(1);
				testDataSource.init();
				dataSourceMapForProfiling.put(dataSource.getDataSourceName(), testDataSource);
			}
			else
			{
				testDataSource.init();
				dataSourceMap.put(dataSource.getDataSourceName(), testDataSource);
			}
		}
	}
	
	public Map<String, TestDataSource> getDataSourceMapForProfiling()
	{
		return dataSourceMapForProfiling;
	}
	
	@SuppressWarnings("rawtypes")
	private void handleHooks(List<GatfTestDataSourceHook> dataSourceHooks)
	{
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooks) {
			Assert.assertNotNull("DataSourceHook name is not defined", dataSourceHook.getHookName());
			Assert.assertNotNull("DataSourceHook query string is not defined", dataSourceHook.getQueryStrs());
			Assert.assertNull("Duplicate DataSourceHook name found", dataSourceHooksMap.get(dataSourceHook.getHookName()));
			
			TestDataSource dataSource = null;
			if(dataSourceHook.getDataSourceName()!=null && dataSourceHook.getHookClass()==null)
			{
				dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
				Assert.assertNotNull("No DataSource found", dataSource);
			}
			else if(dataSourceHook.getDataSourceName()!=null && dataSourceHook.getHookClass()!=null)
			{
				Assert.assertNotNull("Specify either hookClass or dataSourceName", null);
			}
			else if(dataSourceHook.getDataSourceName()==null && dataSourceHook.getHookClass()==null)
			{
				Assert.assertNotNull("Specify any one of hookClass or dataSourceName", null);
			}
			
			dataSourceHooksMap.put(dataSourceHook.getHookName(), dataSourceHook);
			
			TestDataHook testDataHook = null;
			if(dataSourceHook.getHookClass()!=null) {
				try {
					Class claz = loadCustomClass(dataSourceHook.getHookClass());
					Class[] classes = claz.getInterfaces();
					boolean validProvider = false;
					if(classes!=null) {
						for (Class class1 : classes) {
							if(class1.equals(TestDataProvider.class)) {
								validProvider = true;
								break;
							}
						}
					}
					Assert.assertTrue("Hook class should implement the TestDataHook class", validProvider);
					Object providerInstance = claz.newInstance();
					testDataHook = (TestDataHook)providerInstance;
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
			} else {
				testDataHook = dataSource;
			}
			
			if(dataSourceHook.isExecuteOnStart()) {
				for (String query : dataSourceHook.getQueryStrs()) {
					boolean flag = false;
					try {
						flag = testDataHook.execute(query);
					} catch (Throwable e) {
					}
					if(!flag) {
						logger.severe("Startup DataSourceHook execution for " + dataSourceHook.getHookName()
								+ " failed, queryString = " + query);
					}
				}
			}
		}
	}
	
	private void handleProviders(List<GatfTestDataProvider> providerTestDataList)
	{
		for (GatfTestDataProvider provider : providerTestDataList) {
			Assert.assertNotNull("Provider name is not defined", provider.getProviderName());
			
			Assert.assertNotNull("Provider properties is not defined", provider.getProviderProperties());
			
			Assert.assertNull("Duplicate Provider name found", providerTestDataMap.get(provider.getProviderName()));
			
			TestDataSource dataSource = null;
			if(provider.getDataSourceName()!=null && provider.getProviderClass()==null)
			{	
				Assert.assertNotNull("Provider DataSource name is not defined", provider.getDataSourceName());
				dataSource = dataSourceMap.get(provider.getDataSourceName());
				
				Assert.assertNotNull("No DataSource found", dataSource);
				
				if(dataSource instanceof MongoDBTestDataSource)
				{
					Assert.assertNotNull("Provider query string is not defined", provider.getQueryStr());
					Assert.assertNotNull("Provider source properties not defined", provider.getSourceProperties());
				}
				
				if(dataSource instanceof SQLDatabaseTestDataSource)
				{
					Assert.assertNotNull("Provider query string is not defined", provider.getQueryStr());
				}
			}
			else if(provider.getDataSourceName()!=null && provider.getProviderClass()!=null)
			{
				Assert.assertNotNull("Specify either providerClass or dataSourceName", null);
			}
			else if(provider.getDataSourceName()==null && provider.getProviderClass()==null)
			{
				Assert.assertNotNull("Specify any one of providerClass or dataSourceName", null);
			}
			
			
			if(provider.isEnabled()==null) {
				provider.setEnabled(true);
			}
			
			if(!provider.isEnabled()) {
				logger.info("Provider " + provider.getProviderName() + " is Disabled...");
				continue;
			}
			
			if(provider.isLive()) {
				liveProviders.put(provider.getProviderName(), provider);
				logger.info("Provider " + provider.getProviderName() + " is a Live one...");
				continue;
			}
			
			List<Map<String, String>> testData = getProviderData(provider, null);
			if(gatfExecutorConfig.isSeleniumExecutor() && gatfExecutorConfig.getConcurrentUserSimulationNum()>1) {
			    for (int i = 0; i < gatfExecutorConfig.getConcurrentUserSimulationNum(); i++)
                {
			        if(FileTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass().trim())) {
			            if(i==0) {
			                providerTestDataMap.put(provider.getProviderName()+(i+1), testData);
			                continue;
			            }
			            GatfTestDataProvider tp = new GatfTestDataProvider(provider);
	                    tp.setProviderName(provider.getProviderName()+(i+1));
	                    tp.getArgs()[0] = tp.getArgs()[0] + i;
	                    try {
	                        logger.info("Concurrent simulation scenario #"+(i+1)+" fetching provider with filePath "+tp.getArgs()[0]);
	                        List<Map<String, String>> testDataT = getProviderData(tp, null);
	                        if(testDataT==null) {
	                            testDataT = testData;
	                        }
	                        providerTestDataMap.put(tp.getProviderName(), testDataT);
	                    } catch (Throwable e) {
	                        logger.severe("Cannot find data provider for the concurrent simulation scenario #"+(i+1)+" with name " + tp.getProviderName());
	                        providerTestDataMap.put(tp.getProviderName(), testData);
	                    }
			        } else {
			            logger.severe("Concurrent simulation scenarios need file data providers");
			        }
                }
			} else {
			    providerTestDataMap.put(provider.getProviderName(), testData);
			}
		}
	}
	
	public List<Map<String, String>> getLiveProviderData(String provName, TestCase testCase)
	{
		GatfTestDataProvider provider = liveProviders.get(provName);
		return getProviderData(provider, testCase);
	}
	
	public List<Map<String, String>> getAnyProviderData(String provName, TestCase testCase)
	{
		if(liveProviders.containsKey(provName))
		{
			GatfTestDataProvider provider = liveProviders.get(provName);
			return getProviderData(provider, testCase);
		}
		else
		{
			return providerTestDataMap.get(provName);
		}
	}
	
	@SuppressWarnings("rawtypes")
	private List<Map<String, String>> getProviderData(GatfTestDataProvider provider, TestCase testCase) {
		
		TestDataSource dataSource = dataSourceMap.get(provider.getDataSourceName());
		
		TestDataProvider testDataProvider = null;
		List<Map<String, String>> testData = null;
		if(provider.getProviderClass()!=null) {
			if(FileTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass().trim())) {
				testDataProvider = new FileTestDataProvider();
			} else if(InlineValueTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass().trim())) {
				testDataProvider = new InlineValueTestDataProvider();
			} else if(RandomValueTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass().trim())) {
				testDataProvider = new RandomValueTestDataProvider();
			} else {
				try {
					Class claz = loadCustomClass(provider.getProviderClass());
					Class[] classes = claz.getInterfaces();
					boolean validProvider = false;
					if(classes!=null) {
						for (Class class1 : classes) {
							if(class1.equals(TestDataProvider.class)) {
								validProvider = true;
								break;
							}
						}
					}
					Assert.assertTrue("Provider class should implement the TestDataProvider class", validProvider);
					Object providerInstance = claz.newInstance();
					testDataProvider = (TestDataProvider)providerInstance;
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
			}
		} else {
			testDataProvider = dataSource;
		}
		
		//Live provider queries can have templatized parameter names
		if(provider.isLive() && provider.getQueryStr()!=null)
		{
			String oQs = provider.getQueryStr();
			provider = new GatfTestDataProvider(provider);
			if(testCase!=null)
			{
				provider.setQueryStr(getWorkflowContextHandler().evaluateTemplate(testCase, provider.getQueryStr(), this));
				if(provider.getQueryStr()==null || provider.getQueryStr().isEmpty()) {
					provider.setQueryStr(oQs);
				}
			}
		}
		
		testData = testDataProvider.provide(provider, this);
		if(testDataProvider instanceof FileTestDataProvider) {
			fileProviderStateMap.put(provider.getProviderName(), ((FileTestDataProvider)testDataProvider).getHash());
		}
		return testData;
	}

	public DistributedAcceptanceContext getDistributedContext(String node, List<Object[]> selTestdata)
	{
		DistributedAcceptanceContext distributedTestContext = new DistributedAcceptanceContext();
		distributedTestContext.setConfig(gatfExecutorConfig);
		distributedTestContext.setHttpHeaders(httpHeaders);
		distributedTestContext.setSoapActions(soapActions);
		distributedTestContext.setSoapEndpoints(soapEndpoints);
		distributedTestContext.setSoapMessages(soapStrMessages);
		distributedTestContext.setNode(node);
		distributedTestContext.setSelTestdata(selTestdata);
		
		return distributedTestContext;
	}
	
	@SuppressWarnings("rawtypes")
	private Class loadCustomClass(String className) throws ClassNotFoundException
	{
		return getProjectClassLoader().loadClass(className);
	}
	
	public List<TestCase> getServerLogsApiLst() {
		return serverLogsApiLst;
	}
	
	public TestCase getServerLogApi(boolean isAuth)
	{
		if(serverLogsApiLst.size()>0)
		{
			for (TestCase tc : serverLogsApiLst) {
				if(isAuth && gatfExecutorConfig.isServerLogsApiAuthEnabled() && "authapi".equals(tc.getName()))
				{
					tc.setServerApiAuth(true);
					tc.setExternalApi(true);
					return tc;
				}
				else if(!isAuth && "targetapi".equals(tc.getName()))
				{
					tc.setServerApiTarget(true);
					tc.setSecure(gatfExecutorConfig.isServerLogsApiAuthEnabled());
					tc.setExternalApi(true);
					return tc;
				}
			}
		}
		return null;
	}
}
