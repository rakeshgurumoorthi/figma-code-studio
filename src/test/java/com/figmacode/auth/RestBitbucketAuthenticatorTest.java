package com.figmacode.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.figmacode.auth.BitbucketAuthException.Reason;
import com.figmacode.config.AppProperties;
import com.sun.net.httpserver.HttpServer;

/** Fake Bitbucket that behaves like Data Center for the users endpoint. */
class RestBitbucketAuthenticatorTest {

    private HttpServer server;
    private RestBitbucketAuthenticator authenticator;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/1.0/users", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String decoded = auth == null ? "" : new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
            switch (decoded) {
                case "jdoe:secret" -> {
                    byte[] body = """
                            {"values":[{"name":"jdoe","slug":"jdoe","displayName":"Jane Doe","emailAddress":"jane@corp.com"}]}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("X-AUSERNAME", "jdoe");
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
                case "locked:secret" -> {
                    exchange.getResponseHeaders().add("X-Authentication-Denied-Reason",
                            "CAPTCHA required; location=https://bb/login");
                    exchange.sendResponseHeaders(403, -1);
                }
                case "anon:secret" -> {
                    byte[] body = "{\"values\":[]}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
                default -> exchange.sendResponseHeaders(401, -1);
            }
            exchange.close();
        });
        server.start();
        AppProperties props = new AppProperties();
        props.getBitbucket().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        authenticator = new RestBitbucketAuthenticator(HttpClient.newHttpClient(), props, new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void acceptsValidCredentials() {
        BitbucketAuthenticator.BitbucketUser user = authenticator.authenticate("jdoe", "secret");
        assertThat(user.displayName()).isEqualTo("Jane Doe");
        assertThat(user.email()).isEqualTo("jane@corp.com");
    }

    @Test
    void rejectsWrongPassword() {
        assertThatThrownBy(() -> authenticator.authenticate("jdoe", "nope"))
                .isInstanceOfSatisfying(BitbucketAuthException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.BAD_CREDENTIALS));
    }

    @Test
    void detectsCaptcha() {
        assertThatThrownBy(() -> authenticator.authenticate("locked", "secret"))
                .isInstanceOfSatisfying(BitbucketAuthException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.CAPTCHA_REQUIRED));
    }

    @Test
    void treatsAnonymousSuccessAsFailure() {
        assertThatThrownBy(() -> authenticator.authenticate("anon", "secret"))
                .isInstanceOfSatisfying(BitbucketAuthException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.BAD_CREDENTIALS));
    }
}
