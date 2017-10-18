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
package com.gatf.executor.dataprovider;

import java.io.Serializable;
import java.util.Arrays;

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

	private static final long serialVersionUID = 1L;
	
	private String dataSourceName;
	
	private String providerName;
	
	private String providerClass;
	
	private String sourceProperties;
	
	private String providerProperties;
	
	private String queryStr;
	
	private Boolean enabled;
	
	protected String[] args;
	
	private Boolean live;

	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	public Boolean isEnabled() {
		return enabled!=null && enabled;
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

	public Boolean isLive() {
		return live!=null && live;
	}

	public void setLive(Boolean live) {
		this.live = live;
	}

	public Boolean getEnabled() {
		return enabled;
	}
	
	public GatfTestDataProvider(){}

	public GatfTestDataProvider(GatfTestDataProvider other) {
		super();
		this.dataSourceName = other.dataSourceName;
		this.providerName = other.providerName;
		this.providerClass = other.providerClass;
		this.sourceProperties = other.sourceProperties;
		this.providerProperties = other.providerProperties;
		this.queryStr = other.queryStr;
		this.enabled = other.enabled;
		if(other.args!=null) {
		    this.args = (String[])Arrays.asList(other.args).toArray(new String[other.args.length]);
		}
		this.live = other.live;
	}
}
