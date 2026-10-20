package com.figmacode.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Authentication authentication, HttpSession session, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        Object error = session.getAttribute("LOGIN_ERROR");
        Object username = session.getAttribute("LOGIN_USERNAME");
        session.removeAttribute("LOGIN_ERROR");
        session.removeAttribute("LOGIN_USERNAME");
        model.addAttribute("loginError", error);
        model.addAttribute("lastUsername", username);
        return "login";
    }
}
