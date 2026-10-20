package com.figmacode.generation;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** An image the generated code uses, copied from Figma because Figma's asset URLs expire. */
@Entity
@Table(name = "generation_asset", indexes = @Index(name = "idx_asset_generation", columnList = "generationId"))
public class GenerationAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String generationId;

    /** File name inside the project's public/assets folder, e.g. imgHero.png. */
    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(length = 2000)
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.LONG32VARBINARY)
    @Column(nullable = false)
    private byte[] data;

    protected GenerationAsset() {
    }

    public GenerationAsset(String generationId, String fileName, String contentType, String sourceUrl, byte[] data) {
        this.generationId = generationId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sourceUrl = sourceUrl;
        this.data = data;
    }

    public Long getId() { return id; }
    public String getGenerationId() { return generationId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getSourceUrl() { return sourceUrl; }
    public byte[] getData() { return data; }
}
