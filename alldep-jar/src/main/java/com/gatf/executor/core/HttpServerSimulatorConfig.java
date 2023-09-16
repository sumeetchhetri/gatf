package com.gatf.executor.core;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "htttpServerSimulator")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class HttpServerSimulatorConfig {
	
	private int port;
	
	private String body;
	
	private Map<String, String> headers = new HashMap<String, String>();

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
}
