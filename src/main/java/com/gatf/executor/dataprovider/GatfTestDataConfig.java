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
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.executor.core.MapKeyValueCustomXstreamConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

/**
 * @author Sumeet Chhetri
 * The Test data configuration properties
 */
@XStreamAlias("gatf-testdata-config")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataConfig implements Serializable {

	private static final long serialVersionUID = 1L;
	
	@XStreamConverter(value=MapKeyValueCustomXstreamConverter.class)
	private Map<String, String> globalVariables;
	
	private List<String> compareEnvBaseUrls;
	
	private List<GatfTestDataSource> dataSourceList;

	private List<GatfTestDataProvider> providerTestDataList;
	
	private List<GatfTestDataSourceHook> dataSourceHooks;
	
	private List<GatfTestDataSource> dataSourceListForProfiling;

	public Map<String, String> getGlobalVariables() {
		return globalVariables;
	}

	public void setGlobalVariables(Map<String, String> globalVariables) {
		this.globalVariables = globalVariables;
	}

	public List<GatfTestDataProvider> getProviderTestDataList() {
		return providerTestDataList;
	}

	public void setProviderTestDataList(
			List<GatfTestDataProvider> providerTestDataList) {
		this.providerTestDataList = providerTestDataList;
	}

	public List<String> getCompareEnvBaseUrls() {
		return compareEnvBaseUrls;
	}

	public void setCompareEnvBaseUrls(List<String> compareEnvBaseUrls) {
		this.compareEnvBaseUrls = compareEnvBaseUrls;
	}

	public List<GatfTestDataSource> getDataSourceList() {
		return dataSourceList;
	}

	public void setDataSourceList(List<GatfTestDataSource> dataSourceList) {
		this.dataSourceList = dataSourceList;
	}

	public List<GatfTestDataSourceHook> getDataSourceHooks() {
		return dataSourceHooks;
	}

	public void setDataSourceHooks(List<GatfTestDataSourceHook> dataSourceHooks) {
		this.dataSourceHooks = dataSourceHooks;
	}

	public List<GatfTestDataSource> getDataSourceListForProfiling() {
		return dataSourceListForProfiling;
	}

	public void setDataSourceListForProfiling(List<GatfTestDataSource> dataSourceListForProfiling) {
		this.dataSourceListForProfiling = dataSourceListForProfiling;
	}
}
