package com.figmacode.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.figmacode.auth.BitbucketAuthException.Reason;

/**
 * Stand-in for Bitbucket in mock mode. Any username with any non-empty password signs in,
 * and a few reserved values show each error screen.
 */
@Component
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "true")
public class MockBitbucketAuthenticator implements BitbucketAuthenticator {

    @Override
    public BitbucketUser authenticate(String username, String password) {
        if (password == null || password.isBlank() || "wrong".equals(password)) {
            throw new BitbucketAuthException(Reason.BAD_CREDENTIALS, "Wrong Bitbucket username or password.");
        }
        switch (username.toLowerCase()) {
            case "captcha" -> throw new BitbucketAuthException(Reason.CAPTCHA_REQUIRED,
                    "Bitbucket wants a CAPTCHA. Sign in to Bitbucket in your browser once, then try again.");
            case "offline" -> throw new BitbucketAuthException(Reason.UNAVAILABLE, "Bitbucket is unreachable right now.");
            default -> {
                String display = Character.toUpperCase(username.charAt(0)) + username.substring(1);
                return new BitbucketUser(username, username.toLowerCase(), display + " (mock)",
                        username.toLowerCase() + "@example.com");
            }
        }
    }
}
