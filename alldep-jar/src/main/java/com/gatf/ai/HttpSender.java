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
package com.gatf.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * H1 (Δ149): the one network seam of the AI layer. {@link JdkHttpSender} is the real one; tests pass a fake, so no
 * test ever opens a socket. A timeout surfaces as {@link java.net.http.HttpTimeoutException}.
 */
public interface HttpSender {

    final class Response {
        public final int status;
        public final byte[] body;
        public final Map<String, String> headers;   // lower-case names, first value

        public Response(int status, byte[] body, Map<String, String> headers) {
            this.status = status;
            this.body = body == null ? new byte[0] : body;
            this.headers = headers == null ? Map.of() : headers;
        }
    }

    Response post(String url, Map<String, String> headers, byte[] body, Duration timeout) throws IOException, InterruptedException;
}
