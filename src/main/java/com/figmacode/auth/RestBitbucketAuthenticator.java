package com.figmacode.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figmacode.auth.BitbucketAuthException.Reason;
import com.figmacode.config.AppProperties;

/**
 * Verifies credentials with one HTTP Basic call to the Bitbucket DC REST API.
 * Bitbucket answers 401 for a wrong password, 403 with X-Authentication-Denied-Reason when a
 * CAPTCHA is required, and sets X-AUSERNAME on every authenticated response.
 */
@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "false", matchIfMissing = true)
public class RestBitbucketAuthenticator implements BitbucketAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(RestBitbucketAuthenticator.class);

    private final HttpClient http;
    private final AppProperties.Bitbucket config;
    private final ObjectMapper json;

    public RestBitbucketAuthenticator(HttpClient http, AppProperties props, ObjectMapper json) {
        this.http = http;
        this.config = props.getBitbucket();
        this.json = json;
    }

    @Override
    public BitbucketUser authenticate(String username, String password) {
        String baseUrl = trimSlash(config.getBaseUrl());
        if (baseUrl.isBlank()) {
            throw new BitbucketAuthException(Reason.UNAVAILABLE,
                    "Bitbucket URL is not configured (app.bitbucket.base-url).");
        }
        String basic = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create(baseUrl + "/rest/api/1.0/users?limit=25&filter="
                + URLEncoder.encode(username, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.getReadTimeout())
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .header("X-Atlassian-Token", "no-check")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Bitbucket unreachable at {}: {}", baseUrl, e.toString());
            throw new BitbucketAuthException(Reason.UNAVAILABLE, "Bitbucket is unreachable right now.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BitbucketAuthException(Reason.UNAVAILABLE, "Sign-in was interrupted.", e);
        }

        int status = response.statusCode();
        String deniedReason = response.headers().firstValue("X-Authentication-Denied-Reason").orElse("");
        if (status == 401) {
            throw new BitbucketAuthException(Reason.BAD_CREDENTIALS, "Wrong Bitbucket username or password.");
        }
        if (status == 403 && deniedReason.toLowerCase().contains("captcha")) {
            throw new BitbucketAuthException(Reason.CAPTCHA_REQUIRED,
                    "Bitbucket wants a CAPTCHA. Sign in to Bitbucket in your browser once, then try again.");
        }
        if (status == 403) {
            throw new BitbucketAuthException(Reason.BASIC_AUTH_REJECTED,
                    "Bitbucket refused password sign-in for this account" + (deniedReason.isBlank() ? "." : ": " + deniedReason));
        }
        if (status != 200) {
            log.warn("Unexpected Bitbucket status {} for user lookup", status);
            throw new BitbucketAuthException(Reason.UNAVAILABLE, "Bitbucket returned HTTP " + status + ".");
        }

        // A 200 without X-AUSERNAME means Bitbucket served the request anonymously, so the
        // password was never checked.
        String authenticatedAs = response.headers().firstValue("X-AUSERNAME").orElse("");
        if (authenticatedAs.isBlank()) {
            throw new BitbucketAuthException(Reason.BAD_CREDENTIALS, "Wrong Bitbucket username or password.");
        }
        return toUser(authenticatedAs, response.body());
    }

    private BitbucketUser toUser(String authenticatedAs, String body) {
        try {
            JsonNode values = json.readTree(body).path("values");
            for (JsonNode user : values) {
                if (authenticatedAs.equalsIgnoreCase(user.path("name").asText())) {
                    return new BitbucketUser(
                            user.path("name").asText(authenticatedAs),
                            user.path("slug").asText(authenticatedAs.toLowerCase()),
                            user.path("displayName").asText(authenticatedAs),
                            user.path("emailAddress").asText(""));
                }
            }
        } catch (IOException e) {
            log.debug("Could not read Bitbucket user list", e);
        }
        return new BitbucketUser(authenticatedAs, authenticatedAs.toLowerCase(), authenticatedAs, "");
    }

    private static String trimSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
