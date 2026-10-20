package com.figmacode.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits get_design_context's text parts into the component code and Figma's notes,
 * and finds the asset URLs the code refers to.
 */
public final class DesignCodeExtractor {

    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern DEFAULT_FUNCTION = Pattern.compile("export\\s+default\\s+function\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern EXPORTED_FUNCTION = Pattern.compile("export\\s+(?:function|const)\\s+([A-Z][\\w$]*)");
    private static final Pattern DEFAULT_IDENTIFIER = Pattern.compile("export\\s+default\\s+([A-Z][\\w$]*)\\s*;");
    private static final Pattern ANY_COMPONENT = Pattern.compile("(?:function|const)\\s+([A-Z][\\w$]*)");
    /** const imgHero = "https://..." : the shape Figma uses for image assets. */
    private static final Pattern NAMED_URL = Pattern.compile(
            "(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([\"'])(https?://[^\"'\\s]+)\\2");
    private static final Pattern ANY_URL = Pattern.compile("([\"'])(https?://[^\"'\\s]+)\\1");

    private DesignCodeExtractor() {
    }

    public record Extracted(String code, String notes, String componentName, Map<String, String> assetUrls) {
    }

    public static Extracted extract(List<String> texts) {
        String bestCode = null;
        int bestScore = 0;
        List<String> notes = new ArrayList<>();
        for (String text : texts) {
            String candidate = unfence(text);
            int score = codeScore(candidate);
            if (score > bestScore) {
                if (bestCode != null) {
                    notes.add(bestCode);
                }
                bestCode = candidate;
                bestScore = score;
            } else {
                notes.add(text.trim());
            }
        }
        if (bestCode == null) {
            throw new IllegalStateException("Figma returned no code for this node. "
                    + "Try selecting a frame or component rather than a page or section.");
        }
        String code = bestCode.strip() + "\n";
        return new Extracted(code, String.join("\n\n", notes).strip(), componentName(code), assetUrls(code));
    }

    /** Asset URL by suggested file base name, in the order they appear. */
    static Map<String, String> assetUrls(String code) {
        Map<String, String> urls = new LinkedHashMap<>();
        Matcher named = NAMED_URL.matcher(code);
        while (named.find()) {
            urls.putIfAbsent(named.group(3), named.group(1));
        }
        Matcher any = ANY_URL.matcher(code);
        int index = 1;
        while (any.find()) {
            String url = any.group(2);
            if (!urls.containsKey(url) && looksLikeAsset(url)) {
                urls.put(url, "asset" + index++);
            }
        }
        Map<String, String> byName = new LinkedHashMap<>();
        urls.forEach((url, name) -> byName.put(uniqueName(byName, name), url));
        return byName;
    }

    static String componentName(String code) {
        for (Pattern pattern : List.of(DEFAULT_FUNCTION, DEFAULT_IDENTIFIER, EXPORTED_FUNCTION, ANY_COMPONENT)) {
            Matcher m = pattern.matcher(code);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "FigmaDesign";
    }

    private static boolean looksLikeAsset(String url) {
        String lower = url.toLowerCase();
        return lower.contains("figma.com/api/mcp/asset") || lower.contains("localhost:3845/assets")
                || lower.contains("figma-alpha-api") || lower.contains("figmausercontent")
                || lower.matches(".*\\.(png|jpe?g|gif|svg|webp)(\\?.*)?$");
    }

    private static String uniqueName(Map<String, String> existing, String name) {
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "");
        if (safe.isEmpty()) {
            safe = "asset";
        }
        String candidate = safe;
        int n = 2;
        while (existing.containsKey(candidate)) {
            candidate = safe + n++;
        }
        return candidate;
    }

    private static String unfence(String text) {
        Matcher m = FENCE.matcher(text);
        String best = null;
        while (m.find()) {
            String block = m.group(1);
            if (best == null || codeScore(block) > codeScore(best)) {
                best = block;
            }
        }
        return best != null ? best : text;
    }

    /** Rough likelihood that a text is component source. 0 = not code. */
    static int codeScore(String text) {
        int score = 0;
        if (text.contains("export default")) score += 5;
        if (text.matches("(?s).*\\bexport\\s+(function|const)\\b.*")) score += 3;
        if (text.contains("return (") || text.contains("return(")) score += 2;
        if (text.contains("className=")) score += 3;
        if (text.matches("(?s).*<[a-z]+[\\s>].*")) score += 1;
        if (text.contains("SUPER CRITICAL") || text.startsWith("Node ids")) score -= 6;
        return Math.max(score, 0);
    }
}
