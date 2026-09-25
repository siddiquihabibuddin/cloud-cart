package com.cloudcart.agent.client;

public class GroqException extends RuntimeException {

    public GroqException(String message) {
        super(message);
    }

    public GroqException(String message, Throwable cause) {
        super(message, cause);
    }
}
