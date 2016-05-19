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
import java.util.Set;

import com.gatf.executor.core.TestCase;

public class DistributedTestContext implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<TestCase> simTestCases;
	
	private Set<String> relativeFileNames;
	
	private boolean doReporting;
	
	private int index;
	
	private int numberOfRuns;

	public List<TestCase> getSimTestCases() {
		return simTestCases;
	}

	public void setSimTestCases(List<TestCase> simTestCases) {
		this.simTestCases = simTestCases;
	}

	public boolean isDoReporting() {
		return doReporting;
	}

	public void setDoReporting(boolean doReporting) {
		this.doReporting = doReporting;
	}
	
	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getNumberOfRuns() {
		return numberOfRuns;
	}

	public void setNumberOfRuns(int numberOfRuns) {
		this.numberOfRuns = numberOfRuns;
	}

	public Set<String> getRelativeFileNames() {
		return relativeFileNames;
	}

	public void setRelativeFileNames(Set<String> relativeFileNames) {
		this.relativeFileNames = relativeFileNames;
	}
}
