package com.gatf.executor.report;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * @author Sumeet Chhetri
 *
 */
public class RuntimeReportUtil {
	
	public static class LoadTestEntry implements Serializable
	{
		String node;
		String prefix;
		int runNo;
		String url;
		TestSuiteStats currStats;
		
		public LoadTestEntry(String node, String prefix, int runNo, String url, TestSuiteStats currStats) {
			super();
			this.node = node;
			this.prefix = prefix;
			this.runNo = runNo;
			this.url = url;
			this.currStats = currStats;
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
	}
	
	private static volatile boolean registered = false;
	
	private static TestSuiteStats gloadStats = new TestSuiteStats();
	
	private static ConcurrentLinkedQueue<String> Q = new ConcurrentLinkedQueue<String>();
	
	private static ConcurrentLinkedQueue<LoadTestEntry> Qdl = new ConcurrentLinkedQueue<LoadTestEntry>();
	
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
	
	public static void addEntry(LoadTestEntry lentry)
	{
		if(registered)
		{
			Map<String, Object> parts = new HashMap<String, Object>();
			if(lentry.prefix==null)
				lentry.prefix = "Run";
			parts.put("title", lentry.prefix+"-"+lentry.runNo);
			parts.put("runNo", lentry.runNo+"");
			parts.put("url", lentry.url);
			parts.put("node", lentry.node);
			parts.put("stats", lentry.currStats);
			synchronized (gloadStats) {
				gloadStats.updateStats(lentry.currStats, false);
				parts.put("tstats", gloadStats);
				parts.put("error", "Execution already in progress..");
				try {
					Q.add(new ObjectMapper().writeValueAsString(parts));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			synchronized (gloadStats) {
				gloadStats.updateStats(lentry.currStats, false);
				System.out.println(gloadStats);
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
					Q.add(new ObjectMapper().writeValueAsString(parts));
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
	
	public static String getEntry()
	{
		return Q.poll();
	}
	
	public static LoadTestEntry getDLEntry()
	{
		return Qdl.poll();
	}
}
