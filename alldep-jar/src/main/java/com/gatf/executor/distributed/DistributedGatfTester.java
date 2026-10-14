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
package com.gatf.executor.distributed;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.RuntimeReportUtil;

public class DistributedGatfTester {

	private static final Logger logger = LogManager.getLogger(DistributedGatfTester.class.getSimpleName());
	
	public static class DistributedConnection {
		private Socket sock;
		private String node;
		private Output oos = null;
		private Input ois = null;
		/** H8 (Δ162): the protocol version the node accepted; the node as configured (host[@port]). */
		private int protocol = DistributedTransport.PROTOCOL_VERSION;
		private String id;
		
		public String toString()
		{
			return node;
		}
	}
	
	/** H8 (Δ162): nodes already reported as refusing protocol v2 (logged once per node). */
	private static final Set<String> LEGACY_NODES = ConcurrentHashMap.newKeySet();
	
	public DistributedConnection distributeContext(String node, AcceptanceTestContext context, List<Object[]> testdata)
	{
		// H8 (Δ162): speak protocol v2 (trace frames). A node that drops the v2 handshake (an older GATF listener) gets
		// one retry in v1, which works as before, without traces; logged once per node.
		boolean[] connected = new boolean[1];
		DistributedConnection conn = distributeContext(node, context, testdata, DistributedTransport.PROTOCOL_VERSION, connected);
		if(conn==null && connected[0]) {
			if(LEGACY_NODES.add(node)) {
				logger.warn("Node " + node + " refused distributed protocol v" + DistributedTransport.PROTOCOL_VERSION
						+ " (an older GATF listener, or a shared-secret mismatch); retrying with v" + DistributedTransport.MIN_PROTOCOL_VERSION
						+ ": no distributed traces from this node");
			}
			connected[0] = false;
			conn = distributeContext(node, context, testdata, DistributedTransport.MIN_PROTOCOL_VERSION, connected);
		}
		return conn;
	}
	
	private DistributedConnection distributeContext(String node, AcceptanceTestContext context, List<Object[]> testdata, int protocol, boolean[] connected)
	{
		Socket client = null;
		DistributedConnection conn = null;
		final String nodeId = node;
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
			
			client = DistributedTransport.clientSocket(node, port);
			connected[0] = true;
			DistributedAcceptanceContext disContext = context.getDistributedContext(node, testdata);
			
			Output oos = new Output(client.getOutputStream());
			Input ois = new Input(client.getInputStream());
			DistributedTransport.clientHandshake(oos, protocol);
			
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
				conn.protocol = protocol;
				conn.id = nodeId;
				logger.info("Sending GATF configuration Successful to node " + node);
				// NOTE: do not close ois here — this Input is stored in conn.ois and reused by
				// distributeTests(); it is closed with the socket in the result task's finally.
			} else {
				logger.info("Sending GATF configuration Failed to node " + node);
			}
		} catch (Exception e) {
			if(!(connected[0] && protocol>DistributedTransport.MIN_PROTOCOL_VERSION)) {   // H8: a refused v2 handshake is retried in v1, quietly
				e.printStackTrace();
				logger.info("Sending GATF configuration Failed to node " + node);
			}
			if(client!=null)
			{
				try {
					client.close();
				} catch (IOException e1) {
					//e1.printStackTrace();
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
								Object lentry = DistributedAcceptanceContext.unser(connection.ois, RuntimeReportUtil.LoadTestEntry.class);
								RuntimeReportUtil.addEntry(lentry);
								command = Command.values()[connection.ois.readInt()];;
							}
							
							res = DistributedAcceptanceContext.unser(connection.ois, DistributedTestStatus.class);
							logger.info("Received GATF tests Results from node " + connection.node);
							
							String fileName = "dist-" + DistributedTraces.token(res.getZipFileName(), 80);   // H8 (Δ162): a node-supplied name, never a path
							File basePath = null;
				        	if(context.getGatfExecutorConfig().getOutFilesBasePath()!=null)
				        		basePath = new File(context.getGatfExecutorConfig().getOutFilesBasePath());
				        	else
				        	{
				        		basePath = new File(System.getProperty("user.dir"));
				        	}
				        	File resource = new File(basePath, context.getGatfExecutorConfig().getOutFilesDir());
				        	
				        	File zipFile = new File(resource, fileName);
				        	// Δ145d: size-capped, and only the node's top-level .html/.csv reports are extracted (was zip4j extractAll)
				        	ResultsZipGuard.receive(connection.ois, zipFile, resource, ResultsZipGuard.apiReports(), connection.node, logger::info);
							
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
            // H8 (Δ162): traces of every node of this run land under one run id; the node honours our size cap
            final String traceRun = DistributedTraces.runIdFor(testClassesZip);
            if(connection.protocol>=DistributedTraces.TRACE_PROTOCOL) {
                testContext.setTraceMaxBytes(DistributedTraces.maxBytes());
            }
            
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
	                                Object lentry = DistributedAcceptanceContext.unser(connection.ois, RuntimeReportUtil.LoadTestEntry.class);
	                                RuntimeReportUtil.addEntry(lentry);
	                                command = Command.values()[connection.ois.readInt()];
	                            }
	                            
	                            res = DistributedAcceptanceContext.unser(connection.ois, DistributedTestStatus.class);
	                            logger.info("Received GATF Selenium tests Results from node " + connection.node);
	                            
	                            String fileName = "dist-" + DistributedTraces.token(res.getZipFileName(), 80);   // H8 (Δ162): a node-supplied name, never a path
	                            File basePath = null;
	                            if(context.getGatfExecutorConfig().getOutFilesBasePath()!=null)
	                                basePath = new File(context.getGatfExecutorConfig().getOutFilesBasePath());
	                            else
	                            {
	                                basePath = new File(System.getProperty("user.dir"));
	                            }
	                            File resource = new File(basePath, context.getGatfExecutorConfig().getOutFilesDir());
	                            
	                            // H8 (Δ162): the node's trace frames come before its results zip; stored and verified here,
	                            // re-checked and linked once the results zip is extracted into the same dir
	                            String traceNode = connection.id!=null ? connection.id : connection.node;
	                            DistributedTraces.Received traces = null;
	                            if(connection.protocol>=DistributedTraces.TRACE_PROTOCOL) {
	                                traces = DistributedTraces.receive(connection.ois, res.getTraces(), resource, traceRun,
	                                        traceNode, DistributedTraces.maxBytes(), DistributedTraces.maxCount());
	                                logger.info(traces.summary(traceNode));
	                                if(!traces.inSync) {
	                                    DistributedTraces.publish(resource, traceRun, traces.entries);
	                                    logger.info("Skipping the result files of node " + traceNode + ": its trace stream was malformed");
	                                    return res;
	                                }
	                            }
	                            
	                            try {
	                            File zipFile = new File(resource, fileName);
	                            // Δ145d: size-capped, and only this node's top-level DRun-<index> files are extracted (was zip4j extractAll)
	                            ResultsZipGuard.receive(connection.ois, zipFile, resource, ResultsZipGuard.seleniumRun(index), traceNode, logger::info);
	                            } finally {
	                                if(traces!=null) {
	                                    DistributedTraces.reverify(resource, traces.entries);
	                                    if(!DistributedTraces.publish(resource, traceRun, traces.entries)) {
	                                        logger.info("Could not write the distributed trace manifest for node " + traceNode);
	                                    }
	                                }
	                            }
								
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
