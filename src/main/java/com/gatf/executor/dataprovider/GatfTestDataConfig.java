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

import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.gatf.executor.core.MapKeyValueCustomXstreamConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

@XStreamAlias("gatf-testdata-config")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataConfig {

	@XStreamConverter(value=MapKeyValueCustomXstreamConverter.class)
	private Map<String, String> globalVariables;
	
	private List<String> compareEnvBaseUrls;

	private List<GatfTestDataProvider> providerTestDataList;

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
}
