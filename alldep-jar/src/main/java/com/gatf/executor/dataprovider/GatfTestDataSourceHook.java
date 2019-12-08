/*
    Copyright 2013-2019, Sumeet Chhetri
    
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

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("gatf-testdata-source-hook")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataSourceHook implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String hookName;
	
	private String hookClass;
	
	private String dataSourceName;
	
	private String[] queryStrs;
	
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

	public String[] getQueryStrs() {
		return queryStrs;
	}

	public void setQueryStrs(String[] queryStrs) {
		this.queryStrs = queryStrs;
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
