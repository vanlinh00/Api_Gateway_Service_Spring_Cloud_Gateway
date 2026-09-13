package com.example.gateway.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * RFC 7807-compliant Unified Gateway Error Envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static GatewayErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new GatewayErrorResponse(
                Instant.now().toString(),
                status,
                error,
                message,
                path,
                correlationId
        );
    }
}
