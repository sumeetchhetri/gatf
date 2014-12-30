package com.gatf.executor.distributed;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.executor.report.RuntimeReportUtil.LoadTestEntry;

public class DistributedGatfListener {

	private static final Logger logger = Logger.getLogger(DistributedGatfListener.class.getSimpleName());
	
	public static void main(String[] args) throws Exception {
		
		ServerSocket server = new ServerSocket(4567);
		logger.info("Distributed GATF node listening on port 4567");
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
				if(context!=null) {
					oos.writeObject(Command.TESTS_SHARE_RES);
					oos.flush();
					logger.info("Fetched GATF tests ...");
				} else {
					oos.writeObject(Command.INVALID);
					oos.flush();
					logger.info("Invalid GATF tests received...");
				}
			} else {
				oos.writeObject(Command.INVALID);
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
			if(context!=null && tContext!=null) {
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
	        	
				ReportHandler.zipDirectory(resource, new String[]{".html",".csv"}, fileName);
				
				File zipFile = new File(resource, fileName);
				IOUtils.copy(new FileInputStream(zipFile), oos);
				oos.flush();
				logger.info("Done Writing GATF results...");
			}
		} catch (Exception e) {
			oos.write(0);
			logger.info("Error occurred during distributed GATF execution...");
			throw e;
		}
	}
}
