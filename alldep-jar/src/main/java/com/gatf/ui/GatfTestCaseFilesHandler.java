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

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
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
    			String extras = request.getParameter("extras");
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
        			if(testcaseFileName.toLowerCase().endsWith(".xml")) {
        				FileUtils.writeStringToFile(new File(filePath), "<TestCases></TestCases>", "UTF-8");
        			} else {
        				FileUtils.writeStringToFile(new File(filePath), "open chrome", "UTF-8");
        			}
        			if(StringUtils.isNotBlank(extras)) {
        				try {
							String ext = new String(Base64.getDecoder().decode(extras.getBytes("UTF-8")), "UTF-8");
							if(ext.length()>0) {
								FileUtils.writeStringToFile(new File(filePath+".attr"), extras, "UTF-8");
							}
						} catch (Exception e) {
						}
        			}
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
    			String extras = request.getParameter("extras");
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
    				
    				try {
						Path pfrom = Paths.get(testcaseFileName).getParent();
						Path pto = Paths.get(testcaseFileNameTo).getParent();
						if(!pfrom.toString().equals(pto.toString())) {
							throw new RuntimeException("Source and Target filenames should have the same directory tree");
						}
					} catch (Exception e) {
						throw new RuntimeException("Source and Target filenames should be valid");
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
    			} else if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(extras)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        			if(!(testcaseFileName.toLowerCase().endsWith(".xml") || testcaseFileName.toLowerCase().endsWith(".sel")))
        			{
        			    throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
    				String filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileName;
    				if(StringUtils.isNotBlank(extras)) {
        				try {
							String ext = new String(Base64.getDecoder().decode(extras.getBytes("UTF-8")), "UTF-8");
							if(ext.length()>0) {
								FileUtils.writeStringToFile(new File(filePath+".attr"), extras, "UTF-8");
							}
						} catch (Exception e) {
						}
        			}
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
				
				List<String[]> fileNames = new ArrayList<String[]>();
    			if(gatfConfig.isSeleniumExecutor() && gatfConfig.isSeleniumModuleTests()) {
		        	File mdir = new File(dirPath);
		        	Collection<File> dirs = FileUtils.listFilesAndDirs(mdir, FalseFileFilter.FALSE, TrueFileFilter.INSTANCE);
		        	for (File f : dirs) {
		    			if(new File(f, "main.sel").exists()) {
		    				String sfpath = new File(f, "main.sel").getAbsolutePath().replaceFirst(mdir.getAbsolutePath(), StringUtils.EMPTY);
		    				if(sfpath.charAt(0)==File.separatorChar) {
		    					sfpath = sfpath.substring(1);
		    				}
		    				fileNames.add(new String[] {sfpath, null});
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
	    				fileNames.add(new String[] {"selenium-apis.xml", null});
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
	    				String ext = "";
	    				try {
	    					ext = FileUtils.readFileToString(new File(file.getAbsolutePath()+".attr"), "UTF-8");
	    					ext = new String(Base64.getDecoder().decode(ext.getBytes("UTF-8")), "UTF-8");
						} catch (Exception e) {
						}
	    				fileNames.add(new String[] {TestCaseFinder.getRelativePath(file, dirFPath), ext});
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
