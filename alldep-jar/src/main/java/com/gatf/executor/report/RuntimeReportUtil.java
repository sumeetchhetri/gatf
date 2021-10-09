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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fasterxml.jackson.databind.ObjectMapper;

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
		Date time;
		TestSuiteStats currStats;
		Map<String, Map<String, List<Object[]>>> currSelStats;
		
		public LoadTestEntry(String node, String prefix, int runNo, String url, TestSuiteStats currStats, Date time) {
			super();
			this.node = node;
			this.prefix = prefix;
			this.runNo = runNo;
			this.url = url;
			this.currStats = currStats;
			this.time = time;
		}
        
        public LoadTestEntry(String node, String prefix, int runNo, String url, Map<String, Map<String, List<Object[]>>> currSelStats) {
            super();
            this.node = node;
            this.prefix = prefix;
            this.runNo = runNo;
            this.url = url;
            this.currSelStats = currSelStats;
        }
        
        public LoadTestEntry() {
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
		public Date getTime() {
			return time;
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
		        List<Object> parts = new ArrayList<Object>();
	            if(lentry.prefix==null)
	                lentry.prefix = "Run";
	            parts.add(lentry.prefix);//
	            parts.add(lentry.runNo+"");
	            parts.add(lentry.url);
	            parts.add(lentry.node);
	            parts.add(lentry.time);
	            parts.add(lentry.currStats.toList());
	            parts.add(lentry.currSelStats);
	            if(lentry.currStats!=null) {
	                synchronized (gloadStats) {
	                    gloadStats.updateStats(lentry.currStats, false);
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
	
	public static void addEntry(String node, String prefix, int runNo, String url, TestSuiteStats currStats, Date time)
	{
		if(registered)
		{
			List<Object> parts = new ArrayList<Object>();
			if(prefix==null)
				prefix = "Run";
			parts.add(prefix);
			parts.add(runNo+"");
			parts.add(url);
			parts.add(node);
			parts.add(time.getTime());
			parts.add(currStats.toList());
			parts.add(null);
			synchronized (gloadStats) {
				gloadStats.updateStats(currStats, false);
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
    	    List<List<Object>> st = new ArrayList<List<Object>>();
    	    List<String> sst = new ArrayList<String>();
    	    Object parts = null;
    	    while((parts = Q.poll())!=null) {
    	        if(parts instanceof String) {
    	            sst.add((String)parts);
    	        } /*else if(parts instanceof Map) {
    	            st.add((Map<String, Object>)parts);
    	        }*/ else {
    	        	st.add((List<Object>)parts);
    	        }
    	        if(st.size()>=1000 || sst.size()>=1000)break;
    	    }
    	    String gstats = "";
    	    synchronized (gloadStats) {
    	    	gstats = ",\"gstats\":" + new ObjectMapper().writeValueAsString(gloadStats);
    	    }
            String arr = "{\"error\":\"Execution already in progress..\",\"lstats\":" + 
                    new ObjectMapper().writeValueAsString(st) + ",\"sstats\":" + 
                    new ObjectMapper().writeValueAsString(sst) + gstats + "}";
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
