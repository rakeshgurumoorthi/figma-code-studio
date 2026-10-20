package com.figmacode.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the app reads from {@code application.yml} under the {@code app} prefix.
 * Each value can also be set through an environment variable, e.g. {@code APP_FIGMA_OAUTH_CLIENT_ID}.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** When true, Bitbucket and Figma are simulated so every screen can be tried offline. */
    private boolean mockMode = false;

    private final Bitbucket bitbucket = new Bitbucket();
    private final Figma figma = new Figma();
    private final Security security = new Security();

    public boolean isMockMode() { return mockMode; }
    public void setMockMode(boolean mockMode) { this.mockMode = mockMode; }
    public Bitbucket getBitbucket() { return bitbucket; }
    public Figma getFigma() { return figma; }
    public Security getSecurity() { return security; }

    public static class Bitbucket {
        /** Base URL of Bitbucket Data Center, e.g. https://bitbucket.example.com (no trailing slash). */
        private String baseUrl = "";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(10);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    public static class Figma {
        /** Figma's remote MCP endpoint (Streamable HTTP). */
        private String mcpUrl = "https://mcp.figma.com/mcp";
        /** Passed to get_design_context so the output matches what the preview can run. */
        private String clientLanguages = "typescript,html,css";
        private String clientFrameworks = "react,tailwind";
        private Duration callTimeout = Duration.ofSeconds(90);
        /** How long a successful status check is reused before Figma is asked again. */
        private Duration statusCacheTtl = Duration.ofMinutes(5);
        private final OAuth oauth = new OAuth();

        public String getMcpUrl() { return mcpUrl; }
        public void setMcpUrl(String mcpUrl) { this.mcpUrl = mcpUrl; }
        public String getClientLanguages() { return clientLanguages; }
        public void setClientLanguages(String clientLanguages) { this.clientLanguages = clientLanguages; }
        public String getClientFrameworks() { return clientFrameworks; }
        public void setClientFrameworks(String clientFrameworks) { this.clientFrameworks = clientFrameworks; }
        public Duration getCallTimeout() { return callTimeout; }
        public void setCallTimeout(Duration callTimeout) { this.callTimeout = callTimeout; }
        public Duration getStatusCacheTtl() { return statusCacheTtl; }
        public void setStatusCacheTtl(Duration statusCacheTtl) { this.statusCacheTtl = statusCacheTtl; }
        public OAuth getOauth() { return oauth; }
    }

    public static class OAuth {
        /** Client id Figma issued for the whitelisted app. */
        private String clientId = "";
        /** Client secret Figma issued for the whitelisted app. Keep it out of git: set FIGMA_CLIENT_SECRET. */
        private String clientSecret = "";
        /** Leave blank to discover from the MCP server's OAuth metadata. */
        private String authorizationUri = "";
        /** Leave blank to discover from the MCP server's OAuth metadata. */
        private String tokenUri = "";
        /** Space separated. Leave blank to request the scopes the server advertises. */
        private String scopes = "";
        /** Leave blank to use {current host}/figma/callback. Must match what is registered with Figma. */
        private String redirectUri = "";
        /** client_secret_basic (HTTP Basic header) or client_secret_post (form fields). */
        private String clientAuthMethod = "client_secret_basic";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAuthorizationUri() { return authorizationUri; }
        public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }
        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getClientAuthMethod() { return clientAuthMethod; }
        public void setClientAuthMethod(String clientAuthMethod) { this.clientAuthMethod = clientAuthMethod; }
    }

    public static class Security {
        /** Base64 encoded 32 byte key used to encrypt Figma tokens at rest. Blank = random key per run. */
        private String tokenEncryptionKey = "";
        private int maxFailedLoginsPerWindow = 5;
        private Duration failedLoginWindow = Duration.ofMinutes(5);

        public String getTokenEncryptionKey() { return tokenEncryptionKey; }
        public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }
        public int getMaxFailedLoginsPerWindow() { return maxFailedLoginsPerWindow; }
        public void setMaxFailedLoginsPerWindow(int maxFailedLoginsPerWindow) { this.maxFailedLoginsPerWindow = maxFailedLoginsPerWindow; }
        public Duration getFailedLoginWindow() { return failedLoginWindow; }
        public void setFailedLoginWindow(Duration failedLoginWindow) { this.failedLoginWindow = failedLoginWindow; }
    }
}
