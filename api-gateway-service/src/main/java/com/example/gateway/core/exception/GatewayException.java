package com.example.gateway.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base Abstract Gateway Exception.
 */
public abstract class GatewayException extends RuntimeException {

    private final HttpStatus status;

    protected GatewayException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected GatewayException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
