/*
    Copyright 2013-2016, Sumeet Chhetri
    
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
package com.gatf.executor.executor;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.w3c.dom.Document;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.executor.validator.JSONResponseValidator;
import com.gatf.executor.validator.NoContentResponseValidator;
import com.gatf.executor.validator.SOAPResponseValidator;
import com.gatf.executor.validator.TextResponseValidator;
import com.gatf.executor.validator.XMLResponseValidator;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Part;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.multipart.FilePart;
import com.ning.http.multipart.StringPart;
import com.ning.http.util.Base64;

/**
 * @author Sumeet Chhetri
 * The main executor class which handles http layer invocations/reponse handling
 * Uses the async-http-client library to make the execution performant/concurrent
 */
public class TestCaseExecutorUtil {

	private Logger logger = Logger.getLogger(TestCaseExecutorUtil.class.getSimpleName());
	
	private static final JSONResponseValidator jsonResponseValidator = new JSONResponseValidator();
	
	private static final XMLResponseValidator xmlResponseValidator = new XMLResponseValidator();
	
	private static final SOAPResponseValidator soapResponseValidator = new SOAPResponseValidator();
	
	private static final TextResponseValidator textResponseValidator = new TextResponseValidator();
	
	private static final NoContentResponseValidator noContentResponseValidator = new NoContentResponseValidator(); 
	
	private AsyncHttpClient client;
	
	private AcceptanceTestContext context = null;
	
	public TestCaseExecutorUtil(AcceptanceTestContext context)
	{
		int numConcurrentConns = context.getGatfExecutorConfig().getNumConcurrentExecutions();
		if(context.getGatfExecutorConfig().getConcurrentUserSimulationNum()!=null) {
			numConcurrentConns = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
		}
		
		int maxConns = numConcurrentConns>100?numConcurrentConns:100;
		
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setConnectionTimeoutInMs(context.getGatfExecutorConfig().getHttpConnectionTimeout())
				.setMaximumConnectionsPerHost(numConcurrentConns)
				.setMaximumConnectionsTotal(maxConns)
				.setRequestTimeoutInMs(context.getGatfExecutorConfig().getHttpRequestTimeout())
				.setAllowPoolingConnection(true)
				.setCompressionEnabled(context.getGatfExecutorConfig().isHttpCompressionEnabled())
				.setIOThreadMultiplier(2)
				.build();
		
		client = new AsyncHttpClient(builder.build());
		this.context = context;
	}
	
	private TestCaseExecutorUtil(){}
	
	public static TestCaseExecutorUtil getSingleConnection(AcceptanceTestContext context)
	{
		int maxConns = 1;
		
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setConnectionTimeoutInMs(10000)
				.setMaximumConnectionsPerHost(1)
				.setMaximumConnectionsTotal(maxConns)
				.setRequestTimeoutInMs(100000)
				.setAllowPoolingConnection(true)
				.setCompressionEnabled(false)
				.setIOThreadMultiplier(2)
				.build();
		
		TestCaseExecutorUtil util = new TestCaseExecutorUtil();
		util.client = new AsyncHttpClient(builder.build());
		util.context = context;
		return util;
	}
	
	public AcceptanceTestContext getContext() {
		return context;
	}

	/**
	 * @param testCase
	 * @param builder
	 * @throws Exception
	 * Handle the request content, handle flows for rest and soap separately...
	 */
	private void handleRequestContent(TestCase testCase, RequestBuilder builder) throws Exception
	{
		//Set the request body first
		if(testCase.getAcontent()!=null && !testCase.getAcontent().trim().isEmpty() 
				&& !testCase.getMethod().equals(HttpMethod.GET))
		{
			if(!testCase.isSoapBase())
			{
				builder = builder.setBody(testCase.getAcontent());
			}
		}
		else if(testCase.getMultipartContent()!=null && !testCase.getMultipartContent().isEmpty())
		{
			for (String filedet : testCase.getMultipartContent()) {
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
						logger.severe("Invalid type specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(fileNmOrTxt.isEmpty()) {
						logger.severe("No file/text specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(contType.isEmpty() && type.equalsIgnoreCase("text")) {
						logger.severe("No mime-type specified...skipping value - " + filedet);
						continue;
					}
					
					if(type.equalsIgnoreCase("file")) {
						try {
							File file = getResourceFile(context.getGatfExecutorConfig().getTestCasesBasePath(), fileNmOrTxt);
							if(!testCase.isSoapBase())
							{
								Part part = new FilePart(controlname, file, contType, null);
								builder = builder.addBodyPart(part);
							}
							else
							{
								byte[] fileData = IOUtils.toByteArray(new FileInputStream(file));
								String fileContents = Base64.encode(fileData);
								if(testCase.getSoapParameterValues()==null)
								{
									testCase.setSoapParameterValues(new HashMap<String, String>());
								}
								testCase.getSoapParameterValues().put(controlname, fileContents);
							}
						} catch (Exception e) {
							logger.severe("No file found for file upload...skipping value - " + filedet);
							continue;
						}
					} else {
						if(!testCase.isSoapBase())
						{
							Part part = new StringPart(controlname, fileNmOrTxt);
							builder = builder.addBodyPart(part);
						}
						else
						{
							if(testCase.getSoapParameterValues()==null)
							{
								testCase.setSoapParameterValues(new HashMap<String, String>());
							}
							testCase.getSoapParameterValues().put(controlname, fileNmOrTxt);
						}
					}
				}			
			}
		}
		else if(StringUtils.isNotBlank(testCase.getContentFile()))
		{
			String content = null;
			File file = getResourceFile(context.getGatfExecutorConfig().getTestCasesBasePath(), testCase.getContentFile());
			if(testCase.isSoapBase())
			{
				byte[] fileData = IOUtils.toByteArray(new FileInputStream(file));
				content = Base64.encode(fileData);
			}
			else
			{
				content = FileUtils.readFileToString(file);
			}
			builder.setBody(content);
		}
		
		//Now set the URL with authentication tokens etc...
		if(!testCase.isSoapBase()) {
			String turl = testCase.getAurl();
			
			String url = testCase.getAurl();
			if(url.indexOf("?")!=-1) {
				url = url.substring(0, url.indexOf("?"));
			}
			
			if(testCase.getSimulationNumber()>=0 && context.getProviderTestDataMap()!=null 
					&& !context.getProviderTestDataMap().isEmpty() 
					&& context.getProviderTestDataMap()
						.get(context.getGatfExecutorConfig().getAuthDataProvider())!=null
					&& context.getGatfExecutorConfig().isAuthEnabled()
					&& context.getGatfExecutorConfig().getAuthUrl().equals(testCase.getUrl())) 
			{
				String[] authParams = context.getGatfExecutorConfig().getAuthParamDetails();
				List<Map<String, String>> testDataLst = context.getProviderTestDataMap()
						.get(context.getGatfExecutorConfig().getAuthDataProvider());
				String userVal = null, passwordVal = null;
				if(testDataLst.size()>=testCase.getSimulationNumber()) {
					int index = testCase.getSimulationNumber();
					if(index>0)
						index -= 1;
					userVal = testDataLst.get(index).get(authParams[0]);
					passwordVal = testDataLst.get(index).get(authParams[2]);
				} else {
					Random rd = new Random();
					int pos = rd.nextInt(testDataLst.size());
					userVal = testDataLst.get(pos).get(authParams[0]);
					passwordVal = testDataLst.get(pos).get(authParams[2]);
				}
				
				String content = "";
				
				if(authParams[1].equals("header")) {
					if(testCase.getHeaders()==null)
					{
						testCase.setHeaders(new HashMap<String, String>());
					}
					testCase.getHeaders().put(authParams[0], userVal);
				} else if(authParams[1].equals("queryparam")) {
					if(turl.indexOf("?")!=-1 && turl.indexOf("{"+authParams[0]+"}")!=-1) {
						turl = turl.replaceAll("\\{"+authParams[0]+"\\}", userVal);
					}
					else
					{
						if(turl.indexOf("?")!=-1) {
							turl += "&" + authParams[0] + "=" + userVal;
						} else {
							turl += "?" + authParams[0] + "=" + userVal;
						}
					}
				} else {
					content = authParams[0] + "=" + userVal + "&";
				}
				
				if(authParams[3].equals("header")) {
					testCase.getHeaders().put(authParams[2], passwordVal);
				} else if(authParams[3].equals("queryparam")) {
					if(turl.indexOf("?")!=-1 && turl.indexOf("{"+authParams[2]+"}")!=-1) {
						turl = turl.replaceAll("\\{"+authParams[2]+"\\}", passwordVal);
					}
					else
					{
						if(turl.indexOf("?")!=-1) {
							turl += "&" + authParams[2] + "=" + passwordVal;
						} else {
							turl += "?" + authParams[2] + "=" + passwordVal;
						}
					}
				} else {
					content += authParams[0] + "=" + passwordVal + "&";
				}
				
				testCase.setContent(content);
			}
			
			String authUrl = testCase.isServerApiTarget()?"":context.getGatfExecutorConfig().getAuthUrl();
			String[] authExtractAuthParams = testCase.isServerApiTarget()?context.getGatfExecutorConfig().getServerApiAuthExtractAuthParams()
					:context.getGatfExecutorConfig().getAuthExtractAuthParams();
			
			if(testCase.isSecure() && !authUrl.equals(url)) {
				String sessIdentifier = context.getSessionIdentifier(testCase);
				String tokenNm = authExtractAuthParams[2];
				if(context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase)!=null &&
						context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase).get(tokenNm)!=null)
				{
					sessIdentifier = context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase).get(tokenNm);
				}
				
				Assert.assertNotNull("Authentication Token is null", sessIdentifier);
				
				if(authExtractAuthParams[3].equalsIgnoreCase("queryparam"))
				{
					if(turl.indexOf("?")!=-1 && turl.indexOf("{"+authExtractAuthParams[2]+"}")!=-1) {
						turl = turl.replaceAll("\\{"+authExtractAuthParams[2]+"\\}", sessIdentifier);
					}
					else
					{
						if(turl.indexOf("?")!=-1) {
							String urlpart = turl.substring(0, turl.indexOf("?")+1);
							String paramstr = turl.substring(turl.indexOf("?")+1);
							String[] params = paramstr.split("&");
							boolean isAppendAuthToken = false;
							for (String param : params) {
								String[] pair = param.split("=");
								String key = pair[0];
								String value = pair.length>1?pair[1]:"";
								if(key.trim().equals(authExtractAuthParams[2])) {
									urlpart += (key + "=" + sessIdentifier);
									isAppendAuthToken = true;
								} else {
									urlpart += (key + "=" + value);
								}
								urlpart += "&";
							}
							if(!isAppendAuthToken) {
								urlpart += (authExtractAuthParams[2] + "=" + sessIdentifier);
							}
							turl = urlpart;
						} else {
							turl += "?" + authExtractAuthParams[2] + "=" + sessIdentifier;
						}
					}
				}
				else if(authExtractAuthParams[3].equalsIgnoreCase("header"))
				{
					testCase.getHeaders().put(authExtractAuthParams[2], sessIdentifier);
				}
				else if(authExtractAuthParams[3].equalsIgnoreCase("cookie"))
				{
					testCase.getHeaders().put("Cookie", authExtractAuthParams[2] + "=" + sessIdentifier);
				}
			}

			String completeUrl = getUrl(testCase.getBaseUrl(), turl);
			
			testCase.setAurl(completeUrl);
			builder = builder.setUrl(testCase.getAurl());
			
		} else {
			
			Assert.assertNotNull("No wsdlKey specified for SOAP test", testCase.getWsdlKey());
			Assert.assertNotNull("No operation specified for SOAP test", testCase.getOperationName());
			
			String request = testCase.getAcontent();
			
			String soapAction = context.getSoapActions().get(testCase.getWsdlKey()+testCase.getOperationName());
			if (request.contains(AcceptanceTestContext.SOAP_1_1_NAMESPACE)) {
                soapAction = soapAction != null ? "\"" + soapAction + "\"" : "";
                builder.addHeader(AcceptanceTestContext.PROP_SOAP_ACTION_11, soapAction);
                builder.addHeader(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
                builder.addParameter(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
                testCase.getHeaders().put(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
            } else if (request.contains(AcceptanceTestContext.SOAP_1_2_NAMESPACE)) {
                String contentType = AcceptanceTestContext.MIMETYPE_APPLICATION_XML;
                if (soapAction != null) {
                    contentType = contentType + AcceptanceTestContext.PROP_DELIMITER + AcceptanceTestContext.PROP_SOAP_ACTION_12 + "\"" + soapAction + "\"";
                }
                builder.addHeader(AcceptanceTestContext.PROP_CONTENT_TYPE, contentType);
                testCase.getHeaders().put(AcceptanceTestContext.PROP_CONTENT_TYPE, contentType);
            }
			
			String endpoint = context.getSoapEndpoints().get(testCase.getWsdlKey());
			Assert.assertNotNull("No endpoints found for " + testCase.getWsdlKey()
					 + ", please define the wsdlLocFile in your config", endpoint);
			if(testCase.isSecure() && context.getGatfExecutorConfig().isSoapAuthEnabled() 
					&& !context.getGatfExecutorConfig().isSoapAuthTestCase(testCase)) {
				String sessIdentifier = context.getSessionIdentifier(testCase);
				String tokenNm = context.getGatfExecutorConfig().getAuthExtractAuthParams()[2];
				if(context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase)!=null &&
						context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase).get(tokenNm)!=null)
				{
					sessIdentifier = context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase).get(tokenNm);
				}
				
				Assert.assertNotNull("Authentication Token is null", sessIdentifier);
				if(endpoint.indexOf("?")!=-1) {
					endpoint += "&" + context.getGatfExecutorConfig().getSoapAuthExtractAuthParams()[1] + "=" + sessIdentifier;
				} else {
					endpoint += "?" + context.getGatfExecutorConfig().getSoapAuthExtractAuthParams()[1] + "=" + sessIdentifier;
				}
			}
			testCase.setAurl(endpoint);
			
			if(request==null || request.trim().isEmpty())
			{
				Document soapMessage = context.getSoapMessages().get(testCase.getWsdlKey()+testCase.getOperationName());
				SOAPResponseValidator.processSOAPRequest(soapMessage, testCase);
				
				Transformer transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");        
				StringWriter sw = new StringWriter();
				StreamResult result = new StreamResult(sw);
				DOMSource source = new DOMSource(soapMessage);
				transformer.transform(source, result);
				request = sw.toString();
			}
			
			builder = builder.setUrl(testCase.getAurl()).setBody(request);
		}
	}
	
	public ListenableFuture<TestCaseReport> executeTestCase(TestCase testCase, TestCaseReport testCaseReport)
	{
		long start = System.currentTimeMillis();
		
		RequestBuilder builder = new RequestBuilder(testCase.getMethod());
		
		try {
			if(!testCase.isExternalApi() && !testCase.isDisablePreHooks())
			{
				List<Method> preHook = context.getPrePostHook(testCase, true);
				if(preHook!=null) {
					for (Method method : preHook) {
						method.invoke(null, new Object[]{testCase});
					}
				}
				
				if(testCase.getPreExecutionDataSourceHookName()!=null) {
					try {
						context.executeDataSourceHook(testCase.getPreExecutionDataSourceHookName());
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}
			
			handleRequestContent(testCase, builder);
			
			if(testCase.getHeaders()!=null)
			{
				for (Map.Entry<String, String> entry : testCase.getHeaders().entrySet()) {
					builder = builder.addHeader(entry.getKey(), entry.getValue());
				}
			}
			
			testCaseReport.setTestCase(testCase);
			Request request = builder.build();
			
			if(!testCase.isExternalApi() && !testCase.isDisablePreHooks() && testCase.getPreWaitMs()!=null 
					&& testCase.getPreWaitMs()>0) {
				Thread.sleep(testCase.getPreWaitMs());
			}
			
			return client.executeRequest(request, new TestCaseResponseHandler(testCase, testCaseReport, context, start));
		} catch (Throwable e) {
			testCaseReport.setExecutionTime(System.currentTimeMillis() - start);
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.Exception.status);
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			testCaseReport.setError(e.getMessage());
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}
			e.printStackTrace();
			final TestCaseReport testCaseReportt = testCaseReport;
			return new ListenableFuture<TestCaseReport>() {

				public boolean cancel(boolean mayInterruptIfRunning) {
					return false;
				}

				public boolean isCancelled() {
					return false;
				}

				public boolean isDone() {
					return true;
				}

				public TestCaseReport get() throws InterruptedException,
						ExecutionException {
					return testCaseReportt;
				}

				public TestCaseReport get(long timeout, TimeUnit unit)
						throws InterruptedException, ExecutionException,
						TimeoutException {
					return testCaseReportt;
				}

				public void done() {
				}

				public void abort(Throwable t) {
				}

				public void content(TestCaseReport v) {
				}

				public void touch() {
				}

				public boolean getAndSetWriteHeaders(boolean writeHeader) {
					return false;
				}

				public boolean getAndSetWriteBody(boolean writeBody) {
					return false;
				}

				public ListenableFuture<TestCaseReport> addListener(
						Runnable listener, Executor exec) {
					return null;
				}
			};
		}
	}
	
	private File getResourceFile(String baseFolder, String filename) {
		try {
			File basePath = new File(baseFolder);
			File resource = new File(basePath, filename);
			return resource;
		} catch (Exception e) {
			return null;
		}
	}
	
	
	/**
	 * @author Sumeet Chhetri
	 * The Async HTTP Response Handler logic returns a TestCaseReport
	 */
	public static class TestCaseResponseHandler implements AsyncHandler<TestCaseReport> {

		private final Response.ResponseBuilder builder = new Response.ResponseBuilder();
		
		private TestCase testCase;
		
		private TestCaseReport testCaseReport;
		
		private final AcceptanceTestContext context;
		
		private long start;
		
		public TestCaseResponseHandler(TestCase testCase, TestCaseReport testCaseReport, 
				AcceptanceTestContext context, long start)
		{
			this.testCase = testCase;
			this.testCaseReport = testCaseReport;
			Assert.assertNotNull("Testcase cannot be null", testCase);
			Assert.assertNotNull("TestCaseReport cannot be null", testCaseReport);
			this.context = context;
			Assert.assertNotNull("Context cannot be null", context);
			this.start = start;
			this.testCaseReport.setResponseContentType(testCase.getExpectedResContentType());
		}
		
		/*
		 * (non-Javadoc)
		 * @see com.ning.http.client.AsyncHandler#onThrowable(java.lang.Throwable)
		 * Handle error scenarios in the report
		 */
		public void onThrowable(Throwable t) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.Exception.status);
			testCaseReport.setError(t.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(t));
			testCase.setFailed(true);
			t.printStackTrace();
		}
	
		public static boolean isMatchesContentType(MediaType md, String contType) {
		    return md.getType().equalsIgnoreCase(contType) || contType.toLowerCase().startsWith(md.getType().toLowerCase());
		}
		
		public com.ning.http.client.AsyncHandler.STATE onBodyPartReceived(
				HttpResponseBodyPart bodyPart) throws Exception {
			String contType = testCase.getExpectedResContentType();
			if(isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, contType) || isMatchesContentType(MediaType.APPLICATION_XML_TYPE, contType)
					|| isMatchesContentType(MediaType.TEXT_PLAIN_TYPE, contType) || isMatchesContentType(MediaType.TEXT_HTML_TYPE, contType)
					|| isMatchesContentType(MediaType.TEXT_XML_TYPE, contType))
			{
				builder.accumulate(bodyPart);
			}
			else
			{
				bodyPart.writeTo(NullOutputStream.NULL_OUTPUT_STREAM);
			}
			testCaseReport.setResponseContentType(contType);
			return STATE.CONTINUE;
		}
	
		/*
		 * (non-Javadoc)
		 * @see com.ning.http.client.AsyncHandler#onStatusReceived(com.ning.http.client.HttpResponseStatus)
		 * Abort if expected status code does not match the actual status code
		 */
		public com.ning.http.client.AsyncHandler.STATE onStatusReceived(
				HttpResponseStatus responseStatus) throws Exception {
			
			int statusCode = responseStatus.getStatusCode();
			testCaseReport.setResponseStatusCode(statusCode);
			if (statusCode != testCase.getExpectedResCode()) {
				testCaseReport.setError("Expected status code ["+testCase.getExpectedResCode()
						+"] does not match actual status code ["+statusCode+"]");
				testCaseReport.setStatus(TestStatus.Failed.status);
				testCaseReport.setFailureReason(TestFailureReason.InvalidStatusCode.status);
				if(testCase.isAbortOnInvalidStatusCode())
	            {
					return STATE.ABORT;
	            }
	        }
			builder.accumulate(responseStatus);
			return STATE.CONTINUE;
		}
	
		/*
		 * (non-Javadoc)
		 * @see com.ning.http.client.AsyncHandler#onHeadersReceived(com.ning.http.client.HttpResponseHeaders)
		 * Abort if expected content type does not match the actual content type
		 */
		public com.ning.http.client.AsyncHandler.STATE onHeadersReceived(
				HttpResponseHeaders headers) throws Exception {
			if(testCase.getExpectedResContentType()!=null)
			{
				if(testCase.getExpectedResContentType()!=null && testCase.getExpectedResContentType().trim().isEmpty()
						&& headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)!=null 
						&& headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)
							.indexOf(testCase.getExpectedResContentType())!=0)
				{
					testCaseReport.setError("Expected content type ["+testCase.getExpectedResContentType()
							+"] does not match actual content type ["+headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)+"]");
					testCaseReport.setStatus(TestStatus.Failed.status);
					testCaseReport.setFailureReason(TestFailureReason.InvalidContentType.status);
					if(testCase.isAbortOnInvalidContentType())
					{
						return STATE.ABORT;
					}
				}
			}
			else if(headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)!=null)
			{
				testCase.setExpectedResContentType(headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE));
			}
			builder.accumulate(headers);
			return STATE.CONTINUE;
		}
	
		/*
		 * (non-Javadoc)
		 * @see com.ning.http.client.AsyncHandler#onCompleted()
		 * Validate the response as per the content type
		 */
		public TestCaseReport onCompleted() throws Exception {
			Response response = builder.build();
			testCaseReport.setExecutionTime(System.currentTimeMillis() - start);
			if(testCaseReport.getError()==null)
			{
				testCaseReport.setResponseContent(response.getResponseBody());
				testCaseReport.setResHeaders(response.getHeaders());
				if(!testCase.isSoapBase()) {
					if(isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, testCase.getExpectedResContentType()))
					{
						jsonResponseValidator.validate(response, testCase, testCaseReport, context);
					}
					else if(isMatchesContentType(MediaType.APPLICATION_XML_TYPE, testCase.getExpectedResContentType())
							|| isMatchesContentType(MediaType.TEXT_XML_TYPE, testCase.getExpectedResContentType()))
					{
						xmlResponseValidator.validate(response, testCase, testCaseReport, context);
					}
					else if(isMatchesContentType(MediaType.TEXT_PLAIN_TYPE, testCase.getExpectedResContentType())
							|| isMatchesContentType(MediaType.TEXT_HTML_TYPE, testCase.getExpectedResContentType()))
					{
						textResponseValidator.validate(response, testCase, testCaseReport, context);
					}
					else
					{
						noContentResponseValidator.validate(response, testCase, testCaseReport, context);
						testCaseReport.setStatus(TestStatus.Success.status);
					}
				} else {
					soapResponseValidator.validate(response, testCase, testCaseReport, context);
				}
			}
			
			if(!testCase.isExternalApi() && !testCase.isDisablePostHooks() && testCase.getPostWaitMs()!=null 
					&& testCase.getPostWaitMs()>0) {
				try {
					Thread.sleep(testCase.getPostWaitMs());
				} catch (InterruptedException e1) {
				}
			}
			
			return testCaseReport;
		}
	}

	private String getUrl(String baseUrl, String urlPart)
	{
		String fullUrl = baseUrl;
		fullUrl = fullUrl.trim() + "/" + urlPart.trim();
		String parturl = fullUrl.substring(fullUrl.indexOf("://")+3);
		fullUrl = fullUrl.substring(0, fullUrl.indexOf("://")+3) + parturl.replace("//", "/");
		Assert.assertTrue("Testcase URL is not valid " + fullUrl, AcceptanceTestContext.URL_VALIDATOR.isValid(fullUrl));
		return fullUrl;
	}
	
	public void shutdown()
	{
		client.close();
	}
}