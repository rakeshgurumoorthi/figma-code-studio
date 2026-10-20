package com.figmacode.figma;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accepts the link shapes Figma's "Copy link to selection" produces:
 * <ul>
 *   <li>https://www.figma.com/design/{fileKey}/{name}?node-id=12-345</li>
 *   <li>https://www.figma.com/file/{fileKey}/{name}?node-id=12%3A345 (older links)</li>
 *   <li>https://www.figma.com/design/{fileKey}/branch/{branchKey}/{name}?node-id=... (the branch key is used)</li>
 *   <li>https://www.figma.com/proto/{fileKey}/... and /make/{fileKey}/...</li>
 * </ul>
 */
public final class FigmaLinkParser {

    private static final Pattern PATH = Pattern.compile(
            "^/(?:design|file|proto|board|make)/([A-Za-z0-9]+)(?:/branch/([A-Za-z0-9]+))?(?:/.*)?$");
    private static final Pattern NODE_ID = Pattern.compile("^(I?\\d+)[-:](\\d+)(.*)$");

    private FigmaLinkParser() {
    }

    public static FigmaLink parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFigmaLinkException("Paste a Figma link first.");
        }
        String trimmed = raw.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidFigmaLinkException("That doesn't look like a link.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(host.equals("figma.com") || host.endsWith(".figma.com"))) {
            throw new InvalidFigmaLinkException("The link must point to figma.com.");
        }
        Matcher path = PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (!path.matches()) {
            throw new InvalidFigmaLinkException("Use a link to a Figma design file (figma.com/design/...).");
        }
        String fileKey = path.group(2) != null ? path.group(2) : path.group(1);

        String nodeParam = queryParam(uri.getRawQuery(), "node-id");
        if (nodeParam == null || nodeParam.isBlank()) {
            throw new InvalidFigmaLinkException(
                    "The link has no node-id. In Figma, select a frame, then use Copy link to selection.");
        }
        Matcher node = NODE_ID.matcher(nodeParam);
        if (!node.matches()) {
            throw new InvalidFigmaLinkException("The node-id in the link (" + nodeParam + ") is not recognised.");
        }
        String nodeId = node.group(1) + ":" + node.group(2) + node.group(3).replace('-', ':');
        return new FigmaLink(trimmed, fileKey, nodeId);
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (key.equals(name)) {
                return eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public static class InvalidFigmaLinkException extends RuntimeException {
        public InvalidFigmaLinkException(String message) {
            super(message);
        }
    }
}
