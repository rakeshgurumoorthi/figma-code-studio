package com.figmacode.figma;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Mock mode: sends the browser to a local consent page instead of Figma and hands out fake tokens. */
@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "true")
public class MockFigmaOAuthService implements FigmaOAuthService {

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        return "/figma/mock-authorize?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    @Override
    public Tokens exchangeCode(String code, String codeVerifier, String redirectUri) {
        if (!"mock-code".equals(code)) {
            throw new OAuthException("Figma refused the token request: invalid_grant");
        }
        return newTokens();
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return newTokens();
    }

    private static Tokens newTokens() {
        return new Tokens("mock-access-" + UUID.randomUUID(), "mock-refresh-" + UUID.randomUUID(),
                Instant.now().plusSeconds(3600), "mcp:connect");
    }
}
