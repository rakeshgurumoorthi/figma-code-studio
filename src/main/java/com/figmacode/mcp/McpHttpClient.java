package com.figmacode.mcp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A small MCP client for the Streamable HTTP transport (spec 2025-06-18): JSON-RPC over POST,
 * answered either as application/json or as a text/event-stream carrying the response.
 * Only what this app needs is implemented: initialize, tools/list and tools/call.
 */
@Component
public class McpHttpClient {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Logger log = LoggerFactory.getLogger(McpHttpClient.class);

    private final HttpClient http;
    private final ObjectMapper json;

    public McpHttpClient(HttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    /** Opens a session: initialize, then notifications/initialized. Close it when done. */
    public Session open(String url, String bearerToken, Duration timeout) {
        Session session = new Session(URI.create(url), bearerToken, timeout);
        session.initialize();
        return session;
    }

    public class Session implements AutoCloseable {

        private final URI uri;
        private final String bearerToken;
        private final Duration timeout;
        private final AtomicLong ids = new AtomicLong();
        private String sessionId;
        private String protocolVersion = PROTOCOL_VERSION;
        private JsonNode serverInfo;

        Session(URI uri, String bearerToken, Duration timeout) {
            this.uri = uri;
            this.bearerToken = bearerToken;
            this.timeout = timeout;
        }

        public JsonNode serverInfo() {
            return serverInfo;
        }

        void initialize() {
            ObjectNode params = json.createObjectNode();
            params.put("protocolVersion", PROTOCOL_VERSION);
            params.putObject("capabilities");
            params.putObject("clientInfo").put("name", "figma-code-studio").put("version", "0.1.0");
            JsonNode result = request("initialize", params);
            serverInfo = result.path("serverInfo");
            if (result.hasNonNull("protocolVersion")) {
                protocolVersion = result.get("protocolVersion").asText();
            }
            notify("notifications/initialized");
        }

        public List<String> listToolNames() {
            JsonNode result = request("tools/list", json.createObjectNode());
            List<String> names = new ArrayList<>();
            result.path("tools").forEach(tool -> names.add(tool.path("name").asText()));
            return names;
        }

        public McpToolResult callTool(String name, Map<String, Object> arguments) {
            ObjectNode params = json.createObjectNode();
            params.put("name", name);
            params.set("arguments", json.valueToTree(arguments));
            JsonNode result = request("tools/call", params);
            List<McpToolResult.Content> parts = new ArrayList<>();
            for (JsonNode part : result.path("content")) {
                String uriValue = part.path("uri").asText(part.path("resource").path("uri").asText(null));
                String text = part.hasNonNull("text") ? part.get("text").asText()
                        : part.path("resource").path("text").asText(null);
                parts.add(new McpToolResult.Content(
                        part.path("type").asText(),
                        text,
                        part.path("data").asText(null),
                        part.path("mimeType").asText(null),
                        uriValue));
            }
            McpToolResult toolResult = new McpToolResult(parts, result.path("isError").asBoolean(false));
            if (toolResult.isError()) {
                throw new McpException.ToolError(String.join("\n", toolResult.texts()));
            }
            return toolResult;
        }

        private JsonNode request(String method, JsonNode params) {
            long id = ids.incrementAndGet();
            ObjectNode body = json.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", id);
            body.put("method", method);
            body.set("params", params);

            HttpResponse<String> response = post(body);
            response.headers().firstValue("Mcp-Session-Id").ifPresent(value -> sessionId = value);
            JsonNode message = readResponse(response, id);
            if (message.has("error")) {
                JsonNode error = message.get("error");
                throw new McpException("MCP " + method + " failed: " + error.path("message").asText(error.toString()));
            }
            return message.path("result");
        }

        private void notify(String method) {
            ObjectNode body = json.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            post(body);
        }

        private HttpResponse<String> post(JsonNode body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", protocolVersion)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            if (sessionId != null) {
                builder.header("Mcp-Session-Id", sessionId);
            }
            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new McpException("Could not reach the MCP server: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpException("MCP call interrupted", e);
            }
            int status = response.statusCode();
            if (status == 401) {
                throw new McpException.Unauthorized("The MCP server rejected the access token ("
                        + response.headers().firstValue("WWW-Authenticate").orElse("401") + ")");
            }
            if (status >= 400) {
                throw new McpException("MCP server returned HTTP " + status + ": " + abbreviate(response.body()));
            }
            return response;
        }

        private JsonNode readResponse(HttpResponse<String> response, long id) {
            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
            String body = response.body();
            try {
                if (contentType.startsWith("text/event-stream")) {
                    for (String data : sseData(body)) {
                        JsonNode message = json.readTree(data);
                        if (message.path("id").asLong(-1) == id) {
                            return message;
                        }
                    }
                    throw new McpException("The MCP event stream ended without a response");
                }
                return json.readTree(body);
            } catch (IOException e) {
                throw new McpException("The MCP server sent a response that is not JSON: " + abbreviate(body), e);
            }
        }

        @Override
        public void close() {
            if (sessionId == null) {
                return;
            }
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                        .header("Mcp-Session-Id", sessionId).DELETE();
                if (bearerToken != null) {
                    builder.header("Authorization", "Bearer " + bearerToken);
                }
                http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                log.debug("Closing MCP session failed (ignored): {}", e.toString());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** The data payload of each event in a server-sent-events body. */
    static List<String> sseData(String body) {
        List<String> events = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : body.split("\r?\n", -1)) {
            if (line.isEmpty()) {
                if (!current.isEmpty()) {
                    events.add(current.toString());
                    current.setLength(0);
                }
            } else if (line.startsWith("data:")) {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(line.substring(5).stripLeading());
            }
        }
        if (!current.isEmpty()) {
            events.add(current.toString());
        }
        return events;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
