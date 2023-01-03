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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.distributed.DistributedGatfListener;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.executor.TestCaseExecutorUtil.TestCaseResponseHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.generator.postman.PostmanCollection;
import com.gatf.selenium.Command;
import com.gatf.selenium.gatfjdb.GatfSelDebugger;
import com.gatf.ui.GatfConfigToolUtil;

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
public class GatfTestGeneratorUtil implements GatfPlugin {

	private Logger logger = Logger.getLogger(GatfTestGeneratorUtil.class.getSimpleName());

    private boolean debugEnabled;
	
	private boolean enabled;

    private String requestDataType;
    
    private String[] testPaths;

    private String[] soapWsdlKeyPairs;
    
    private String urlPrefix;

    private String resourcepath;
    
	private String outDataType;
    
    private String urlSuffix;
	
	private boolean useSoapClient;
	
	private String testCaseFormat;
	
	private int postmanCollectionVersion;

	private boolean overrideSecure;
	
	private String configFile;
	
	public Logger getLogger() {
		return logger;
	}

	public void setLogger(Logger logger) {
		this.logger = logger;
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

	private String getUrl(String url)
	{
		if(url==null || url.trim().isEmpty())return null;
		url = url.trim();
		if(getUrlPrefix()!=null)
		{
			if(getUrlPrefix().trim().charAt(getUrlPrefix().trim().length() - 1) != '/')
				url = getUrlPrefix() + "/" + url;
			else
				url = getUrlPrefix() + url;
		}
		if(getUrlSuffix()!=null && getUrlSuffix().indexOf("/")==-1)
		{
			if(url.indexOf("?")!=-1)
				url += getUrlSuffix();
			else
				url += "?" + getUrlSuffix();
		}
		return url;
	}

	public void setProject(Object project) {
	}

	public String getRequestDataType() {
		return requestDataType;
	}

	public void setRequestDataType(String requestDataType) {
		this.requestDataType = requestDataType;
	}

	/**
     * @param classes Generates all testcases for all the rest-full services found in the classes discovered
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void generateRestTestCases(List<Class> classes)
    {
        try
        {
            Map<String,Integer> classTypes = new HashMap<String,Integer>();
            for (Class claz : classes)
            {
                classTypes.put(claz.getSimpleName(), 0);

                Class<?> pathCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.Path");
                Annotation theClassPath = claz.getAnnotation(pathCls);
                if (theClassPath == null) {
                	pathCls = Thread.currentThread().getContextClassLoader().loadClass("org.springframework.web.bind.annotation.RequestMapping");
                    theClassPath = claz.getAnnotation(pathCls);
                }

                if (theClassPath!=null)
                {
                	List<TestCase> tcases = new ArrayList<TestCase>();
                    Method[] methods = claz.getMethods();
                    for (Method method : methods)
                    {
                    	Annotation[] mal = method.getDeclaredAnnotations();
                    	Annotation theMethodPath = null;
                    	for(Annotation a : mal) {
                    		if(a.annotationType().getName().equals("javax.ws.rs.Path") || a.annotationType().getName().equals("org.springframework.web.bind.annotation.RequestMapping")) {
                    			theMethodPath = a;
                    			break;
                    		}
                    	}
                        if (theMethodPath != null)
                        {
                        	TestCase tcase = new TestCase();
                            String completeServicePath = null;
                            String httpMethod = "";
                            String consumes = "text/plain";

                            if (theClassPath.annotationType().getName().equals("javax.ws.rs.Path") && theMethodPath.annotationType().getName().equals("javax.ws.rs.Path"))
                            {
                            	Method cvm = theClassPath.annotationType().getMethod("value");
                            	Method mvm = theMethodPath.annotationType().getMethod("value");
                                completeServicePath = (String)cvm.invoke(theClassPath) + (String)mvm.invoke(theMethodPath);
                            }
                            else if (theClassPath.annotationType().getName().equals("org.springframework.web.bind.annotation.RequestMapping")
                                    && theMethodPath.annotationType().getName().equals("org.springframework.web.bind.annotation.RequestMapping"))
                            {
                            	Method vm = theClassPath.annotationType().getMethod("value");
                            	String[] cv = (String[])vm.invoke(theClassPath);
                            	vm = theMethodPath.annotationType().getMethod("value");
                            	String[] mv = (String[])vm.invoke(theMethodPath);
                                completeServicePath = cv[0] + mv[0];
                                vm = theMethodPath.annotationType().getMethod("method");
                                Object[] arr = (Object[])vm.invoke(theMethodPath);
                                httpMethod = arr[0].toString();
                                vm = theMethodPath.annotationType().getMethod("consumes");
                                arr = (Object[])vm.invoke(theMethodPath);
                                consumes = arr[0].toString();
                            }
                            else
                            {
                                throw new Exception("Invalid Annotation found on the Service class - "
                                        + claz.getSimpleName());
                            }

                            if (completeServicePath != null && completeServicePath.charAt(0) == '/')
                            {
                                completeServicePath = completeServicePath.substring(1);
                            }

                            String url = getUrl(completeServicePath);

                            String completeServiceSubmitPath = url;
                            Type[] argTypes = method.getGenericParameterTypes();
                            Annotation[][] argAnot = method.getParameterAnnotations();
                            
                            boolean mayBemultipartContent = false;
                            
                            if(isDebugEnabled())
                            	logger.info("Started looking at " + claz.getSimpleName() + " " + method.getName());
                            
                            ViewField contentvf = null;
                            Map<String, String> params = new HashMap<String, String>();
                            Map<String, String> hparams = new HashMap<String, String>();
                            for (int i = 0; i < argTypes.length; i++)
                            {
                            	Annotation[] annotations = getRestArgAnnotation(argAnot[i]);
                                if (annotations[0]!=null)
                                {
                                    String formpnm = null;

                                    if (annotations[0].annotationType().getName().equals("javax.ws.rs.FormParam"))
                                    {
                                    	Method vm = annotations[0].annotationType().getMethod("value");
                                    	formpnm = (String)vm.invoke(annotations[0]);
                                    	if(annotations[1]!=null && annotations[1].annotationType().getName().equals("javax.ws.rs.DefaultValue")) {
                                    		vm = annotations[1].annotationType().getMethod("value");
                                    		params.put(formpnm, (String)vm.invoke(annotations[1]));
                                    	}
                                    	else
                                    		params.put(formpnm, String.valueOf(getPrimitiveValue(argTypes[i])));
                                    	continue;
                                    }
                                    
                                    if (annotations[0].annotationType().getName().equals("org.springframework.web.bind.annotation.RequestParam"))
                                    {
                                    	Method vm = annotations[0].annotationType().getMethod("value");
                                    	String v = (String)vm.invoke(annotations[0]);
                                    	vm = annotations[0].annotationType().getMethod("defaultValue");
                                    	String dv = (String)vm.invoke(annotations[0]);
                                        if (completeServiceSubmitPath.indexOf("?") == -1)
                                            completeServiceSubmitPath += "?";
                                        if(completeServiceSubmitPath.charAt(completeServiceSubmitPath.length()-1)!='&')
                                        	completeServiceSubmitPath += "&";
                                        if(dv!=null)
                                        	completeServiceSubmitPath += v + "={" + dv + "}&";
                                        else
                                        	completeServiceSubmitPath += v + "={" + v + "}&";
                                        continue;
                                    }

                                    if (annotations[0].annotationType().getName().equals("javax.ws.rs.QueryParam"))
                                    {
                                    	Method vm = annotations[0].annotationType().getMethod("value");
                                    	String v = (String)vm.invoke(annotations[0]);
                                        if (completeServiceSubmitPath.indexOf("?") == -1)
                                            completeServiceSubmitPath += "?";
                                        if(completeServiceSubmitPath.charAt(completeServiceSubmitPath.length()-1)!='&')
                                        	completeServiceSubmitPath += "&";
                                        if(annotations[1]!=null && annotations[1].annotationType().getName().equals("javax.ws.rs.DefaultValue")) {
                                    		vm = annotations[1].annotationType().getMethod("value");
                                    		completeServiceSubmitPath += v + "={" + (String)vm.invoke(annotations[1]) + "}&";
                                    	}
                                        else
                                        	completeServiceSubmitPath += v + "={" + v + "}&";
                                        continue;
                                    }

                                    if (annotations[0].annotationType().getName().equals("javax.ws.rs.PathParam"))
                                    {
                                        continue;
                                    }

                                    if (annotations[0].annotationType().getName().equals("javax.ws.rs.HeaderParam"))
                                    {
                                    	Method vm = annotations[0].annotationType().getMethod("value");
                                    	formpnm = (String)vm.invoke(annotations[0]);
                                    	if(annotations[1]!=null && annotations[1].annotationType().getName().equals("javax.ws.rs.DefaultValue")) {
                                    		vm = annotations[1].annotationType().getMethod("value");
                                    		hparams.put(formpnm, (String)vm.invoke(annotations[1]));
                                    	}
                                    	else
                                    		hparams.put(formpnm, String.valueOf(getPrimitiveValue(argTypes[i])));
                                        continue;
                                    }
                                }
                                else
                                {
                                	ViewField vf = getViewField(argTypes[i]);
                                    if(vf!=null) {
                                    	contentvf = vf;
                                    	if(isDebugEnabled())
                                    		logger.info("Done looking at " + claz.getSimpleName() + " " + method.getName());
                                    	break;
                                    } else {
                                    	mayBemultipartContent = true;
                                    }
                                }
                            }

                            classTypes.put(claz.getSimpleName(), classTypes.get(claz.getSimpleName()) + 1);

                            Class postCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.POST");
                            Annotation hm = method.getAnnotation(postCls);
                            if (hm != null)
                            {
                                httpMethod = "POST";
                            }
                            else
                            {
                            	Class getCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.GET");
                                hm = method.getAnnotation(getCls);
                                if (hm != null)
                                {
                                    httpMethod = "GET";
                                }
                                else
                                {
                                	Class putCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.PUT");
                                    hm = method.getAnnotation(putCls);
                                    if (hm != null)
                                    {
                                        httpMethod = "PUT";
                                    }
                                    else
                                    {
                                    	Class delCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.DELETE");
                                        hm = method.getAnnotation(delCls);
                                        if (hm != null)
                                        {
                                            httpMethod = "DELETE";
                                        }
                                    }
                                }
                            }

                            Class conCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.Consumes");
                            Annotation annot = method.getAnnotation(conCls);
                            if (annot != null)
                            {
                            	Method vm = annot.annotationType().getMethod("value");
                                consumes = ((String[])vm.invoke(annot))[0];
                            }
                            
                            String produces = null;
                            if("JSON".equalsIgnoreCase(getOutDataType())) {
                            	produces = MediaType.APPLICATION_JSON;
                            } else if("XML".equalsIgnoreCase(getOutDataType())) {
                            	produces = MediaType.APPLICATION_XML;
                            } else {
                            	produces = MediaType.TEXT_PLAIN;
                            }
                            
                            Class prdCls = Thread.currentThread().getContextClassLoader().loadClass("javax.ws.rs.Produces");
                            annot = method.getAnnotation(prdCls);
                            if (annot != null)
                            {
                            	Method vm = annot.annotationType().getMethod("value");
                            	produces = ((String[])vm.invoke(annot))[0];
                            }

                            String content = "";
                            try
                            {
	                            if (!params.isEmpty() || TestCaseResponseHandler.isMatchesContentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE, consumes))
	                            {
	                            	for (Map.Entry<String, String> entry: params.entrySet()) {
	                            		content += entry.getKey() + "=" + entry.getValue() + "&";
									}
	                            	consumes = MediaType.APPLICATION_FORM_URLENCODED;
	                            }
	                            else if(contentvf!=null && contentvf.getValue()!=null && contentvf.getValue() instanceof String)
	                            {
	                            	content = (String)contentvf.getValue();
	                            }
	                            else if(("JSON".equalsIgnoreCase(getRequestDataType()) || TestCaseResponseHandler.isMatchesContentType(MediaType.APPLICATION_JSON_TYPE, consumes))
	                            		&& contentvf!=null && contentvf.getValue()!=null)
	                            {
	                            	if(contentvf.getValue().getClass().isAnnotationPresent(org.codehaus.jackson.map.annotate.JsonSerialize.class)
                            				|| contentvf.getValue().getClass().isAnnotationPresent(org.codehaus.jackson.annotate.JsonAutoDetect.class))
                            		{
	                            		content = WorkflowContextHandler.OM.writerWithDefaultPrettyPrinter().writeValueAsString(contentvf.getValue());
                            		}
                                	else
                                	{
                                		content = WorkflowContextHandler.OM.writerWithDefaultPrettyPrinter().writeValueAsString(contentvf.getValue());
                                	}
	                            	consumes = MediaType.APPLICATION_JSON;
	                            }
	                            else if (("XML".equalsIgnoreCase(getRequestDataType()) || TestCaseResponseHandler.isMatchesContentType(MediaType.APPLICATION_XML_TYPE, consumes))
	                            		&& contentvf!=null && contentvf.getValue()!=null)
	                            {
	                            	JAXBContext context = JAXBContext.newInstance(contentvf.getValue().getClass());
	                                Marshaller m = context.createMarshaller();
	                                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
	                                StringWriter writer = new StringWriter();
	                                m.marshal(contentvf.getValue(), writer);
	                                content = writer.toString();
	                                consumes = MediaType.APPLICATION_XML;
	                            }
	                            else if((httpMethod.equalsIgnoreCase("POST") || httpMethod.equalsIgnoreCase("PUT")) && mayBemultipartContent) {
	                            	consumes = MediaType.MULTIPART_FORM_DATA;
	                            } else if(httpMethod.equalsIgnoreCase("GET") || httpMethod.equalsIgnoreCase("DELETE")) {
	                            	consumes = "";
	                            }
                            } catch (Exception e) {
                            	logger.severe(ExceptionUtils.getStackTrace(e));
                            }
                            
                            if(consumes!=null && !consumes.trim().isEmpty()) {
                            	hparams.put(HttpHeaders.CONTENT_TYPE, consumes);
                            }

                            completeServiceSubmitPath = completeServiceSubmitPath.replaceAll("\\?&", "?");
                            completeServiceSubmitPath = completeServiceSubmitPath.replaceAll("&&", "&");
                            completeServiceSubmitPath = completeServiceSubmitPath.replaceAll("/\\?", "?");
                            completeServiceSubmitPath = completeServiceSubmitPath.trim();
                            if(completeServiceSubmitPath.charAt(completeServiceSubmitPath.length()-1)=='&')
                            {
                            	completeServiceSubmitPath = completeServiceSubmitPath.substring(0, completeServiceSubmitPath.length()-1);
                            }
                            
                            tcase.setUrl(completeServiceSubmitPath);
                            tcase.setMethod(httpMethod);
                            tcase.setContent(content);
                            tcase.setName(claz.getName()+"."+method.getName());
                            tcase.setDescription(tcase.getName());
                            tcase.setDetailedLog(false);
                            tcase.setSkipTest(false);
                            tcase.setSecure(isOverrideSecure());
                            tcase.setSoapBase(false);
                            tcase.setExpectedResCode(200);
                            tcase.setExpectedResContentType(produces);
                            
                            if(hparams.size()>0)
                            	tcase.setHeaders(hparams);
                            if(tcase.getNumberOfExecutions()==null)
                            	tcase.setNumberOfExecutions(1);
                			if(tcase.getExpectedNodes().size()==0)
                				tcase.setExpectedNodes(null);
                			if(tcase.getWorkflowContextParameterMap().size()==0)
                				tcase.setWorkflowContextParameterMap(null);
                			if(tcase.getSoapParameterValues().size()==0)
                				tcase.setSoapParameterValues(null);
                			if(tcase.getRepeatScenarios().size()==0)
                				tcase.setRepeatScenarios(null);
                			if(tcase.getMultipartContent().size()==0)
                				tcase.setMultipartContent(null);
                            
                            tcases.add(tcase);
                        }
                    }
                    if(!tcases.isEmpty()) {
                    	
                    	if("json".equalsIgnoreCase(getTestCaseFormat()))
                    	{
                    		String postManJson = WorkflowContextHandler.OM.writeValueAsString(tcases);
                			String file = getResourcepath() + File.separator + claz.getName().replaceAll("\\.", "_") + "_testcases_rest.json";
                    		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    		bw.write(postManJson);
                    		bw.close();
                    	}
                    	else if("csv".equalsIgnoreCase(getTestCaseFormat()))
                    	{
                    		StringBuilder build = new StringBuilder();
                    		for (TestCase testCase : tcases) {
                    			build.append(testCase.toCSV());
                    			build.append(System.lineSeparator());
							}
                    		String file = getResourcepath() + File.separator + claz.getName().replaceAll("\\.", "_") + "_testcases_rest.csv";
                    		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    		bw.write(build.toString());
                    		bw.close();
                    	}
                    	else
                    	{
	                		String file = getResourcepath() + File.separator + claz.getName().replaceAll("\\.", "_") + "_testcases_rest.xml";
	                		WorkflowContextHandler.XOM.writeValue(new FileOutputStream(file), tcases);
                    	}
                		
                		if(getPostmanCollectionVersion()>0)
                		{
                			PostmanCollection postmanCollection = new PostmanCollection();
                			postmanCollection.setName(claz.getSimpleName());
                			postmanCollection.setDescription(postmanCollection.getName());
                			for (TestCase testCase : tcases) {
                				postmanCollection.addTestCase(testCase, getPostmanCollectionVersion());
							}
                			
                			String postManJson = WorkflowContextHandler.OM.writeValueAsString(postmanCollection);
                			String file = getResourcepath() + File.separator + claz.getName().replaceAll("\\.", "_") + "_testcases_postman.json";
                    		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    		bw.write(postManJson);
                    		bw.close();
                		}
                    }
                }
            }
        }
        catch (Exception e)
        {
            logger.severe(ExceptionUtils.getStackTrace(e));
        }
    }


    private Annotation[] getRestArgAnnotation(Annotation[] annotations) {
    	
    	Annotation[] annot = new Annotation[2];
    	if(annotations!=null && annotations.length>0) 
    	{
	    	for (Annotation annotation : annotations) 
	    	{
	    		@SuppressWarnings("rawtypes")
				Class clas = annotation.annotationType();
				if(clas.getName().equals("javax.ws.rs.FormParam") || clas.getName().equals("javax.ws.rs.QueryParam")
						|| clas.getName().equals("javax.ws.rs.HeaderParam") || clas.getName().equals("javax.ws.rs.PathParam")
		                || clas.getName().equals("org.springframework.web.bind.annotation.RequestParam")
		                || clas.getName().equals("org.springframework.web.bind.annotation.PathParam"))
					annot[0] = annotation;
				if(clas.getName().equals("javax.ws.rs.DefaultValue"))
					annot[1] = annotation;
			}
    	}
		return annot;
	}

	public static String getFileExtension(String fileName)
    {
        if (fileName == null || fileName.indexOf(".") == -1)
            return null;
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * @param sourceFile
     * @param destFile
     * @throws IOException Copy a file to some desired location
     */
    @SuppressWarnings("resource")
    public static void copyFile(File sourceFile, File destFile) throws IOException
    {
        if (!destFile.exists())
        {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;
        try
        {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();

            // previous code: destination.transferFrom(source, 0, source.size());
            // to avoid infinite loops, should be:
            long count = 0;
            long size = source.size();
            while ((count += destination.transferFrom(source, count, size - count)) < size)
                ;
        }
        finally
        {
            if (source != null)
            {
                source.close();
            }
            if (destination != null)
            {
                destination.close();
            }
        }
    }

    /**
     * @param claz
     * @param parameters
     * @param naming
     * @param formpnm
     * @param isheaderParam
     * @param heirarchy
     * @throws Exception Add new ViewFeild objects that represnt the form elements on the test page of the give rest
     *             full service
     */
    @SuppressWarnings("rawtypes")
    private ViewField getViewField(Type claz) throws Exception
    {
    	ViewField viewField = null;
    	try 
    	{
    		ParameterizedType type = null;
    		Class clas = null;
    		if(claz instanceof ParameterizedType)
    		{
    			type = (ParameterizedType)claz;
    			clas = (Class)type.getRawType();
    		}
    		else
    		{
    			clas = (Class)claz;
    		}
    		
    		List<Type> heirarchies = new ArrayList<Type>();
	        if (isPrimitive(clas))
	        {
	            viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getPrimitiveValue(claz));
	        }
	        else if (clas.getName().equals("org.apache.cxf.jaxrs.ext.multipart.Attachment")
	        		|| clas.getName().equals("org.apache.cxf.message.Attachment")
	        		|| clas.getName().equals("org.apache.cxf.jaxrs.ext.multipart.MultipartBody")
	        		|| clas.getName().equals("javax.ws.rs.core.MultivaluedMap")
	        		|| clas.getName().equals("javax.ws.rs.core.MultivaluedHashMap")
	        		|| 
	        		(isCollection(clas) && clas.getTypeParameters().length>0 
	        				&& clas.getTypeParameters()[0].toString().equals("class org.apache.cxf.jaxrs.ext.multipart.Attachment"))
	        		||
	        		(isCollection(clas) && clas.getTypeParameters().length>0 
	        				&& clas.getTypeParameters()[0].toString().equals("class org.apache.cxf.message.Attachment")))
	        {
	            return null;
	        }
	        else if (isMap(clas))
	        {
	        	viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getMapValue(clas, type.getActualTypeArguments(), heirarchies));
	        }
	        else if (isCollection(clas))
	        {
	        	viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getListSetValue(clas, type.getActualTypeArguments(), heirarchies));
	        }
	        else if(!clas.isInterface())
        	{
	            viewField = new ViewField();
	            viewField.setClaz(clas);
	            viewField.setValue(getObject(clas, heirarchies));
        	}
    	} catch (Exception e) {
    		logger.severe(ExceptionUtils.getStackTrace(e));
    		logger.info("Invalid class, cannot be represented as a form/object in a test case - class name = " + claz);
    	}
        return viewField;
    }
    
    @SuppressWarnings("rawtypes")
    private List<Field> getAllFields(Class claz) {
    	List<Field> allFields = new ArrayList<Field>();
    	allFields.addAll(Arrays.asList(claz.getFields()));
    	allFields.addAll(Arrays.asList(claz.getDeclaredFields()));
    	
    	if(!Object.class.equals(claz.getSuperclass())) {
    		allFields.addAll(getAllFields(claz.getSuperclass()));
    	}
    	return allFields;
    }
    
    private String getHeirarchyStr(List<Type> fheirlst) {
    	StringBuilder b = new StringBuilder();
    	if(!fheirlst.isEmpty()) {
    		for (Type type : fheirlst) {
				b.append(type.toString()+".");
			}
    	}
    	return b.toString();
    }
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object getObject(Class claz, List<Type> heirarchies) throws Exception {

    	if(claz.isEnum())return claz.getEnumConstants()[0];
    	
    	if (isMap(claz))
        {
    		return getMapValue(claz, claz.getTypeParameters(), heirarchies);
        }
        else if (isCollection(claz))
        {
        	return getListSetValue(claz, claz.getTypeParameters(), heirarchies);
        }
        else if(claz.isInterface() || Modifier.isAbstract(claz.getModifiers())) 
        {
        	return null;
        }
    	
    	if(heirarchies.contains(claz) || heirarchies.size()>=2)return null;
    	heirarchies.add(claz);
    	
    	Constructor cons = null;
    	try {
    		cons = claz.getConstructor(new Class[]{});
		} catch (Exception e) {
			if(isPrimitive(claz)) {
				return getPrimitiveValue(claz);
			}
			logger.severe("No public no-args constructor found for class " + claz.getName());
			return null;
		}
    	
    	Object object = cons.newInstance(new Object[]{});
    	if(claz.equals(Object.class)) return null;
    	List<Field> allFields = getAllFields(claz);
    	
    	for (Field field : allFields) {
    		
    		if (Modifier.isStatic(field.getModifiers()))
    			continue;
    			
			//if(!field.isAccessible())
			{
				field.setAccessible(true);
			}
    		
			List<Type> fheirlst = new ArrayList<Type>(heirarchies);
			
			if(isDebugEnabled())
				logger.info("Parsing Class " + getHeirarchyStr(fheirlst) + " field " + field.getName() + " type " + field.getType().equals(boolean.class));
            
			if (isPrimitive(field.getType()))
            {
                field.set(object, getPrimitiveValue(field.getType()));
            }
            else if (isMap(field.getType()) && field.getGenericType() instanceof ParameterizedType)
            {
            	ParameterizedType type = (ParameterizedType)field.getGenericType();
            	field.set(object, getMapValue(field.getType(), type.getActualTypeArguments(), fheirlst));
            }
            else if (isCollection(field.getType()) && field.getGenericType() instanceof ParameterizedType)
            {
            	ParameterizedType type = (ParameterizedType)field.getGenericType();
            	field.set(object, getListSetValue(field.getType(), type.getActualTypeArguments(), fheirlst));
            }
            else if (!claz.equals(field.getType()))
            {
            	Object fieldval = getObject(field.getType(), fheirlst);
            	field.set(object, fieldval);
            }
            else if (claz.equals(field.getType()))
            {
            	if(isDebugEnabled())
            		logger.info("Ignoring recursive fields...");
            }
		}
        return object;
    }
    
    @SuppressWarnings("rawtypes")
	private Object getObject(Type claz, List<Type> heirarchies) throws Exception {
    	
    	ParameterizedType type = null;
		Class clas = null;
		if(claz instanceof Class)
		{
			clas = (Class)claz;
		}
		else if(claz instanceof ParameterizedType)
		{
			type = (ParameterizedType)claz;
			clas = (Class)type.getRawType();
		}
		else return null;
		if (isPrimitive(clas))
        {
            return getPrimitiveValue(clas);
        }
        else if (isMap(clas))
        {
        	return getMapValue(clas, type.getActualTypeArguments(), heirarchies);
        }
        else if (isCollection(clas))
        {
        	return getListSetValue(clas, type.getActualTypeArguments(), heirarchies);
        }
        else if(!clas.isInterface())
    	{
            return getObject(clas, heirarchies);
    	}
    	return null;
    }
    
    private Object getPrimitiveValue(Type claz) {
    	 if (isPrimitive(claz))
    	 {
    		 if(claz.equals(boolean.class) || claz.equals(Boolean.class)) {
    			 Random rand = new Random();
    			 return rand.nextBoolean();
    		 } else if(claz.equals(Date.class)) {
    			 return new Date();
    		 } else if(claz.equals(java.sql.Date.class)) {
    			 return new java.sql.Date(new Date().getTime());
    		 } else if(claz.equals(java.sql.Time.class)) {
    			 return new java.sql.Time(new Date().getTime());
    		 } else if(claz.equals(java.sql.Timestamp.class)) {
    			 return new java.sql.Timestamp(new Date().getTime());
    		 } else if(claz.equals(Double.class) || claz.equals(double.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextDouble();
    		 } else if(claz.equals(Float.class) || claz.equals(float.class)) {
    			 Random rand = new Random(12345678L);
    			 return rand.nextFloat();
    		 } else if(claz.equals(String.class)) {
    			 return RandomStringUtils.randomAlphabetic(10);
    		 } else if(claz.equals(Long.class) || claz.equals(long.class) || claz.equals(Number.class)) {
    			 Random rand = new Random();
    			 return Long.valueOf(rand.nextInt(123));
    		 } else if(claz.equals(Integer.class) || claz.equals(int.class)) {
    			 Random rand = new Random();
    			 return Integer.valueOf(rand.nextInt(123));
    		 } else if(claz.equals(BigInteger.class)) {
    			 Random rand = new Random();
    			 return new BigInteger(new BigInteger("1234567890123456789").bitLength(), rand);
    		 } else if(claz.equals(BigDecimal.class)) {
    			 Random rand = new Random();
    			 return new BigDecimal(rand.nextInt(123));
    		 } else if(claz.equals(Short.class) || claz.equals(short.class)) {
    			 Random rand = new Random();
    			 return Short.valueOf((short)rand.nextInt(123));
    		 }
    	 }
    	 return null;
    }
    
    private boolean isPrimitive(Type claz) {
    	return (claz.equals(Integer.class) || claz.equals(String.class) || claz.equals(Short.class)
                || claz.equals(Long.class) || claz.equals(Double.class) || claz.equals(Float.class)
                || claz.equals(Boolean.class) || claz.equals(int.class) || claz.equals(short.class)
                || claz.equals(long.class) || claz.equals(double.class) || claz.equals(float.class)
                || claz.equals(boolean.class) || claz.equals(Number.class) || claz.equals(Date.class)
                || claz.equals(BigInteger.class) || claz.equals(BigDecimal.class)
                || claz.equals(java.sql.Date.class) || claz.equals(java.sql.Timestamp.class)
                || claz.equals(java.sql.Time.class));
    }
    
    private boolean isCollection(Type claz) {
    	return (claz.equals(List.class) || claz.equals(ArrayList.class)
                || claz.equals(LinkedList.class) || claz.equals(Set.class) 
                || claz.equals(HashSet.class) || claz.equals(LinkedHashSet.class)
                || claz.equals(Collection.class));
    }
    
    private boolean isMap(Type claz) {
    	return (claz.equals(Map.class) || claz.equals(HashMap.class)
                || claz.equals(LinkedHashMap.class) || claz.equals(TreeMap.class));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getMapValue(Type type, Type[] types, List<Type> heirarchies) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(Map.class) || type.equals(HashMap.class)
                || type.equals(LinkedHashMap.class))
    	{
    		if(type.equals(Map.class))
    		{
    			claz = HashMap.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
    	for (int i = 0; i < 1; i++)
    	{
	    	Object k = null;
	    	if(isPrimitive(types[0])) {
	    		k = getPrimitiveValue(types[0]);
	    	} else if(!heirarchies.contains(types[0])) {
	    		if(types[0] instanceof Class)
	    			k = getObject((Class)types[0], heirarchies);
	    		else
	    			k = getObject(types[0], heirarchies);
	    		heirarchies.remove(types[0]);
	    	}
	    	Object v = null;
	    	if(isPrimitive(types[1])) {
	    		v = getPrimitiveValue(types[1]);
	    	} else if(!heirarchies.contains(types[1])) {
	    		if(types[1] instanceof Class)
	    			v = getObject((Class)types[1], heirarchies);
	    		else
	    			v = getObject(types[1], heirarchies);
	    		heirarchies.remove(types[1]);
	    	}
	    	if(k==null && isDebugEnabled()) {
	    		logger.info(types[0].toString());
	    		logger.info(types[1].toString());
	    		logger.severe("Null key " + types[0]);
	    	}
	    	if(k!=null) {
	    	    ((Map)object).put(k, v);
	    	}
    	}
    	if(!isPrimitive(types[0]))
    	{
    		heirarchies.add(types[0]);
    	}
    	if(!isPrimitive(types[1]))
    	{
    		heirarchies.add(types[1]);
    	}
    	return object;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Object getListSetValue(Type type, Type[] types, List<Type> heirarchies) throws Exception {
    	
    	Class claz = null;
    	if(type.equals(List.class) || type.equals(Collection.class)
    			|| type.equals(ArrayList.class) || type.equals(LinkedList.class))
    	{
    		if(type.equals(List.class) || type.equals(Collection.class))
    		{
    			claz = ArrayList.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	else if(type.equals(Set.class) || type.equals(HashSet.class) || type.equals(LinkedHashSet.class))
    	{
    		if(type.equals(Set.class))
    		{
    			claz = HashSet.class;
    		}
    		else
    		{
    			claz = (Class)type;
    		}
    	}
    	
    	Constructor cons = claz.getConstructor(new Class[]{});
    	Object object = cons.newInstance(new Object[]{});
    	
        if (types.length == 1)
        {
        	for (int i = 0; i < 1; i++) {
	        	Object v = null;
	        	if(isPrimitive(types[0])) {
	        		v = getPrimitiveValue(types[0]);
	        	} else if(!heirarchies.contains(types[0])) {
		    		if(types[0] instanceof Class)
		    			v = getObject((Class)types[0], heirarchies);
		    		else
		    			v = getObject(types[0], heirarchies);
		    		heirarchies.remove(types[0]);
		    	}
	        	((Collection)object).add(v);
			}
        	if(!isPrimitive(types[0]))
        	{
        		heirarchies.add(types[0]);
        	}
        }
    	return object;
    }
    

    private ClassLoader getClassLoader()
    {
        return getClass().getClassLoader();
    }

    public static void main(String[] args) throws Exception
    {
    	boolean showHelp = false;
    	if(args.length>=1) {
    		if(args.length>1 && args[0].equals("-generator") && !args[1].trim().isEmpty())
    		{
    			GatfConfiguration config = null;
    			
    			if(args[1].trim().endsWith(".xml")) {
    				InputStream io = new FileInputStream(args[1]);
		    		config = (GatfConfiguration) WorkflowContextHandler.XOM.readValue(io, GatfConfiguration.class);
    			} else if(args[1].trim().endsWith(".json")) {
    				InputStream io = new FileInputStream(args[1]);
    				config = WorkflowContextHandler.OM.readValue(io, GatfConfiguration.class);
    			} else {
    				showHelp = true;
    			}
	    		
	    		GatfTestGeneratorUtil testGenerator = new GatfTestGeneratorUtil();
	    		testGenerator.setDebugEnabled(config.isDebugEnabled());
	    		testGenerator.setEnabled(config.isEnabled());
	    		testGenerator.setRequestDataType(config.getRequestDataType());
	    		testGenerator.setTestPaths(config.getTestPaths());
	    		testGenerator.setSoapWsdlKeyPairs(config.getSoapWsdlKeyPairs());
	    		testGenerator.setUrlPrefix(config.getUrlPrefix());
	    		testGenerator.setResourcepath(config.getResourcepath());
	    		testGenerator.setOutDataType(config.getResponseDataType()); 
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.setUrlSuffix(config.getUrlSuffix());
	    		testGenerator.setUseSoapClient(config.isUseSoapClient());
	    		testGenerator.setTestCaseFormat(config.getTestCaseFormat());
	    		testGenerator.setPostmanCollectionVersion(config.getPostmanCollectionVersion());
	    		testGenerator.setOverrideSecure(config.isOverrideSecure());
	    		testGenerator.execute();
    		}
    		else if(args.length>1 && (args[0].equals("-executor") || args[0].equals("-selenium")) && !args[1].trim().isEmpty())
    		{
    			try {
    				GatfTestCaseExecutorUtil.main(args);
				} catch (Exception e) {
					e.printStackTrace();
					showHelp = true;
				}
    			
    		}
    		else if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
    				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty())
    		{
    			GatfConfigToolUtil.main(args);
    		}
    		else if(args[0].equals("-listener"))
    		{
    			DistributedGatfListener.main(args);
    		}
    		else if(args[0].equals("-validate-sel"))
    		{
    			Command.validateSel(args, null, false);
    		}
    		else
    		{
    			showHelp = true;
    		}
    	} else {
    		showHelp = true;
    	}
    	
    	if(showHelp) {
    		System.out.println("Invalid invocation - valid invocation options are, \n" +
					"java -jar gatf-plugin-{version}.jar -generator {generator-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -executor {executor-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -selenium {selenium-config-file}.xml\n" +
					"java -jar gatf-plugin-{version}.jar -configtool {http_port} {ip_address} {project_folder}\n" + 
					"java -jar gatf-plugin-{version}.jar -validate-sel {file-name/relative/to/workdir}\n" + 
					"java -jar gatf-plugin-{version}.jar -listener\n");
    	}
    	
		System.exit(0);
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
    		logger.info("Skipping gatf-plugin execution....");
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
    				config = WorkflowContextHandler.OM.readValue(io, GatfConfiguration.class);
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
    		logger.info("No resource path specified, using the current working directory to generate testcases...");
    	} else {
    		File dir = new File(getResourcepath());
    		if(!dir.exists())
            {
    			dir.mkdirs();
            }
    	}
    	
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try
        {
            currentThread.setContextClassLoader(getClassLoader());
            logger.info("Inside execute");
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
                            logger.info("Adding package " + item);
                        }
                        else
                        {
                            logger.severe("Error:package not found - " + item);
                        }
                    }
                    else
                    {
                        try
                        {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            logger.info("Adding class " + item);
                        }
                        catch (Exception e)
                        {
                            logger.severe("Error:class not found - " + item);
                        }
                    }
                }
                if(!allClasses.isEmpty())
                {
                	generateRestTestCases(allClasses);
                }
            }
            else
            {
            	logger.info("Nothing to generate..");
            }
            generateSoapTestCases();
            
            logger.info("Done execute");
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

	protected void generateSoapTestCases() {
		try {
			
			if(getSoapWsdlKeyPairs()==null || getSoapWsdlKeyPairs().length==0)
				return;
			
			StringBuilder build = new StringBuilder();
			for (String wsdlLoc : getSoapWsdlKeyPairs()) {
				if(!wsdlLoc.trim().isEmpty())
				{
					String[] wsdlLocParts = wsdlLoc.split(",");
					logger.info("Started Parsing WSDL location - " + wsdlLocParts[1]);
					
					Wsdl wsdl = Wsdl.parse(wsdlLocParts[1]);
					for (QName bindingName : wsdl.getBindings()) {
						SoapBuilder builder = wsdl.getBuilder(bindingName);
						List<TestCase> tcases = new ArrayList<TestCase>();
						for (SoapOperation operation : builder.getOperations()) {
							String request = builder.buildInputMessage(operation);
							
							TestCase tcase = new TestCase();
							String url = builder.getServiceUrls().get(0);
							if(getUrlSuffix()!=null)
                            {
                            	if(url.indexOf("?")!=-1)
                            	{
                            		url += "&" + getUrlSuffix();
                            	}
                            	else
                            	{
                            		url += "?" + getUrlSuffix();
                                }
                            }
    						tcase.setUrl(url);
                            tcase.setMethod(HttpMethod.POST);
                            tcase.setContent(request);
                            tcase.setName(wsdlLocParts[0]+"."+operation.getOperationName());
                            tcase.setDescription(tcase.getName());
                            tcase.setDetailedLog(false);
                            tcase.setSkipTest(false);
                            tcase.setSecure(isOverrideSecure());
                            tcase.setExpectedResCode(200);
                            tcase.setExpectedResContentType(MediaType.TEXT_XML);
                            if(isUseSoapClient())
                            {
                            	tcase.setSoapBase(true);
                            	tcase.setWsdlKey(wsdlLocParts[0]);
                                tcase.setOperationName(operation.getOperationName());
                            }
                            else
                            {
                            	tcase.setSoapBase(false);
                            	if(tcase.getHeaders()==null)
            					{
            						tcase.setHeaders(new HashMap<String, String>());
            					}
                            	tcase.getHeaders().put("SOAPAction", operation.getSoapAction());
                            	tcase.getHeaders().put(HttpHeaders.CONTENT_TYPE, "application/soap+xml");
                            }
                            
                            tcases.add(tcase);
                            if(!tcases.isEmpty()) 
                            {
                        		String file = getResourcepath() + File.separator + wsdlLocParts[0] + "_testcases_soap.xml";
                        		WorkflowContextHandler.XOM.writeValue(new FileOutputStream(file), tcases);
                            }
							logger.info("Adding message for SOAP operation - " + operation.getOperationName());
						}
					}
					logger.info("Done Parsing WSDL location - " + wsdlLocParts[1]);
					build.append(wsdlLoc.trim()+"\n");
				}
			}
			
			String file = getResourcepath() + "/wsdl-locations.csv";
			Writer writer = new BufferedWriter(new FileWriter(file));
			writer.write(build.toString());
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static GatfConfiguration getConfig(InputStream resource) throws Exception
	{
		GatfConfiguration config = WorkflowContextHandler.XOM.readValue(resource, GatfConfiguration.class);
		return config;
	}
	
	public static String getConfigStr(GatfConfiguration configuration) throws JsonProcessingException
	{
		return WorkflowContextHandler.XOM.writeValueAsString(configuration);
	}

	public void doExecute(GatfPluginConfig configuration, List<String> files) throws Exception {
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
	public void doSeleniumTest(GatfExecutorConfig configuration, List<String> files) {
	}

	@Override
	public GatfSelDebugger debugSeleniumTest(GatfExecutorConfig configuration, String selscript, String configPath) {
		return null;
	}
}
