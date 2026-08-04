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
package com.gatf.executor.executor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.gatf.executor.core.GatfResponse;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp-backed {@link GatfResponse}. A thin, lazy delegator: header access maps straight onto
 * okhttp {@code Headers}, and {@link #bodyString()} delegates to okhttp's own (single-use)
 * {@code body().string()} so charset handling is unchanged.
 *
 * <p>Introduced by delta-066 (Phase 6 core, increment 2a). To be replaced by an
 * {@code java.net.http.HttpResponse}-backed implementation when the transport is swapped
 * (increment 2b); the {@link GatfResponse} interface stays put, so the validators are untouched.
 *
 * @author Sumeet Chhetri
 */
public class OkHttpGatfResponse implements GatfResponse {

	private final Response response;

	public OkHttpGatfResponse(Response response) {
		this.response = response;
	}

	@Override
	public int code() {
		return response.code();
	}

	@Override
	public String header(String name) {
		return response.header(name);
	}

	@Override
	public List<String> headers(String name) {
		List<String> values = response.headers().values(name);
		return values != null ? values : Collections.<String>emptyList();
	}

	@Override
	public Map<String, List<String>> headersMultimap() {
		return response.headers().toMultimap();
	}

	@Override
	public String bodyString() throws IOException {
		ResponseBody body = response.body();
		return body != null ? body.string() : null;
	}

	@Override
	public void closeBody() {
		ResponseBody body = response.body();
		if (body != null) {
			body.close();
		}
	}
}
