package com.gatf.test;

/*
	Copyright 2013-2014, Sumeet Chhetri
	
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

import static com.jayway.restassured.RestAssured.given;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.reficio.ws.client.TransmissionException;
import org.reficio.ws.client.core.SoapClient;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.internal.http.Method;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

import edu.emory.mathcs.backport.java.util.Collections;


public class ApiAcceptanceTest {

	private static final Logger logger = Logger.getLogger(ApiAcceptanceTest.class);

	private static final Map<String, String> httpHeaders = new HashMap<String, String>();

	private static String testCaseDir = null;
	
	private static String testCasesBasePath = null;
	
	private static String authUrl = null;
	
	private static String soapAuthWsdlKey = null;
	
	private static String soapAuthOperation = null;
	
	private static String[] authExtractAuthParams = null;
	
	private static String[] soapAuthExtractAuthParams = null;
	
	private static String sessionIdentifier = null;
	
	private static final Map<String, String> soapEndpoints = new HashMap<String, String>();
	
	private static final Map<String, Document> soapMessages = new HashMap<String, Document>();
	
	private static final Map<String, String> soapActions = new HashMap<String, String>();
	
	private static final Map<String, String> globalworkflowContext = new ConcurrentHashMap<String, String>();
	
	private static VelocityEngine engine = new VelocityEngine();
	
	private static boolean isAuthEnabled() {
		return authExtractAuthParams!=null;
	}
	
	private static boolean isSoapAuthEnabled() {
		return soapAuthExtractAuthParams!=null;
	}
	
	private static boolean isSoapAuthTestCase(TestCase tcase) {
		return soapAuthWsdlKey!=null && soapAuthOperation!=null &&
				(soapAuthWsdlKey+soapAuthOperation).equals(tcase.getWsdlKey()+tcase.getOperationName());
	}
	
	private static File getResourceFile(String filename) {
		try {
			if(testCasesBasePath!=null && !testCasesBasePath.equalsIgnoreCase("null"))
			{
				File basePath = new File(testCasesBasePath);
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
	
	@BeforeSuite
	@Parameters({"testCasesPath", "baseUrl", "authEnabled", "authUrl", "authExtractAuth", "wsdlLocFile",
		"soapAuthEnabled", "soapAuthWsdlKey", "soapAuthOperation", "soapAuthExtractAuth", "testCasesBasePath"})
	public void init(ITestContext context, String testCasesPath,
			String baseUrl, @Optional("false") boolean authEnabled, @Optional("null") String authUrlp,
			@Optional("null") String authExtractAuthp, @Optional("null") String wsdlLocFile, @Optional("false") boolean soapAuthEnabled,
			@Optional("null") String soapAuthWsdlKeyp, @Optional("null") String soapAuthOperationp, @Optional("null") String soapAuthExtractAuthp,
			@Optional("null") String testCasesBasePathp) throws Exception
	{
		authUrlp = (authUrlp==null||authUrlp.equalsIgnoreCase("null")?null:authUrlp);
		authExtractAuthp = (authExtractAuthp==null||authExtractAuthp.equalsIgnoreCase("null")?null:authExtractAuthp);
		wsdlLocFile = (wsdlLocFile==null||wsdlLocFile.equalsIgnoreCase("null")?null:wsdlLocFile);
		soapAuthWsdlKeyp = (soapAuthWsdlKeyp==null||soapAuthWsdlKeyp.equalsIgnoreCase("null")?null:soapAuthWsdlKeyp);
		soapAuthOperationp = (soapAuthOperationp==null||soapAuthOperationp.equalsIgnoreCase("null")?null:soapAuthOperationp);
		soapAuthOperationp = (soapAuthOperationp==null||soapAuthOperationp.equalsIgnoreCase("null")?null:soapAuthOperationp);
		soapAuthExtractAuthp = (soapAuthExtractAuthp==null||soapAuthExtractAuthp.equalsIgnoreCase("null")?null:soapAuthExtractAuthp);
		testCasesBasePathp = (testCasesBasePathp==null||testCasesBasePathp.equalsIgnoreCase("null")?null:testCasesBasePathp);
		
		engine.init();
		
		RestAssured.baseURI =  baseUrl;
		
		authUrl = authUrlp;
		testCaseDir = testCasesPath;
		testCasesBasePath = testCasesBasePathp;
		if(authExtractAuthp!=null)
		{
			authExtractAuthParams = authExtractAuthp.split(",");
		}

		if(authEnabled) {
			Assert.assertTrue(authExtractAuthParams.length==4);
			Assert.assertTrue(!authExtractAuthParams[0].isEmpty());
			Assert.assertTrue(authExtractAuthParams[1].equalsIgnoreCase("json") ||
					authExtractAuthParams[1].equalsIgnoreCase("xml") ||
					authExtractAuthParams[1].equalsIgnoreCase("header") ||
					authExtractAuthParams[1].equalsIgnoreCase("plain") ||
					authExtractAuthParams[1].equalsIgnoreCase("cookie"));
			Assert.assertTrue(!authExtractAuthParams[2].isEmpty());
			Assert.assertTrue(authExtractAuthParams[3].equalsIgnoreCase("queryparam") ||
					authExtractAuthParams[3].equalsIgnoreCase("postparam") ||
					authExtractAuthParams[3].equalsIgnoreCase("header"));
			Assert.assertTrue(authUrl!=null && !authUrl.isEmpty());
		} else {
			authExtractAuthParams = null;
		}
		
		if(soapAuthEnabled) {
			soapAuthWsdlKey = soapAuthWsdlKeyp;
			soapAuthOperation = soapAuthOperationp;
			Assert.assertTrue(soapAuthWsdlKey!=null && !soapAuthWsdlKey.isEmpty());
			Assert.assertTrue(soapAuthOperation!=null && !soapAuthOperation.isEmpty());
			
			soapAuthExtractAuthParams = soapAuthExtractAuthp.split(",");
			Assert.assertTrue(soapAuthExtractAuthParams.length==3);
			Assert.assertTrue(!soapAuthExtractAuthParams[0].isEmpty());
			Assert.assertTrue(!soapAuthExtractAuthParams[1].isEmpty());
			Assert.assertTrue(!soapAuthExtractAuthParams[2].isEmpty());
			Assert.assertTrue(soapAuthExtractAuthParams[2].equalsIgnoreCase("queryparam"));
		} else {
			soapAuthExtractAuthParams = null;
		}
		
		Field[] declaredFields = HttpHeaders.class.getDeclaredFields();
		for (Field field : declaredFields) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
					&& field.getType().equals(String.class)) {
				httpHeaders.put(field.get(null).toString().toLowerCase(), field.get(null).toString());
			}
		}
		
		File file = null;
		if(wsdlLocFile!=null && !wsdlLocFile.trim().isEmpty())
			file = getResourceFile(wsdlLocFile);
		
		if(file!=null)
		{
			Scanner s = new Scanner(file);
			s.useDelimiter("\n");
			List<String> list = new ArrayList<String>();
			while (s.hasNext()) {
				list.add(s.next().replace("\r", ""));
			}
			s.close();
	
			try {
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	} 


	@DataProvider(name = "tests")
	@SuppressWarnings("unchecked")
    public static Object[][] getTestCases() throws Exception {
		
		XStream xstream = new XStream(
			new XppDriver() {
				public HierarchicalStreamWriter createWriter(Writer out) {
					return new PrettyPrintWriter(out) {
						boolean cdata = false;
						@SuppressWarnings("rawtypes")
						public void startNode(String name, Class clazz){
							super.startNode(name, clazz);
							cdata = (name.equals("content") || name.equals("expectedResContent"));
						}
						protected void writeText(QuickWriter writer, String text) {
							if(cdata) {
								writer.write("<![CDATA[");
								writer.write(text);
								writer.write("]]>");
							} else {
								writer.write(text);
							}
						}
					};
				}
			}
		);
		xstream.processAnnotations(new Class[]{TestCase.class});
		xstream.alias("TestCases", List.class);
		
		List<TestCase> testcases = new ArrayList<TestCase>();
		File dir = getResourceFile(testCaseDir);
		if (dir.isDirectory()) {
			File[] xmlFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".xml");
				}
			});

			for (File file : xmlFiles) {
				try {
					testcases.addAll((List<TestCase>)xstream.fromXML(file));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			File[] jsonFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".json");
				}
			});

			for (File file : jsonFiles) {
				try {
					testcases.addAll((List<TestCase>)new ObjectMapper().readValue(file, new TypeReference<List<TestCase>>(){}));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			File[] csvFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".csv");
				}
			});

			for (File file : csvFiles) {

				Scanner s = new Scanner(file);
				s.useDelimiter("\n");
				List<String> list = new ArrayList<String>();
				while (s.hasNext()) {
					String csvLine = s.next().replace("\r", "");
					if(!csvLine.trim().isEmpty() && !csvLine.trim().startsWith("//")) {
						list.add(csvLine);
					}
				}
				s.close();

				try {
					for (String csvLine : list) {
						testcases.add(new TestCase(csvLine));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		List<TestCase> pretestcases = new ArrayList<TestCase>();
		List<TestCase> posttestcases = new ArrayList<TestCase>();
		for (TestCase testCase : testcases) {
			if(testCase.getUrl().equalsIgnoreCase(authUrl) || isSoapAuthTestCase(testCase)) {
				pretestcases.add(testCase);
			}
		}
		for (TestCase testCase : testcases) {
			if(!testCase.getUrl().equalsIgnoreCase(authUrl) && !isSoapAuthTestCase(testCase)) {
				posttestcases.add(testCase);
			}
		}
		testcases.clear();
		testcases.addAll(pretestcases);
		testcases.addAll(posttestcases);
		
		Collections.sort(testcases, new Comparator<TestCase>() {
			public int compare(TestCase o1, TestCase o2) {
				 return o1==null ?
				         (o2==null ? 0 : Integer.MIN_VALUE) :
				         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getSequence()).compareTo(o2.getSequence()));
			}
		});
		
		Object[][] objects = new Object[testcases.size()][1];
		for (int i=0;i<objects.length;i++) {
			objects[i] = new Object[]{testcases.get(i)};
		}
        return objects;
    }


	@Test(enabled = true, dataProvider = "tests")
	public void test(TestCase testCase) throws Exception {

		Reporter.log("Running acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
		logger.info("Running acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
		if(testCase.isSkipTest())
		{
			Reporter.log("Skipping acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
			logger.info("Skipping acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
			return;
		}
		if(testCase.isDetailedLog())
		{
			Reporter.log(testCase.toString());
			logger.info(testCase);
		}
		try {
			testCase.validate(httpHeaders);
		} catch (RuntimeException e) {
			Reporter.log("Got exception while running acceptance test " + testCase.getName()+"/"+testCase.getDescription() + " - " + e.getMessage());
			logger.error("Got exception while running acceptance test " + testCase.getName()+"/"+testCase.getDescription(), e);
			throw e;
		}
		
		handleWorklowContext(testCase);
		
		if(!testCase.isSoapBase()) {
			testRestBased(testCase);
		} else {
			testSoapBased(testCase);
		}
		Reporter.log("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
		logger.info("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
		logger.info("============================================================\n");

	}

	private void handleWorklowContext(TestCase testCase) throws Exception {
		if(testCase!=null) {
			VelocityContext context = new VelocityContext(globalworkflowContext);
			if(testCase.getUrl()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getUrl());
				testCase.setUrl(writer.toString());
			}
			if(testCase.getContent()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getContent());
				testCase.setContent(writer.toString());
			}
			if(testCase.getExQueryPart()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getExQueryPart());
				testCase.setExQueryPart(writer.toString());
			}
			if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty()) {
				List<String> expectedNodes = new ArrayList<String>();
				for (String nodecase : testCase.getExpectedNodes()) {
					StringWriter writer = new StringWriter();
					engine.evaluate(context, writer, "ERROR", nodecase);
					expectedNodes.add(writer.toString());
				}
				testCase.setExpectedNodes(expectedNodes);
			}
		}
	}

	private void testRestBased(TestCase testCase) throws Exception {

		RequestSpecification request = given();
		if(testCase.getHeaders()!=null)
			request = request.headers(testCase.getHeaders());
		if(testCase.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE))
			request = request.contentType(testCase.getHeaders().get(HttpHeaders.CONTENT_TYPE));

		if(testCase.isDetailedLog())
		{
			request.log().all();
		}

		ResponseSpecification resspec = request.expect().
				contentType(testCase.getExpectedResContentType()).
				statusCode(testCase.getExpectedResCode()).when();

		if(testCase.getContent()!=null && !testCase.getContent().trim().isEmpty() 
				&& !testCase.getMethod().equals(Method.GET.name()))
		{
			request.content(testCase.getContent());
		}
		else if(testCase.getFilesToUpload()!=null && !testCase.getFilesToUpload().isEmpty())
		{
			for (String filedet : testCase.getFilesToUpload()) {
				String[] mulff = filedet.split(":");
				if(mulff.length==4 || mulff.length==3) {
					String controlname = mulff[0].trim();
					String type = mulff[1].trim();
					String fileNmOrTxt = mulff[2].trim();
					
					String contType = "";
					if(mulff.length==4)
					{
						contType= mulff[3].trim();
					}
					
					if(!type.equalsIgnoreCase("text") && !type.equalsIgnoreCase("file")) {
						logger.error("Invalid type specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(fileNmOrTxt.isEmpty()) {
						logger.error("No file/text specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(type.equalsIgnoreCase("text") && contType.isEmpty()) {
						logger.error("No mime-type specified for text data upload...skipping value - " + filedet);
						continue;
					}
					
					if(type.equalsIgnoreCase("file")) {
						try {
							File file = getResourceFile(fileNmOrTxt);
							request.multiPart(controlname, file);
						} catch (Exception e) {
							logger.error("No file found for file upload...skipping value - " + filedet);
							continue;
						}
					} else {
						request.multiPart(controlname, fileNmOrTxt, contType);
					}
				}			
			}
		}

		if(testCase.isDetailedLog())
		{
			resspec.log().all();
		}
		
		String turl = testCase.getUrl();
		
		String url = testCase.getUrl();
		if(url.indexOf("?")!=-1) {
			url = url.substring(0, url.indexOf("?"));
		}
		
		if(testCase.isSecure() && !authUrl.equals(url)) {
			Assert.assertNotNull(sessionIdentifier);
			
			if(turl.indexOf("?")!=-1 && turl.indexOf("{"+authExtractAuthParams[2]+"}")!=-1) {
				turl = turl.replaceAll("\\{"+authExtractAuthParams[2]+"\\}", sessionIdentifier);
			}
			else
			{
				if(turl.indexOf("?")!=-1) {
					turl += "&" + authExtractAuthParams[2] + "=" + sessionIdentifier;
				} else {
					turl += "?" + authExtractAuthParams[2] + "=" + sessionIdentifier;
				}
			}
		}

		Response restResponse = null;
		if(testCase.getMethod().equals(Method.POST.name()))
			restResponse = resspec.post(turl);
		else if(testCase.getMethod().equals(Method.PUT.name()))
			restResponse = resspec.put(turl);
		else if(testCase.getMethod().equals(Method.GET.name()))
			restResponse = resspec.get(turl);
		else if(testCase.getMethod().equals(Method.DELETE.name()))
			restResponse = resspec.delete(turl);

		String resText = restResponse.asString();

		if(testCase.getExpectedResContentType().equals(MediaType.APPLICATION_JSON))
		{
			JsonPath jsonPath = new JsonPath(resText);
			if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty())
			{
				for (String node : testCase.getExpectedNodes()) {
					String[] nodeCase = node.split(",");
					String nvalue = jsonPath.getString(nodeCase[0]);
					Assert.assertNotNull(nvalue);
					if(nodeCase.length==2) {
						Assert.assertEquals(nvalue, nodeCase[1]);
					}
				}
			}
			if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
			{
				for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
					String jsonValue = jsonPath.getString(entry.getValue());
					Assert.assertNotNull(jsonValue);
					globalworkflowContext.put(entry.getKey(), jsonValue);
				}
			}
			if(isAuthEnabled() && authUrl.equals(url) && authExtractAuthParams[1].equalsIgnoreCase("json")) {
				sessionIdentifier = jsonPath.getString(authExtractAuthParams[0]);
				Assert.assertNotNull(sessionIdentifier);
			}
		}
		else if(testCase.getExpectedResContentType().equals(MediaType.APPLICATION_XML))
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document xmlDocument = db.parse(new ByteArrayInputStream(resText.getBytes()));

				if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty())
				{
					for (String node : testCase.getExpectedNodes()) {
						String[] nodeCase = node.split(",");
						
						String expression = nodeCase[0].replaceAll("\\.", "\\/");
						if(expression.charAt(0)!='/')
							expression = "/" + expression;
						XPath xPath =  XPathFactory.newInstance().newXPath();
						NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
						Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
						String xmlValue = xmlNodeList.item(0).getNodeValue();
						Assert.assertNotNull(xmlValue);
						if(nodeCase.length==2) {
							Assert.assertEquals(xmlValue, nodeCase[1]);
						}
					}
				}
				if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
				{
					for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
						String expression = entry.getValue().replaceAll("\\.", "\\/");
						if(expression.charAt(0)!='/')
							expression = "/" + expression;
						XPath xPath =  XPathFactory.newInstance().newXPath();
						NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
						Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
						String xmlValue = xmlNodeList.item(0).getNodeValue();
						Assert.assertNotNull(xmlValue);
						globalworkflowContext.put(entry.getKey(), xmlValue);
					}
				}
				if(isAuthEnabled() && authUrl.equals(url) && authExtractAuthParams[1].equalsIgnoreCase("xml")) {
					String expression = authExtractAuthParams[0].replaceAll("\\.", "\\/");
					if(expression.charAt(0)!='/')
						expression = "/" + expression;
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
					sessionIdentifier = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull(sessionIdentifier);
				}
			} catch (Exception e) {
				throw e;
			}
		}
	}
	
	private String getLocalNodeName(String nodeName) {
		if(nodeName!=null && nodeName.indexOf(":")!=-1) {
			return nodeName.substring(nodeName.indexOf(":")+1);
		}
		return nodeName;
	}
	
	private Node getNodeByNameCaseInsensitive(Node node, String nodeName) {
		if(node.getNodeName().equalsIgnoreCase(nodeName) ||
				getLocalNodeName(node.getNodeName()).equalsIgnoreCase(nodeName))
			return node;
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				if(node.getChildNodes().item(i).getNodeName().equalsIgnoreCase(nodeName) ||
						getLocalNodeName(node.getChildNodes().item(i).getNodeName()).equalsIgnoreCase(nodeName)) {
					return node.getChildNodes().item(i);
				}
			}
		}
		return null;
	}
	
	private Node getNextElement(Node node) {
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				return node.getChildNodes().item(i);
			}
		}
		return null;
	}
	
	private String createXPathExpression(String suffix, Node... nodes) {
		StringBuilder build = new StringBuilder();
		for (Node node : nodes) {
			build.append("/");
			build.append(getLocalNodeName(node.getNodeName()));
		}
		build.append("/");
		build.append(suffix.replaceAll("\\.", "\\/"));
		return build.toString();
	}
	
	private void testSoapBased(TestCase testCase) throws Exception {
		String endpoint = soapEndpoints.get(testCase.getWsdlKey());
		
		if(testCase.isSecure() && isSoapAuthEnabled() && !isSoapAuthTestCase(testCase)) {
			Assert.assertNotNull(sessionIdentifier);
			if(endpoint.indexOf("?")!=-1) {
				endpoint += "&" + soapAuthExtractAuthParams[1] + "=" + sessionIdentifier;
			} else {
				endpoint += "?" + soapAuthExtractAuthParams[1] + "=" + sessionIdentifier;
			}
		}
		SoapClient client = SoapClient.builder().endpointUri(endpoint).build();
		
		if(testCase.getFilesToUpload()!=null && !testCase.getFilesToUpload().isEmpty())
		{
			for (String filedet : testCase.getFilesToUpload()) {
				String[] mulff = filedet.split(":");
				if(mulff.length==4 || mulff.length==3) {
					String controlname = mulff[0].trim();
					String type = mulff[1].trim();
					String fileNmOrTxt = mulff[2].trim();
					
					String contType = "";
					if(mulff.length==4)
					{
						contType= mulff[3].trim();
					}
					
					if(!type.equalsIgnoreCase("file") || !type.equalsIgnoreCase("file")) {
						logger.error("Invalid type specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(fileNmOrTxt.isEmpty()) {
						logger.error("No file/text specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(type.equalsIgnoreCase("text") && contType.isEmpty()) {
						logger.error("No mime-type specified for text data upload...skipping value - " + filedet);
						continue;
					}
					
					if(type.equalsIgnoreCase("file")) {
						try {
							File file = getResourceFile(fileNmOrTxt);
							byte[] fileData = IOUtils.toByteArray(new FileInputStream(file));
							String fileContents = Base64.encodeBase64URLSafeString(fileData);
							testCase.getSoapParameterValues().put(controlname, fileContents);
						} catch (Exception e) {
							logger.error("No file found for file upload...skipping value - " + filedet);
							continue;
						}
					} else {
						testCase.getSoapParameterValues().put(controlname, fileNmOrTxt);
					}
				}
			}
		}
		
		String request = testCase.getContent();
		
		if(request==null || request.trim().isEmpty())
		{
			Document soapMessage = soapMessages.get(testCase.getWsdlKey()+testCase.getOperationName());
			for (Map.Entry<String, String> entry : testCase.getSoapParameterValues().entrySet()) {
				Node envelope = getNodeByNameCaseInsensitive(soapMessage.getFirstChild(), "envelope");
				Node body = getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = getNextElement(body);
				String expression = createXPathExpression(entry.getKey(), envelope, body, requestBody);
				XPath xPath = XPathFactory.newInstance().newXPath();
				NodeList nodelist = (NodeList) xPath.compile(expression).evaluate(soapMessage, XPathConstants.NODESET);
				Assert.assertNotNull(nodelist!=null && nodelist.getLength()>0);
				nodelist.item(0).getFirstChild().setNodeValue(entry.getValue());
			}
			
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");        
			StringWriter sw = new StringWriter();
			StreamResult result = new StreamResult(sw);
			DOMSource source = new DOMSource(soapMessage);
			transformer.transform(source, result);
			request = sw.toString();
		}
		
		logger.info(request);
		String resText = null;
		try {
			if(soapActions.get(testCase.getWsdlKey()+testCase.getOperationName())!=null)
				resText = client.post(soapActions.get(testCase.getWsdlKey()+testCase.getOperationName()), request);
			else
				resText = client.post(request);
			logger.info(resText);
		} catch(TransmissionException e) {
			Assert.assertEquals(testCase.getExpectedResCode(), e.getErrorCode().intValue());
			return;
		}
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xmlDocument = db.parse(new ByteArrayInputStream(resText.getBytes()));

			if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty())
			{
				for (String node : testCase.getExpectedNodes()) {
					String[] nodeCase = node.split(",");
					Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
					Node body = getNodeByNameCaseInsensitive(envelope, "body");
					Node requestBody = getNextElement(body);
					Node returnBody = getNextElement(requestBody);
					String expression = createXPathExpression(nodeCase[0], envelope, body, requestBody, returnBody);
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
					String xmlValue = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull(xmlValue);
					if(nodeCase.length==2) {
						Assert.assertEquals(xmlValue, nodeCase[1]);
					}
				}
			}
			if(testCase.getWorkflowContextParameterMap()!=null && !testCase.getWorkflowContextParameterMap().isEmpty())
			{
				for (Map.Entry<String, String> entry : testCase.getWorkflowContextParameterMap().entrySet()) {
					Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
					Node body = getNodeByNameCaseInsensitive(envelope, "body");
					Node requestBody = getNextElement(body);
					Node returnBody = getNextElement(requestBody);
					String expression = createXPathExpression(entry.getValue(), envelope, body, requestBody, returnBody);
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
					String xmlValue = xmlNodeList.item(0).getNodeValue();
					Assert.assertNotNull(xmlValue);
					globalworkflowContext.put(entry.getKey(), xmlValue);
				}
			}
			if(isSoapAuthEnabled() && isSoapAuthTestCase(testCase)) {
				Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
				Node body = getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = getNextElement(body);
				Node returnBody = getNextElement(requestBody);
				String expression = createXPathExpression(soapAuthExtractAuthParams[0], envelope, body, requestBody, returnBody);
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
				sessionIdentifier = xmlNodeList.item(0).getFirstChild().getNodeValue();
				Assert.assertNotNull(sessionIdentifier);
			}
		} catch (Exception e) {
			throw e;
		}
	}
}
