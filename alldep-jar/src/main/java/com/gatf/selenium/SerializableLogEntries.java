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
package com.gatf.selenium;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.openqa.selenium.logging.LogEntry;

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
