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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * M14: a Model Context Protocol server over stdio — newline-delimited JSON-RPC 2.0, one message per line (the MCP
 * stdio transport). Handles {@code initialize}, {@code ping}, {@code tools/list} and {@code tools/call}; notifications
 * and client responses are accepted and ignored. Pure: Jackson only, no GATF types, so it is tested with fakes.
 */
public final class McpServer {

    /** Newest first; an unknown client version is answered with the newest. */
    public static final List<String> PROTOCOLS = Arrays.asList("2025-06-18", "2025-03-26", "2024-11-05");

    static final int PARSE_ERROR = -32700, INVALID_REQUEST = -32600, METHOD_NOT_FOUND = -32601, INVALID_PARAMS = -32602,
            INTERNAL_ERROR = -32603;

    private final ObjectMapper om = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private final Map<String, McpTool> tools = new LinkedHashMap<String, McpTool>();
    private final String name, version, instructions;

    public McpServer(String name, String version, String instructions) {
        this.name = name;
        this.version = version == null ? "dev" : version;
        this.instructions = instructions;
    }

    public McpServer register(McpTool t) {
        if (tools.containsKey(t.name())) throw new IllegalArgumentException("duplicate tool " + t.name());
        tools.put(t.name(), t);
        return this;
    }

    public Map<String, McpTool> tools() { return tools; }

    /** Reads messages until EOF; each response is written as one line and flushed. */
    public void serve(Reader in, Writer out) throws IOException {
        BufferedReader r = in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String resp = handleLine(line);
            if (resp != null) {
                synchronized (out) {
                    out.write(resp);
                    out.write('\n');
                    out.flush();
                }
            }
        }
    }

    /** One incoming line → the response line, or null when nothing is to be sent (notification / response). */
    public String handleLine(String line) {
        JsonNode msg;
        try {
            msg = om.readTree(line);
        } catch (Exception e) {
            return write(error(null, PARSE_ERROR, "parse error: " + e.getMessage()));
        }
        if (msg != null && msg.isArray()) {             // JSON-RPC batch (older protocol revisions)
            if (msg.size() == 0) return write(error(null, INVALID_REQUEST, "empty batch"));
            ArrayNode outs = om.createArrayNode();
            for (JsonNode m : msg) {
                ObjectNode o = handle(m);
                if (o != null) outs.add(o);
            }
            return outs.size() == 0 ? null : write(outs);
        }
        ObjectNode o = handle(msg);
        return o == null ? null : write(o);
    }

    ObjectNode handle(JsonNode msg) {
        if (msg == null || !msg.isObject()) return error(null, INVALID_REQUEST, "invalid request");
        JsonNode id = msg.get("id");
        JsonNode method = msg.get("method");
        if (method == null || !method.isTextual()) {
            // a response to a server request (none are sent) or garbage
            return id == null || msg.has("result") || msg.has("error") ? null : error(validId(id) ? id : null, INVALID_REQUEST, "missing method");
        }
        // JSON-RPC 2.0 / MCP: "jsonrpc":"2.0"; an id is a string or a number (MCP forbids null); no id = notification
        if (!"2.0".equals(msg.path("jsonrpc").asText(null)) || (id != null && !validId(id))) {
            return error(id != null && validId(id) ? id : null, INVALID_REQUEST, "invalid request: jsonrpc must be \"2.0\" and id a string or number");
        }
        boolean notification = id == null;
        JsonNode params = msg.get("params");
        try {
            JsonNode result = dispatch(method.asText(), params);
            if (notification) return null;
            if (result == null || result.isNull()) result = om.createObjectNode();   // e.g. a notifications/* method sent with an id
            ObjectNode o = om.createObjectNode();
            o.put("jsonrpc", "2.0");
            o.set("id", id);
            o.set("result", result);
            return o;
        } catch (RpcError e) {
            return notification ? null : error(id, e.code, e.getMessage());
        } catch (Exception e) {
            return notification ? null : error(id, INTERNAL_ERROR, String.valueOf(e.getMessage()));
        }
    }

    private JsonNode dispatch(String method, JsonNode params) throws Exception {
        switch (method) {
            case "initialize": {
                String asked = params == null ? null : params.path("protocolVersion").asText(null);
                ObjectNode r = om.createObjectNode();
                r.put("protocolVersion", asked != null && PROTOCOLS.contains(asked) ? asked : PROTOCOLS.get(0));
                ObjectNode caps = r.putObject("capabilities");
                caps.putObject("tools").put("listChanged", false);
                ObjectNode info = r.putObject("serverInfo");
                info.put("name", name);
                info.put("version", version);
                if (instructions != null) r.put("instructions", instructions);
                return r;
            }
            case "ping":
                return om.createObjectNode();
            case "tools/list": {
                ObjectNode r = om.createObjectNode();
                ArrayNode a = r.putArray("tools");
                for (McpTool t : tools.values()) {
                    ObjectNode o = a.addObject();
                    o.put("name", t.name());
                    o.put("description", t.description());
                    o.set("inputSchema", om.valueToTree(t.inputSchema()));
                }
                return r;
            }
            case "tools/call":
                return call(params);
            default:
                if (method.startsWith("notifications/")) return om.nullNode();
                throw new RpcError(METHOD_NOT_FOUND, "method not found: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private JsonNode call(JsonNode params) {
        String tn = params == null ? null : params.path("name").asText(null);
        McpTool t = tn == null ? null : tools.get(tn);
        if (t == null) throw new RpcError(INVALID_PARAMS, "unknown tool: " + tn);
        JsonNode argn = params.get("arguments");
        if (argn != null && !argn.isNull() && !argn.isObject()) throw new RpcError(INVALID_PARAMS, "arguments must be an object");
        Map<String, Object> args = argn == null || argn.isNull() ? new LinkedHashMap<String, Object>() : om.convertValue(argn, Map.class);
        ObjectNode r = om.createObjectNode();
        try {
            Object v = t.call(args);
            JsonNode tree = om.valueToTree(v);
            r.putArray("content").addObject().put("type", "text").put("text", tree.isTextual() ? tree.asText() : pretty(tree));
            if (tree.isObject()) r.set("structuredContent", tree);
            r.put("isError", false);
        } catch (Throwable e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            if (!(e instanceof McpTool.ToolError) && !(e instanceof IllegalArgumentException)) m = e.getClass().getSimpleName() + ": " + m;
            r.putArray("content").addObject().put("type", "text").put("text", m);
            r.put("isError", true);
        }
        return r;
    }

    private static boolean validId(JsonNode id) {
        return id.isTextual() || id.isIntegralNumber();
    }

    private String pretty(JsonNode n) {
        try { return om.writerWithDefaultPrettyPrinter().writeValueAsString(n); }
        catch (Exception e) { return n.toString(); }
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode o = om.createObjectNode();
        o.put("jsonrpc", "2.0");
        if (id == null) o.putNull("id"); else o.set("id", id);
        ObjectNode e = o.putObject("error");
        e.put("code", code);
        e.put("message", message);
        return o;
    }

    private String write(JsonNode n) {
        try { return om.writeValueAsString(n); }
        catch (Exception e) { return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"serialisation failed\"}}"; }
    }

    static final class RpcError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;
        RpcError(int code, String m) { super(m); this.code = code; }
    }
}
