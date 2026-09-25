package com.cloudcart.cart.util;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtVerifier {

    private final byte[] secret;

    public JwtVerifier(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifies the Authorization: Bearer <jwt> header and returns the authenticated
     * userId (the token's `sub` claim). Throws JwtVerificationException if the header
     * is missing, malformed, unsigned by us, or expired.
     */
    public String verifyFromHeaders(Map<String, Object> headers) {
        String authHeader = extractHeader(headers, "Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new JwtVerificationException("Missing or malformed Authorization header");
        }
        String token = authHeader.substring("Bearer ".length());

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secret))) {
                throw new JwtVerificationException("Invalid token signature");
            }
            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new JwtVerificationException("Token expired");
            }
            String subject = signedJWT.getJWTClaimsSet().getSubject();
            if (subject == null || subject.isBlank()) {
                throw new JwtVerificationException("Token missing subject");
            }
            return subject;
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("Invalid token: " + e.getMessage());
        }
    }

    private String extractHeader(Map<String, Object> headers, String name) {
        if (headers == null) return null;
        Object val = headers.get(name);
        if (val == null) val = headers.get(name.toLowerCase());
        return val != null ? val.toString() : null;
    }
}
