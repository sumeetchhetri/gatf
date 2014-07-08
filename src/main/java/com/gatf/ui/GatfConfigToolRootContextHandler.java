package com.gatf.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.zeroturnaround.zip.ZipUtil;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.finder.TestCaseFinder;

public class GatfConfigToolRootContextHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	protected GatfConfigToolRootContextHandler(GatfConfigToolMojo mojo) {
		super();
		this.mojo = mojo;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	List<String> res = new ArrayList<String>();
    	List<String> err = new ArrayList<String>();
    	File configDir = new File(mojo.rootDir+SystemUtils.FILE_SEPARATOR+"gatf-config-tool");
    	
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
    		String configJson = new ObjectMapper().writeValueAsString(res);
        	response.setContentLength(configJson.length());
            response.getWriter().write(configJson);
			response.setStatus(HttpStatus.OK_200);
    	}
    	else
    	{
    		String configJson = new ObjectMapper().writeValueAsString(err);
        	response.setContentLength(configJson.length());
            response.getWriter().write(configJson);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
    	}
    }

	protected static String zipDirectory(String zipFileName, GatfConfigToolMojo mojo, boolean isPomProject)
    {
        try
        {
        	GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);

        	String outbasepath = gatfConfig.getOutFilesBasePath()==null?mojo.rootDir:gatfConfig.getOutFilesBasePath();
        	String testbasepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
        	
        	File directory = new File(testbasepath);
        	File outdirectory = new File(outbasepath);
        	String folder = System.currentTimeMillis() + "";
        	
        	File configDir = new File(mojo.rootDir+SystemUtils.FILE_SEPARATOR+"gatf-config-tool");
        	File zipFile = new File(configDir, zipFileName);
        	
        	File zfolder = new File(directory, folder);
        	zfolder.mkdir();
        	
        	File[] files = directory.listFiles(TestCaseFinder.NOZIP_FILE_FILTER);
        	File[] folders = directory.listFiles(TestCaseFinder.DIR_FILTER);
        	
        	File testCaseDir = new File(directory, gatfConfig.getTestCaseDir());
        	File outDir = new File(outdirectory, gatfConfig.getOutFilesDir());
        	
        	File ozFolder = zfolder;
        	if(isPomProject)
        	{
        		FileUtils.copyFileToDirectory(new File(configDir, "pom.xml"), zfolder);
        		new File(zfolder, "src\\test\\resources").mkdirs();
        		zfolder = new File(zfolder, "src\\test\\resources");
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
