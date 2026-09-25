package com.cloudcart.auth.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class NimbusJwtIssuer implements JwtIssuer {

    private static final long EXPIRY_HOURS = 24;

    private final byte[] secret;

    public NimbusJwtIssuer(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String issue(String userId, String email) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("email", email)
                .issuer("cloudcart")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(EXPIRY_HOURS, ChronoUnit.HOURS)))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(secret));
        return signedJWT.serialize();
    }
}
