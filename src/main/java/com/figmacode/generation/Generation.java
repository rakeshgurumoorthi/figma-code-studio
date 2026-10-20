package com.figmacode.generation;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One run of get_design_context: the link, the code it produced and any later edits. */
@Entity
@Table(name = "generation", indexes = @Index(name = "idx_generation_owner", columnList = "owner, createdAt"))
public class Generation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String owner;

    @Column(nullable = false, length = 2000)
    private String figmaUrl;

    @Column(nullable = false, length = 255)
    private String fileKey;

    @Column(nullable = false, length = 255)
    private String nodeId;

    @Column(nullable = false, length = 100)
    private String componentName;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String code;

    /** The code exactly as Figma returned it (after asset rewriting), so edits can be reverted. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String originalCode;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String notes;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY)
    private byte[] screenshot;

    @Column(length = 100)
    private String screenshotType;

    private Instant createdAt;
    private Instant updatedAt;

    protected Generation() {
    }

    public Generation(String owner, String figmaUrl, String fileKey, String nodeId) {
        this.id = UUID.randomUUID().toString();
        this.owner = owner;
        this.figmaUrl = figmaUrl;
        this.fileKey = fileKey;
        this.nodeId = nodeId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getOwner() { return owner; }
    public String getFigmaUrl() { return figmaUrl; }
    public String getFileKey() { return fileKey; }
    public String getNodeId() { return nodeId; }
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; this.updatedAt = Instant.now(); }
    public String getOriginalCode() { return originalCode; }
    public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public byte[] getScreenshot() { return screenshot; }
    public void setScreenshot(byte[] screenshot) { this.screenshot = screenshot; }
    public String getScreenshotType() { return screenshotType; }
    public void setScreenshotType(String screenshotType) { this.screenshotType = screenshotType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isEdited() { return code != null && !code.equals(originalCode); }
    public boolean hasScreenshot() { return screenshotType != null; }
}
