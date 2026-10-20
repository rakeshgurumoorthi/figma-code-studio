package com.figmacode.auth;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.figmacode.auth.BitbucketAuthenticator.BitbucketUser;

/** Plugs the Bitbucket check into Spring Security's form login. The password is never stored. */
@Component
public class BitbucketAuthenticationProvider implements AuthenticationProvider {

    private final BitbucketAuthenticator bitbucket;
    private final LoginAttemptLimiter limiter;

    public BitbucketAuthenticationProvider(BitbucketAuthenticator bitbucket, LoginAttemptLimiter limiter) {
        this.bitbucket = bitbucket;
        this.limiter = limiter;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName() == null ? "" : authentication.getName().trim();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (username.isEmpty() || password.isEmpty()) {
            throw new BadCredentialsException("Enter your Bitbucket username and password.");
        }
        if (limiter.isBlocked(username)) {
            throw new LockedException("Too many failed attempts. Wait a few minutes and try again.");
        }
        try {
            BitbucketUser user = bitbucket.authenticate(username, password);
            limiter.recordSuccess(username);
            CurrentUser principal = new CurrentUser(user.username(), user.slug(), user.displayName(), user.email());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
        } catch (BitbucketAuthException e) {
            switch (e.getReason()) {
                case BAD_CREDENTIALS -> {
                    limiter.recordFailure(username);
                    throw new BadCredentialsException(e.getMessage());
                }
                case CAPTCHA_REQUIRED, RATE_LIMITED -> throw new LockedException(e.getMessage());
                default -> throw new AuthenticationServiceException(e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
