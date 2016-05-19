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
package com.gatf.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.xstream.GatfPrettyPrintWriter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

public class GatfTestCaseHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	protected GatfTestCaseHandler(GatfConfigToolMojo mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	String configType = request.getParameter("configType");
    	String testcaseFileName = request.getParameter("testcaseFileName");
    	boolean isApiIntType = configType!=null && (configType.equals("loggingapi") || configType.equals("issuetrackerapi"));
		if(StringUtils.isBlank(testcaseFileName)) {
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
			return;
		}
		GatfExecutorConfig gatfConfig = null;
		String filePath = null;
		if(!isApiIntType)
		{
			try {
				gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
    			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
    			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
				if(!new File(dirPath).exists()) {
					new File(dirPath).mkdir();
				}
    			filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR
    					+ testcaseFileName;
    			if(!new File(filePath).exists()) {
    				throw new RuntimeException("Test case file does not exist");
    			}
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, null);
				return;
			}
		}
		else
		{
			gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
			filePath = basepath + SystemUtils.FILE_SEPARATOR + testcaseFileName;
		}
		boolean isUpdate = request.getMethod().equals(Method.PUT);
		if(!isApiIntType && request.getMethod().equals(Method.DELETE)) {
    		try {
    			String testCaseName = request.getHeader("testcasename");
    			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
    			TestCase found = null;
    			for (TestCase tc : tcs) {
					if(tc.getName().equals(testCaseName)) {
						found = tc;
						break;
					}
				}
    			if(found==null) {
    				throw new RuntimeException("Testcase does not exist");
    			}
    			tcs.remove(found);
    			
    			XStream xstream = new XStream(
            			new XppDriver() {
            				public HierarchicalStreamWriter createWriter(Writer out) {
            					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
            				}
            			}
            		);
            		xstream.processAnnotations(new Class[]{TestCase.class});
            		xstream.alias("TestCases", List.class);
            		xstream.toXML(tcs, new FileOutputStream(filePath));
        			
        			response.setStatus(HttpStatus.OK_200);
    		} catch (Exception e) {
    			GatfConfigToolMojo.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
		} else if(request.getMethod().equals(Method.POST) || isUpdate) {
    		try {
    			TestCase testCase = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), 
    					TestCase.class);
    			if(testCase.getName()==null) {
    				throw new RuntimeException("Testcase does not specify name");
    			}
    			
    			if(isApiIntType && !testCase.getName().equals("authapi") && !testCase.getName().equals("targetapi"))
    			{
    				throw new RuntimeException("Only authapi or targetapi allowed for name");
    			}
    			
    			testCase.validate(AcceptanceTestContext.getHttpHeadersMap(), gatfConfig.getBaseUrl());
    			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
    			if(!isUpdate)
    			{
        			for (TestCase tc : tcs) {
						if(tc.getName().equals(testCase.getName())) {
							throw new RuntimeException("Testcase with same name already exists");
						}
					}
        			tcs.add(testCase);
    			}
    			else
    			{
    				boolean found = false;
    				for (TestCase tc : tcs) {
						if(tc.getName().equals(testCase.getName())) {
							found = true;
							break;
						}
					}
    				
    				if(!found) {
    					throw new RuntimeException("Testcase with name does not exist");
    				}
    				
    				List<TestCase> ttcs = new ArrayList<TestCase>();
    				for (TestCase tc : tcs) {
						if(tc.getName().equals(testCase.getName())) {
							ttcs.add(testCase);
						} else {
							ttcs.add(tc);
						}
					}
    				tcs = ttcs;
    			}
    			
    			XStream xstream = new XStream(
        			new XppDriver() {
        				public HierarchicalStreamWriter createWriter(Writer out) {
        					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
        				}
        			}
        		);
        		xstream.processAnnotations(new Class[]{TestCase.class});
        		xstream.alias("TestCases", List.class);
        		xstream.toXML(tcs, new FileOutputStream(filePath));
    			
    			response.setStatus(HttpStatus.OK_200);
			} catch (Throwable e) {
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.GET) ) {
    		try {
    			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
    			List<TestCase> tcsn = new ArrayList<TestCase>();
    			boolean isAuthApi = false;
    			boolean isTargetApi = false;
    			if(tcs==null)
    				throw new RuntimeException("No Testcases found...");
    			for (TestCase testCase : tcs) {
    				if(!isApiIntType || (isApiIntType && (testCase.getName().equals("authapi") 
    						|| testCase.getName().equals("targetapi"))))
        			{
    					if(!isAuthApi)isAuthApi = testCase.getName().equals("authapi");
    					if(!isTargetApi)isTargetApi = testCase.getName().equals("targetapi");
    					tcsn.add(testCase);
        			}
				}
    			if(isApiIntType)
    			{
    				boolean changed = false;
    				if(!isAuthApi)
    				{
    					TestCase tc = new TestCase();
    					tc.setMethod(HttpMethod.POST);
    					tc.setUrl("auth");
    					tc.setName("authapi");
    					tc.getHeaders().put(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
    					tc.setRepeatScenarioProviderName("");
    					tc.setPreExecutionDataSourceHookName("");
    					tc.setPostExecutionDataSourceHookName("");
    					tc.setServerApiAuth(true);
    					tcsn.add(tc);
    					changed = true;
    				}
    				if(!isTargetApi)
    				{
    					TestCase tc = new TestCase();
    					tc.setMethod(HttpMethod.POST);
    					tc.setUrl("target");
    					tc.setName("targetapi");
    					tc.setSecure(isAuthApi);
    					tc.getHeaders().put(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
    					tc.setRepeatScenarioProviderName("");
    					tc.setPreExecutionDataSourceHookName("");
    					tc.setPostExecutionDataSourceHookName("");
    					tc.setServerApiTarget(true);
    					tcsn.add(tc);
    					changed = true;
    				}
    				if(changed)
    				{
    					XStream xstream = new XStream(
                			new XppDriver() {
                				public HierarchicalStreamWriter createWriter(Writer out) {
                					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
                				}
                			}
                		);
                		xstream.processAnnotations(new Class[]{TestCase.class});
                		xstream.alias("TestCases", List.class);
                		xstream.toXML(tcsn, new FileOutputStream(filePath));
    				}
    			}
    			String json = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(tcsn);
    			response.setContentType(MediaType.APPLICATION_JSON);
	            response.setContentLength(json.length());
	            response.getWriter().write(json);
    			response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }

}
