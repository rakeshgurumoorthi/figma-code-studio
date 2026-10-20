package com.figmacode.figma;

import java.util.List;

import com.figmacode.mcp.McpToolResult;

/** What the app needs from Figma's MCP server. */
public interface FigmaMcpGateway {

    /** Handshake + tools/list; also asks who the Figma user is when the server offers whoami. */
    Probe probe(String accessToken);

    /** get_design_context for the node, plus get_screenshot when available. */
    Design fetchDesign(String accessToken, FigmaLink link);

    /** Downloads an image or SVG the generated code points at. */
    Asset downloadAsset(String accessToken, String url);

    record Probe(List<String> tools, String account) {
    }

    /** {@code screenshot} is null when the server could not provide one. */
    record Design(McpToolResult context, McpToolResult.Content screenshot) {
    }

    record Asset(byte[] data, String contentType) {
    }
}
