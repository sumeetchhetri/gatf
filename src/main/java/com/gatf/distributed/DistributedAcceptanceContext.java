package com.gatf.distributed;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.gatf.executor.core.GatfExecutorConfig;

public class DistributedAcceptanceContext implements Serializable {

	public static enum Command
	{
		CONFIG_SHARE_REQ,
		CONFIG_SHARE_RES,
		TESTS_SHARE_REQ,
		TESTS_SHARE_RES,
		INVALID
	}
	
	private String node = null;
	
	private GatfExecutorConfig config = null;
	
	private Map<String, String> httpHeaders = null;
	
	private Map<String, String> soapEndpoints = null;
	
	private Map<String, String> soapMessages = null;
	
	private Map<String, String> soapActions = new HashMap<String, String>();
	
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
}
