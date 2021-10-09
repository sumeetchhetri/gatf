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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.SeleniumTest;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class DistributedGatfListener {

	private static final Logger logger = Logger.getLogger(DistributedGatfListener.class.getSimpleName());
	
	public static void main(String[] args) throws Exception {
		String workingDir = System.getProperty("user.dir");
	    int port = 9567;
	    if(args.length>1) {
	        try {
                port = Integer.parseInt(args[1]);
            } catch (Exception e) {
                logger.info("Invalid port number specified for listener, defaulting to 9567");
            }
	    }
	    if(args.length>2) {
	        try {
	        	workingDir = args[2].trim();
	        	if(!new File(workingDir).exists()) {
	        		throw new RuntimeException("Invalid working directory");
	        	}
            } catch (Exception e) {
            	throw new RuntimeException("Invalid working directory");
            }
	    }
	    
	    final String wd = workingDir;
		ServerSocket server = new ServerSocket(port);
		logger.info("Distributed GATF node listening on port "+port);
		try {
			while(true) {
				final Socket client = server.accept();
				
				Output oos = new Output(client.getOutputStream());
				Input ios = new Input(client.getInputStream());
				
				new Thread(new Runnable() {
					public void run() {
						try {
							handleCommand(ios, oos, wd);
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
	
	private static void handleCommand(Input ois, final Output oos, final String workingDir) throws Exception {
		
		logger.info("Got a new distributed GATF request...");
		
		DistributedAcceptanceContext context = null;
		DistributedTestContext tContext = null;
		try {
			
			Command command = Command.values()[ois.readInt()];
			logger.info("Received command - " + command);
			if(command==Command.CONFIG_SHARE_REQ) {
				context = DistributedAcceptanceContext.unser(ois);
				if(context!=null) {
					oos.writeInt(Command.CONFIG_SHARE_RES.ordinal());
					oos.flush();
					logger.info("Fetched GATF configuration...");
				} else {
					oos.writeInt(Command.INVALID.ordinal());
					oos.flush();
					logger.info("Invalid GATF configuration received...");
				}
			} else {
				oos.writeInt(Command.INVALID.ordinal());
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
			command = Command.values()[ois.readInt()];;
			logger.info("Received command - " + command);
			if(command==Command.TESTS_SHARE_REQ) {
				tContext = DistributedAcceptanceContext.unser(ois);
				if(tContext!=null) {
					oos.writeInt(Command.TESTS_SHARE_RES.ordinal());
					oos.flush();
					logger.info("Fetched GATF tests ...");
					
					logger.info("Started executing GATF tests...");
					GatfTestCaseExecutorUtil mojo = new GatfTestCaseExecutorUtil();
					
					boolean isLoadTestingEnabled = context.getConfig().isLoadTestingEnabled() && context.getConfig().getLoadTestingTime()>10000;
					
					Thread dlreporter = new Thread(new Runnable() {
						public void run() {
							try {
								while(true) {
									Object entry = RuntimeReportUtil.getDLEntry();
									if(entry!=null) {
										oos.writeInt(Command.LOAD_TESTS_RES.ordinal());
										DistributedAcceptanceContext.ser(oos, entry);
										oos.flush();
									}
									Thread.sleep(500);
								}
							} catch (Exception e) {
							    Object entry = null;
								while((entry=RuntimeReportUtil.getDLEntry())!=null)
								{
									if(entry!=null) {
										oos.writeInt(Command.LOAD_TESTS_RES.ordinal());
										DistributedAcceptanceContext.ser(oos, entry);
										oos.flush();
									}
								}
							}
						}
					});
					if(isLoadTestingEnabled) {
					    dlreporter.start();  
					}
					
					context.getConfig().setTestCasesBasePath(workingDir);
					context.getConfig().setOutFilesBasePath(workingDir);
					logger.info("Current working directory is: " + workingDir);
					
					GatfExecutorConfig localConfig = getConfig("gatf-config.xml", context.getConfig().getTestCasesBasePath());
					if(localConfig!=null) {
					    if(localConfig.getJavaHome()!=null) {
					        context.getConfig().setJavaHome(localConfig.getJavaHome());
					    }
                        if(localConfig.getTestCasesBasePath()!=null) {
                            if(new File(localConfig.getTestCasesBasePath()).exists()) {
                                context.getConfig().setTestCasesBasePath(localConfig.getTestCasesBasePath());
                            }
                        }
					}
					
					String[] remoteJavaVersion = context.getConfig().getJavaVersion().split("\\.");
					String[] localJavaVersion = System.getProperty("java.version").split("\\.");
					if(remoteJavaVersion[0].equals(localJavaVersion[0])) {
					    if(remoteJavaVersion[1].compareTo(localJavaVersion[1])>0) {
					        logger.severe("Local java version is less than the remote java version, please upgrade local java to version >= " + context.getConfig().getJavaVersion());
					        throw new RuntimeException("Invalid java version found");
					    }
					} else if(remoteJavaVersion[0].compareTo(localJavaVersion[0])>0) {
					    logger.severe("Local java version is less than the remote java version, please upgrade local java to version >= " + context.getConfig().getJavaVersion());
					    throw new RuntimeException("Invalid java version found");
					}
					
					DistributedTestStatus report = mojo.handleDistributedTests(context, tContext);
					mojo.shutdown();
					Thread.sleep(2000);
					
					if(isLoadTestingEnabled) {
                        dlreporter.interrupt();
                        Thread.sleep(3000);
                    }
					
					oos.writeInt(Command.TESTS_SHARE_RES.ordinal());
					oos.flush();
					
					String fileName = UUID.randomUUID().toString()+".zip";
					report.setZipFileName(fileName);
					
					DistributedAcceptanceContext.ser(oos, report);
					oos.flush();
					logger.info("Writing GATF results...");
					
		        	File resource = new File(workingDir, context.getConfig().getOutFilesDir());
		        	
					ReportHandler.zipDirectory(resource, new String[]{".html",".csv"}, fileName, false, true);
					
					File zipFile = new File(resource, fileName);
					FileInputStream fis = new FileInputStream(zipFile);
					IOUtils.copy(fis, oos);
					fis.close();
					oos.flush();
					logger.info("Done Writing GATF results...");
				} else {
					oos.writeInt(Command.INVALID.ordinal());
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
	        	long length = ois.readLong();
	        	for (int i=0;i<length;i++) {
	        		fos.write(ois.read());
				}
				fos.flush();
				fos.close();
				
				ReportHandler.unzipZipFile(zipFile, gcdir.getAbsolutePath());
				zipFile.delete();
				
				gcdir = new File(gcdir, "gatf-code");
				
				boolean isLoadTestingEnabled = context.getConfig().isLoadTestingEnabled() && context.getConfig().getLoadTestingTime()>10000;
				
				final DistributedAcceptanceContext context1 = context;
				Thread dlreporter = new Thread(new Runnable() {
                    public void run() {
                        try {
                            while(true) {
                                Object entry = RuntimeReportUtil.getDLEntry();
                                if(entry!=null) {
                                    if(entry instanceof String) {
                                        String sentry = context1.getNode() + "|" + entry;
                                        entry = sentry;
                                    }
                                    oos.writeInt(Command.LOAD_TESTS_RES.ordinal());
                                    DistributedAcceptanceContext.ser(oos, entry);
                                    oos.flush();
                                }
                                Thread.sleep(500);
                            }
                        } catch (Exception e) {
                            Object entry = null;
                            while((entry=RuntimeReportUtil.getDLEntry())!=null)
                            {
                                if(entry!=null) {
                                    if(entry instanceof String) {
                                        String sentry = context1.getNode() + "|" + entry;
                                        entry = sentry;
                                    }
                                    oos.writeInt(Command.LOAD_TESTS_RES.ordinal());
                                    DistributedAcceptanceContext.ser(oos, entry);
                                    oos.flush();
                                }
                            }
                        }
                    }
                });
				if(isLoadTestingEnabled) {
				    dlreporter.start();
				}
				
				context.getConfig().setTestCasesBasePath(workingDir);
                context.getConfig().setOutFilesBasePath(workingDir);
                logger.info("Current working directory is: " + workingDir);
                
                GatfExecutorConfig localConfig = getConfig("gatf-config.xml", context.getConfig().getTestCasesBasePath());
                if(localConfig!=null) {
                    if(localConfig.getJavaHome()!=null) {
                        context.getConfig().setJavaHome(localConfig.getJavaHome());
                    }
                    if(localConfig.getSeleniumDriverConfigs()!=null && localConfig.getSeleniumDriverConfigs().length>0) {
                        SeleniumDriverConfig[] lconfigs = localConfig.getSeleniumDriverConfigs();
                        SeleniumDriverConfig[] rconfigs = context.getConfig().getSeleniumDriverConfigs();
                        for (int i=0;i<rconfigs.length;i++) {
                            SeleniumDriverConfig rseleniumDriverConfig = rconfigs[i];
                            for (SeleniumDriverConfig lseleniumDriverConfig : lconfigs) {
                                if(rseleniumDriverConfig.getDriverName().equals(lseleniumDriverConfig.getDriverName()) && lseleniumDriverConfig.getPath()!=null) {
                                    rconfigs[i] = lseleniumDriverConfig;
                                    break;
                                }
                            }
                        }
                        context.getConfig().setSeleniumDriverConfigs(rconfigs);
                        context.getConfig().setJavaHome(localConfig.getJavaHome());
                        if(localConfig.getTestCasesBasePath()!=null) {
                            if(new File(localConfig.getTestCasesBasePath()).exists()) {
                                context.getConfig().setTestCasesBasePath(localConfig.getTestCasesBasePath());
                            }
                        }
                    }
                }
                
                String[] remoteJavaVersion = context.getConfig().getJavaVersion().split("\\.");
                String[] localJavaVersion = System.getProperty("java.version").split("\\.");
                if(remoteJavaVersion[0].equals(localJavaVersion[0])) {
                    if(remoteJavaVersion[1].compareTo(localJavaVersion[1])>0) {
                        logger.severe("Local java version is less than the remote java version, please upgrade local java to version >= " + context.getConfig().getJavaVersion());
                        throw new RuntimeException("Invalid java version found");
                    }
                } else if(remoteJavaVersion[0].compareTo(localJavaVersion[0])>0) {
                    logger.severe("Local java version is less than the remote java version, please upgrade local java to version >= " + context.getConfig().getJavaVersion());
                    throw new RuntimeException("Invalid java version found");
                }
				
                System.out.println(gcdir.getAbsolutePath());
				URL[] urls = new URL[1];
	            urls[0] = gcdir.toURI().toURL();
				URLClassLoader classLoader = new URLClassLoader(urls, DistributedGatfListener.class.getClassLoader());
				Thread.currentThread().setContextClassLoader(classLoader);
				
				List<Class<SeleniumTest>> tests = new ArrayList<Class<SeleniumTest>>();
				List<String> testClassNames = DistributedAcceptanceContext.unser(ois);
				for (String clsname : testClassNames) {
					@SuppressWarnings("unchecked")
					Class<SeleniumTest> loadedClass = (Class<SeleniumTest>)classLoader.loadClass(clsname);
					tests.add(loadedClass);
				}
				
				tContext = DistributedAcceptanceContext.unser(ois);
				
				oos.writeInt(Command.SELENIUM_RES.ordinal());
				oos.flush();
				
				if(tests==null || tests.size()==0 || context.getConfig().isValidSeleniumRequest()) {
					boolean driverfound = true;
					for (SeleniumDriverConfig selConf : context.getConfig().getSeleniumDriverConfigs())
		            {
					    if(!new File(selConf.getPath()).exists()) {
	                        Path p = Paths.get(selConf.getPath());
	                        File df = context.getResourceFile(p.getFileName().toString());
	                        driverfound &= df.exists();
	                        if(df.exists()) {
	                            selConf.setPath(df.getAbsolutePath());
	                            System.setProperty(selConf.getName(), df.getAbsolutePath());
	                        }
	                    } else {
	                        driverfound &= true;
	                        System.setProperty(selConf.getName(), selConf.getPath());
	                    }
		            }
	                    
                    if(driverfound) {
                        oos.writeInt(0);
                        oos.flush();
                        logger.info("Selenium Test Request");
                        
                        GatfTestCaseExecutorUtil mojo = new GatfTestCaseExecutorUtil();
                        DistributedTestStatus report = mojo.handleDistributedSeleniumTests(context, tests, tContext);
                        mojo.shutdown();
                        Thread.sleep(2000);
                        
                        if(isLoadTestingEnabled) {
                            dlreporter.interrupt();
                            Thread.sleep(3000);
                        }
                        
                        oos.writeInt(Command.TESTS_SHARE_RES.ordinal());
                        oos.flush();
                        
                        fileName = UUID.randomUUID().toString()+".zip";
                        report.setZipFileName(fileName);
                        
                        DistributedAcceptanceContext.ser(oos, report);
                        oos.flush();
                        logger.info("Writing Selenium results...");
                        
                        File resource = new File(workingDir, context.getConfig().getOutFilesDir());
                        
                        String runPrefix = "DRun-" + tContext.getIndex();
                        
                        ReportHandler.zipDirectory(resource, new String[]{runPrefix}, fileName, false, false);
                        
                        zipFile = new File(resource, fileName);
                        FileInputStream fis = new FileInputStream(zipFile);
                        IOUtils.copy(fis, oos);
                        fis.close();
                        oos.flush();
                        logger.info("Done Writing Selenium results...");
                    } else {
                        oos.writeInt(1);
                        oos.flush();
                        logger.info("Selenium Test Request");
		            }
					
				} else {
					oos.writeInt(2);
					oos.flush();
					logger.info("Selenium Test Request");
				}
			} else {
				oos.writeInt(Command.INVALID.ordinal());
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
		} catch (Exception e) {
			oos.write(0);
			oos.flush();
			logger.info("Error occurred during distributed GATF execution...");
			throw e;
		}
	}
	
	private static GatfExecutorConfig getConfig(String configFile, String testCasesBasePath) {
        GatfExecutorConfig configuration = null;
        if(configFile!=null) {
            try {
                File resource = null;
                File basePath = new File(testCasesBasePath);
                resource = new File(basePath, configFile);
                if(resource.exists()) {
                	if(configFile.trim().endsWith(".xml")) {
	                    XStream xstream = new XStream(new DomDriver("UTF-8"));
	                   
	                    xstream.allowTypes(new Class[]{GatfExecutorConfig.class,
	                            GatfTestDataConfig.class, GatfTestDataProvider.class});
	                    xstream.processAnnotations(new Class[]{GatfExecutorConfig.class,
	                             GatfTestDataConfig.class, GatfTestDataProvider.class});
	                    xstream.alias("gatf-testdata-source", GatfTestDataSource.class);
	                    xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
	                    xstream.alias("gatf-testdata-source-hook", GatfTestDataSourceHook.class);
	                    xstream.alias("gatfTestDataConfig", GatfTestDataConfig.class);
	                    xstream.alias("seleniumDriverConfigs", SeleniumDriverConfig[].class);
	                    xstream.alias("seleniumDriverConfig", SeleniumDriverConfig.class);
	                    xstream.alias("testCaseHooksPaths", String[].class);
	                    xstream.alias("testCaseHooksPath", String.class);
	                    xstream.alias("args", String[].class);
	                    xstream.alias("arg", String.class);
	                    xstream.alias("testCaseHooksPaths", String[].class);
	                    xstream.alias("testCaseHooksPath", String.class);
	                    xstream.alias("queryStrs", String[].class);
	                    xstream.alias("queryStr", String.class);
	                    xstream.alias("distributedNodes", String[].class);
	                    xstream.alias("distributedNode", String.class);
	                    xstream.alias("ignoreFiles", String[].class);
	                    xstream.alias("orderedFiles", String[].class);
	                    xstream.alias("string", String.class);
	                    xstream.alias("seleniumScripts", String[].class);
	                    xstream.alias("seleniumScript", String.class);
	                    
	                    configuration = (GatfExecutorConfig)xstream.fromXML(resource);
                	} else if(configFile.trim().endsWith(".json")) {
                		configuration = new ObjectMapper().readValue(resource, GatfExecutorConfig.class);
                	} else {
                		throw new RuntimeException("Invalid Config file, please provide either an xml or a json config file");
                	}
                	
                    if(configuration.getTestCasesBasePath()==null)
                        configuration.setTestCasesBasePath(testCasesBasePath);
                    
                    if(configuration.getOutFilesBasePath()==null)
                        configuration.setOutFilesBasePath(testCasesBasePath);
                    
                    if(configuration.getTestCaseDir()==null)
                        configuration.setTestCaseDir("");
                    
                    if(configuration.getNumConcurrentExecutions()==null)
                        configuration.setNumConcurrentExecutions(1);
                    
                    if(configuration.getHttpConnectionTimeout()==null)
                        configuration.setHttpConnectionTimeout(10000);
                    
                    if(configuration.getHttpRequestTimeout()==null)
                        configuration.setHttpRequestTimeout(10000);
                    
                    if(!configuration.isHttpCompressionEnabled())
                        configuration.setHttpCompressionEnabled(true);
                    
                    if(configuration.getConcurrentUserSimulationNum()==null)
                        configuration.setConcurrentUserSimulationNum(1);
                    
                    if(configuration.getLoadTestingReportSamples()==null)
                        configuration.setLoadTestingReportSamples(1);
                    
                    if(configuration.getConcurrentUserRampUpTime()==null)
                        configuration.setConcurrentUserRampUpTime(10000L);
                    
                    if(configuration.isEnabled()==null)
                        configuration.setEnabled(true);
                    
                    if(configuration.getRepeatSuiteExecutionNum()==null)
                        configuration.setRepeatSuiteExecutionNum(0);
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return configuration;
    }
}
