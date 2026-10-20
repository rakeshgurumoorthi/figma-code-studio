package com.figmacode.figma;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.figmacode.mcp.McpException;
import com.figmacode.mcp.McpToolResult;
import com.figmacode.mcp.McpToolResult.Content;

/**
 * Mock mode: answers like Figma's MCP server with a canned design.
 * Node id 0:0 returns a "node not found" error so the error path can be seen too.
 */
@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "true")
public class MockFigmaMcpGateway implements FigmaMcpGateway {

    @Override
    public Probe probe(String accessToken) {
        return new Probe(List.of("get_design_context", "get_screenshot", "get_metadata",
                "get_variable_defs", "get_code_connect_map", "whoami"), "mock.designer@example.com");
    }

    @Override
    public Design fetchDesign(String accessToken, FigmaLink link) {
        if ("0:0".equals(link.nodeId())) {
            throw new McpException.ToolError("Node 0:0 was not found in file " + link.fileKey()
                    + ". (Mock mode: use any other node-id to get the sample design.)");
        }
        sleep(900);
        String code = read("mock/design-context.tsx");
        String notes = """
                SUPER CRITICAL: The generated React+Tailwind code MUST be converted to match the target project's \
                technology stack and styling system.
                1. Analyze the target codebase to identify technology stack, styling approach, component patterns, \
                and design tokens
                2. Convert React syntax to the target framework/library (if non-React)
                3. Reuse existing components and design tokens where possible

                Node ids: 12:345 (Product launch), 12:346 (Hero image), 12:360 (Content column)
                (Mock mode sample for file %s, node %s)""".formatted(link.fileKey(), link.nodeId());
        McpToolResult context = new McpToolResult(List.of(Content.text(code), Content.text(notes)), false);
        Content screenshot = Content.image(
                Base64.getEncoder().encodeToString(read("mock/screenshot.svg").getBytes(StandardCharsets.UTF_8)),
                "image/svg+xml");
        return new Design(context, screenshot);
    }

    @Override
    public Asset downloadAsset(String accessToken, String url) {
        if (url.endsWith("mock-hero")) {
            return new Asset(read("mock/hero.svg").getBytes(StandardCharsets.UTF_8), "image/svg+xml");
        }
        if (url.endsWith("mock-avatar")) {
            return new Asset(read("mock/avatar.svg").getBytes(StandardCharsets.UTF_8), "image/svg+xml");
        }
        throw new McpException("Mock mode has no asset at " + url);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
