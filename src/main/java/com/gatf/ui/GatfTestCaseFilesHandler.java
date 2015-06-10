package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.finder.TestCaseFinder;

public class GatfTestCaseFilesHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	
	protected GatfTestCaseFilesHandler(GatfConfigToolMojo mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	if(request.getMethod().equals(Method.POST)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			if(StringUtils.isNotBlank(testcaseFileName)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
        			if(!testcaseFileName.endsWith(".xml"))
        			{
        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
    				if(!new File(dirPath).exists()) {
    					new File(dirPath).mkdir();
    				}
        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileName;
        			if(new File(filePath).exists()) {
        				throw new RuntimeException("Testcase file already exists");
        			}
        			new File(filePath).createNewFile();
        			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        			bos.write("<TestCases></TestCases>".getBytes());
        			bos.close();
        			response.setStatus(HttpStatus.OK_200);
    			} else {
    				throw new RuntimeException("No testcaseFileName query parameter specified");
    			}
			} catch (Exception e) {
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.PUT)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			String testcaseFileNameTo = request.getParameter("testcaseFileNameTo");
    			if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(testcaseFileNameTo)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
        			if(!testcaseFileName.endsWith(".xml") || !testcaseFileNameTo.endsWith(".xml"))
        			{
        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
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
    				
        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileName;
        			String tofilePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileNameTo;
        			if(new File(filePath).exists()) {
        				if(!new File(filePath).renameTo(new File(tofilePath))) {
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
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.DELETE)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			if(StringUtils.isNotBlank(testcaseFileName)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
        			if(!testcaseFileName.endsWith(".xml"))
        			{
        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
    				if(!new File(dirPath).exists()) {
    					new File(dirPath).mkdir();
    				}
        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileName;
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
				GatfConfigToolMojo.handleError(e, response, null);
			}
    	} else if(request.getMethod().equals(Method.GET) ) {
    		try {
    			GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
    			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
    			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
				if(!new File(dirPath).exists()) {
					new File(dirPath).mkdir();
				}
    			
    			FilenameFilter filter = new FilenameFilter() {
    				public boolean accept(File folder, String name) {
    					return name.toLowerCase().endsWith(".xml");
    				}
    			};
    			
    			File dirFPath = new File(dirPath);
    			List<File> fileLst = new ArrayList<File>();
    			TestCaseFinder.getFiles(dirFPath, filter, fileLst);
    			
    			List<File> allFiles = TestCaseFinder.filterFiles(gatfConfig.getIgnoreFiles(), fileLst, dirFPath);
    			allFiles = TestCaseFinder.filterValidTestCaseFiles(allFiles);
    			
    			List<String> fileNames = new ArrayList<String>();
    			for (File file : allFiles) {
    				fileNames.add(TestCaseFinder.getRelativePath(file, dirFPath));
				}
    			
    			String json = new ObjectMapper().writeValueAsString(fileNames);
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
