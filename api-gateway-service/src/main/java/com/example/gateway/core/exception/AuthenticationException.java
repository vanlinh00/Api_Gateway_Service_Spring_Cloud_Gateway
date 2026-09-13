package com.example.gateway.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when Bearer token is missing, expired, malformed, or fails signature verification.
 */
public class AuthenticationException extends GatewayException {

    public AuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED);
    }
}
