package com.gatf.executor.distributed;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.gatf.executor.report.TestSuiteStats;

public class DistributedTestStatus implements Serializable {

	private String node;
	
	private String identifier;
	
	private String zipFileName;
	
	private Map<String, List<Long>> testPercentileTimes;
	
	private Map<String, List<Long>> runPercentileTimes;
	
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

	public Map<String, List<Long>> getTestPercentileTimes() {
		return testPercentileTimes;
	}

	public void setTestPercentileTimes(Map<String, List<Long>> percentileTimes) {
		this.testPercentileTimes = percentileTimes;
	}

	public Map<String, List<Long>> getRunPercentileTimes() {
		return runPercentileTimes;
	}

	public void setRunPercentileTimes(Map<String, List<Long>> runPercentileTimes) {
		this.runPercentileTimes = runPercentileTimes;
	}
}
