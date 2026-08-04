/*
 * Copyright 2013-2019, Sumeet Chhetri
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gatf.executor.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A transport-neutral view of an HTTP response, so the executor's response handler and the
 * response validators do not depend on any particular HTTP client library.
 *
 * <p>Introduced by delta-066 (Phase 6 core, increment 2a) ahead of the
 * OkHttp -&gt; {@code java.net.http.HttpClient} transport swap: the validators talk to this
 * interface, and only the implementation changes when the transport is replaced (increment 2b).
 * The OkHttp-backed implementation is {@code com.gatf.executor.executor.OkHttpGatfResponse}.
 *
 * @author Sumeet Chhetri
 */
public interface GatfResponse {

	/** HTTP status code of the response. */
	int code();

	/** First value of the named response header (case-insensitive), or {@code null} if absent. */
	String header(String name);

	/** All values of the named response header; never {@code null} (empty list if absent). */
	List<String> headers(String name);

	/** All response headers as a header-name -&gt; values multimap. */
	Map<String, List<String>> headersMultimap();

	/**
	 * The response body decoded as a String using the response's charset (UTF-8 if unspecified).
	 * Must be called at most once for a given response, as the underlying body may be a
	 * single-use stream.
	 */
	String bodyString() throws IOException;

	/** Releases any stream/resources held by the response body. Safe to call when there is no body. */
	void closeBody();
}
