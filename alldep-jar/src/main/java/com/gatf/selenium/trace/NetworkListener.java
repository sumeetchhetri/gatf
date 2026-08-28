/*
    Copyright 2013-2026, Sumeet Chhetri

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
