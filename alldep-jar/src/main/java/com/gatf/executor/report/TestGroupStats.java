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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * @author Sumeet Chhetri
 * Provides statistics for a group of testcases found in a file(run)
 */
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class TestGroupStats implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String sourceFile;
	
	private Long executionTime;
	
	private Integer totalTestCount;
	
	private Integer failedTestCount;
	
	private Integer skippedTestCount;

	private Integer totalRuns;
	
	private Integer failedRuns;
	
	private String baseUrl;
	
	public String getSourceFile() {
		return sourceFile;
	}

	public void setSourceFile(String sourceFile) {
		this.sourceFile = sourceFile;
	}

	public Long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(Long executionTime) {
		this.executionTime = executionTime;
	}

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

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public Integer getSkippedTestCount() {
		return skippedTestCount;
	}

	public void setSkippedTestCount(Integer skippedTestCount) {
		this.skippedTestCount = skippedTestCount;
	}
}
