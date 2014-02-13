package com.gatf.test;


import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.w3c.dom.Document;

import com.gatf.report.TestCaseReport;
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

public class TestCaseExecutorUtil {

	private Logger logger = Logger.getLogger(TestCaseExecutorUtil.class.getSimpleName());
	
	private static final JSONResponseValidator jsonResponseValidator = new JSONResponseValidator();
	
	private static final XMLResponseValidator xmlResponseValidator = new XMLResponseValidator();
	
	private static final SOAPResponseValidator soapResponseValidator = new SOAPResponseValidator();
	
	private AsyncHttpClient client;
	
	private AcceptanceTestContext context = null;
	
	public TestCaseExecutorUtil(AcceptanceTestContext context)
	{
		Builder builder = new AsyncHttpClientConfig.Builder();
		builder.setConnectionTimeoutInMs(context.getGatfExecutorConfig().getHttpConnectionTimeout())
				.setMaximumConnectionsPerHost(context.getGatfExecutorConfig().getNumConcurrentExecutions())
				.setRequestTimeoutInMs(context.getGatfExecutorConfig().getHttpRequestTimeout())
				.setAllowPoolingConnection(true)
				.setCompressionEnabled(context.getGatfExecutorConfig().isHttpCompressionEnabled()).build();
		
		client = new AsyncHttpClient(builder.build());
		this.context = context;
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
						logger.severe("Invalid type specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(fileNmOrTxt.isEmpty()) {
						logger.severe("No file/text specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(type.equalsIgnoreCase("text") && contType.isEmpty()) {
						logger.severe("No mime-type specified for text data upload...skipping value - " + filedet);
						continue;
					}
					
					if(type.equalsIgnoreCase("file")) {
						try {
							File file = getResourceFile(fileNmOrTxt, context.getGatfExecutorConfig().getTestCasesBasePath());
							if(!testCase.isSoapBase())
							{
								Part part = new FilePart(controlname, file);
								builder = builder.addBodyPart(part);
							}
							else
							{
								byte[] fileData = IOUtils.toByteArray(new FileInputStream(file));
								String fileContents = Base64.encodeBase64URLSafeString(fileData);
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
							testCase.getSoapParameterValues().put(controlname, fileNmOrTxt);
						}
					}
				}			
			}
		}
		
		//Now set the URL with authentication tokens etc...
		if(!testCase.isSoapBase()) {
			String turl = testCase.getAurl();
			
			String url = testCase.getAurl();
			if(url.indexOf("?")!=-1) {
				url = url.substring(0, url.indexOf("?"));
			}
			
			if(testCase.isSecure() && !context.getGatfExecutorConfig().getAuthUrl().equals(url)) {
				Assert.assertNotNull(context.getSessionIdentifier());
				
				if(turl.indexOf("?")!=-1 && turl.indexOf("{"+context.getGatfExecutorConfig().getAuthExtractAuthParams()[2]+"}")!=-1) {
					turl = turl.replaceAll("\\{"+context.getGatfExecutorConfig().getAuthExtractAuthParams()[2]+"\\}", context.getSessionIdentifier());
				}
				else
				{
					if(turl.indexOf("?")!=-1) {
						turl += "&" + context.getGatfExecutorConfig().getAuthExtractAuthParams()[2] + "=" + context.getSessionIdentifier();
					} else {
						turl += "?" + context.getGatfExecutorConfig().getAuthExtractAuthParams()[2] + "=" + context.getSessionIdentifier();
					}
				}
			}

			String baseUrl = context.getGatfExecutorConfig().getBaseUrl().trim();
			if(baseUrl.charAt(baseUrl.length()-1)=='/')
			{
				if(turl.charAt(0)=='/')
					turl = baseUrl + turl.substring(1);
				else
					turl = baseUrl + turl;
			}
			else
			{
				if(turl.charAt(0)=='/')
					turl = baseUrl + turl;
				else
					turl = baseUrl + "/" + turl;
			}
			testCase.setAurl(turl);
			builder = builder.setUrl(testCase.getAurl());
			
		} else {
			String request = testCase.getAcontent();
			
			String soapAction = context.getSoapActions().get(testCase.getWsdlKey()+testCase.getOperationName());
			if (request.contains(AcceptanceTestContext.SOAP_1_1_NAMESPACE)) {
                soapAction = soapAction != null ? "\"" + soapAction + "\"" : "";
                builder.addHeader(AcceptanceTestContext.PROP_SOAP_ACTION_11, soapAction);
                builder.addHeader(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
                builder.addParameter(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
            } else if (request.contains(AcceptanceTestContext.SOAP_1_2_NAMESPACE)) {
                String contentType = AcceptanceTestContext.MIMETYPE_APPLICATION_XML;
                if (soapAction != null) {
                    contentType = contentType + AcceptanceTestContext.PROP_DELIMITER + AcceptanceTestContext.PROP_SOAP_ACTION_12 + "\"" + soapAction + "\"";
                }
                builder.addHeader(AcceptanceTestContext.PROP_CONTENT_TYPE, contentType);
            }
			
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
				
				String endpoint = context.getSoapEndpoints().get(testCase.getWsdlKey());
				if(testCase.isSecure() && context.getGatfExecutorConfig().isSoapAuthEnabled() 
						&& !context.getGatfExecutorConfig().isSoapAuthTestCase(testCase)) {
					Assert.assertNotNull(context.getSessionIdentifier());
					if(endpoint.indexOf("?")!=-1) {
						endpoint += "&" + context.getGatfExecutorConfig().getSoapAuthExtractAuthParams()[1] + "=" + context.getSessionIdentifier();
					} else {
						endpoint += "?" + context.getGatfExecutorConfig().getSoapAuthExtractAuthParams()[1] + "=" + context.getSessionIdentifier();
					}
				}
				testCase.setAurl(endpoint);
				builder = builder.setUrl(testCase.getAurl()).setBody(request);
			}
		}
	}
	
	public ListenableFuture<TestCaseReport> executeTestCase(TestCase testCase, TestCaseReport testCaseReport)
	{
		long start = System.currentTimeMillis();
		RequestBuilder builder = new RequestBuilder(testCase.getMethod());
		
		for (Map.Entry<String, String> entry : testCase.getHeaders().entrySet()) {
			builder = builder.addHeader(entry.getKey(), entry.getValue());
		}
		
		try {
			handleRequestContent(testCase, builder);
			testCaseReport.setTestCase(testCase);
			Request request = builder.build();
			return client.executeRequest(request, new TestCaseResponseHandler(testCase, testCaseReport, context, start));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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
	 * @author sumeetc
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
			Assert.assertNotNull(testCase);
			Assert.assertNotNull(testCaseReport);
			this.context = context;
			Assert.assertNotNull(context);
			this.start = start;
		}
		
		/*
		 * (non-Javadoc)
		 * @see com.ning.http.client.AsyncHandler#onThrowable(java.lang.Throwable)
		 * Handle error scenarios in the report
		 */
		public void onThrowable(Throwable t) {
			if(context.getGatfExecutorConfig().isReportingEnabled())
			{
				testCaseReport.setStatus("Failed");
				testCaseReport.setError(t.getMessage());
				testCaseReport.setErrorText(ExceptionUtils.getStackTrace(t));
			}
			testCase.setFailed(true);
			t.printStackTrace();
		}
	
		public com.ning.http.client.AsyncHandler.STATE onBodyPartReceived(
				HttpResponseBodyPart bodyPart) throws Exception {
			String contType = builder.build().getHeader(HttpHeaders.CONTENT_TYPE);
			if(MediaType.APPLICATION_JSON.equalsIgnoreCase(contType) || MediaType.APPLICATION_XML.equalsIgnoreCase(contType)
					|| MediaType.TEXT_PLAIN.equalsIgnoreCase(contType) || MediaType.TEXT_HTML.equalsIgnoreCase(contType)
					|| MediaType.TEXT_XML.equalsIgnoreCase(contType))
			{
				builder.accumulate(bodyPart);
			}
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
			if (statusCode != testCase.getExpectedResCode()) {
				testCaseReport.setError("Expected status code ["+testCase.getExpectedResCode()
						+"] does not match actual status code ["+statusCode+"]");
	            return STATE.ABORT;
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
				if(!testCase.getExpectedResContentType().equals(
						headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)))
				{
					testCaseReport.setError("Expected content type ["+testCase.getExpectedResContentType()
							+"] does not match actual content type ["+headers.getHeaders().getFirstValue(HttpHeaders.CONTENT_TYPE)+"]");
					return STATE.ABORT;
				}
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
			if(!testCase.isSoapBase()) {
				if(testCase.getExpectedResContentType().equals(MediaType.APPLICATION_JSON))
				{
					jsonResponseValidator.validate(response, testCase, testCaseReport, context);
				}
				else if(testCase.getExpectedResContentType().equals(MediaType.APPLICATION_XML))
				{
					xmlResponseValidator.validate(response, testCase, testCaseReport, context);
				}
			} else {
				soapResponseValidator.validate(response, testCase, testCaseReport, context);
			}
			testCaseReport.setResponseContent(response.getResponseBody());
			StringBuilder build = new StringBuilder();
			for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
				build.append(entry.getKey());
				build.append(": ");
				
				boolean needsComma = false;
	            for (String value : entry.getValue()) {
	                if (needsComma) {
	                	build.append(", ");
	                } else {
	                    needsComma = true;
	                }
	                build.append(value);
	            }
	            build.append("\n");
			}
			testCaseReport.setResponseHeaders(build.toString());
			testCaseReport.setExecutionTime(System.currentTimeMillis() - start);
			return testCaseReport;
		}
	}
}
