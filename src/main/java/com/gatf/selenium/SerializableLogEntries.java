package com.gatf.selenium;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.openqa.selenium.logging.LogEntry;

@SuppressWarnings("serial")
public class SerializableLogEntries implements Iterable<SerializableLogEntry>, Serializable {
	private final List<SerializableLogEntry> entries;
	public SerializableLogEntries(List<LogEntry> entries) {
		List<SerializableLogEntry> mutableEntries = new ArrayList<SerializableLogEntry>();
		for (LogEntry e : entries) {
			mutableEntries.add(new SerializableLogEntry(e.getLevel(), e.getTimestamp(), e.getMessage()));
		}
		this.entries = Collections.unmodifiableList(mutableEntries);
	}
	public List<SerializableLogEntry> getAll() {
		return this.entries;
	}
	public Iterator<SerializableLogEntry> iterator() {
		return this.entries.iterator();
	}
	
}
