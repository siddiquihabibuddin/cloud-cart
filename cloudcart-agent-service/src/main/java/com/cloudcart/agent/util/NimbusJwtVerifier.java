package com.cloudcart.agent.util;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class NimbusJwtVerifier implements JwtVerifier {

    private final byte[] secret;

    public NimbusJwtVerifier(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String verify(String token) {
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
}
