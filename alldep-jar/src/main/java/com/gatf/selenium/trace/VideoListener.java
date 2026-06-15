package com.gatf.selenium.trace;

/**
 * Transport-neutral screencast callback: one decoded JPEG frame at a time (CDP
 * Page.screencastFrame today). The {@code VideoRecorder} is the only implementor; no CDP types
 * cross this boundary.
 *
 * @author Sumeet Chhetri
 */
public interface VideoListener {
    void onFrame(byte[] jpeg, long tsMillis);
}
