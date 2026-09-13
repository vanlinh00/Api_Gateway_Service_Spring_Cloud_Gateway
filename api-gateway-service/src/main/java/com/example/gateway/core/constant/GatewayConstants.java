package com.example.gateway.core.constant;

/**
 * Enterprise Gateway Constants:
 * Centralizes HTTP headers, attribute keys, and Redis prefixes.
 */
public final class GatewayConstants {

    private GatewayConstants() {}

    // Distributed Tracing Headers
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_RESPONSE_TIME_MS = "X-Response-Time-Ms";
    public static final String HEADER_GATEWAY_NAME = "X-Gateway-Name";
    public static final String GATEWAY_NAME_VALUE = "api-gateway-service";

    // Downstream Identity Headers
    public static final String HEADER_AUTH_USER_ID = "X-Auth-User-Id";
    public static final String HEADER_AUTH_USERNAME = "X-Auth-Username";
    public static final String HEADER_AUTH_ROLES = "X-Auth-Roles";
    public static final String HEADER_AUTH_EMAIL = "X-Auth-Email";
    public static final String HEADER_AUTH_TOKEN_ID = "X-Auth-Token-Id";

    // Request Attribute Keys (for WebFlux exchange context)
    public static final String ATTR_START_TIME = "gateway.startTime";
    public static final String ATTR_CORRELATION_ID = "gateway.correlationId";
    public static final String ATTR_USER_CONTEXT = "gateway.userContext";

    // Standard Auth
    public static final String BEARER_PREFIX = "Bearer ";
}
