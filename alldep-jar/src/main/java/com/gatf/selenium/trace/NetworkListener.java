package com.gatf.selenium.trace;

import java.util.Map;

/**
 * Transport-neutral network callback. A {@link BrowserControl} translates its underlying
 * protocol events (CDP Network.* today, BiDi network.* later) into these calls; the
 * {@code NetworkRecorder} is the only implementor. No CDP/BiDi types cross this boundary.
 *
 * @author Sumeet Chhetri
 */
public interface NetworkListener {
    void onRequest(String requestId, String url, String method, Map<String, String> headers, long tsMillis);
    void onResponse(String requestId, int status, String statusText, String mimeType,
                    Map<String, String> headers, long tsMillis);
    void onFinished(String requestId, long encodedDataLength, long tsMillis);
    void onFailed(String requestId, String errorText, long tsMillis);
}
