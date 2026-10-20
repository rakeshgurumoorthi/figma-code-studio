package com.figmacode.figma;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.figmacode.crypto.TokenCipher;

/** Stores each user's Figma tokens encrypted and refreshes them shortly before they expire. */
@Service
public class FigmaTokenService {

    private static final Logger log = LoggerFactory.getLogger(FigmaTokenService.class);
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

    private final FigmaConnectionRepository repository;
    private final FigmaOAuthService oauth;
    private final TokenCipher cipher;

    public FigmaTokenService(FigmaConnectionRepository repository, FigmaOAuthService oauth, TokenCipher cipher) {
        this.repository = repository;
        this.oauth = oauth;
        this.cipher = cipher;
    }

    @Transactional
    public void save(String username, FigmaOAuthService.Tokens tokens) {
        FigmaConnection connection = repository.findById(username).orElseGet(() -> new FigmaConnection(username));
        apply(connection, tokens);
        connection.setConnectedAt(Instant.now());
        repository.save(connection);
    }

    @Transactional
    public void setAccount(String username, String account) {
        repository.findById(username).ifPresent(c -> c.setFigmaAccount(account));
    }

    public Optional<FigmaConnection> find(String username) {
        return repository.findById(username);
    }

    /** A usable access token, refreshed first if it is about to expire. Empty when not connected or revoked. */
    @Transactional
    public Optional<String> validAccessToken(String username) {
        Optional<FigmaConnection> found = repository.findById(username);
        if (found.isEmpty() || found.get().isRevoked()) {
            return Optional.empty();
        }
        FigmaConnection connection = found.get();
        Instant expiresAt = connection.getExpiresAt();
        if (expiresAt != null && Instant.now().plus(REFRESH_MARGIN).isAfter(expiresAt)) {
            return refresh(connection);
        }
        try {
            return Optional.of(cipher.decrypt(connection.getAccessTokenEncrypted()));
        } catch (TokenCipher.TokenDecryptionException e) {
            log.warn("Dropping unreadable Figma token for {}", username);
            connection.setRevoked(true);
            return Optional.empty();
        }
    }

    /** Called after Figma answered 401. Tries one refresh; marks the connection revoked if that fails. */
    @Transactional
    public Optional<String> forceRefresh(String username) {
        return repository.findById(username).flatMap(this::refresh);
    }

    @Transactional
    public void disconnect(String username) {
        repository.deleteById(username);
    }

    private Optional<String> refresh(FigmaConnection connection) {
        String refreshToken = null;
        try {
            refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted());
        } catch (TokenCipher.TokenDecryptionException e) {
            log.debug("Refresh token unreadable", e);
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            connection.setRevoked(true);
            return Optional.empty();
        }
        try {
            FigmaOAuthService.Tokens tokens = oauth.refresh(refreshToken);
            apply(connection, tokens);
            return Optional.of(tokens.accessToken());
        } catch (RuntimeException e) {
            log.info("Figma token refresh failed for {}: {}", connection.getUsername(), e.getMessage());
            connection.setRevoked(true);
            return Optional.empty();
        }
    }

    private void apply(FigmaConnection connection, FigmaOAuthService.Tokens tokens) {
        connection.setAccessTokenEncrypted(cipher.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            connection.setRefreshTokenEncrypted(cipher.encrypt(tokens.refreshToken()));
        }
        connection.setExpiresAt(tokens.expiresAt());
        connection.setScope(tokens.scope());
        connection.setRevoked(false);
    }
}
