package com.cloudcart.agent.util;

public interface JwtVerifier {

    /**
     * Verifies a raw (already Bearer-prefix-stripped) JWT and returns the
     * authenticated userId (the token's `sub` claim). Throws
     * JwtVerificationException if the token is missing, malformed, unsigned
     * by us, or expired.
     */
    String verify(String token);
}
