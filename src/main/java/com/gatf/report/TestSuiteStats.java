package com.gatf.report;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestSuiteStats {

	private Integer totalTestCount;
	
	private Integer failedTestCount;
	
	private Long executionTime;
	
	private Integer totalRuns;
	
	private List<TestGroupStats> groupStats = new ArrayList<TestGroupStats>();

	public Integer getTotalTestCount() {
		return totalTestCount;
	}

	public void setTotalTestCount(Integer totalTestCount) {
		this.totalTestCount = totalTestCount;
	}

	public Integer getFailedTestCount() {
		return failedTestCount;
	}

	public void setFailedTestCount(Integer failedTestCount) {
		this.failedTestCount = failedTestCount;
	}

	public List<TestGroupStats> getGroupStats() {
		return groupStats;
	}

	public void setGroupStats(List<TestGroupStats> groupStats) {
		this.groupStats = groupStats;
	}

	public Long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(Long executionTime) {
		this.executionTime = executionTime;
	}

	public Integer getTotalRuns() {
		return totalRuns;
	}

	public void setTotalRuns(Integer totalRuns) {
		this.totalRuns = totalRuns;
	}
}
