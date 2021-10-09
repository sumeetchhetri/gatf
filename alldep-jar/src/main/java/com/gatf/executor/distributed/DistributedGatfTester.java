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
package com.gatf.executor.distributed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.RuntimeReportUtil;

public class DistributedGatfTester {

	private static final Logger logger = Logger.getLogger(DistributedGatfTester.class.getSimpleName());
	
	public static class DistributedConnection {
		private Socket sock;
		private String node;
		private Output oos = null;
		private Input ois = null;
		
		public String toString()
		{
			return node;
		}
	}
	
	public DistributedConnection distributeContext(String node, AcceptanceTestContext context, List<Object[]> testdata)
	{
		Socket client = null;
		DistributedConnection conn = null;
		try {
			logger.info("Connecting to node " + node);
			
			int port = 9567;
	        if(node.indexOf("@")!=-1) {
	            try {
	                String sp = node.substring(node.indexOf("@")+1);
	                node = node.substring(0, node.indexOf("@"));
	                port = Integer.parseInt(sp);
	            } catch (Exception e) {
	                logger.info("Invalid port number specified for distributed listener, defaulting to 9567");
	            }
	        }
			
			client = new Socket(node, port);
			DistributedAcceptanceContext disContext = context.getDistributedContext(node, testdata);
			
			Output oos = new Output(client.getOutputStream());
			Input ois = new Input(client.getInputStream());
			
			logger.info("Sending GATF configuration to node " + node);
			oos.writeInt(Command.CONFIG_SHARE_REQ.ordinal());
			DistributedAcceptanceContext.ser(oos, disContext);
			oos.flush();
			logger.info("Sent GATF configuration to node " + node);
			
			Command command = Command.values()[ois.readInt()];
			if(command==Command.CONFIG_SHARE_RES) {
				conn = new DistributedConnection();
				conn.sock = client;
				conn.node = node;
				conn.ois = ois;
				conn.oos = oos;
				logger.info("Sending GATF configuration Successful to node " + node);
			} else {
				logger.info("Sending GATF configuration Failed to node " + node);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("Sending GATF configuration Failed to node " + node);
			if(client!=null)
			{
				try {
					client.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		return conn;
	}
	
	public FutureTask<DistributedTestStatus> distributeTests(List<TestCase> simTestCases, 
			final DistributedConnection connection, boolean dorep, int index, int numberOfRuns,
			final AcceptanceTestContext context, Set<String> relativeFileNames)
	{
		if(connection==null)return null;
		
		FutureTask<DistributedTestStatus> task = null;
		try {
			DistributedTestContext testContext = new DistributedTestContext();
			testContext.setSimTestCases(simTestCases);
			testContext.setIndex(index);
			testContext.setNumberOfRuns(numberOfRuns);
			testContext.setRelativeFileNames(relativeFileNames);
			
			logger.info("Sending GATF tests to node " + connection.node);
			connection.oos.writeInt(Command.TESTS_SHARE_REQ.ordinal());
			DistributedAcceptanceContext.ser(connection.oos, testContext);
			logger.info("Sent GATF tests to node " + connection.node);
			
			Command command = Command.values()[connection.ois.readInt()];;
			if(command==Command.TESTS_SHARE_RES) {
				task = new FutureTask<DistributedTestStatus>(new Callable<DistributedTestStatus>() {
					public DistributedTestStatus call() throws Exception {
						DistributedTestStatus res = null;
						Socket fClient = connection.sock;
						try {
							logger.info("Waiting for GATF tests Results from node " + connection.node);
							
							Command command = Command.values()[connection.ois.readInt()];;
							while(command==Command.LOAD_TESTS_RES)
							{
								Object lentry = DistributedAcceptanceContext.unser(connection.ois);
								RuntimeReportUtil.addEntry(lentry);
								command = Command.values()[connection.ois.readInt()];;
							}
							
							res = DistributedAcceptanceContext.unser(connection.ois);
							logger.info("Received GATF tests Results from node " + connection.node);
							
							String fileName = "dist-" + res.getZipFileName();
							File basePath = null;
				        	if(context.getGatfExecutorConfig().getOutFilesBasePath()!=null)
				        		basePath = new File(context.getGatfExecutorConfig().getOutFilesBasePath());
				        	else
				        	{
				        		basePath = new File(System.getProperty("user.dir"));
				        	}
				        	File resource = new File(basePath, context.getGatfExecutorConfig().getOutFilesDir());
				        	
				        	File zipFile = new File(resource, fileName);
				        	FileOutputStream fos = new FileOutputStream(zipFile);
							IOUtils.copy(connection.ois, fos);
							fos.flush();
							fos.close();
				        	
				        	ReportHandler.unzipZipFile(zipFile, resource.getAbsolutePath());
							
							logger.info("Done Receiving GATF tests Result files from node " + connection.node);
						} catch (Exception e) {
							e.printStackTrace();
							logger.info("Failure occurred while waiting for GATF tests Results from node " + connection.node);
						} finally {
							if(fClient!=null)
							{
								try {
									fClient.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
						return res;
					}
				});
				new Thread(task).start();
				logger.info("Sending GATF tests Successful to node " + connection.node);
			} else {
				logger.info("Sending GATF tests Failed to node " + connection.node);
				if(connection!=null && connection.sock!=null)
				{
					try {
						connection.sock.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("Sending GATF tests Failed to node " + connection.node);
			if(connection!=null && connection.sock!=null)
			{
				try {
					connection.sock.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		return task;
	}
	
	public FutureTask<Object> distributeSeleniumTests(final DistributedConnection connection, int index, int numberOfRuns, File testClassesZip, 
			final List<String> testClassNames, final AcceptanceTestContext context)
	{
		if(connection==null)return null;
		
		FutureTask<Object> task = null;
		try {
            DistributedTestContext testContext = new DistributedTestContext();
            testContext.setIndex(index);
            testContext.setNumberOfRuns(numberOfRuns);
            
			logger.info("Sending GATF Selenium tests to node " + connection.node);
			connection.oos.writeInt(Command.SELENIUM_REQ.ordinal());
			FileInputStream fis = new FileInputStream(testClassesZip);
			connection.oos.writeLong(fis.getChannel().size());
			IOUtils.copy(fis, connection.oos);
			fis.close();
			DistributedAcceptanceContext.ser(connection.oos, testClassNames);
            DistributedAcceptanceContext.ser(connection.oos, testContext);
			
			Command command = Command.values()[connection.ois.readInt()];;
			if(command==Command.SELENIUM_RES) {
				int code = connection.ois.readInt();
				if(code==0) {
					task = new FutureTask<Object>(new Callable<Object>() {
						public Object call() throws Exception {
						    DistributedTestStatus res = null;
							Socket fClient = connection.sock;
							try {
								logger.info("Waiting for GATF Selenium tests Results from node " + connection.node);
								
								Command command = Command.values()[connection.ois.readInt()];;
	                            while(command==Command.LOAD_TESTS_RES)
	                            {
	                                Object lentry = DistributedAcceptanceContext.unser(connection.ois);
	                                RuntimeReportUtil.addEntry(lentry);
	                                command = Command.values()[connection.ois.readInt()];
	                            }
	                            
	                            res = DistributedAcceptanceContext.unser(connection.ois);
	                            logger.info("Received GATF Selenium tests Results from node " + connection.node);
	                            
	                            String fileName = "dist-" + res.getZipFileName();
	                            File basePath = null;
	                            if(context.getGatfExecutorConfig().getOutFilesBasePath()!=null)
	                                basePath = new File(context.getGatfExecutorConfig().getOutFilesBasePath());
	                            else
	                            {
	                                basePath = new File(System.getProperty("user.dir"));
	                            }
	                            File resource = new File(basePath, context.getGatfExecutorConfig().getOutFilesDir());
	                            
	                            File zipFile = new File(resource, fileName);
	                            FileOutputStream fos = new FileOutputStream(zipFile);
	                            IOUtils.copy(connection.ois, fos);
	                            fos.flush();
	                            fos.close();
	                            
	                            ReportHandler.unzipZipFile(zipFile, resource.getAbsolutePath());
								
								logger.info("Done Receiving GATF Selenium tests Results from node " + connection.node);
							} catch (Exception e) {
								e.printStackTrace();
								logger.info("Failure occurred while waiting for GATF Selenium tests Results from node " + connection.node);
							} finally {
								if(fClient!=null)
								{
									try {
										fClient.close();
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
							}
							return res;
						}
					});
					new Thread(task).start();
					logger.info("Sending GATF Selenium tests Successful to node " + connection.node);
				} else {
				    if(code == 1) {
				        task = new FutureTask<Object>(new Callable<Object>() {
	                        public Object call() throws Exception {
	                            return new RuntimeException("No Driver found for specified browser");
	                        }
				        });
				    } else {
				        task = new FutureTask<Object>(new Callable<Object>() {
                            public Object call() throws Exception {
                                return new RuntimeException("Invalid configuration specified for selenium");
                            }
                        }); 
				    }
					logger.info("Sending GATF Selenium tests Failed to node " + connection.node);
					if(connection!=null && connection.sock!=null)
					{
						try {
							connection.sock.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			} else {
				logger.info("Sending GATF Selenium tests Failed to node " + connection.node);
				if(connection!=null && connection.sock!=null)
				{
					try {
						connection.sock.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				task = new FutureTask<Object>(new Callable<Object>() {
                    public Object call() throws Exception {
                        return new RuntimeException("Invalid configuration specified for selenium");
                    }
                });
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("Sending GATF Selenium tests Failed to node " + connection.node);
			if(connection!=null && connection.sock!=null)
			{
				try {
					connection.sock.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
            task = new FutureTask<Object>(new Callable<Object>() {
                public Object call() throws Exception {
                    return new RuntimeException("Invalid configuration specified for selenium");
                    }
            });
		}
		return task;
	}
}
