package com.figmacode.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.figmacode.auth.CurrentUser;
import com.figmacode.config.AppProperties;

/** Values every page template can use. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final AppProperties props;

    public GlobalModelAttributes(AppProperties props) {
        this.props = props;
    }

    @ModelAttribute("mockMode")
    public boolean mockMode() {
        return props.isMockMode();
    }

    @ModelAttribute("currentUser")
    public CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        return null;
    }
}
