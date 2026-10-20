package com.figmacode.figma;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.figmacode.auth.CurrentUser;
import com.figmacode.config.AppProperties;

import jakarta.servlet.http.HttpSession;

@Controller
public class FigmaController {

    private static final String STATE = "FIGMA_OAUTH_STATE";
    private static final String VERIFIER = "FIGMA_OAUTH_VERIFIER";
    private static final String REDIRECT = "FIGMA_OAUTH_REDIRECT";

    private final FigmaOAuthService oauth;
    private final FigmaTokenService tokens;
    private final FigmaStatusService statusService;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public FigmaController(FigmaOAuthService oauth, FigmaTokenService tokens, FigmaStatusService statusService,
            AppProperties props) {
        this.oauth = oauth;
        this.tokens = tokens;
        this.statusService = statusService;
        this.props = props;
    }

    /** htmx fragment for the dashboard's connection card. */
    @GetMapping("/figma/status")
    public String status(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "false") boolean refresh, Model model) {
        model.addAttribute("status", statusService.status(user.username(), refresh));
        return "fragments/figma-status :: card";
    }

    @GetMapping("/figma/connect")
    public String connect(HttpSession session, RedirectAttributes redirect) {
        String state = randomToken();
        String verifier = randomToken() + randomToken();
        String redirectUri = redirectUri();
        session.setAttribute(STATE, state);
        session.setAttribute(VERIFIER, verifier);
        session.setAttribute(REDIRECT, redirectUri);
        try {
            return "redirect:" + oauth.authorizationUrl(state, challenge(verifier), redirectUri);
        } catch (FigmaOAuthService.OAuthException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/figma/callback")
    public String callback(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpSession session, RedirectAttributes redirect) {
        String expectedState = (String) session.getAttribute(STATE);
        String verifier = (String) session.getAttribute(VERIFIER);
        String redirectUri = (String) session.getAttribute(REDIRECT);
        session.removeAttribute(STATE);
        session.removeAttribute(VERIFIER);
        session.removeAttribute(REDIRECT);

        if (error != null) {
            redirect.addFlashAttribute("error", "Figma connection was not completed: "
                    + (errorDescription != null ? errorDescription : error));
            return "redirect:/";
        }
        if (expectedState == null || !expectedState.equals(state) || code == null) {
            redirect.addFlashAttribute("error", "The Figma sign-in expired or did not match. Please try again.");
            return "redirect:/";
        }
        try {
            tokens.save(user.username(), oauth.exchangeCode(code, verifier, redirectUri));
            statusService.evict(user.username());
            redirect.addFlashAttribute("success", "Figma is connected.");
        } catch (FigmaOAuthService.OAuthException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/figma/disconnect")
    public String disconnect(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        tokens.disconnect(user.username());
        statusService.evict(user.username());
        redirect.addFlashAttribute("success", "Figma was disconnected from this app.");
        return "redirect:/";
    }

    /** Mock mode only: a stand-in for Figma's consent screen. */
    @GetMapping("/figma/mock-authorize")
    public String mockAuthorize(@RequestParam String state, @RequestParam("redirect_uri") String redirectUri,
            Model model) {
        if (!props.isMockMode()) {
            return "redirect:/";
        }
        model.addAttribute("state", state);
        model.addAttribute("redirectUri", redirectUri);
        return "figma/mock-authorize";
    }

    private String redirectUri() {
        String configured = props.getFigma().getOauth().getRedirectUri();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/figma/callback").toUriString();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
