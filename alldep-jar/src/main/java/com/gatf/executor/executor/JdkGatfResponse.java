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
package com.gatf.executor.executor;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.gatf.executor.core.GatfResponse;

/**
 * {@link GatfResponse} backed by a {@code java.net.http.HttpResponse<byte[]>}. This is the response
 * half of the OkHttp -&gt; java.net.http.HttpClient transport swap (Phase 6 core, increment 2b): once
 * {@code TestCaseExecutorUtil} dispatches via {@code HttpClient.sendAsync(req, BodyHandlers.ofByteArray())},
 * the completion stage wraps the {@code HttpResponse} in one of these and hands it to the same
 * validators, replacing the earlier OkHttp-backed response wrapper.
 *
 * <p>The body is captured as a byte[] by the {@code ofByteArray()} handler, so {@link #bodyString()}
 * decodes it using the charset from the response's {@code Content-Type} (UTF-8 when unspecified) to
 * match OkHttp's {@code ResponseBody.string()} behaviour, and can be called more than once.
 *
 * @author Sumeet Chhetri
 */
public class JdkGatfResponse implements GatfResponse {

	private final HttpResponse<byte[]> response;
	private String cachedBody;

	public JdkGatfResponse(HttpResponse<byte[]> response) {
		this.response = response;
	}

	@Override
	public int code() {
		return response.statusCode();
	}

	@Override
	public String header(String name) {
		return response.headers().firstValue(name).orElse(null);
	}

	@Override
	public List<String> headers(String name) {
		// HttpHeaders.allValues never returns null (empty list when absent).
		return response.headers().allValues(name);
	}

	@Override
	public Map<String, List<String>> headersMultimap() {
		return response.headers().map();
	}

	@Override
	public String bodyString() {
		if (cachedBody == null) {
			byte[] body = response.body();
			cachedBody = body != null ? new String(body, charset()) : null;
		}
		return cachedBody;
	}

	@Override
	public void closeBody() {
		// no-op: the whole body was already read into memory by BodyHandlers.ofByteArray()
	}

	/** Charset from the Content-Type header (e.g. {@code ...; charset=ISO-8859-1}), UTF-8 if absent/unknown. */
	private Charset charset() {
		String contentType = header("Content-Type");
		if (contentType != null) {
			for (String part : contentType.split(";")) {
				part = part.trim();
				if (part.regionMatches(true, 0, "charset=", 0, 8)) {
					String cs = part.substring(8).trim().replace("\"", "");
					try {
						if (!cs.isEmpty() && Charset.isSupported(cs)) {
							return Charset.forName(cs);
						}
					} catch (Exception ignore) {
						// fall through to default
					}
				}
			}
		}
		return StandardCharsets.UTF_8;
	}
}
