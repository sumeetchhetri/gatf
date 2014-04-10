package com.gatf.executor.dataprovider;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("gatf-testdata-source-hook")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataSourceHook {

	private String hookName;
	
	private String hookClass;
	
	private String dataSourceName;
	
	private String queryStr;
	
	private boolean executeOnStart;
	
	private boolean executeOnShutdown;
	
	public String getHookName() {
		return hookName;
	}

	public void setHookName(String hookName) {
		this.hookName = hookName;
	}

	public String getDataSourceName() {
		return dataSourceName;
	}

	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	public String getQueryStr() {
		return queryStr;
	}

	public void setQueryStr(String queryStr) {
		this.queryStr = queryStr;
	}

	public String getHookClass() {
		return hookClass;
	}

	public void setHookClass(String hookClass) {
		this.hookClass = hookClass;
	}

	public boolean isExecuteOnStart() {
		return executeOnStart;
	}

	public void setExecuteOnStart(boolean executeOnStart) {
		this.executeOnStart = executeOnStart;
	}

	public boolean isExecuteOnShutdown() {
		return executeOnShutdown;
	}

	public void setExecuteOnShutdown(boolean executeOnShutdown) {
		this.executeOnShutdown = executeOnShutdown;
	}
}
