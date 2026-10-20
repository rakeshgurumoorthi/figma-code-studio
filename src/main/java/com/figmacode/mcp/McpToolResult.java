package com.figmacode.mcp;

import java.util.List;

/** The content parts returned by an MCP tools/call. */
public record McpToolResult(List<Content> content, boolean isError) {

    /**
     * One content part. {@code type} is "text", "image", "resource_link" or "resource";
     * {@code data} is base64 for images.
     */
    public record Content(String type, String text, String data, String mimeType, String uri) {

        public static Content text(String text) {
            return new Content("text", text, null, null, null);
        }

        public static Content image(String base64, String mimeType) {
            return new Content("image", null, base64, mimeType, null);
        }
    }

    public List<String> texts() {
        return content.stream().filter(c -> "text".equals(c.type()) && c.text() != null).map(Content::text).toList();
    }
}
