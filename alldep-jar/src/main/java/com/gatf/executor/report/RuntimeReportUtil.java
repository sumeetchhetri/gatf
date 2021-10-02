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
package com.gatf.executor.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Sumeet Chhetri
 *
 */
public class RuntimeReportUtil {
    
	public static class LoadTestEntry implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		String node;
		String prefix;
		int runNo;
		String url;
		TestSuiteStats currStats;
		Map<String, Map<String, List<Object[]>>> currSelStats;
		
		public LoadTestEntry(String node, String prefix, int runNo, String url, TestSuiteStats currStats) {
			super();
			this.node = node;
			this.prefix = prefix;
			this.runNo = runNo;
			this.url = url;
			this.currStats = currStats;
		}
        
        public LoadTestEntry(String node, String prefix, int runNo, String url, Map<String, Map<String, List<Object[]>>> currSelStats) {
            super();
            this.node = node;
            this.prefix = prefix;
            this.runNo = runNo;
            this.url = url;
            this.currSelStats = currSelStats;
        }
		
		public String getNode() {
			return node;
		}
		public String getPrefix() {
			return prefix;
		}
		public int getRunNo() {
			return runNo;
		}
		public String getUrl() {
			return url;
		}
		public TestSuiteStats getCurrStats() {
			return currStats;
		}
        public Map<String, Map<String, List<Object[]>>> getCurrSelStats() {
            return currSelStats;
        }
	}
	
	private static volatile boolean registered = false;
	
	private static TestSuiteStats gloadStats = new TestSuiteStats();
	
	private static ConcurrentLinkedQueue<Object> Q = new ConcurrentLinkedQueue<Object>();
	
	private static ConcurrentLinkedQueue<Object> Qdl = new ConcurrentLinkedQueue<Object>();
	
	public static void registerConfigUI()
	{
		registered = true;
	}
	
	public static void unRegisterConfigUI()
	{
		registered = false;
		Q.clear();
		Qdl.clear();
		synchronized (gloadStats) {
			gloadStats = new TestSuiteStats();
		}
	}
	
	public static void addLEntry(LoadTestEntry lentry)
	{
		Qdl.add(lentry);
	}
	
	public static void addEntry(int runNo, boolean subtestPassed)
    {
	    if(registered)
        {
	        try {
                Q.add("local|"+runNo+"|"+(subtestPassed?1:0));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	    else
	    {
	        try {
	            Qdl.add(runNo+"|"+(subtestPassed?1:0));
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
    }
	
	public static void addEntry(Object entry)
	{
		if(registered)
		{
		    if(entry instanceof LoadTestEntry) {
		        LoadTestEntry lentry = (LoadTestEntry)entry;
		        Map<String, Object> parts = new HashMap<String, Object>();
	            if(lentry.prefix==null)
	                lentry.prefix = "Run";
	            parts.put("title", lentry.prefix+"-"+lentry.runNo);
	            parts.put("runNo", lentry.runNo+"");
	            parts.put("url", lentry.url);
	            parts.put("node", lentry.node);
	            parts.put("stats", lentry.currStats);
	            parts.put("selStats", lentry.currSelStats);
	            if(lentry.currStats!=null) {
	                synchronized (gloadStats) {
	                    gloadStats.updateStats(lentry.currStats, false);
	                    parts.put("tstats", gloadStats);
	                    parts.put("error", "Execution already in progress..");
	                    try {
	                        Q.add(parts);
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
	                }
	            }
		    } else {
		        try {
                    Q.add(entry);
                } catch (Exception e) {
                    e.printStackTrace();
                }
		    }
		}
		else
		{
		    if(entry instanceof LoadTestEntry) {
                LoadTestEntry lentry = (LoadTestEntry)entry;
    		    if(lentry.currStats!=null) {
                    synchronized (gloadStats) {
        				gloadStats.updateStats(lentry.currStats, false);
        				System.out.println(gloadStats);
        			}
    		    }
		    }
		}
	}
	
	public static void addEntry(String node, String prefix, int runNo, String url, TestSuiteStats currStats)
	{
		if(registered)
		{
			Map<String, Object> parts = new HashMap<String, Object>();
			if(prefix==null)
				prefix = "Run";
			parts.put("title", prefix+"-"+runNo);
			parts.put("runNo", runNo+"");
			parts.put("url", url);
			parts.put("node", node);
			parts.put("stats", currStats);
			synchronized (gloadStats) {
				gloadStats.updateStats(currStats, false);
				parts.put("tstats", gloadStats);
				parts.put("error", "Execution already in progress..");
				try {
					Q.add(parts);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			synchronized (gloadStats) {
				gloadStats.updateStats(currStats, false);
				System.out.println(gloadStats);
			}
		}
	}
	
	public static boolean isDone() {
	    return Q.isEmpty();
	}
	
	@SuppressWarnings("unchecked")
    public static String getEntry()
	{
	    try {
    	    List<Map<String, Object>> st = new ArrayList<Map<String, Object>>();
    	    List<String> sst = new ArrayList<String>();
    	    Object parts = null;
    	    while((parts = Q.poll())!=null) {
    	        if(parts instanceof String) {
    	            sst.add((String)parts);
    	        } else {
    	            st.add((Map<String, Object>)parts);
    	        }
    	        if(st.size()>100 || sst.size()>100)break;
    	    }
            String arr = "{\"error\": \"Execution already in progress..\", \"lstats\": " + 
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(st) + ", \"sstats\": " + 
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sst) + "}";
            return arr;
	    } catch (Exception e) {
            e.printStackTrace();
        }
	    return "";
	}
	
	public static Object getDLEntry()
	{
		return Qdl.poll();
	}
}
