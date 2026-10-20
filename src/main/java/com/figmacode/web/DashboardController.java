package com.figmacode.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.figmacode.auth.CurrentUser;
import com.figmacode.generation.GenerationService;

@Controller
public class DashboardController {

    private final GenerationService generations;

    public DashboardController(GenerationService generations) {
        this.generations = generations;
    }

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("history", generations.recent(user.username()));
        return "dashboard";
    }
}
