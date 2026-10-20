package com.figmacode.generation;

import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.figmacode.auth.CurrentUser;
import com.figmacode.figma.FigmaStatusService;

@Controller
public class GenerationController {

    private final GenerationService service;
    private final ProjectZipper zipper;
    private final FigmaStatusService statusService;

    public GenerationController(GenerationService service, ProjectZipper zipper, FigmaStatusService statusService) {
        this.service = service;
        this.zipper = zipper;
        this.statusService = statusService;
    }

    @PostMapping("/generations")
    public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String figmaUrl,
            RedirectAttributes redirect) {
        try {
            Generation generation = service.generate(user.username(), figmaUrl);
            return "redirect:/generations/" + generation.getId();
        } catch (GenerationService.GenerationException e) {
            statusService.evict(user.username());
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("figmaUrl", figmaUrl);
            return "redirect:/";
        }
    }

    @GetMapping("/generations/{id}")
    public String show(@AuthenticationPrincipal CurrentUser user, @PathVariable String id, Model model) {
        Generation generation = load(user, id);
        model.addAttribute("generation", generation);
        model.addAttribute("assets", service.assetsOf(id));
        return "generation";
    }

    @PostMapping(path = "/generations/{id}/code", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> saveCode(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @RequestBody Map<String, String> body) {
        load(user, id);
        String code = body.getOrDefault("code", "");
        if (code.length() > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Code is too large");
        }
        service.saveCode(user.username(), id, code);
        return Map.of("saved", true);
    }

    @PostMapping("/generations/{id}/revert")
    public String revert(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        load(user, id);
        service.revert(user.username(), id);
        return "redirect:/generations/" + id;
    }

    @PostMapping("/generations/{id}/delete")
    public String delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            RedirectAttributes redirect) {
        service.delete(user.username(), id);
        redirect.addFlashAttribute("success", "Generation deleted.");
        return "redirect:/";
    }

    @GetMapping("/generations/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        Generation generation = load(user, id);
        byte[] zip = zipper.zip(generation, service.assetsOf(id));
        String fileName = ProjectZipper.kebab(ProjectZipper.safeComponentName(generation.getComponentName())) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @GetMapping("/generations/{id}/assets/{fileName:.+}")
    public ResponseEntity<byte[]> asset(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @PathVariable String fileName) {
        load(user, id);
        GenerationAsset asset = service.asset(id, fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return image(asset.getData(), asset.getContentType());
    }

    @GetMapping("/generations/{id}/screenshot")
    public ResponseEntity<byte[]> screenshot(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        Generation generation = load(user, id);
        if (!generation.hasScreenshot()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return image(generation.getScreenshot(), generation.getScreenshotType());
    }

    private Generation load(CurrentUser user, String id) {
        return service.find(user.username(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static ResponseEntity<byte[]> image(byte[] data, String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // SVGs can carry script; never let them run in our origin
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:")
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }
}
