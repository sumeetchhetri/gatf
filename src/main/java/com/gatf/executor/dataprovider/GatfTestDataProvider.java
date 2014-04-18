package com.gatf.executor.dataprovider;

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

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * @author Sumeet Chhetri
 * The test case provider implementation properties
 */
@XStreamAlias("gatf-testdata-provider")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataProvider implements Serializable {

	private String dataSourceName;
	
	private String providerName;
	
	private String providerClass;
	
	private String sourceProperties;
	
	private String providerProperties;
	
	private String queryStr;
	
	private Boolean enabled;
	
	protected String[] args;

	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	public Boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public String getDataSourceName() {
		return dataSourceName;
	}

	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	public String getProviderProperties() {
		return providerProperties;
	}

	public void setProviderProperties(String providerProperties) {
		this.providerProperties = providerProperties;
	}

	public String getSourceProperties() {
		return sourceProperties;
	}

	public void setSourceProperties(String sourceProperties) {
		this.sourceProperties = sourceProperties;
	}

	public String getQueryStr() {
		return queryStr;
	}

	public void setQueryStr(String queryStr) {
		this.queryStr = queryStr;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	public String getProviderClass() {
		return providerClass;
	}

	public void setProviderClass(String providerClass) {
		this.providerClass = providerClass;
	}
}
