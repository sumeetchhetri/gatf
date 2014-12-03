package com.gatf.executor.report;

/*
Copyright 2013-2014, Sumeet Chhetri

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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

/**
 * @author Sumeet Chhetri
 * Provides final statistics for the overall test case/group executions
 */
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestSuiteStats implements Serializable {

	private Integer totalTestCount = 0;
	
	private Integer failedTestCount = 0;
	
	private Integer skippedTestCount = 0;
	
	private Long executionTime = 0L;
	
	private Long actualExecutionTime = 0L;
	
	private Integer totalRuns = 0;
	
	private Integer failedRuns = 0;
	
	private Integer totalSuiteRuns = 0;
	
	private Integer totalUserSuiteRuns = 0;
	
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

	public Long getActualExecutionTime() {
		return actualExecutionTime;
	}

	public void setActualExecutionTime(Long actualExecutionTime) {
		this.actualExecutionTime = actualExecutionTime;
	}

	public Integer getTotalRuns() {
		return totalRuns;
	}

	public void setTotalRuns(Integer totalRuns) {
		this.totalRuns = totalRuns;
	}

	public Integer getFailedRuns() {
		return failedRuns;
	}

	public void setFailedRuns(Integer failedRuns) {
		this.failedRuns = failedRuns;
	}
	
	public Integer getTotalSuiteRuns() {
		return totalSuiteRuns;
	}

	public void setTotalSuiteRuns(Integer totalSuiteRuns) {
		this.totalSuiteRuns = totalSuiteRuns;
	}

	public Integer getSkippedTestCount() {
		return skippedTestCount;
	}

	public void setSkippedTestCount(Integer skippedTestCount) {
		this.skippedTestCount = skippedTestCount;
	}

	public Integer getTotalUserSuiteRuns() {
		return totalUserSuiteRuns;
	}

	public void setTotalUserSuiteRuns(Integer totalUserSuiteRuns) {
		this.totalUserSuiteRuns = totalUserSuiteRuns;
	}

	public void updateStats(TestSuiteStats stats, boolean isUserRuns) {
		setActualExecutionTime(getActualExecutionTime() + stats.getActualExecutionTime());
		setFailedRuns(getFailedRuns() +  stats.getFailedRuns());
		setFailedTestCount(getFailedTestCount() + stats.getFailedTestCount());
		setTotalRuns(getTotalRuns() + stats.getTotalRuns());
		setTotalTestCount(getTotalTestCount() + stats.getTotalTestCount());
		if(!isUserRuns)
			setTotalSuiteRuns(getTotalSuiteRuns() + stats.getTotalSuiteRuns());
		setSkippedTestCount(getSkippedTestCount() + stats.getSkippedTestCount());
		setExecutionTime(getExecutionTime() + stats.getExecutionTime());
	}
	
	public void copy(TestSuiteStats stats) {
		totalTestCount = stats.getTotalTestCount();
		failedTestCount = stats.getFailedTestCount();
		skippedTestCount = stats.getSkippedTestCount();
		executionTime = stats.getExecutionTime();
		actualExecutionTime = stats.getActualExecutionTime();
		totalRuns = stats.getTotalRuns();
		failedRuns = stats.getFailedRuns();
		totalSuiteRuns = stats.getTotalSuiteRuns();
		totalUserSuiteRuns = stats.getTotalUserSuiteRuns();
		groupStats = stats.getGroupStats();
	}

	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("TestSuiteStats [totalTestCount=");
		builder.append(totalTestCount);
		builder.append(", TotalUserSuiteRuns=");
		builder.append(totalUserSuiteRuns);
		builder.append(", failedTestCount=");
		builder.append(failedTestCount);
		builder.append(", skippedTestCount=");
		builder.append(skippedTestCount);
		builder.append(", executionTime=");
		builder.append(executionTime);
		builder.append(", totalRuns=");
		builder.append(totalRuns);
		builder.append(", failedRuns=");
		builder.append(failedRuns);
		builder.append(", groupStats=");
		builder.append(groupStats != null ? groupStats.subList(0,
				Math.min(groupStats.size(), maxLen)) : null);
		builder.append("]");
		return builder.toString();
	}
	
	public String show() {
		StringBuilder builder = new StringBuilder();
		builder.append("Result:\nTotalSuiteRuns=");
		builder.append(totalSuiteRuns);
		builder.append(", TotalUserSuiteRuns=");
		builder.append(totalUserSuiteRuns);
		builder.append(", TotalTestCount=");
		builder.append(totalTestCount);
		builder.append(", FailedTestCount=");
		builder.append(failedTestCount);
		builder.append(", SkippedTestCount=");
		builder.append(skippedTestCount);
		builder.append(", ExecutionTime=");
		builder.append(executionTime);
		builder.append(", TotalRuns=");
		builder.append(totalRuns);
		builder.append(", FailedRuns=");
		builder.append(failedRuns);
		builder.append("\n");
		return builder.toString();
	}
}
