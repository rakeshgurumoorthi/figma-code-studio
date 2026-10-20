package com.figmacode.figma;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One Bitbucket user's Figma OAuth tokens, encrypted. */
@Entity
@Table(name = "figma_connection")
public class FigmaConnection {

    @Id
    @Column(length = 255)
    private String username;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String accessTokenEncrypted;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String refreshTokenEncrypted;

    private Instant expiresAt;

    @Column(length = 1000)
    private String scope;

    @Column(length = 255)
    private String figmaAccount;

    private Instant connectedAt;

    /** Set when Figma rejected the token and it could not be refreshed. */
    private boolean revoked;

    protected FigmaConnection() {
    }

    public FigmaConnection(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String value) { this.accessTokenEncrypted = value; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String value) { this.refreshTokenEncrypted = value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getFigmaAccount() { return figmaAccount; }
    public void setFigmaAccount(String figmaAccount) { this.figmaAccount = figmaAccount; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
