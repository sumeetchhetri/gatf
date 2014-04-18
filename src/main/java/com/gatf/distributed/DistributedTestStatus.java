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
	
	private Map<String, String> reportFileContent = new HashMap<String, String>();
	
	private TestSuiteStats suiteStats; 
	
	private List<LoadTestResource> loadTestResources = new ArrayList<LoadTestResource>();
	
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

	public Map<String, String> getReportFileContent() {
		return reportFileContent;
	}

	public void setReportFileContent(Map<String, String> reportFileContent) {
		this.reportFileContent = reportFileContent;
	}

	public TestSuiteStats getSuiteStats() {
		return suiteStats;
	}

	public void setSuiteStats(TestSuiteStats suiteStats) {
		this.suiteStats = suiteStats;
	}

	public List<LoadTestResource> getLoadTestResources() {
		return loadTestResources;
	}

	public void setLoadTestResources(List<LoadTestResource> loadTestResources) {
		this.loadTestResources = loadTestResources;
	}
}
