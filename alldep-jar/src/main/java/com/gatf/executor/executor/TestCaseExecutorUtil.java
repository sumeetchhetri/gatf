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
package com.gatf.executor.executor;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.w3c.dom.Document;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfResponse;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.ext.AutocannonTestCaseExecutor;
import com.gatf.executor.ext.VegetaTestCaseExecutor;
import com.gatf.executor.ext.WrkTestCaseExecutor;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.gatf.executor.validator.JSONResponseValidator;
import com.gatf.executor.validator.NoContentResponseValidator;
import com.gatf.executor.validator.SOAPResponseValidator;
import com.gatf.executor.validator.TextResponseValidator;
import com.gatf.executor.validator.XMLResponseValidator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;


/**
 * @author Sumeet Chhetri
 * The main executor class which handles http layer invocations/reponse handling
 * Uses the async-http-client library to make the execution performant/concurrent
 */
public class TestCaseExecutorUtil {

	private Logger logger = LogManager.getLogger(TestCaseExecutorUtil.class.getSimpleName());
	
	private static final JSONResponseValidator jsonResponseValidator = new JSONResponseValidator();
	
	private static final XMLResponseValidator xmlResponseValidator = new XMLResponseValidator();
	
	private static final SOAPResponseValidator soapResponseValidator = new SOAPResponseValidator();
	
	private static final TextResponseValidator textResponseValidator = new TextResponseValidator();
	
	private static final NoContentResponseValidator noContentResponseValidator = new NoContentResponseValidator(); 
	
	private HttpClient client;
	
	private AcceptanceTestContext context = null;
	
	private boolean singleExecutionContext  = false;
	
	
	private static HttpClient buildHttpClient(AcceptanceTestContext context) {
		HttpClient.Builder b = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
		if(context.getGatfExecutorConfig().getHttpConnectionTimeout()>0) {
			b.connectTimeout(Duration.ofMillis(context.getGatfExecutorConfig().getHttpConnectionTimeout()));
		}
		SSLContext ssl = trustAllSslContext(context.getGatfExecutorConfig().isAcceptAllCerts());
		if(ssl!=null) {
			b.sslContext(ssl);
		}
		return b.build();
	}

	// Trust-all SSLContext for acceptAllCerts mode (self-signed / mismatched certs in test envs). HttpClient
	// has no per-client hostname verifier, so hostname verification is disabled via the documented system
	// property when trust-all is on.
	private static SSLContext trustAllSslContext(boolean acceptAll) {
		if(!acceptAll && !Boolean.getBoolean("gatf.acceptAllCerts")) {
			return null; // default: standard TLS certificate + hostname verification
		}
		final TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {}
					public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {}
					public X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[]{};
					}
				}
		};
		try {
			System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, trustAllCerts, new java.security.SecureRandom());
			return ctx;
		} catch (Exception e) {
			return null;
		}
	}

	private static final java.util.Set<String> RESTRICTED_HEADERS = new java.util.HashSet<String>(
			java.util.Arrays.asList("connection", "content-length", "expect", "host", "upgrade"));

	// HttpRequest.Builder rejects a small set of restricted headers (Host/Content-Length/etc.) the JDK sets
	// itself. Skip them (and anything else the builder rejects) instead of failing.
	private static void addHeaderSafe(HttpRequest.Builder b, String name, String value) {
		if(name==null || value==null || RESTRICTED_HEADERS.contains(name.trim().toLowerCase())) {
			return;
		}
		try {
			b.header(name, value);
		} catch (IllegalArgumentException e) {
		}
	}

	public TestCaseExecutorUtil(AcceptanceTestContext context)
	{
		client = buildHttpClient(context);
		this.context = context;
	}
	
	private TestCaseExecutorUtil(){}
	
	public static TestCaseExecutorUtil getSingleConnection(AcceptanceTestContext context)
	{
		TestCaseExecutorUtil util = new TestCaseExecutorUtil();
		util.client = buildHttpClient(context);
		util.context = context;
		return util;
	}
	
	public static HttpClient getClient() {
		HttpClient.Builder b = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
		SSLContext ssl = trustAllSslContext(true);
		if(ssl!=null) {
			b.sslContext(ssl);
		}
		return b.build();
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
	private byte[] handleRequestContent(TestCase testCase, HttpRequest.Builder builder, StringBuilder curlAddPars) throws Exception
	{
		byte[] raw = null;
		//Set the request body first
		if(testCase.getAcontent()!=null && !testCase.getAcontent().trim().isEmpty() 
				&& !testCase.getMethod().equals(HttpMethod.GET))
		{
			if(!testCase.isSoapBase())
			{
				raw = testCase.getAcontent().getBytes(StandardCharsets.UTF_8);
			}
		}
		else if(testCase.getMultipartContent()!=null && !testCase.getMultipartContent().isEmpty())
		{
			String boundary = "----gatfFormBoundary" + Long.toHexString(System.nanoTime());
			ByteArrayOutputStream mp = new ByteArrayOutputStream();
			boolean anyFormPart = false;
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
						logger.error("Invalid type specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(fileNmOrTxt.isEmpty()) {
						logger.error("No file/text specified for file upload...skipping value - " + filedet);
						continue;
					}
					if(contType.isEmpty() && type.equalsIgnoreCase("text")) {
						logger.error("No mime-type specified...skipping value - " + filedet);
						continue;
					}
					
					if(type.equalsIgnoreCase("file")) {
						try {
							File file = getResourceFile(context.getGatfExecutorConfig().getTestCasesBasePath(), fileNmOrTxt);
							if(!testCase.isSoapBase())
							{
								FileInputStream ffis = new FileInputStream(file);
								byte[] partData = IOUtils.toByteArray(ffis);
								ffis.close();
								mp.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
								mp.write(("Content-Disposition: form-data; name=\"" + controlname + "\"; filename=\"" + fileNmOrTxt + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
								mp.write(partData);
								mp.write("\r\n".getBytes(StandardCharsets.UTF_8));
								anyFormPart = true;
								curlAddPars.append(String.format("-F \"%s=@%s\" ", controlname, file.getAbsolutePath()));
							}
							else
							{
							    FileInputStream fis = new FileInputStream(file);
								byte[] fileData = IOUtils.toByteArray(fis);
								fis.close();
								String fileContents = Base64.getEncoder().encodeToString(fileData);
								if(testCase.getSoapParameterValues()==null)
								{
									testCase.setSoapParameterValues(new HashMap<String, String>());
								}
								testCase.getSoapParameterValues().put(controlname, fileContents);
							}
							
						} catch (Exception e) {
							logger.error("No file found for file upload...skipping value - " + filedet);
							continue;
						}
					} else {
						if(!testCase.isSoapBase())
						{
							mp.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
							mp.write(("Content-Disposition: form-data; name=\"" + controlname + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
							mp.write(fileNmOrTxt.getBytes(StandardCharsets.UTF_8));
							mp.write("\r\n".getBytes(StandardCharsets.UTF_8));
							anyFormPart = true;
							curlAddPars.append(String.format("-F \"%s='%s'\" ", controlname, fileNmOrTxt.replace("\n", "\\n").replace("'", "\\'")));
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
			if(anyFormPart) {
				mp.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
				raw = mp.toByteArray();
				addHeaderSafe(builder, "Content-Type", "multipart/form-data; boundary=" + boundary);
			}
		}
		else if(StringUtils.isNotBlank(testCase.getContentFile()))
		{
			String content = null;
			File file = getResourceFile(context.getGatfExecutorConfig().getTestCasesBasePath(), testCase.getContentFile());
			if(testCase.isSoapBase())
			{
			    FileInputStream fis = new FileInputStream(file);
                byte[] fileData = IOUtils.toByteArray(fis);
                fis.close();
				content = Base64.getEncoder().encodeToString(fileData);
			}
			else
			{
				content = FileUtils.readFileToString(file, "UTF-8");
				curlAddPars.append(String.format("--data-raw \"%s=@%s\" ", file.getAbsolutePath()));
			}
			raw = content.getBytes(StandardCharsets.UTF_8);
		}
		
		//Now set the URL with authentication tokens etc...
		if(!testCase.isSoapBase()) {
			String turl = testCase.getAurl();
			
			String url = testCase.getAurl();
			if(url.indexOf("?")!=-1) {
				url = url.substring(0, url.indexOf("?"));
			}
			
			if(testCase.getSimulationNumber()>=0 && context.getProviderTestDataMap(context.getGatfExecutorConfig().getAuthDataProvider())!=null
					&& context.getGatfExecutorConfig().isAuthEnabled()
					&& context.getGatfExecutorConfig().getAuthUrl().equals(testCase.getUrl())) 
			{
				String[] authParams = context.getGatfExecutorConfig().getAuthParamDetails();
				List<Map<String, String>> testDataLst = context.getProviderTestDataMap(context.getGatfExecutorConfig().getAuthDataProvider());
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
				} else if(authParams[1].equals("postparam")) {
					content = authParams[0] + "=" + userVal + "&";
				} else if(authParams[1].equals("content")) {
					String tcon = testCase.getAcontent();
					tcon = tcon.replaceAll("\\{"+authParams[0]+"\\}", userVal);
					content = tcon;
					testCase.setAcontent(content);
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
				} else if(authParams[3].equals("postparam")) {
					content += authParams[2] + "=" + passwordVal + "&";
				} else if(authParams[1].equals("content")) {
					String tcon = testCase.getAcontent();
					tcon = tcon.replaceAll("\\{"+authParams[2]+"\\}", passwordVal);
					content = tcon;
					testCase.setAcontent(content);
				} 
				
				testCase.setAcontent(content);
				if(StringUtils.isNotBlank(content)) {
					raw = content.getBytes(StandardCharsets.UTF_8);
				}
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
			try {
			    builder.uri(URI.create(testCase.getAurl()));
            } catch (IllegalArgumentException e) {
                Assert.assertTrue("Invalid URL, please check the url provided ("+ completeUrl + ")", false);
            }
			
		} else {
			
			Assert.assertNotNull("No wsdlKey specified for SOAP test", testCase.getWsdlKey());
			Assert.assertNotNull("No operation specified for SOAP test", testCase.getOperationName());
			
			String request = testCase.getAcontent();
			
			String soapAction = context.getSoapActions().get(testCase.getWsdlKey()+testCase.getOperationName());
			if (request.contains(AcceptanceTestContext.SOAP_1_1_NAMESPACE)) {
                soapAction = soapAction != null ? "\"" + soapAction + "\"" : "";
                builder.header(AcceptanceTestContext.PROP_SOAP_ACTION_11, soapAction);
                builder.header(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
                testCase.getHeaders().put(AcceptanceTestContext.PROP_CONTENT_TYPE, AcceptanceTestContext.MIMETYPE_TEXT_XML);
            } else if (request.contains(AcceptanceTestContext.SOAP_1_2_NAMESPACE)) {
                String contentType = AcceptanceTestContext.MIMETYPE_APPLICATION_XML;
                if (soapAction != null) {
                    contentType = contentType + AcceptanceTestContext.PROP_DELIMITER + AcceptanceTestContext.PROP_SOAP_ACTION_12 + "\"" + soapAction + "\"";
                }
                builder.header(AcceptanceTestContext.PROP_CONTENT_TYPE, contentType);
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
				raw = request.getBytes(StandardCharsets.UTF_8);
			}
			
			try {
			    builder.uri(URI.create(testCase.getAurl()));
            } catch (IllegalArgumentException e) {
                Assert.assertTrue("Invalid URL, please check the url provided ("+ testCase.getAurl() + ")", false);
            }
		}
		
		if(testCase.getMethod().equalsIgnoreCase("post") || testCase.getMethod().equalsIgnoreCase("put")) {
			if(raw==null) {
				raw = new byte[0];
			}
		}
		builder.method(testCase.getMethod().toUpperCase(), raw!=null ? BodyPublishers.ofByteArray(raw) : BodyPublishers.noBody());
		return raw;
	}
	
	private String curlize(HttpRequest request, byte[] rawBody, String curlOptions, boolean isLog) {
		String curlCmd = "curl";
        curlCmd += " -X " + request.method();
        if (curlOptions != null) {
            curlCmd += " " + curlOptions;
        }

        boolean compressed = false;
        for (Map.Entry<String, List<String>> he : request.headers().map().entrySet()) {
            for (String value : he.getValue()) {
                if ("Accept-Encoding".equalsIgnoreCase(he.getKey()) && "gzip".equalsIgnoreCase(value)) {
                    compressed = true;
                }
                curlCmd += " -H " + "\"" + he.getKey() + ": " + value + "\"";
            }
        }

        if (rawBody != null && rawBody.length > 0) {
            String bodyStr = new String(rawBody, StandardCharsets.UTF_8);
            // try to keep to a single line and use a subshell to preserve any line breaks
            curlCmd += " --data $'" + bodyStr.replace("\n", "\\n").replace("'", "\\'") + "'";
        }

        curlCmd += ((compressed) ? " --compressed " : " ") + request.uri();

        if(isLog) {
	        logger.info("╭--- cURL (" + request.uri() + ")");
	        logger.info(curlCmd);
	        logger.info("╰--- (copy and paste the above line to a terminal)");
        }
        return curlCmd;
	}
	
	public CompletableFuture<TestCaseReport> executeTestCase(TestCase testCase, TestCaseReport testCaseReport)
	{
		long start = System.currentTimeMillis();
		
		HttpRequest.Builder builder = HttpRequest.newBuilder();
		
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
			
			StringBuilder curlAddPars = new StringBuilder();
			byte[] rawBody = handleRequestContent(testCase, builder, curlAddPars);
			
			boolean hasReferer = false;
			if(testCase.getHeaders()!=null)
			{
				for (Map.Entry<String, String> entry : testCase.getHeaders().entrySet()) {
					addHeaderSafe(builder, entry.getKey(), entry.getValue());
					hasReferer |= entry.getKey().equalsIgnoreCase("Referer");
				}
			}
			
			if(!hasReferer) {
				addHeaderSafe(builder, "Referer", WorkflowContextHandler.getBaseUrl(testCase.getAurl()));
			}
			
			testCaseReport.setTestCase(testCase);
			if(context.getGatfExecutorConfig().getHttpRequestTimeout()>0) {
				builder.timeout(Duration.ofMillis(context.getGatfExecutorConfig().getHttpRequestTimeout()));
			}
			HttpRequest request = builder.build();
			
			if(!testCase.isExternalApi() && !testCase.isDisablePreHooks() && testCase.getPreWaitMs()!=null && testCase.getPreWaitMs()>0) {
				Thread.sleep(testCase.getPreWaitMs());
			}
			
			String curlCmd = curlize(request, rawBody, curlAddPars.toString(), (testCase.isDetailedLog() || context.getGatfExecutorConfig().isDebugEnabled()));
			testCaseReport.setCurlCmd(curlCmd);
			
			if(testCase.getPerfConfig()!=null && testCase.getPerfConfig().getType()!=null && !testCase.getPerfConfig().getType().equals("none")) {
				CompletableFuture<TestCaseReport> future = new CompletableFuture<TestCaseReport>();
				if(testCase.getPerfConfig().getType().startsWith("wrk")) {
					WrkTestCaseExecutor.execute(context, testCase, testCaseReport, isSingleExecutionContext());
				} else if(testCase.getPerfConfig().getType().equals("vegeta")) {
					VegetaTestCaseExecutor.execute(context, testCase, testCaseReport, isSingleExecutionContext());
				} else {
					AutocannonTestCaseExecutor.execute(context, testCase, testCaseReport, isSingleExecutionContext());
				}
				future.complete(testCaseReport);
				return future;
			} else {
				TestCaseResponseHandler rp = new TestCaseResponseHandler(testCase, testCaseReport, context, start);
				client.sendAsync(request, BodyHandlers.ofByteArray()).whenComplete((resp, err) -> rp.handle(resp, err));
				return rp.future;
			}
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
			TestCaseResponseHandler rp = new TestCaseResponseHandler(testCase, testCaseReport, context, start);
			rp.onError(e);
			return rp.future;
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
	public static class TestCaseResponseHandler {
		private final CompletableFuture<TestCaseReport> future = new CompletableFuture<>();
		
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
		
		public void onError(Throwable e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.Exception.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			testCase.setFailed(true);
			e.printStackTrace();
			future.completeExceptionally(e);
		}
		
		public void handle(HttpResponse<byte[]> response, Throwable err) {
			if(err!=null) {
				onError(err instanceof java.util.concurrent.CompletionException && err.getCause()!=null ? err.getCause() : err);
				return;
			}
			try {
				GatfResponse gresp = new JdkGatfResponse(response);
				testCaseReport.setExecutionTime(System.currentTimeMillis() - start);
				
				if(testCase.getExpectedResContentType()!=null)
				{
					if(testCase.getExpectedResContentType()!=null && !testCase.getExpectedResContentType().trim().isEmpty()
							&& gresp.headers(HttpHeaders.CONTENT_TYPE)!=null && gresp.headers(HttpHeaders.CONTENT_TYPE).size()>0
							&& gresp.headers(HttpHeaders.CONTENT_TYPE).get(0).indexOf(testCase.getExpectedResContentType())!=0)
					{
						testCaseReport.setError("Expected content type ["+testCase.getExpectedResContentType()
								+"] does not match actual content type ["+gresp.headers(HttpHeaders.CONTENT_TYPE).get(0)+"]");
						testCaseReport.setStatus(TestStatus.Failed.status);
						testCaseReport.setFailureReason(TestFailureReason.InvalidContentType.status);
						if(testCase.isAbortOnInvalidContentType())
						{
							gresp.closeBody();
							future.complete(testCaseReport);
							return;
						}
					}
				}
				else if(gresp.headers(HttpHeaders.CONTENT_TYPE)!=null && gresp.headers(HttpHeaders.CONTENT_TYPE).size()>0)
				{
					testCase.setExpectedResContentType(gresp.headers(HttpHeaders.CONTENT_TYPE).get(0));
				}
				
				String contType = testCase.getExpectedResContentType();
				testCaseReport.setResponseContentType(contType);
				
				testCaseReport.setExecutionTime(System.currentTimeMillis() - start);
				if(isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, contType) || isMatchesContentType(MediaType.APPLICATION_XML_TYPE, contType)
						|| isMatchesContentType(MediaType.TEXT_PLAIN_TYPE, contType) || isMatchesContentType(MediaType.TEXT_HTML_TYPE, contType)
						|| isMatchesContentType(MediaType.TEXT_XML_TYPE, contType))
				{
					testCaseReport.setResponseContent(gresp.bodyString());
				}
				else if(contType.indexOf("json")!=-1 || contType.indexOf("xml")!=-1 || contType.indexOf("text")!=-1)
				{
					testCaseReport.setResponseContent(gresp.bodyString());
				}
				
				testCaseReport.setResHeaders(gresp.headersMultimap());
				if(!testCase.isSoapBase()) {
					if(isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, testCase.getExpectedResContentType()))
					{
						jsonResponseValidator.validate(gresp, testCase, testCaseReport, context);
					}
					else if(isMatchesContentType(MediaType.APPLICATION_XML_TYPE, testCase.getExpectedResContentType())
							|| isMatchesContentType(MediaType.TEXT_XML_TYPE, testCase.getExpectedResContentType()))
					{
						xmlResponseValidator.validate(gresp, testCase, testCaseReport, context);
					}
					else if(isMatchesContentType(MediaType.TEXT_PLAIN_TYPE, testCase.getExpectedResContentType())
							|| isMatchesContentType(MediaType.TEXT_HTML_TYPE, testCase.getExpectedResContentType()))
					{
						textResponseValidator.validate(gresp, testCase, testCaseReport, context);
					}
					else
					{
						noContentResponseValidator.validate(gresp, testCase, testCaseReport, context);
						testCaseReport.setStatus(TestStatus.Success.status);
					}
				} else {
					soapResponseValidator.validate(gresp, testCase, testCaseReport, context);
				}
				
				if(!testCase.isExternalApi() && !testCase.isDisablePostHooks() && testCase.getPostWaitMs()!=null 
						&& testCase.getPostWaitMs()>0) {
					try {
						Thread.sleep(testCase.getPostWaitMs());
					} catch (InterruptedException e1) {
					}
				}
				
				future.complete(testCaseReport);
				
			} catch (Throwable ex) {
				future.completeExceptionally(ex);
			}
		}
		
		public static boolean isMatchesContentType(MediaType md, String contType) {
			MediaType md2 = MediaType.valueOf(contType);
			return md.getType().equalsIgnoreCase(md2.getType()) && md.getSubtype().equalsIgnoreCase(md2.getSubtype());
		}
	}

	private String getUrl(String baseUrl, String urlPart)
	{
		String fullUrl = baseUrl;
		fullUrl = fullUrl.trim() + "/" + urlPart.trim();
		String parturl = fullUrl.substring(fullUrl.indexOf("://")+3);
		fullUrl = fullUrl.substring(0, fullUrl.indexOf("://")+3) + parturl.replace("//", "/");
		Assert.assertTrue("Testcase URL is not valid " + fullUrl,AcceptanceTestContext.isValidUrl(fullUrl));
		return fullUrl;
	}
	
	public void shutdown()
	{
		client.close();
	}

	public boolean isSingleExecutionContext() {
		return singleExecutionContext;
	}

	public void setSingleExecutionContext(boolean singleExecutionContext) {
		this.singleExecutionContext = singleExecutionContext;
	}
}