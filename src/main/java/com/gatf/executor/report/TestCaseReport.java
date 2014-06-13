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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.executor.core.TestCase;


/**
 * @author Sumeet Chhetri
 * Represents the outcome of a test case execution, holds the request/response/error details
 * This data is used in generating graphical reports
 */
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class TestCaseReport implements Serializable {
	
	private String actualUrl;
	
	private String url;
	
	private String method;
	
	private String workflowName;

	private TestCase testCase;
	
	private String status;
	
	private String failureReason;
	
	private Integer numberOfRuns;
	
	private Long executionTime = 0L;
	
	private List<Long> executionTimes = new ArrayList<Long>();
	
	private Long averageExecutionTime;
	
	private String requestContent;
	
	private String requestHeaders;
	
	private String responseHeaders;
	
	private String requestContentType;
	
	private String responseContentType;
	
	private String responseContent;
	
	private Integer responseStatusCode;
	
	private String error;
	
	private String errorText;
	
	private Map<String, String> errors = new HashMap<String, String>();
	
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
			if(testCase.getIdentifier().equals(testCase.getSourcefileName()))
				setTestIdentifier(testCase.getIdentifier()+"\n"+testCase.getName());
			else
				setTestIdentifier(testCase.getIdentifier()+"\n"+testCase.getSourcefileName()+"\n"+testCase.getName());
			if(testCase.getAurl()!=null)
			{
				setActualUrl(testCase.getAurl());
			}
			else
			{
				setActualUrl(testCase.getUrl());
			}
			setRequestContent(testCase.getAcontent());
			setUrl(testCase.getUrl());
			setMethod(testCase.getMethod());
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

	public Map<String, String> getErrors() {
		return errors;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setErrors(Map<String, String> errors) {
		this.errors = errors;
	}

	public String getRequestHeaders() {
		return requestHeaders;
	}

	public void setRequestHeaders(String requestHeaders) {
		this.requestHeaders = requestHeaders;
	}

	public String getRequestContentType() {
		return requestContentType;
	}

	public void setRequestContentType(String requestContentType) {
		this.requestContentType = requestContentType;
	}

	public String getResponseContentType() {
		return responseContentType;
	}

	public void setResponseContentType(String responseContentType) {
		this.responseContentType = responseContentType;
	}

	public Integer getResponseStatusCode() {
		return responseStatusCode;
	}

	public void setResponseStatusCode(Integer responseStatusCode) {
		this.responseStatusCode = responseStatusCode;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
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
		this.testIdentifier = other.testIdentifier;
		if(other.errors!=null)
		{
			this.errors = other .errors;
		}
		this.responseStatusCode = other.responseStatusCode;
		this.method = other.method;
		this.failureReason = other.failureReason;
	}

	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("TestCaseReport = [\n");
		builder.append("actualUrl=");
		builder.append(actualUrl);
		builder.append("\nurl=");
		builder.append(url);
		builder.append("\nmethod=");
		builder.append(method);
		builder.append("\nworkflowName=");
		builder.append(workflowName);
		builder.append("\nstatus=");
		builder.append(status);
		builder.append("\nfailureReason=");
		builder.append(failureReason);
		builder.append("\nnumberOfRuns=");
		builder.append(numberOfRuns);
		builder.append("\nexecutionTime=");
		builder.append(executionTime);
		builder.append("\nexecutionTimes=");
		builder.append(executionTimes != null ? toString(executionTimes, maxLen)
				: null);
		builder.append("\naverageExecutionTime=");
		builder.append(averageExecutionTime);
		builder.append("\nrequestContent=");
		builder.append(requestContent);
		builder.append("\nrequestHeaders=");
		builder.append(requestHeaders);
		builder.append("\nresponseHeaders=");
		builder.append(responseHeaders);
		builder.append("\nrequestContentType=");
		builder.append(requestContentType);
		builder.append("\nresponseContentType=");
		builder.append(responseContentType);
		builder.append("\nresponseContent=");
		builder.append(responseContent);
		builder.append("\nresponseStatusCode=");
		builder.append(responseStatusCode);
		builder.append("\nerror=");
		builder.append(error);
		builder.append("\nerrorText=");
		builder.append(errorText);
		builder.append("\nerrors=");
		builder.append(errors != null ? toString(errors.entrySet(), maxLen)
				: null);
		builder.append("\ntestIdentifier=");
		builder.append(testIdentifier);
		builder.append("]\n");
		return builder.toString();
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
				&& i < maxLen; i++) {
			if (i > 0)
				builder.append("\n");
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}
	
	public static enum TestStatus {
		Success("Success"),
		Skipped("Skipped"),
		Failed("Failed");
		
		public String status;
		private TestStatus(String status) {
			this.status = status;
		}
	}
	
	public static enum TestFailureReason {
		InvalidStatusCode("InvalidStatusCode"),
		Exception("Exception"),
		NodeValidationFailed("NodeValidationFailed"),
		InvalidContentType("InvalidContentType"),
		ExecuteConditionFailed("ExecConditionFailed");
		
		public String status;
		private TestFailureReason(String status) {
			this.status = status;
		}
	}
}
