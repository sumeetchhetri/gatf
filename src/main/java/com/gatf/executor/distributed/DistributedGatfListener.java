package com.gatf.executor.distributed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.executor.report.RuntimeReportUtil.LoadTestEntry;
import com.gatf.selenium.SeleniumTest;
import com.gatf.selenium.SerializableLogEntries;

public class DistributedGatfListener {

	private static final Logger logger = Logger.getLogger(DistributedGatfListener.class.getSimpleName());
	
	public static void main(String[] args) throws Exception {
		
		ServerSocket server = new ServerSocket(4567);
		logger.info("Distributed GATF node listening on port 4567");
		try {
			while(true) {
				final Socket client = server.accept();
				InputStream in = client.getInputStream();
				OutputStream out = client.getOutputStream();
				
				final ObjectInputStream ois = new ObjectInputStream(in);
				final ObjectOutputStream oos = new ObjectOutputStream(out);
				
				new Thread(new Runnable() {
					public void run() {
						try {
							handleCommand(ois, oos);
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							if(client!=null)
							{
								try {
									client.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}).start();
			}
		} finally {
			server.close();
		}
	}
	
	private static void handleCommand(ObjectInputStream ois, final ObjectOutputStream oos) throws Exception {
		
		logger.info("Got a new distributed GATF request...");
		
		DistributedAcceptanceContext context = null;
		DistributedTestContext tContext = null;
		try {
			
			Command command = (Command)ois.readObject();
			logger.info("Received command - " + command);
			if(command==Command.CONFIG_SHARE_REQ) {
				context = (DistributedAcceptanceContext)ois.readObject();
				if(context!=null) {
					oos.writeObject(Command.CONFIG_SHARE_RES);
					oos.flush();
					logger.info("Fetched GATF configuration...");
				} else {
					oos.writeObject(Command.INVALID);
					oos.flush();
					logger.info("Invalid GATF configuration received...");
				}
			} else {
				oos.writeObject(Command.INVALID);
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
			command = (Command)ois.readObject();
			logger.info("Received command - " + command);
			if(command==Command.TESTS_SHARE_REQ) {
				tContext = (DistributedTestContext)ois.readObject();
				if(tContext!=null) {
					oos.writeObject(Command.TESTS_SHARE_RES);
					oos.flush();
					logger.info("Fetched GATF tests ...");
					
					logger.info("Started executing GATF tests...");
					GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
					
					Thread dlreporter = new Thread(new Runnable() {
						public void run() {
							try {
								while(true) {
									LoadTestEntry entry = RuntimeReportUtil.getDLEntry();
									if(entry!=null) {
										oos.writeObject(Command.LOAD_TESTS_RES);
										oos.writeObject(entry);
										oos.flush();
									}
									Thread.sleep(500);
								}
							} catch (Exception e) {
								LoadTestEntry entry = null;
								while((entry=RuntimeReportUtil.getDLEntry())!=null)
								{
									try {
										if(entry!=null) {
											oos.writeObject(Command.LOAD_TESTS_RES);
											oos.writeObject(entry);
											oos.flush();
										}
									} catch (IOException e1) {
									}
								}
							}
						}
					});
					dlreporter.start();
					context.getConfig().setTestCasesBasePath(System.getProperty("user.dir"));
					context.getConfig().setOutFilesBasePath(System.getProperty("user.dir"));
					logger.info("Current working directory is: " + System.getProperty("user.dir"));
					DistributedTestStatus report = mojo.handleDistributedTests(context, tContext);
					mojo.shutdown();
					Thread.sleep(2000);
					
					dlreporter.interrupt();
					Thread.sleep(3000);
					
					oos.writeObject(Command.TESTS_SHARE_RES);
					oos.flush();
					
					String fileName = UUID.randomUUID().toString()+".zip";
					report.setZipFileName(fileName);
					
					oos.writeObject(report);
					oos.flush();
					logger.info("Writing GATF results...");
					
					File basePath = null;
		        	if(context.getConfig().getOutFilesBasePath()!=null)
		        		basePath = new File(context.getConfig().getOutFilesBasePath());
		        	else
		        	{
		        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
		        		basePath = new File(url.getPath());
		        	}
		        	File resource = new File(basePath, context.getConfig().getOutFilesDir());
		        	
					ReportHandler.zipDirectory(resource, new String[]{".html",".csv"}, fileName, false);
					
					File zipFile = new File(resource, fileName);
					IOUtils.copy(new FileInputStream(zipFile), oos);
					oos.flush();
					logger.info("Done Writing GATF results...");
				} else {
					oos.writeObject(Command.INVALID);
					oos.flush();
					logger.info("Invalid GATF tests received...");
				}
			} else if(command==Command.SELENIUM_REQ) {
				
				File gcdir = new File(FileUtils.getTempDirectory(), "dist-gatf-code");
		        if(gcdir.exists()) {
		        	FileUtils.deleteDirectory(gcdir);
		        }
				
				gcdir.mkdir();
				String fileName = UUID.randomUUID().toString()+".zip";
				File zipFile = new File(gcdir, fileName);
	        	FileOutputStream fos = new FileOutputStream(zipFile);
				IOUtils.copy(ois, fos);
				fos.flush();
				fos.close();
				
				ReportHandler.unzipZipFile(new FileInputStream(zipFile), gcdir.getAbsolutePath());
				zipFile.delete();
				
				URL[] urls = new URL[1];
	            urls[0] = gcdir.toURI().toURL();
				URLClassLoader classLoader = new URLClassLoader(urls, DistributedGatfListener.class.getClassLoader());
				Thread.currentThread().setContextClassLoader(classLoader);
				
				List<SeleniumTest> tests = new ArrayList<SeleniumTest>();
				@SuppressWarnings("unchecked")
				List<String> testClassNames = (List<String>)ois.readObject();
				for (String clsname : testClassNames) {
					@SuppressWarnings("unchecked")
					Class<SeleniumTest> loadedClass = (Class<SeleniumTest>)classLoader.loadClass(clsname);
					tests.add(loadedClass.newInstance());
				}
				
				oos.writeObject(Command.SELENIUM_RES);
				oos.flush();
				
				if(tests==null || tests.size()==0 || context.getConfig().isValidSeleniumRequest()) {
					boolean driverfound = false;
					if(!new File(context.getConfig().getSeleniumDriverPath()).exists()) {
						Path p = Paths.get(context.getConfig().getSeleniumDriverPath());
						File df = new File(System.getProperty("user.dir"), p.getFileName().toString());
						if(df!=null && df.exists()) {
							driverfound = true;
							System.setProperty(context.getConfig().getSeleniumDriverName(), df.getAbsolutePath());
						}
					} else {
						driverfound = true;
						System.setProperty(context.getConfig().getSeleniumDriverName(), context.getConfig().getSeleniumDriverPath());
					}
					
					if(driverfound) {
						oos.writeInt(0);
						oos.flush();
						logger.info("Selenium Test Request");
						
						GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
						List<Map<String, SerializableLogEntries>> logs = mojo.handleDistributedSeleniumTests(context, tests);
						oos.writeObject(logs);
						oos.flush();
						logger.info("Done Writing Selenium results...");
					} else {
						oos.writeInt(1);
						oos.flush();
						logger.info("Selenium Test Request");
					}
				} else {
					oos.writeInt(1);
					oos.flush();
					logger.info("Selenium Test Request");
				}
			} else {
				oos.writeObject(Command.INVALID);
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
		} catch (Exception e) {
			oos.write(0);
			logger.info("Error occurred during distributed GATF execution...");
			throw e;
		}
	}
}
