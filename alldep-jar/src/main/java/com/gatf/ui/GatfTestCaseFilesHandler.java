/*
    Copyright 2013-2026, Sumeet Chhetri

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.selenium.Command;

public class GatfTestCaseFilesHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	public GatfTestCaseFilesHandler(GatfConfigToolMojoInt mojo) {
		super();
		this.mojo = mojo;
	}

	// the directory test-file paths are relative to (basepath[/testCaseDir]), as every branch below builds it
	private File testCaseBase(GatfExecutorConfig gatfConfig) {
		String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
		File base = gatfConfig.getTestCaseDir()!=null ? new File(basepath, gatfConfig.getTestCaseDir()) : new File(basepath);
		if(!base.exists()) base.mkdirs();
		return base;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
	    response.setHeader("Cache-Control", "no-cache, no-store");
    	if(request.getMethod().equals(Method.POST) && "true".equals(request.getParameter("upload"))) {
    		// Δ163n (bug 8): upload a test file (raw body), or a .zip of a project/folder that is unpacked into the
    		// target's folder. Paths go through TestFilePaths (no '..', no absolute, zip-slip safe).
    		try {
    			String ctype = request.getContentType();
    			if(ctype != null && ctype.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
    				// Grizzly reads such a body as form fields: the file would arrive empty
    				GatfConfigToolUtil.handleError(new IllegalArgumentException("Send the file as application/octet-stream (got " + ctype + ")"), response, HttpStatus.UNSUPPORTED_MEDIA_TYPE_415);
    				return;
    			}
    			GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
    			File base = testCaseBase(gatfConfig);
    			String target = TestFilePaths.normalize(request.getParameter("testcaseFileName"));
    			boolean overwrite = "true".equals(request.getParameter("overwrite"));
    			Map<String, Object> out = new LinkedHashMap<>();
    			if(target.toLowerCase().endsWith(".zip")) {
    				TestFilePaths.Extracted x = TestFilePaths.extractZip(request.getInputStream(), base, TestFilePaths.dirOf(target), overwrite, 2000, 200L * 1024 * 1024);
    				out.put("written", x.written);
    				out.put("skipped", x.skipped);
    			} else {
    				if(!TestFilePaths.uploadable(target)) {
    					throw new IllegalArgumentException("File type not allowed: " + target + " (.sel .xml .props .csv .json .txt or a .zip)");
    				}
    				File f = TestFilePaths.resolve(base, target);
    				if(f.exists() && !overwrite) {
    					throw new IllegalArgumentException("Testcase file already exists: " + target);
    				}
    				try (java.io.InputStream in = request.getInputStream()) {
    					TestFilePaths.copyCapped(in, f, 50L * 1024 * 1024, "A test file upload is limited to 50 MB");
    				}
    				out.put("written", java.util.Collections.singletonList(target));
    				out.put("skipped", java.util.Collections.emptyList());
    			}
    			String json = WorkflowContextHandler.OM.writeValueAsString(out);
    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
    			response.setContentLength(json.getBytes("UTF-8").length);
    			response.getWriter().write(json);
    			response.setStatus(HttpStatus.OK_200);
    		} catch (IllegalArgumentException e) {
    			GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
    		} catch (TestFilePaths.LimitException e) {
    			GatfConfigToolUtil.handleError(e, response, HttpStatus.REQUEST_ENTITY_TOO_LARGE_413);
    		} catch (Exception e) {
    			GatfConfigToolUtil.handleError(e, response, null);
    		}
    	} else if(request.getMethod().equals(Method.POST)) {
    		try {
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			String extras = request.getParameter("extras");
    			if(StringUtils.isNotBlank(testcaseFileName)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
        			if(!testcaseFileName.toLowerCase().endsWith(".xml") && !testcaseFileName.toLowerCase().endsWith(".sel"))
        			{
        				throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel)");
        			}
        			// Δ163n: '/' or '\\' paths, no '..' / absolute target, and a new sub-folder is created (+ New file
        			// "newdir/x.sel" failed: createNewFile without mkdirs)
        			String filePath;
        			try {
        				filePath = TestFilePaths.resolve(testCaseBase(gatfConfig), testcaseFileName).getPath();
        			} catch (IllegalArgumentException e) {
        				GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
        				return;
        			}
        			
        			if(new File(filePath).exists()) {
        				throw new RuntimeException("Testcase file already exists");
        			}
        			new File(filePath).getParentFile().mkdirs();
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
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
    				// Δ163n (bug 4): compare the DIRECTORIES of '/'-separated client paths (the old check compared the names
    				// after the last File.separator, so every rename inside a folder failed), refuse '..' / absolute targets.
    				String[] ft;
    				try {
    					ft = TestFilePaths.checkRename(testcaseFileName, testcaseFileNameTo);
    				} catch (IllegalArgumentException e) {
    					GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
    					return;
    				}
    				File base = testCaseBase(gatfConfig);
    				File from = TestFilePaths.resolve(base, ft[0]);
    				File to = TestFilePaths.resolve(base, ft[1]);
        			if(from.exists()) {
        				if(!from.equals(to)) {
        					// a case-only rename (a.sel -> A.sel) on a case-insensitive file system: the target "exists" as the source
        					boolean sameFile = to.exists() && java.nio.file.Files.isSameFile(from.toPath(), to.toPath());
        					File attr = new File(from.getPath() + ".attr"), toAttr = new File(to.getPath() + ".attr");
        					if(!sameFile && (to.exists() || toAttr.exists())) {   // a stale target .attr would attach itself to the renamed file
        						GatfConfigToolUtil.handleError(new IllegalArgumentException("Target file already exists: " + ft[1] + (to.exists() ? "" : ".attr")), response, HttpStatus.CONFLICT_409);
        						return;
        					}
        					try {
        						if(sameFile) {
        							java.nio.file.Files.move(from.toPath(), to.toPath());   // same inode: changes the name's case
        						} else {
        							FileUtils.moveFile(from, to);
        						}
        						if(attr.exists() && !sameFile) FileUtils.moveFile(attr, toAttr);
        					} catch (Exception e) {
        						throw new RuntimeException("File rename operation failed");
        					}
        				}
        			} else {
        				throw new RuntimeException("Source Testcase file does not exist");
        			}
        			response.setStatus(HttpStatus.OK_200);
    			} else if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(extras)) {
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
        			if(!(testcaseFileName.toLowerCase().endsWith(".xml") || testcaseFileName.toLowerCase().endsWith(".sel") || testcaseFileName.toLowerCase().endsWith(".props")))
        			{
        			    throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel/.props)");
        			}
        			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
        			String filePath = basepath + File.separator + testcaseFileName;
        			if(gatfConfig.getTestCaseDir()!=null) {
	        			filePath = basepath + File.separator + gatfConfig.getTestCaseDir() + File.separator + testcaseFileName;
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
    				GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
        			if(!testcaseFileName.toLowerCase().endsWith(".xml") && !testcaseFileName.toLowerCase().endsWith(".sel") && !testcaseFileName.toLowerCase().endsWith(".props"))
        			{
        				throw new RuntimeException("Testcase File should be an xml or sel file, extension should be (.xml/.sel/.props)");
        			}
        			// Δ163n: same guard as create / rename / upload (a '../' name deleted files outside the test dir)
        			String filePath;
        			try {
        				filePath = TestFilePaths.resolve(testCaseBase(gatfConfig), testcaseFileName).getPath();
        			} catch (IllegalArgumentException e) {
        				GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
        				return;
        			}
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
    			String testcaseFileName = request.getParameter("testcaseFileName");
    			String possibleSubtestFuncCall = request.getParameter("possibleSubtestFuncCall");
    			String referencedFile = request.getParameter("referencedFile");
    			String allsubtests = request.getParameter("allsubtests");
    			
    			final GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
    			String basepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
    			String dirPath = basepath;
    			if(gatfConfig.getTestCaseDir()!=null) {
	    			dirPath = basepath + File.separator + gatfConfig.getTestCaseDir();
					if(!new File(dirPath).exists()) {
						new File(dirPath).mkdir();
					}
    			}
				
    			if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(referencedFile)) {
    				GatfTestCaseExecutorUtil executorMojo = new GatfTestCaseExecutorUtil();
					executorMojo.initilaizeContext(gatfConfig, true);
					File tfl = executorMojo.getContext().getResourceFile(testcaseFileName);
					if(tfl.exists()) {
						File par = tfl.getParentFile();
						File tcdir = executorMojo.getContext().getTestCasesDir();
						File rfl = executorMojo.getContext().getResourceFile(referencedFile);
						String filePath = "";
						if(!rfl.exists()) {
							rfl = new File(par, referencedFile);
						}
						if(rfl.exists()) {
							filePath = rfl.getAbsolutePath().replace(tcdir.getAbsolutePath(), "");
							if(filePath.startsWith(File.separator)) {
								filePath = filePath.substring(1);
							}
						}
						String json = WorkflowContextHandler.OM.writeValueAsString(new Object[] {filePath});
		    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
			            response.setContentLength(json.getBytes("UTF-8").length);
			            response.getWriter().write(json);
		    			response.setStatus(HttpStatus.OK_200);
						return;
					}
    			} else if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(possibleSubtestFuncCall)) {
					GatfTestCaseExecutorUtil executorMojo = new GatfTestCaseExecutorUtil();
					executorMojo.initilaizeContext(gatfConfig, true);
					Object[] fld = Command.getSubtestFromCall(possibleSubtestFuncCall, testcaseFileName, executorMojo.getContext(), null);
					String json = WorkflowContextHandler.OM.writeValueAsString(new Object[] {fld[5], fld[1]});
	    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		            response.setContentLength(json.getBytes("UTF-8").length);
		            response.getWriter().write(json);
	    			response.setStatus(HttpStatus.OK_200);
					return;
				} else if(StringUtils.isNotBlank(testcaseFileName) && StringUtils.isNotBlank(allsubtests)) {
					GatfTestCaseExecutorUtil executorMojo = new GatfTestCaseExecutorUtil();
					executorMojo.initilaizeContext(gatfConfig, true);
					List<String> lst = Command.getSubtestsForFile(testcaseFileName, executorMojo.getContext(), false);
					String json = WorkflowContextHandler.OM.writeValueAsString(lst);
	    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		            response.setContentLength(json.getBytes("UTF-8").length);
		            response.getWriter().write(json);
	    			response.setStatus(HttpStatus.OK_200);
					return;
				}
				
				List<String[]> fileNames = new ArrayList<String[]>();
    			if(gatfConfig.isSeleniumModuleTests()) {
		        	File mdir = new File(dirPath);
		        	Collection<File> dirs = FileUtils.listFilesAndDirs(mdir, FalseFileFilter.FALSE, TrueFileFilter.INSTANCE);
		        	for (File f : dirs) {
		    			if(new File(f, "main.sel").exists()) {
		    				String sfpath = TestCaseFinder.getRelativePath(new File(f, "main.sel"), mdir);   // Δ163n: was replaceFirst(<path as regex>)
		    				fileNames.add(new String[] {TestFilePaths.toClient(sfpath), null});
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
    			}
    			
				FilenameFilter filter = new FilenameFilter() {
    				public boolean accept(File folder, String name) {
    					return (name.toLowerCase().endsWith(".xml") || name.toLowerCase().endsWith(".props") || name.toLowerCase().endsWith(".csv") 
    					        || (!gatfConfig.isSeleniumModuleTests() && name.toLowerCase().endsWith(".sel")));
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
    				fileNames.add(new String[] {TestFilePaths.toClient(TestCaseFinder.getRelativePath(file, dirFPath)), ext});   // Δ163n: '/' on Windows too
				}
    			
    			String json = WorkflowContextHandler.OM.writeValueAsString(fileNames);
    			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
	            response.setContentLength(json.getBytes("UTF-8").length);
	            response.getWriter().write(json);
    			response.setStatus(HttpStatus.OK_200);
			} catch (Exception e) {
				GatfConfigToolUtil.handleError(e, response, HttpStatus.BAD_REQUEST_400);
			}
    	}
    }
}
