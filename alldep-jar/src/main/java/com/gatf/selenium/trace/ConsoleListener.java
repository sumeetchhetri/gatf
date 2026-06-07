package com.gatf.selenium.trace;

/**
 * Transport-neutral console/log callback (CDP Log.entryAdded + Runtime.consoleAPICalled
 * today; BiDi log.entryAdded later). The {@code ConsoleRecorder} is the only implementor.
 *
 * @author Sumeet Chhetri
 */
public interface ConsoleListener {
    void onEntry(long tsMillis, String level, String text, String url, Integer line, String source);
}
