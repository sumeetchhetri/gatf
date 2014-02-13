package com.gatf.report;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.test.TestCase;

@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestCaseReport {
	
	private String actualUrl;
	
	private String workflowName;

	private TestCase testCase;
	
	private String status;
	
	private Integer numberOfRuns;
	
	private Long executionTime;
	
	private List<Long> executionTimes = new ArrayList<Long>();
	
	private Long averageExecutionTime;
	
	private String requestContent;
	
	private String responseHeaders;
	
	private String responseContent;
	
	private String error;
	
	private String errorText;
	
	private Integer errorRunNumber;
	
	public Integer getErrorRunNumber() {
		return errorRunNumber;
	}

	public void setErrorRunNumber(Integer errorRunNumber) {
		this.errorRunNumber = errorRunNumber;
	}

	private String testIdentifier;

	public String getWorkflowName() {
		return workflowName;
	}

	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}

	public TestCase getTestCase() {
		return testCase;
	}

	public void setTestCase(TestCase testCase) {
		this.testCase = testCase;
		if(testCase!=null) {
			setTestIdentifier(testCase.getSourcefileName()+"\n"+testCase.getName());
			setActualUrl(testCase.getAurl());
			setRequestContent(testCase.getAcontent());
		}
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getNumberOfRuns() {
		return numberOfRuns;
	}

	public void setNumberOfRuns(Integer numberOfRuns) {
		this.numberOfRuns = numberOfRuns;
	}

	public Long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(Long executionTime) {
		this.executionTime = executionTime;
	}

	public List<Long> getExecutionTimes() {
		return executionTimes;
	}

	public void setExecutionTimes(List<Long> executionTimes) {
		this.executionTimes = executionTimes;
	}

	public String getRequestContent() {
		return requestContent;
	}

	public void setRequestContent(String requestContent) {
		this.requestContent = requestContent;
	}

	public String getResponseHeaders() {
		return responseHeaders;
	}

	public void setResponseHeaders(String responseHeaders) {
		this.responseHeaders = responseHeaders;
	}

	public String getResponseContent() {
		return responseContent;
	}

	public void setResponseContent(String responseContent) {
		this.responseContent = responseContent;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getActualUrl() {
		return actualUrl;
	}

	public void setActualUrl(String actualUrl) {
		this.actualUrl = actualUrl;
	}

	public String getErrorText() {
		return errorText;
	}

	public void setErrorText(String errorText) {
		this.errorText = errorText;
	}

	public String getTestIdentifier() {
		return testIdentifier;
	}

	public void setTestIdentifier(String testIdentifier) {
		this.testIdentifier = testIdentifier;
	}

	public Long getAverageExecutionTime() {
		return averageExecutionTime;
	}

	public void setAverageExecutionTime(Long averageExecutionTime) {
		this.averageExecutionTime = averageExecutionTime;
	}

	public TestCaseReport(){}
	
	public TestCaseReport(TestCaseReport other) {
		super();
		this.actualUrl = other.actualUrl;
		this.workflowName = other.workflowName;
		this.testCase = other.testCase;
		this.status = other.status;
		this.numberOfRuns = other.numberOfRuns;
		this.executionTime = other.executionTime;
		if(other.executionTimes!=null)
		{
			this.executionTimes = new ArrayList<Long>(other.executionTimes);
		}
		this.requestContent = other.requestContent;
		this.responseHeaders = other.responseHeaders;
		this.responseContent = other.responseContent;
		this.error = other.error;
		this.errorText = other.errorText;
		this.errorRunNumber = other.errorRunNumber;
		this.testIdentifier = other.testIdentifier;
	}
}
