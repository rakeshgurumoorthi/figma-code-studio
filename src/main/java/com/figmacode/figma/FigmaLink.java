package com.figmacode.figma;

/** A Figma file key and node id taken from a pasted link. {@code nodeId} uses the API form "12:345". */
public record FigmaLink(String url, String fileKey, String nodeId) {
}
