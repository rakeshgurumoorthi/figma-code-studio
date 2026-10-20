package com.figmacode.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Runs the client against a tiny fake MCP server speaking Streamable HTTP. */
class McpHttpClientTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> seenAuth = new CopyOnWriteArrayList<>();
    private final List<String> seenSession = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String url;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        seenAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
        String session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (session != null) {
            seenSession.add(session);
        }
        if (exchange.getRequestMethod().equals("DELETE")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if ("Bearer bad".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        JsonNode request = json.readTree(exchange.getRequestBody());
        String method = request.path("method").asText();
        if (!request.has("id")) {
            exchange.sendResponseHeaders(202, -1);
            return;
        }
        Object result = switch (method) {
            case "initialize" -> Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                    "serverInfo", Map.of("name", "fake-figma", "version", "1"));
            case "tools/list" -> Map.of("tools", List.of(Map.of("name", "get_design_context"), Map.of("name", "whoami")));
            case "tools/call" -> request.path("params").path("name").asText().equals("broken")
                    ? Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", "Node not found")))
                    : Map.of("content", List.of(
                            Map.of("type", "text", "text", "export default function A(){}"),
                            Map.of("type", "image", "data", "aGk=", "mimeType", "image/png")));
            default -> Map.of();
        };
        String message = json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", request.get("id").asLong(), "result", result));
        byte[] body;
        if (method.equals("tools/call")) {
            // Answer tool calls as an event stream, the way remote servers usually do
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            body = ("event: message\ndata: " + message + "\n\n").getBytes(StandardCharsets.UTF_8);
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            body = message.getBytes(StandardCharsets.UTF_8);
        }
        if (method.equals("initialize")) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "session-42");
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private McpHttpClient client() {
        return new McpHttpClient(HttpClient.newHttpClient(), json);
    }

    @Test
    void initializesListsAndCallsTools() {
        try (McpHttpClient.Session session = client().open(url, "good", Duration.ofSeconds(5))) {
            assertThat(session.serverInfo().path("name").asText()).isEqualTo("fake-figma");
            assertThat(session.listToolNames()).containsExactly("get_design_context", "whoami");
            McpToolResult result = session.callTool("get_design_context", Map.of("fileKey", "K", "nodeId", "1:2"));
            assertThat(result.texts()).containsExactly("export default function A(){}");
            assertThat(result.content().get(1).data()).isEqualTo("aGk=");
        }
        assertThat(seenAuth).allMatch("Bearer good"::equals);
        assertThat(seenSession).isNotEmpty().allMatch("session-42"::equals);
    }

    @Test
    void reportsToolErrors() {
        try (McpHttpClient.Session session = client().open(url, "good", Duration.ofSeconds(5))) {
            assertThatThrownBy(() -> session.callTool("broken", Map.of()))
                    .isInstanceOf(McpException.ToolError.class)
                    .hasMessageContaining("Node not found");
        }
    }

    @Test
    void reportsUnauthorized() {
        assertThatThrownBy(() -> client().open(url, "bad", Duration.ofSeconds(5)))
                .isInstanceOf(McpException.Unauthorized.class);
    }

    @Test
    void parsesMultiLineSseData() {
        assertThat(McpHttpClient.sseData("id: 1\ndata: {\"a\":\ndata: 1}\n\ndata: x\n"))
                .containsExactly("{\"a\":\n1}", "x");
    }
}
