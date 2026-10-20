package com.figmacode.figma;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.figmacode.config.AppProperties;
import com.figmacode.mcp.McpException;
import com.figmacode.mcp.McpHttpClient;
import com.figmacode.mcp.McpToolResult;

@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "false", matchIfMissing = true)
public class RemoteFigmaMcpGateway implements FigmaMcpGateway {

    private static final Logger log = LoggerFactory.getLogger(RemoteFigmaMcpGateway.class);
    private static final long MAX_ASSET_BYTES = 10L * 1024 * 1024;

    private final McpHttpClient mcp;
    private final HttpClient http;
    private final AppProperties.Figma figma;

    public RemoteFigmaMcpGateway(McpHttpClient mcp, HttpClient http, AppProperties props) {
        this.mcp = mcp;
        this.http = http;
        this.figma = props.getFigma();
    }

    @Override
    public Probe probe(String accessToken) {
        try (McpHttpClient.Session session = mcp.open(figma.getMcpUrl(), accessToken, Duration.ofSeconds(20))) {
            List<String> tools = session.listToolNames();
            String account = null;
            if (tools.contains("whoami")) {
                try {
                    account = describeAccount(session.callTool("whoami", Map.of()));
                } catch (McpException e) {
                    log.debug("whoami failed: {}", e.getMessage());
                }
            }
            return new Probe(tools, account);
        }
    }

    @Override
    public Design fetchDesign(String accessToken, FigmaLink link) {
        try (McpHttpClient.Session session = mcp.open(figma.getMcpUrl(), accessToken, figma.getCallTimeout())) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("fileKey", link.fileKey());
            args.put("nodeId", link.nodeId());
            args.put("clientLanguages", figma.getClientLanguages());
            args.put("clientFrameworks", figma.getClientFrameworks());
            McpToolResult context = session.callTool("get_design_context", args);

            McpToolResult.Content screenshot = null;
            try {
                McpToolResult shot = session.callTool("get_screenshot", Map.of(
                        "fileKey", link.fileKey(), "nodeId", link.nodeId(),
                        "clientLanguages", figma.getClientLanguages(),
                        "clientFrameworks", figma.getClientFrameworks()));
                screenshot = shot.content().stream().filter(c -> "image".equals(c.type())).findFirst().orElse(null);
            } catch (McpException e) {
                log.info("get_screenshot failed, continuing without it: {}", e.getMessage());
            }
            return new Design(context, screenshot);
        }
    }

    @Override
    public Asset downloadAsset(String accessToken, String url) {
        URI uri = URI.create(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET();
        // Only Figma's own hosts get the user's token; any other URL is fetched anonymously
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.equals("figma.com") || host.endsWith(".figma.com")) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        try {
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new McpException("Asset download returned HTTP " + response.statusCode());
            }
            if (response.body().length > MAX_ASSET_BYTES) {
                throw new McpException("Asset is larger than 10 MB");
            }
            String type = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new Asset(response.body(), type.split(";")[0].trim());
        } catch (IOException e) {
            throw new McpException("Asset download failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("Asset download interrupted", e);
        }
    }

    private static String describeAccount(McpToolResult result) {
        String text = String.join(" ", result.texts()).trim();
        java.util.regex.Matcher email = java.util.regex.Pattern
                .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
        if (email.find()) {
            return email.group();
        }
        return text.length() > 80 ? text.substring(0, 80) : text;
    }
}
