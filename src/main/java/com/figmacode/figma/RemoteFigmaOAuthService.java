package com.figmacode.figma;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figmacode.config.AppProperties;

/**
 * Talks to Figma's OAuth server with the client id and secret Figma issued for this app.
 * Endpoints come from config when set; otherwise they are discovered the way the MCP spec
 * describes: the MCP server's protected-resource metadata names the authorization server,
 * whose metadata names the authorize and token endpoints.
 */
@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "false", matchIfMissing = true)
public class RemoteFigmaOAuthService implements FigmaOAuthService {

    private static final Logger log = LoggerFactory.getLogger(RemoteFigmaOAuthService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;
    private final ObjectMapper json;
    private final AppProperties.Figma figma;
    private volatile Endpoints endpoints;

    public RemoteFigmaOAuthService(HttpClient http, ObjectMapper json, AppProperties props) {
        this.http = http;
        this.json = json;
        this.figma = props.getFigma();
    }

    record Endpoints(String authorization, String token, List<String> scopes) {
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge, String redirectUri) {
        AppProperties.OAuth oauth = requireClient();
        Endpoints ep = endpoints();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", oauth.getClientId());
        params.put("redirect_uri", redirectUri);
        params.put("state", state);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        String scope = !oauth.getScopes().isBlank() ? oauth.getScopes() : String.join(" ", ep.scopes());
        if (!scope.isBlank()) {
            params.put("scope", scope);
        }
        // RFC 8707 resource indicator, required by the MCP authorization spec
        params.put("resource", figma.getMcpUrl());
        return ep.authorization() + (ep.authorization().contains("?") ? "&" : "?") + form(params);
    }

    @Override
    public Tokens exchangeCode(String code, String codeVerifier, String redirectUri) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("code_verifier", codeVerifier);
        params.put("resource", figma.getMcpUrl());
        return tokenRequest(params, null);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        params.put("resource", figma.getMcpUrl());
        return tokenRequest(params, refreshToken);
    }

    private Tokens tokenRequest(Map<String, String> params, String previousRefreshToken) {
        AppProperties.OAuth oauth = requireClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoints().token()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        if ("client_secret_post".equalsIgnoreCase(oauth.getClientAuthMethod())) {
            params.put("client_id", oauth.getClientId());
            params.put("client_secret", oauth.getClientSecret());
        } else {
            String basic = Base64.getEncoder().encodeToString(
                    (urlEncode(oauth.getClientId()) + ":" + urlEncode(oauth.getClientSecret()))
                            .getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
            params.put("client_id", oauth.getClientId());
        }
        HttpResponse<String> response = send(builder.POST(HttpRequest.BodyPublishers.ofString(form(params))).build());
        JsonNode body = readJson(response.body());
        if (response.statusCode() != 200 || !body.hasNonNull("access_token")) {
            String error = body.path("error_description").asText(body.path("error").asText("HTTP " + response.statusCode()));
            log.warn("Figma token request ({}) failed: {}", params.get("grant_type"), error);
            throw new OAuthException("Figma refused the token request: " + error);
        }
        long expiresIn = body.path("expires_in").asLong(0);
        return new Tokens(
                body.get("access_token").asText(),
                body.path("refresh_token").asText(previousRefreshToken),
                expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null,
                body.path("scope").asText(""));
    }

    private Endpoints endpoints() {
        Endpoints cached = endpoints;
        if (cached != null) {
            return cached;
        }
        AppProperties.OAuth oauth = figma.getOauth();
        if (!oauth.getAuthorizationUri().isBlank() && !oauth.getTokenUri().isBlank()) {
            cached = new Endpoints(oauth.getAuthorizationUri(), oauth.getTokenUri(), List.of());
        } else {
            cached = discover();
        }
        endpoints = cached;
        return cached;
    }

    /** MCP authorization discovery: protected resource metadata, then authorization server metadata. */
    private Endpoints discover() {
        URI mcp = URI.create(figma.getMcpUrl());
        String origin = mcp.getScheme() + "://" + mcp.getAuthority();
        String path = mcp.getPath() == null ? "" : mcp.getPath();

        JsonNode resource = firstJson(List.of(
                origin + "/.well-known/oauth-protected-resource" + path,
                origin + "/.well-known/oauth-protected-resource"));
        String issuer = resource != null && resource.path("authorization_servers").size() > 0
                ? resource.path("authorization_servers").get(0).asText()
                : origin;
        List<String> scopes = resource == null ? List.of()
                : streamText(resource.path("scopes_supported"));

        URI issuerUri = URI.create(issuer);
        String issuerOrigin = issuerUri.getScheme() + "://" + issuerUri.getAuthority();
        String issuerPath = issuerUri.getPath() == null || issuerUri.getPath().equals("/") ? "" : issuerUri.getPath();
        JsonNode server = firstJson(List.of(
                issuerOrigin + "/.well-known/oauth-authorization-server" + issuerPath,
                issuerOrigin + "/.well-known/openid-configuration" + issuerPath,
                issuer.replaceAll("/$", "") + "/.well-known/openid-configuration"));
        if (server == null || !server.hasNonNull("authorization_endpoint") || !server.hasNonNull("token_endpoint")) {
            throw new OAuthException("Could not discover Figma's OAuth endpoints from " + figma.getMcpUrl()
                    + ". Set app.figma.oauth.authorization-uri and token-uri.");
        }
        if (scopes.isEmpty()) {
            scopes = streamText(server.path("scopes_supported"));
        }
        log.info("Discovered Figma OAuth endpoints: authorize={} token={}",
                server.get("authorization_endpoint").asText(), server.get("token_endpoint").asText());
        return new Endpoints(server.get("authorization_endpoint").asText(), server.get("token_endpoint").asText(), scopes);
    }

    private JsonNode firstJson(List<String> urls) {
        for (String url : urls) {
            try {
                HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                        .header("Accept", "application/json").GET().build());
                if (response.statusCode() == 200) {
                    return json.readTree(response.body());
                }
            } catch (IOException | OAuthException e) {
                log.debug("Metadata lookup {} failed: {}", url, e.toString());
            }
        }
        return null;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new OAuthException("Could not reach Figma: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("Interrupted while calling Figma", e);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException e) {
            return json.createObjectNode();
        }
    }

    private AppProperties.OAuth requireClient() {
        AppProperties.OAuth oauth = figma.getOauth();
        if (oauth.getClientId().isBlank() || oauth.getClientSecret().isBlank()) {
            throw new OAuthException("Figma client id and secret are not configured. "
                    + "Set FIGMA_CLIENT_ID and FIGMA_CLIENT_SECRET (see README).");
        }
        return oauth;
    }

    private static List<String> streamText(JsonNode array) {
        return array.isArray()
                ? java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList()
                : List.of();
    }

    private static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
