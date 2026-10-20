package com.figmacode.auth;

/** Checks a username and password against Bitbucket Data Center. */
public interface BitbucketAuthenticator {

    /**
     * @return the Bitbucket user when the credentials are valid
     * @throws BitbucketAuthException with a {@link BitbucketAuthException.Reason} when they are not
     */
    BitbucketUser authenticate(String username, String password);

    record BitbucketUser(String username, String slug, String displayName, String email) {
    }
}
