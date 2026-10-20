package com.figmacode.generation;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.figmacode.figma.FigmaLink;
import com.figmacode.figma.FigmaLinkParser;
import com.figmacode.figma.FigmaMcpGateway;
import com.figmacode.figma.FigmaStatusService;
import com.figmacode.figma.FigmaTokenService;
import com.figmacode.mcp.McpException;
import com.figmacode.mcp.McpToolResult;

/** Turns a pasted Figma link into a saved generation: code, notes, screenshot and assets. */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
    private static final int MAX_ASSETS = 60;

    private final FigmaTokenService tokens;
    private final FigmaMcpGateway gateway;
    private final FigmaStatusService statusService;
    private final GenerationRepository generations;
    private final GenerationAssetRepository assets;

    public GenerationService(FigmaTokenService tokens, FigmaMcpGateway gateway, FigmaStatusService statusService,
            GenerationRepository generations, GenerationAssetRepository assets) {
        this.tokens = tokens;
        this.gateway = gateway;
        this.statusService = statusService;
        this.generations = generations;
        this.assets = assets;
    }

    public static class GenerationException extends RuntimeException {
        public GenerationException(String message) {
            super(message);
        }
    }

    @Transactional
    public Generation generate(String username, String figmaUrl) {
        FigmaLink link;
        try {
            link = FigmaLinkParser.parse(figmaUrl);
        } catch (FigmaLinkParser.InvalidFigmaLinkException e) {
            throw new GenerationException(e.getMessage());
        }
        String token = tokens.validAccessToken(username)
                .orElseThrow(() -> new GenerationException("Connect your Figma account first."));

        FigmaMcpGateway.Design design;
        try {
            design = fetchWithRetry(username, token, link);
        } catch (McpException.ToolError e) {
            throw new GenerationException("Figma could not generate code for this frame: " + e.getMessage());
        } catch (McpException e) {
            throw new GenerationException("Figma MCP call failed: " + e.getMessage());
        }

        DesignCodeExtractor.Extracted extracted;
        try {
            extracted = DesignCodeExtractor.extract(design.context().texts());
        } catch (IllegalStateException e) {
            throw new GenerationException(e.getMessage());
        }

        Generation generation = new Generation(username, link.url(), link.fileKey(), link.nodeId());
        generation.setComponentName(ProjectZipper.safeComponentName(extracted.componentName()));
        String code = copyAssets(generation.getId(), token, extracted.code(), extracted.assetUrls());
        generation.setCode(code);
        generation.setOriginalCode(code);
        generation.setNotes(extracted.notes());
        storeScreenshot(generation, design.screenshot());
        storeInlineImages(generation.getId(), design.context());
        return generations.save(generation);
    }

    public List<Generation> recent(String username) {
        return generations.findTop20ByOwnerOrderByCreatedAtDesc(username);
    }

    public Optional<Generation> find(String username, String id) {
        return generations.findByIdAndOwner(id, username);
    }

    public List<GenerationAsset> assetsOf(String generationId) {
        return assets.findByGenerationId(generationId);
    }

    public Optional<GenerationAsset> asset(String generationId, String fileName) {
        return assets.findByGenerationIdAndFileName(generationId, fileName);
    }

    @Transactional
    public void saveCode(String username, String id, String code) {
        Generation generation = find(username, id).orElseThrow(() -> new GenerationException("Not found"));
        generation.setCode(code);
    }

    @Transactional
    public void revert(String username, String id) {
        Generation generation = find(username, id).orElseThrow(() -> new GenerationException("Not found"));
        generation.setCode(generation.getOriginalCode());
    }

    @Transactional
    public void delete(String username, String id) {
        find(username, id).ifPresent(generation -> {
            assets.deleteByGenerationId(generation.getId());
            generations.delete(generation);
        });
    }

    private FigmaMcpGateway.Design fetchWithRetry(String username, String token, FigmaLink link) {
        try {
            return gateway.fetchDesign(token, link);
        } catch (McpException.Unauthorized e) {
            String refreshed = tokens.forceRefresh(username).orElseThrow(() -> {
                statusService.evict(username);
                return new GenerationException("Your Figma connection expired. Reconnect Figma and try again.");
            });
            return gateway.fetchDesign(refreshed, link);
        }
    }

    /** Downloads each asset and points the code at /assets/{file}, which works in the zip and the preview. */
    private String copyAssets(String generationId, String token, String code, Map<String, String> assetUrls) {
        String result = code;
        int count = 0;
        for (Map.Entry<String, String> entry : assetUrls.entrySet()) {
            if (count++ >= MAX_ASSETS) {
                log.info("More than {} assets; leaving the rest as remote URLs", MAX_ASSETS);
                break;
            }
            String url = entry.getValue();
            try {
                FigmaMcpGateway.Asset asset = gateway.downloadAsset(token, url);
                String fileName = entry.getKey() + extension(asset.contentType(), url);
                assets.save(new GenerationAsset(generationId, fileName, asset.contentType(), url, asset.data()));
                result = result.replace(url, "/assets/" + fileName);
            } catch (RuntimeException e) {
                log.info("Could not copy asset {}: {}", url, e.getMessage());
            }
        }
        return result;
    }

    private void storeScreenshot(Generation generation, McpToolResult.Content screenshot) {
        if (screenshot == null || screenshot.data() == null) {
            return;
        }
        try {
            generation.setScreenshot(Base64.getDecoder().decode(screenshot.data()));
            generation.setScreenshotType(screenshot.mimeType() != null ? screenshot.mimeType() : "image/png");
        } catch (IllegalArgumentException e) {
            log.info("Screenshot was not valid base64");
        }
    }

    /** Some MCP responses carry images inline; keep them so the notes panel can reference them. */
    private void storeInlineImages(String generationId, McpToolResult context) {
        int n = 1;
        for (McpToolResult.Content part : context.content()) {
            if ("image".equals(part.type()) && part.data() != null) {
                try {
                    byte[] data = Base64.getDecoder().decode(part.data());
                    String type = part.mimeType() != null ? part.mimeType() : "image/png";
                    assets.save(new GenerationAsset(generationId, "inline" + n++ + extension(type, ""), type, null, data));
                } catch (IllegalArgumentException e) {
                    log.debug("Skipping invalid inline image");
                }
            }
        }
    }

    static String extension(String contentType, String url) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("svg")) return ".svg";
        if (type.contains("png")) return ".png";
        if (type.contains("jpeg") || type.contains("jpg")) return ".jpg";
        if (type.contains("gif")) return ".gif";
        if (type.contains("webp")) return ".webp";
        String path = url.split("\\?")[0].toLowerCase();
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/') && path.length() - dot <= 5) {
            return path.substring(dot);
        }
        return ".png";
    }
}
