package com.gatf.test.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.w3c.dom.Document;

import com.gatf.core.GatfExecutorConfig;
import com.gatf.report.TestCaseReport;
import com.gatf.test.dataprovider.DatabaseTestCaseDataProvider;
import com.gatf.test.dataprovider.FileTestCaseDataProvider;
import com.gatf.test.dataprovider.GatfTestDataConfig;
import com.gatf.test.dataprovider.GatfTestDataProvider;
import com.gatf.test.dataprovider.InlineValueTestCaseDataProvider;
import com.gatf.test.dataprovider.TestDataProvider;
import com.gatf.test.executor.PerformanceTestCaseExecutor;
import com.gatf.test.executor.ScenarioTestCaseExecutor;
import com.gatf.test.executor.SingleTestCaseExecutor;
import com.gatf.test.executor.TestCaseExecutorUtil;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;

public class AcceptanceTestContext {

	private Logger logger = Logger.getLogger(TestCaseExecutorUtil.class.getSimpleName());
	
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
	
	private final Map<String, String> httpHeaders = new HashMap<String, String>();

	private ConcurrentHashMap<Integer, String> sessionIdentifiers = new ConcurrentHashMap<Integer, String>();
	
	private final Map<String, String> soapEndpoints = new HashMap<String, String>();
	
	private final Map<String, Document> soapMessages = new HashMap<String, Document>();
	
	private final Map<String, String> soapActions = new HashMap<String, String>();
	
	private final Map<String, ConcurrentLinkedQueue<TestCaseReport>> finalTestResults = 
			new ConcurrentHashMap<String, ConcurrentLinkedQueue<TestCaseReport>>();
	
	private final WorkflowContextHandler workflowContextHandler = new WorkflowContextHandler();
	
	private GatfExecutorConfig gatfExecutorConfig;
	
	private ClassLoader projectClassLoader;
	
	public ClassLoader getProjectClassLoader() {
		return projectClassLoader;
	}

	private final Map<String, List<Map<String, String>>> providerTestDataMap = new HashMap<String, List<Map<String,String>>>();
	
	public Map<String, List<Map<String, String>>> getProviderTestDataMap() {
		return providerTestDataMap;
	}

	private final SingleTestCaseExecutor singleTestCaseExecutor = new SingleTestCaseExecutor();
	
	private final ScenarioTestCaseExecutor scenarioTestCaseExecutor = new ScenarioTestCaseExecutor();
	
	private final PerformanceTestCaseExecutor performanceTestCaseExecutor = new PerformanceTestCaseExecutor();

	public SingleTestCaseExecutor getSingleTestCaseExecutor() {
		return singleTestCaseExecutor;
	}

	public ScenarioTestCaseExecutor getScenarioTestCaseExecutor() {
		return scenarioTestCaseExecutor;
	}

	public PerformanceTestCaseExecutor getPerformanceTestCaseExecutor() {
		return performanceTestCaseExecutor;
	}

	public AcceptanceTestContext(GatfExecutorConfig gatfExecutorConfig, ClassLoader projectClassLoader)
	{
		this.gatfExecutorConfig = gatfExecutorConfig;
		this.projectClassLoader = projectClassLoader;
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
		if(simulationNumber==null) {
			simulationNumber = 0;
		}
		sessionIdentifiers.put(simulationNumber, identifier);
	}
	
	public String getSessionIdentifier(TestCase testCase) {
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

	public Map<String, ConcurrentLinkedQueue<TestCaseReport>> getFinalTestResults() {
		return finalTestResults;
	}

	public WorkflowContextHandler getWorkflowContextHandler() {
		return workflowContextHandler;
	}
	
	public File getResourceFile(String filename) {
		try {
			if(gatfExecutorConfig.getTestCasesBasePath()!=null)
			{
				File basePath = new File(gatfExecutorConfig.getTestCasesBasePath());
				File resource = new File(basePath, filename);
				return resource;
			}
			else
			{
				URL url = Thread.currentThread().getContextClassLoader().getResource(filename);
				File resource = new File(url.getPath());
				return resource;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	public File getNewOutResourceFile(String filename) {
		try {
			if(gatfExecutorConfig.getOutFilesBasePath()!=null)
			{
				File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
				File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
				File file = new File(resource, filename);
				if(!file.exists())
					file.createNewFile();
				return file;
			}
			else
			{
				URL url = Thread.currentThread().getContextClassLoader().getResource(gatfExecutorConfig.getOutFilesDir());
				File resource = new File(url.getPath());
				File file = new File(resource, filename);
				if(!file.exists())
					file.createNewFile();
				return file;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	public static void removeFolder(File folder)
	{
		if(folder==null || !folder.exists())return;
		String[] entries = folder.list();
		for(String s: entries){
		    File currentFile = new File(folder.getPath(), s);
		    currentFile.delete();
		}
		folder.delete();
	}
	
	public void validateAndInit() throws Exception
	{
		gatfExecutorConfig.validate();
		
		Assert.assertEquals("Testcase directory not found...", getResourceFile(gatfExecutorConfig.getTestCaseDir()).exists(), true);
		
		if(gatfExecutorConfig.getOutFilesDir()!=null && !gatfExecutorConfig.getOutFilesDir().trim().isEmpty())
		{
			try
			{
				if(gatfExecutorConfig.getOutFilesBasePath()!=null)
				{
					File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
					File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
					removeFolder(resource);
					File nresource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
					nresource.mkdirs();
				}
				else
				{
					URL url = Thread.currentThread().getContextClassLoader().getResource(".");
					File resource = new File(url.getPath());
					File file = new File(resource, gatfExecutorConfig.getOutFilesDir());
					removeFolder(file);
					File nresource = new File(resource, gatfExecutorConfig.getOutFilesDir());
					nresource.mkdirs();
				}
			} catch (Exception e) {
				gatfExecutorConfig.setOutFilesDir(null);
			}
			Assert.assertNotNull("Testcase out file directory not found...", gatfExecutorConfig.getOutFilesDir());
		}
		else
		{
			URL url = Thread.currentThread().getContextClassLoader().getResource("out");
			File resource = new File(url.getPath());
			removeFolder(resource);
			File nresource = new File(url.getPath());
			nresource.mkdir();
			gatfExecutorConfig.setOutFilesDir("out");
			gatfExecutorConfig.setOutFilesBasePath(gatfExecutorConfig.getTestCasesBasePath());
		}
		
		initSoapContextAndHttpHeaders();
		
		initTestDataProviderAndGlobalVariables();
	}
	
	@SuppressWarnings("rawtypes")
	private void initTestDataProviderAndGlobalVariables() {
		GatfTestDataConfig gatfTestDataConfig = null;
		if(gatfExecutorConfig.getTestDataConfigFile()!=null) {
			File file = getResourceFile(gatfExecutorConfig.getTestDataConfigFile());
			Assert.assertNotNull("Testdata configuration file not found...", file);
			Assert.assertEquals("Testdata configuration file not found...", file.exists(), true);
			
			XStream xstream = new XStream(new XppDriver());
			xstream.processAnnotations(new Class[]{GatfTestDataConfig.class, GatfTestDataProvider.class});
			xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
			xstream.alias("args", String[].class);
			xstream.alias("arg", String.class);
			
			gatfTestDataConfig = (GatfTestDataConfig)xstream.fromXML(file);
			gatfExecutorConfig.setGatfTestDataConfig(gatfTestDataConfig);
		} else {
			gatfTestDataConfig = gatfExecutorConfig.getGatfTestDataConfig();
		}
		
		if(gatfTestDataConfig!=null) {
			getWorkflowContextHandler().addGlobalVariables(gatfTestDataConfig.getGlobalVariables());
			
			for (GatfTestDataProvider provider : gatfTestDataConfig.getProviderTestDataList()) {
				Assert.assertNotNull("Provider name is not defined", provider.getProviderName());
				Assert.assertNull("Duplicate Provider name found", providerTestDataMap.get(provider.getProviderName()));
				Assert.assertNotNull("Provider class is not defined", provider.getProviderClass());
				Assert.assertNotNull("Provider args are not defined", provider.getArgs());
				
				TestDataProvider testDataProvider = null;
				if(DatabaseTestCaseDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new DatabaseTestCaseDataProvider();
				} else if(FileTestCaseDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new FileTestCaseDataProvider();
				} else if(InlineValueTestCaseDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new InlineValueTestCaseDataProvider();
				} else {
					try {
						Class claz = getProjectClassLoader().loadClass(provider.getProviderClass());
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
						Assert.assertTrue("Provider class should implement the TestDataProvider interface", validProvider);
						Object providerInstance = claz.newInstance();
						testDataProvider = (TestDataProvider)providerInstance;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				List<Map<String, String>> testData = testDataProvider.provide(provider.getArgs(), this);
				providerTestDataMap.put(provider.getProviderName(), testData);
			}
		}
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
}
