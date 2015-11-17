package com.gatf.selenium;

import java.io.Serializable;
import java.util.logging.Level;

@SuppressWarnings("serial")
public class SerializableLogEntry implements Serializable {
	private final Level level;
	private final long timestamp;
	private final String message;

	public SerializableLogEntry(Level level, long timestamp, String message) {
		this.level = level;
		this.timestamp = timestamp;
		this.message = message;
	}

	public Level getLevel() {
		return this.level;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getMessage() {
		return this.message;
	}
}
