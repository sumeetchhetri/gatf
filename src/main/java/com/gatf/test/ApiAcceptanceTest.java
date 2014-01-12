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
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.reficio.ws.client.core.SoapClient;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
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


public class ApiAcceptanceTest {

	private static final Logger logger = Logger.getLogger(ApiAcceptanceTest.class);

	private static final Map<String, String> httpHeaders = new HashMap<String, String>();

	private static String testCaseDir = null;
	
	private static String authUrl = null;
	
	private static String soapAuthWsdlKey = null;
	
	private static String soapAuthOperation = null;
	
	private static String[] authExtractAuthParams = null;
	
	private static String[] soapAuthExtractAuthParams = null;
	
	private static String sessionIdentifier = null;
	
	private static Map<String, String> soapEndpoints = new HashMap<String, String>();
	
	private static Map<String, Document> soapMessages = new HashMap<String, Document>();
	
	private static Map<String, String> soapActions = new HashMap<String, String>();
	
	private static boolean isAuthEnabled() {
		return authExtractAuthParams!=null;
	}
	
	private static boolean isSoapAuthEnabled() {
		return soapAuthExtractAuthParams!=null;
	}
	
	private static boolean isSoapAuthTestCase(TestCase tcase) {
		return (soapAuthWsdlKey+soapAuthOperation).equals(tcase.getWsdlKey()+tcase.getOperationName());
	}
	
	@BeforeSuite
	@Parameters({"testCasesPath", "baseUrl", "authEnabled", "authUrl", "authExtractAuth", "wsdlLocFile",
		"soapAuthEnabled", "soapAuthWsdlKey", "soapAuthOperation", "soapAuthExtractAuth"})
	public void init(ITestContext context, String testCasesPath,
			String baseUrl, boolean authEnabled, String authUrlp,
			String authExtractAuthp, String wsdlLocFile, boolean soapAuthEnabled,
			String soapAuthWsdlKeyp, String soapAuthOperationp, String soapAuthExtractAuthp) throws Exception
	{
		RestAssured.baseURI =  baseUrl;
		
		authUrl = authUrlp;
		testCaseDir = testCasesPath;
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
		
		URL url = Thread.currentThread().getContextClassLoader().getResource(wsdlLocFile);
		File file = new File(url.getPath());
		Scanner s = new Scanner(file);
		s.useDelimiter("\n");
		List<String> list = new ArrayList<String>();
		while (s.hasNext()){
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
		URL url = Thread.currentThread().getContextClassLoader().getResource(testCaseDir);
		File dir = new File(url.getPath());
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
				while (s.hasNext()){
					list.add(s.next().replace("\r", ""));
				}
				s.close();

				try {
					for (String csvLine : list) {
						if(!csvLine.trim().isEmpty())
						{
							testcases.add(new TestCase(csvLine));
						}
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
		
		if(!testCase.isSoapBase()) {
			testRestBased(testCase);
		} else {
			testSoapBased(testCase);
		}
		Reporter.log("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
		logger.info("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
		logger.info("============================================================\n");

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

		if(testCase.getContent()!=null && !testCase.getMethod().equals(Method.GET.name()))
			request.content(testCase.getContent());

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
			if(turl.indexOf("?")!=-1) {
				turl += "&" + authExtractAuthParams[2] + "=" + sessionIdentifier;
			} else {
				turl += "?" + authExtractAuthParams[2] + "=" + sessionIdentifier;
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
		if(testCase.getExpectedNodes()==null || testCase.getExpectedNodes().isEmpty())
			return;

		if(testCase.getExpectedResContentType().equals(MediaType.APPLICATION_JSON))
		{
			JsonPath jsonPath = new JsonPath(resText);
			for (String node : testCase.getExpectedNodes()) {
				Assert.assertNotNull(jsonPath.getString(node));
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

				for (String node : testCase.getExpectedNodes()) {
					String expression = node.replaceAll("\\.", "\\/");
					expression = "/" + expression;
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
				}
				if(isAuthEnabled() && authUrl.equals(url) && authExtractAuthParams[1].equalsIgnoreCase("xml")) {
					String expression = authExtractAuthParams[0].replaceAll("\\.", "\\/");
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
		String request = sw.toString();
		logger.info(request);
		String resText = null;
		if(soapActions.get(testCase.getWsdlKey()+testCase.getOperationName())!=null)
			resText = client.post(soapActions.get(testCase.getWsdlKey()+testCase.getOperationName()), request);
		else
			resText = client.post(request);
		logger.info(resText);
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xmlDocument = db.parse(new ByteArrayInputStream(resText.getBytes()));

			for (String node : testCase.getExpectedNodes()) {
				Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
				Node body = getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = getNextElement(body);
				Node returnBody = getNextElement(requestBody);
				String expression = createXPathExpression(node, envelope, body, requestBody, returnBody);
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue(xmlNodeList!=null && xmlNodeList.getLength()>0);
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
