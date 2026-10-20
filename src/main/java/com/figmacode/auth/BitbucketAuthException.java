package com.figmacode.auth;

public class BitbucketAuthException extends RuntimeException {

    public enum Reason {
        /** Username or password is wrong. */
        BAD_CREDENTIALS,
        /** Bitbucket requires a CAPTCHA after repeated failures; the user must sign in on the web once. */
        CAPTCHA_REQUIRED,
        /** Basic auth is disabled on the Bitbucket REST API or the user is not allowed to use it. */
        BASIC_AUTH_REJECTED,
        /** Bitbucket could not be reached or returned an unexpected response. */
        UNAVAILABLE,
        /** Too many failed attempts against this app. */
        RATE_LIMITED
    }

    private final Reason reason;

    public BitbucketAuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BitbucketAuthException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
