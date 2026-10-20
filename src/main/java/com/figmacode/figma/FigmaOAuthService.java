package com.figmacode.figma;

import java.time.Instant;

/** The OAuth 2.1 (authorization code + PKCE) flow that grants this app access to a user's Figma MCP. */
public interface FigmaOAuthService {

    /** The URL to send the browser to. */
    String authorizationUrl(String state, String codeChallenge, String redirectUri);

    Tokens exchangeCode(String code, String codeVerifier, String redirectUri);

    Tokens refresh(String refreshToken);

    record Tokens(String accessToken, String refreshToken, Instant expiresAt, String scope) {
    }

    class OAuthException extends RuntimeException {
        public OAuthException(String message) {
            super(message);
        }

        public OAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
