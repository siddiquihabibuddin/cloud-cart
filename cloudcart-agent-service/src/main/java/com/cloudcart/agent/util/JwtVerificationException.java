package com.cloudcart.agent.util;

public class JwtVerificationException extends RuntimeException {
    public JwtVerificationException(String message) {
        super(message);
    }
}
