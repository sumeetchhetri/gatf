package com.gatf.distributed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gatf.executor.report.LoadTestResource;
import com.gatf.executor.report.TestSuiteStats;

public class DistributedTestStatus implements Serializable {

	private String node;
	
	private String identifier;
	
	private String zipFileName;
	
	private Map<String, List<Long>> percentileTimes;
	
	private TestSuiteStats suiteStats; 
	
	public String getNode() {
		return node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public TestSuiteStats getSuiteStats() {
		return suiteStats;
	}

	public void setSuiteStats(TestSuiteStats suiteStats) {
		this.suiteStats = suiteStats;
	}

	public String getZipFileName() {
		return zipFileName;
	}

	public void setZipFileName(String zipFileName) {
		this.zipFileName = zipFileName;
	}

	public Map<String, List<Long>> getPercentileTimes() {
		return percentileTimes;
	}

	public void setPercentileTimes(Map<String, List<Long>> percentileTimes) {
		this.percentileTimes = percentileTimes;
	}
}
