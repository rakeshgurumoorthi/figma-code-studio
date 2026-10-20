package com.figmacode.preview;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.figmacode.auth.CurrentUser;
import com.figmacode.generation.Generation;
import com.figmacode.generation.GenerationAsset;
import com.figmacode.generation.GenerationService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The document loaded into the sandboxed preview iframe. It compiles the TSX in the browser
 * with Babel and renders it with React 18 and Tailwind's browser build. The iframe has no
 * allow-same-origin, so this page runs with an opaque origin: it cannot read our cookies or DOM,
 * and it receives later edits from the editor only through postMessage.
 */
@Controller
public class PreviewController {

    static final String CSP = String.join("; ",
            "default-src 'none'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: https:",
            "font-src 'self' data: https:",
            "connect-src 'none'",
            "form-action 'none'",
            "base-uri 'none'",
            "frame-ancestors 'self'");

    private final GenerationService service;

    public PreviewController(GenerationService service) {
        this.service = service;
    }

    @GetMapping("/preview/{id}")
    public String preview(@AuthenticationPrincipal CurrentUser user, @PathVariable String id, Model model,
            HttpServletResponse response) {
        Generation generation = service.find(user.username(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Assets go in as data: URIs because the opaque-origin frame cannot send our session cookie
        Map<String, String> assets = new LinkedHashMap<>();
        for (GenerationAsset asset : service.assetsOf(id)) {
            assets.put("/assets/" + asset.getFileName(),
                    "data:" + asset.getContentType() + ";base64," + Base64.getEncoder().encodeToString(asset.getData()));
        }
        model.addAttribute("code", generation.getCode());
        model.addAttribute("assets", assets);
        model.addAttribute("componentName", generation.getComponentName());
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("Cache-Control", "no-store");
        return "preview";
    }
}
