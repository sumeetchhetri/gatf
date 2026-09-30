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
package com.gatf.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** M14: JSON-RPC framing and the MCP lifecycle, with fake tools. */
class McpServerTest {

    private final ObjectMapper om = new ObjectMapper();

    private McpServer server() {
        McpServer s = new McpServer("gatf", "9.9", "hint");
        s.register(McpTool.of("echo", "echo", McpTool.schema("msg*", "string", "text", "n", "integer", "count"), a -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("msg", McpTool.req(a, "msg"));
            m.put("n", McpTool.num(a, "n", 1, 1, 5));
            return m;
        }));
        s.register(McpTool.of("boom", "fails", McpTool.schema(), a -> { throw new IllegalStateException("kaput"); }));
        return s;
    }

    private JsonNode rpc(McpServer s, String line) throws Exception {
        String r = s.handleLine(line);
        return r == null ? null : om.readTree(r);
    }

    @Test
    void initializeNegotiatesTheProtocolAndAdvertisesTools() throws Exception {
        McpServer s = server();
        JsonNode r = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}");
        assertEquals(1, r.get("id").asInt());
        assertEquals("2025-03-26", r.at("/result/protocolVersion").asText());
        assertEquals("gatf", r.at("/result/serverInfo/name").asText());
        assertEquals("9.9", r.at("/result/serverInfo/version").asText());
        assertFalse(r.at("/result/capabilities/tools").isMissingNode());
        assertEquals("hint", r.at("/result/instructions").asText());
        JsonNode unknown = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}");
        assertEquals(McpServer.PROTOCOLS.get(0), unknown.at("/result/protocolVersion").asText());
        assertEquals("a", unknown.get("id").asText());
    }

    @Test
    void notificationsAndResponsesGetNoReply() throws Exception {
        McpServer s = server();
        assertNull(s.handleLine("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertNull(s.handleLine("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"msg\":\"x\"}}}"));
        assertNull(s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}"));
    }

    @Test
    void toolsListCarriesSchemas() throws Exception {
        JsonNode r = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonNode t = r.at("/result/tools/0");
        assertEquals("echo", t.get("name").asText());
        assertEquals("object", t.at("/inputSchema/type").asText());
        assertEquals("msg", t.at("/inputSchema/required/0").asText());
        assertEquals("integer", t.at("/inputSchema/properties/n/type").asText());
        assertEquals(2, r.at("/result/tools").size());
    }

    @Test
    void toolsCallReturnsTextAndStructuredContent() throws Exception {
        JsonNode r = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"msg\":\"hi\",\"n\":99}}}");
        assertFalse(r.at("/result/isError").asBoolean());
        assertEquals("text", r.at("/result/content/0/type").asText());
        assertEquals("hi", om.readTree(r.at("/result/content/0/text").asText()).get("msg").asText());
        assertEquals(5, r.at("/result/structuredContent/n").asInt());   // clamped
    }

    @Test
    void toolFailuresAreToolErrorsNotProtocolErrors() throws Exception {
        McpServer s = server();
        JsonNode missing = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{}}}");
        assertTrue(missing.at("/result/isError").asBoolean());
        assertTrue(missing.at("/result/content/0/text").asText().contains("msg"));
        JsonNode boom = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"boom\"}}");
        assertTrue(boom.at("/result/isError").asBoolean());
        assertEquals("IllegalStateException: kaput", boom.at("/result/content/0/text").asText());
    }

    @Test
    void protocolErrors() throws Exception {
        McpServer s = server();
        assertEquals(McpServer.PARSE_ERROR, rpc(s, "{nope").at("/error/code").asInt());
        assertTrue(rpc(s, "{nope").get("id").isNull());
        assertEquals(McpServer.METHOD_NOT_FOUND, rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/list\"}").at("/error/code").asInt());
        assertEquals(McpServer.INVALID_PARAMS, rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}").at("/error/code").asInt());
        assertEquals(McpServer.INVALID_PARAMS, rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":[1]}}").at("/error/code").asInt());
        assertEquals(McpServer.INVALID_REQUEST, rpc(s, "42").at("/error/code").asInt());
        assertEquals("ok", rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\"}").get("result").isObject() ? "ok" : "no");
    }

    @Test
    void strictJsonRpcEnvelope() throws Exception {
        McpServer s = server();
        assertEquals(McpServer.INVALID_REQUEST, rpc(s, "{\"id\":1,\"method\":\"ping\"}").at("/error/code").asInt());          // no jsonrpc
        assertEquals(McpServer.INVALID_REQUEST, rpc(s, "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}").at("/error/code").asInt());
        JsonNode nullId = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}");                           // MCP: ids are never null
        assertEquals(McpServer.INVALID_REQUEST, nullId.at("/error/code").asInt());
        assertTrue(nullId.get("id").isNull());
        JsonNode objId = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":{\"a\":1},\"method\":\"ping\"}");
        assertEquals(McpServer.INVALID_REQUEST, objId.at("/error/code").asInt());
        assertTrue(objId.get("id").isNull());
        assertEquals(McpServer.INVALID_REQUEST, rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":1.5,\"method\":\"ping\"}").at("/error/code").asInt());
        assertEquals(McpServer.INVALID_REQUEST, rpc(s, "[]").at("/error/code").asInt());
        JsonNode notifWithId = rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"notifications/initialized\"}");
        assertTrue(notifWithId.get("result").isObject());
        assertEquals("s1", rpc(s, "{\"jsonrpc\":\"2.0\",\"id\":\"s1\",\"method\":\"ping\"}").get("id").asText());
    }

    @Test
    void batchesAreAnsweredAsArrays() throws Exception {
        JsonNode r = rpc(server(), "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"method\":\"notifications/x\"},{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]");
        assertTrue(r.isArray());
        assertEquals(2, r.size());
    }

    @Test
    void serveWritesOneLinePerResponse() throws Exception {
        String in = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"msg\":\"line\\nbreak\"}}}\n";
        StringWriter out = new StringWriter();
        server().serve(new StringReader(in), out);
        String[] lines = out.toString().split("\n");
        assertEquals(2, lines.length);
        assertEquals(1, om.readTree(lines[0]).get("id").asInt());
        assertEquals("line\nbreak", om.readTree(lines[1]).at("/result/structuredContent/msg").asText());
    }

    @Test
    void duplicateToolNamesAreRejected() {
        McpServer s = server();
        try {
            s.register(McpTool.of("echo", "x", McpTool.schema(), a -> null));
            throw new AssertionError("expected a failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("echo"));
        }
    }
}
