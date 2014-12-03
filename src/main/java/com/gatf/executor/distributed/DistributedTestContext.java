package com.gatf.executor.distributed;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.gatf.executor.core.TestCase;

public class DistributedTestContext implements Serializable {

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
