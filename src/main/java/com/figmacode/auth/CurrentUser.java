package com.figmacode.auth;

import java.io.Serializable;
import java.security.Principal;

/** The signed-in Bitbucket user, kept in the HTTP session. */
public record CurrentUser(String username, String slug, String displayName, String email)
        implements Principal, Serializable {

    @Override
    public String getName() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }
}
