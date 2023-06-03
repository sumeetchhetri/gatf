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
package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.finder.TestCaseFinder;

public class GatfTestCaseFilesHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	public GatfTestCaseFilesHandler(GatfConfigToolMojoInt mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.setCorsHeaders(response);
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	if(request.getMethod().equals(Method.POST)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			if(StringUtils.isNotBlank(testcaseFileName)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        			if(!testcaseFileName.toLowerCase().endsWith(".xml") && !testcaseFileName.toLowerCase().endsWith(".sel"))
        			{
        				throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + File.separator + gatfConfig.getTestCaseDir();
    				if(!new File(dirPath).exists()) {
    					new File(dirPath).mkdir();
    				}
        			String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileName;
        			if(new File(filePath).exists()) {
        				throw new RuntimeException("Testcase file already exists");
        			}
        			new File(filePath).createNewFile();
        			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        			if(testcaseFileName.toLowerCase().endsWith(".xml")) {
        			    bos.write("<TestCases></TestCases>".getBytes());
        			} else {
        			    bos.write("open chrome".getBytes());
        			}
        			bos.close();
        			response.setStatus(HttpStatus.OK_200);
    			} else {
    				throw new RuntimeException("No testcaseFileName query parameter specified");
    			}
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.PUT)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			String testcaseFileNameTo = request.getParameter("testcaseFileNameTo");
    			if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(testcaseFileNameTo)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        			if(!(testcaseFileName.toLowerCase().endsWith(".xml") && testcaseFileNameTo.toLowerCase().endsWith(".xml")) && 
        			        !(testcaseFileName.toLowerCase().endsWith(".sel") && testcaseFileNameTo.toLowerCase().endsWith(".sel")))
        			{
        			    throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + File.separator + gatfConfig.getTestCaseDir();
    				if(!new File(dirPath).exists()) {
    					new File(dirPath).mkdir();
    				}
    				
    				if(testcaseFileName.indexOf("\\")!=-1)
    				{
    					if(testcaseFileNameTo.indexOf("\\")!=-1)
    					{
    						String pres = testcaseFileName.substring(0, testcaseFileName.lastIndexOf("\\"));
    						String pret = testcaseFileNameTo.substring(0, testcaseFileNameTo.lastIndexOf("\\"));
    						if(!pres.equals(pret)) {
    							throw new RuntimeException("Source and Target filenames should have the same directory tree");
    						}
    					}
    					else
    					{
    						throw new RuntimeException("Source and Target filenames should have the same directory tree");
    					}
    				}
    				else if(testcaseFileNameTo.indexOf("\\")!=-1)
    				{
    					if(testcaseFileName.indexOf("\\")!=-1)
    					{
    						String pres = testcaseFileName.substring(0, testcaseFileName.lastIndexOf("\\"));
    						String pret = testcaseFileNameTo.substring(0, testcaseFileNameTo.lastIndexOf("\\"));
    						if(!pres.equals(pret)) {
    							throw new RuntimeException("Source and Target filenames should have the same directory tree");
    						}
    					}
    					else
    					{
    						throw new RuntimeException("Source and Target filenames should have the same directory tree");
    					}
    				}
    				
        			String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileName;
        			String tofilePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileNameTo;
        			if(new File(filePath).exists()) {
        			    try
                        {
        			    	if(!filePath.equals(tofilePath))
        			    		FileUtils.moveFile(new File(filePath), new File(tofilePath));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException("File rename operation failed");
                        }
        			} else {
        				throw new RuntimeException("Source Testcase file does not exist");
        			}
        			response.setStatus(HttpStatus.OK_200);
    			} else {
    				throw new RuntimeException("Both testcaseFileName and testcaseFileNameTo required");
    			}
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.DELETE)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			if(StringUtils.isNotBlank(testcaseFileName)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        			if(!testcaseFileName.toLowerCase().endsWith(".xml") && !testcaseFileName.toLowerCase().endsWith(".sel"))
        			{
        				throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + File.separator + gatfConfig.getTestCaseDir();
    				if(!new File(dirPath).exists()) {
    					new File(dirPath).mkdir();
    				}
        			String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileName;
        			if(new File(filePath).exists()) {
        				new File(filePath).delete();
        			} else {
        				throw new RuntimeException("Testcase file does not exist");
        			}
        			response.setStatus(HttpStatus.OK_200);
    			} else {
    				throw new RuntimeException("No testcaseFileName query parameter specified");
    			}
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.GET) ) {
    		try {
    			final GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
    			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
    			String dirPath = basepath + File.separator + gatfConfig.getTestCaseDir();
				if(!new File(dirPath).exists()) {
					new File(dirPath).mkdir();
				}
    			
				List<String> fileNames = new ArrayList<String>();
    			if(gatfConfig.isSeleniumExecutor() && gatfConfig.isSeleniumModuleTests()) {
		        	File mdir = new File(dirPath);
		        	Collection<File> dirs = FileUtils.listFilesAndDirs(mdir, FalseFileFilter.FALSE, TrueFileFilter.INSTANCE);
		        	for (File f : dirs) {
		    			if(new File(f, "main.sel").exists()) {
		    				String sfpath = new File(f, "main.sel").getAbsolutePath().replaceFirst(mdir.getAbsolutePath(), StringUtils.EMPTY);
		    				if(sfpath.charAt(0)==File.separatorChar) {
		    					sfpath = sfpath.substring(1);
		    				}
		    				fileNames.add(sfpath);
		    			}
		    		}
		        	
		        	FilenameFilter filter = new FilenameFilter() {
	    				public boolean accept(File folder, String name) {
	    					return name.toLowerCase().equalsIgnoreCase("selenium-apis.xml");
	    				}
	    			};
	    			
	    			List<File> fileLst = new ArrayList<File>();
	    			TestCaseFinder.getFiles(mdir, filter, fileLst);
	    			if(fileLst.size()>0) {
	    				fileNames.add("selenium-apis.xml");
	    			}
				} else {
					FilenameFilter filter = new FilenameFilter() {
	    				public boolean accept(File folder, String name) {
	    					return (!gatfConfig.isSeleniumExecutor() && name.toLowerCase().endsWith(".xml")) 
	    					        || (gatfConfig.isSeleniumExecutor() && (name.toLowerCase().endsWith(".sel") || name.toLowerCase().equalsIgnoreCase("selenium-apis.xml")));
	    				}
	    			};
	    			
	    			File dirFPath = new File(dirPath);
	    			List<File> fileLst = new ArrayList<File>();
	    			TestCaseFinder.getFiles(dirFPath, filter, fileLst);
	    			
	    			List<File> allFiles = TestCaseFinder.filterFiles(gatfConfig.getIgnoreFiles(), fileLst, dirFPath);
	    			allFiles = TestCaseFinder.filterValidTestCaseFiles(allFiles);
	    			
	    			for (File file : allFiles) {
	    				fileNames.add(TestCaseFinder.getRelativePath(file, dirFPath));
					}
				}
    			
    			String json = WorkflowContextHandler.OM.writeValueAsString(fileNames);
    			response.setContentType(MediaType.APPLICATION_JSON);
	            response.setContentLength(json.length());
	            response.getWriter().write(json);
    			response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }

}
