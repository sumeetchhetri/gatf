package com.gatf.test.dataprovider;

import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("gatf-testdata-config")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataConfig {

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
