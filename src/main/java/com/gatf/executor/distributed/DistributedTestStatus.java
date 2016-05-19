/*
    Copyright 2013-2016, Sumeet Chhetri
    
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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.gatf.executor.report.TestSuiteStats;

public class DistributedTestStatus implements Serializable {

	private static final long serialVersionUID = 1L;
	
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
