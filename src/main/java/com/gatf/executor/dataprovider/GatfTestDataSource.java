package com.gatf.executor.dataprovider;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("gatf-testdata-source")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonSerialize(include=Inclusion.NON_NULL)
public class GatfTestDataSource {

	private String dataSourceName;
	
	private String dataSourceClass;
	
	private int poolSize = 1;
	
	private long newResourceCheckoutTimeMs = 1000;
	
	private String[] args;

	public String getDataSourceName() {
		return dataSourceName;
	}

	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	public String getDataSourceClass() {
		return dataSourceClass;
	}

	public void setDataSourceClass(String dataSourceClass) {
		this.dataSourceClass = dataSourceClass;
	}

	public int getPoolSize() {
		return poolSize;
	}

	public void setPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}
	
	public long getNewResourceCheckoutTimeMs() {
		return newResourceCheckoutTimeMs;
	}

	public void setNewResourceCheckoutTimeMs(long newResourceCheckoutTimeMs) {
		this.newResourceCheckoutTimeMs = newResourceCheckoutTimeMs;
	}
}
