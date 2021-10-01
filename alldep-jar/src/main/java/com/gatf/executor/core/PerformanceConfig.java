package com.gatf.executor.core;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("wrkConfig")
@JsonAutoDetect(getterVisibility=Visibility.NONE, fieldVisibility=Visibility.ANY, isGetterVisibility=Visibility.NONE)
@JsonInclude(value = Include.NON_NULL)
public class PerformanceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;//wrk, wrk2, autocannon
    
    private int connections = 0;
    
    private int durationSeconds = 0;
    
    private int threads = 0;
    
    private int rate = 0;
    
    private String scriptPath;
    
    private int timeout;
    
    private boolean latency;
    
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getRate() {
		return rate;
	}

	public void setRate(int rate) {
		this.rate = rate;
	}

	public int getConnections() {
		return connections;
	}

	public void setConnections(int connections) {
		this.connections = connections;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(int durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public int getThreads() {
		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public String getScriptPath() {
		return scriptPath;
	}

	public void setScriptPath(String scriptPath) {
		this.scriptPath = scriptPath;
	}

	public boolean isLatency() {
		return latency;
	}

	public void setLatency(boolean latency) {
		this.latency = latency;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
}
