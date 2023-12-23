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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.zeroturnaround.zip.ZipUtil;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.finder.TestCaseFinder;

public class GatfProjectZipHandler extends HttpHandler {

	private GatfConfigToolMojoInt mojo;
	
	public GatfProjectZipHandler(GatfConfigToolMojoInt mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
		AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
		if(response.getStatus()==401) return;
    	List<String> res = new ArrayList<String>();
    	List<String> err = new ArrayList<String>();
    	File configDir = new File(mojo.getRootDir()+File.separator+"gatf-config-tool");
    	
    	if(new File(configDir, "gatf-test-bin.zip").exists())
    		new File(configDir, "gatf-test-bin.zip").delete();
    	
    	if(new File(configDir, "gatf-test-mvn.zip").exists())
    		new File(configDir, "gatf-test-mvn.zip").delete();
    	
    	String resStr = zipDirectory("gatf-test-bin.zip", mojo, false);
    	if(resStr==null)res.add("binary");
    	else err.add(resStr);
    	resStr = zipDirectory("gatf-test-mvn.zip", mojo, true);
    	if(resStr==null)res.add("maven");
    	else err.add(resStr);
    	if(res.size()>0)
    	{
    		String configJson = WorkflowContextHandler.OM.writeValueAsString(res);
        	response.setContentLength(configJson.getBytes("UTF-8").length);
            response.getWriter().write(configJson);
			response.setStatus(HttpStatus.OK_200);
    	}
    	else
    	{
    		String configJson = WorkflowContextHandler.OM.writeValueAsString(err);
        	response.setContentLength(configJson.length());
            response.getWriter().write(configJson);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
    	}
    }

	protected static String zipDirectory(String zipFileName, GatfConfigToolMojoInt mojo, boolean isPomProject)
    {
        try
        {
        	GatfExecutorConfig gatfConfig = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);

        	String outbasepath = gatfConfig.getOutFilesBasePath()==null?mojo.getRootDir():gatfConfig.getOutFilesBasePath();
        	String testbasepath = gatfConfig.getTestCasesBasePath()==null?mojo.getRootDir():gatfConfig.getTestCasesBasePath();
        	
        	File directory = new File(testbasepath);
        	File outdirectory = new File(outbasepath);
        	String folder = System.currentTimeMillis() + "";
        	
        	File configDir = new File(mojo.getRootDir()+File.separator+"gatf-config-tool");
        	File zipFile = new File(configDir, zipFileName);
        	
        	File zfolder = new File(directory, folder);
        	zfolder.mkdir();
        	
        	File[] files = directory.listFiles(TestCaseFinder.NOZIP_FILE_FILTER);
        	File[] folders = directory.listFiles(TestCaseFinder.DIR_FILTER);
        	
        	File testCaseDir = gatfConfig.getTestCaseDir()==null?directory:new File(directory, gatfConfig.getTestCaseDir());
        	File outDir = new File(outdirectory, gatfConfig.getOutFilesDir());
        	
        	File ozFolder = zfolder;
        	if(isPomProject)
        	{
        		FileUtils.copyFileToDirectory(new File(configDir, "pom.xml"), zfolder);
        		FileUtils.copyFileToDirectory(new File(configDir, "README_MAVEN"), zfolder);
        		new File(zfolder, "src\\test\\resources").mkdirs();
        		zfolder = new File(zfolder, "src\\test\\resources");
        	}
        	else
        	{
        		FileUtils.copyFileToDirectory(new File(configDir, "README_BINARY"), zfolder);
        	}
        	
        	boolean isTestDirDone = false, isOutDirDone = false;
        	
        	for (File dir : folders) {
        		if(dir.equals(testCaseDir)) {
        			isTestDirDone = true;
        		}
        		if(dir.equals(outDir)) {
        			isOutDirDone = true;
        		}
        		if(dir.equals(ozFolder) || dir.equals(configDir)) {
        			continue;
        		}
        		FileUtils.copyDirectoryToDirectory(dir, zfolder);
			}
        	
        	if(!isTestDirDone && testCaseDir.exists())
        		FileUtils.copyDirectoryToDirectory(testCaseDir, zfolder);
        	
        	if(!isOutDirDone && outDir.exists())
        		FileUtils.copyDirectoryToDirectory(outDir, zfolder);
        	
        	for (File file : files) {
        		FileUtils.copyFileToDirectory(file, zfolder);
			}
        	
        	ZipUtil.pack(ozFolder, zipFile);
        	FileUtils.deleteDirectory(ozFolder);
        	return null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
            return configJson;
        }
    }
}
