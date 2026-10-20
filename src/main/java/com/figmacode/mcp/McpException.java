package com.figmacode.mcp;

public class McpException extends RuntimeException {

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The server answered 401: the access token is missing, expired or revoked. */
    public static class Unauthorized extends McpException {
        public Unauthorized(String message) {
            super(message);
        }
    }

    /** The tool ran but reported an error (isError: true), e.g. node not found or no file access. */
    public static class ToolError extends McpException {
        public ToolError(String message) {
            super(message);
        }
    }
}
