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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

/** delta-101: java.net.http does not decompress; JdkGatfResponse must (OkHttp parity). */
class JdkGatfResponseTest {

    static final String JSON = "{\"name\":\"Sumeet\",\"city\":\"Pune\",\"note\":\"न\"}";

    static byte[] gzip(byte[] b) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(o)) { g.write(b); }
        return o.toByteArray();
    }

    static byte[] deflate(byte[] b, boolean raw) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(o, new Deflater(Deflater.DEFAULT_COMPRESSION, raw))) { d.write(b); }
        return o.toByteArray();
    }

    @Test
    void decodesGzipAndDeflateVariants() throws Exception {
        byte[] plain = JSON.getBytes(StandardCharsets.UTF_8);
        assertThat(new String(JdkGatfResponse.decoded(gzip(plain), "gzip"), StandardCharsets.UTF_8)).isEqualTo(JSON);
        assertThat(new String(JdkGatfResponse.decoded(gzip(plain), " X-GZIP "), StandardCharsets.UTF_8)).isEqualTo(JSON);
        assertThat(new String(JdkGatfResponse.decoded(deflate(plain, false), "deflate"), StandardCharsets.UTF_8)).isEqualTo(JSON);
        assertThat(new String(JdkGatfResponse.decoded(deflate(plain, true), "deflate"), StandardCharsets.UTF_8)).isEqualTo(JSON);
    }

    @Test
    void identityUnknownAndCorruptBodiesAreReturnedUnchanged() {
        byte[] plain = JSON.getBytes(StandardCharsets.UTF_8);
        assertThat(JdkGatfResponse.decoded(plain, null)).isSameAs(plain);
        assertThat(JdkGatfResponse.decoded(plain, "br")).isSameAs(plain);
        assertThat(JdkGatfResponse.decoded(plain, "gzip")).isSameAs(plain);     // mislabelled: not gzip -> raw
        assertThat(JdkGatfResponse.decoded(new byte[0], "gzip")).isEmpty();
    }

    @Test
    void bodyStringDecodesThenAppliesCharset() throws Exception {
        byte[] body = gzip(JSON.getBytes(StandardCharsets.UTF_8));
        JdkGatfResponse r = new JdkGatfResponse(fake(body, Map.of("Content-Encoding", List.of("gzip"),
                "Content-Type", List.of("application/json; charset=UTF-8"))));
        assertThat(r.bodyString()).isEqualTo(JSON);
        assertThat(r.bodyString()).isEqualTo(JSON);                            // cached, repeatable
    }

    static HttpResponse<byte[]> fake(byte[] body, Map<String, List<String>> headers) {
        HttpHeaders h = HttpHeaders.of(headers, (a, b) -> true);
        return new HttpResponse<byte[]>() {
            public int statusCode() { return 200; }
            public HttpRequest request() { return HttpRequest.newBuilder(URI.create("http://localhost/")).build(); }
            public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return h; }
            public byte[] body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return URI.create("http://localhost/"); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
