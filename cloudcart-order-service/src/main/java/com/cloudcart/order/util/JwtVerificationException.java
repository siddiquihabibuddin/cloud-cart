package com.cloudcart.order.util;

public class JwtVerificationException extends RuntimeException {
    public JwtVerificationException(String message) {
        super(message);
    }
}
