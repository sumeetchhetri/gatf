package com.gatf.generator.postman;

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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.executor.core.TestCase;

/**
 * @author Sumeet Chhetri
 * The postman test case json representation
 */
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class PostmanTestCase {

	private String id = UUID.randomUUID().toString();
	
	private String name;
	
	private String description;
	
	private String headers;
	
	private String url;
	
	private String pathVariables;
	
	private String method;
	
	private String dataMode;
	
	private String data;
	
	private int version;
	
	private Date time = new Date();
	
	private List<String> responses;
	
	private boolean synced = false;
	
	private String tests;
	
	private String collectionId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getHeaders() {
		return headers;
	}

	public void setHeaders(String headers) {
		this.headers = headers;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPathVariables() {
		return pathVariables;
	}

	public void setPathVariables(String pathVariables) {
		this.pathVariables = pathVariables;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getDataMode() {
		return dataMode;
	}

	public void setDataMode(String datamode) {
		this.dataMode = datamode;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	public List<String> getResponses() {
		return responses;
	}

	public void setResponses(List<String> responses) {
		this.responses = responses;
	}

	public boolean isSynced() {
		return synced;
	}

	public void setSynced(boolean synced) {
		this.synced = synced;
	}

	public String getTests() {
		return tests;
	}

	public void setTests(String tests) {
		this.tests = tests;
	}

	public String getCollectionId() {
		return collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}
	
	public PostmanTestCase(TestCase testCase)
	{
		setUrl("{{host}}/"+testCase.getUrl());
		setName(testCase.getName());
		setDescription(testCase.getDescription());
		setMethod(testCase.getMethod());
		if(testCase.getContent()!=null && !testCase.getContent().trim().isEmpty())
			setDataMode("raw");
		else
			setDataMode("params");
		setData(testCase.getContent());
		if(testCase.getHeaders()==null)
		{
			testCase.setHeaders(new HashMap<String, String>());
		}
		Map<String, String> headers = testCase.getHeaders();
		if(testCase.getExpectedResContentType()!=null && !testCase.getExpectedResContentType().trim().isEmpty())
		{
			headers.put(HttpHeaders.ACCEPT, testCase.getExpectedResContentType());
		}
		else
		{
			headers.put(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN);
		}
		StringBuilder build = new StringBuilder();
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			build.append(entry.getKey());
			build.append(": ");
			build.append(entry.getValue());
			build.append("\n");
		}
		setHeaders(build.toString());
		
		StringBuilder resBuild = new StringBuilder();
		resBuild.append("var data = JSON.parse(responseBody);\n\n");
		resBuild.append("tests[\""+testCase.getName()+"\"] = responseCode.code === "+testCase.getExpectedResCode()+";\n\n");
		setTests(resBuild.toString());
	}
}
