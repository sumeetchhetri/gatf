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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.gatf.executor.core.AcceptanceTestContext;

/**
 * @author Sumeet Chhetri
 * This interface defines the contract for implementation of a test data provider
 * The outcome is expected to be a list of key/value pairs
 * for e.g, (database rows = list, database row column values = map)
 */
public abstract class TestDataSource implements TestDataProvider, TestDataHook {

	protected String className;
	
	protected String[] args;
	
	protected String dataSourceName;
	
	protected int poolSize = 1;
	
	protected long newResourceCheckoutTimeMs = 1000;
	
	private AcceptanceTestContext context;
	
	protected final List<Resource> pool = new ArrayList<Resource>();
	
	private final List<Boolean> poolBusyStatus = new ArrayList<Boolean>();
	
	public void setContext(AcceptanceTestContext context) {
		this.context = context;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	public String getDataSourceName() {
		return dataSourceName;
	}

	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	public ClassLoader getProjectClassLoader() {
		return context.getProjectClassLoader();
	}

	protected File getResourceFile(String filePath) {
		return context.getResourceFile(filePath);
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

	public void init() {
	}
	
	public void destroy() {
	}
	
	public Resource addToPool(Object object, boolean initialStatus) {
		synchronized (pool) {
			Resource res = new Resource();
			res.index = pool.size();
			res.object = object;
			pool.add(res);
			poolBusyStatus.add(initialStatus);
			return res;
		}
	}
	
	public void releaseToPool(Resource res) {
		synchronized (pool) {
			poolBusyStatus.set(res.index, false);
		}
	}
	
	public Resource checkoutFromPool() {
		synchronized (pool) {
			for (int i=0;i<poolBusyStatus.size();i++) {
				if(!poolBusyStatus.get(i)) {
					poolBusyStatus.set(i, true);
					return pool.get(i);
				}
			}
		}
		return null;
	}
	
	protected static class Resource {
		int index;
		Object object;
	}
}
