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
package com.gatf.executor.distributed;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;

public class DistributedAcceptanceContext implements Serializable {

	private static final long serialVersionUID = 1L;

	public static enum Command
	{
		CONFIG_SHARE_REQ,
		CONFIG_SHARE_RES,
		TESTS_SHARE_REQ,
		TESTS_SHARE_RES,
		LOAD_TESTS_RES,
		SELENIUM_REQ,
		SELENIUM_RES,
		INVALID
	}
	
	private String node = null;
	
	private GatfExecutorConfig config = null;
	
	private Map<String, String> httpHeaders = null;
	
	private Map<String, String> soapEndpoints = null;
	
	private Map<String, String> soapMessages = null;
	
	private Map<String, String> soapActions = new HashMap<String, String>();
	
	List<Object[]> selTestdata = new ArrayList<Object[]>();
    
    private int index;
    
	// Distributed payloads cross a socket; the ser/unser below serialize as JSON (Jackson) rather than
	// Kryo object graphs. That removes arbitrary-class instantiation on the read path (no deserialization
	// gadget RCE) and the Kryo binary/version coupling between nodes. The Kryo Input/Output are kept only
	// as stream framing (length-prefixed strings + varint command ints), which never instantiates classes.
	
	public String getNode() {
		return node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public GatfExecutorConfig getConfig() {
		return config;
	}

	public void setConfig(GatfExecutorConfig config) {
		this.config = config;
	}

	public Map<String, String> getHttpHeaders() {
		return httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	public Map<String, String> getSoapEndpoints() {
		return soapEndpoints;
	}

	public void setSoapEndpoints(Map<String, String> soapEndpoints) {
		this.soapEndpoints = soapEndpoints;
	}

	public Map<String, String> getSoapMessages() {
		return soapMessages;
	}

	public void setSoapMessages(Map<String, String> soapMessages) {
		this.soapMessages = soapMessages;
	}

	public Map<String, String> getSoapActions() {
		return soapActions;
	}

	public void setSoapActions(Map<String, String> soapActions) {
		this.soapActions = soapActions;
	}
	
	public File getResourceFile(String filename) {
		File basePath = new File(config.getTestCasesBasePath());
		if(filename==null) return basePath;
		File resource = null;
		if(config.getTestCaseDir()!=null) {
			File testPath = new File(basePath, config.getTestCaseDir());
		    resource = new File(testPath, filename);
		}
        if(resource==null || !resource.exists()) {
            resource = new File(basePath, filename);
        }
        if(resource==null || !resource.exists()) {
            resource = new File(basePath, filename);
        }
		return resource;
    }

    public List<Object[]> getSelTestdata() {
        return selTestdata;
    }

    public void setSelTestdata(List<Object[]> selTestdata) {
        this.selTestdata = selTestdata;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
    
	public static void ser(Output output, Object o) {
		try {
			output.writeString(WorkflowContextHandler.OM.writeValueAsString(o));
			output.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static <T> T unser(Input input, Class<T> type) {
		try {
			String json = input.readString();
			if(json == null) return null;
			return WorkflowContextHandler.OM.readValue(json, type);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
