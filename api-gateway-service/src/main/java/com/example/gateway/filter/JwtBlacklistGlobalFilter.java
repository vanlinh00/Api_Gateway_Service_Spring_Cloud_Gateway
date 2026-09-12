package com.example.gateway.filter;

import com.example.gateway.config.JwtProperties;
import com.example.gateway.dto.ErrorResponse;
import com.example.gateway.util.JwtUtils;
import com.example.gateway.util.KeycloakTokenClaims;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Keycloak JWT Blacklist GlobalFilter:
 * 1. Checks whitelist (public endpoints).
 * 2. Validates Bearer token presence and format.
 * 3. Verifies Keycloak JWT signature, expiration, and claims.
 * 4. Extracts Keycloak 'jti' (JWT ID).
 * 5. Queries Redis Reactive blacklist store non-blockingly.
 * 6. Rejects revoked tokens with HTTP 401 & detailed WARN audit log.
 * 7. Enriches downstream request headers with Keycloak User Context (Subject, Username, Roles).
 */
@Component
public class JwtBlacklistGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtBlacklistGlobalFilter(JwtUtils jwtUtils,
                                    JwtProperties jwtProperties,
                                    ReactiveStringRedisTemplate reactiveRedisTemplate) {
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. White-list check (public endpoints like login, public docs, health)
        if (isExcludedPath(path)) {
            log.debug("Path [{}] is excluded from JWT verification. Skipping.", path);
            return chain.filter(exchange);
        }

        // 2. Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for request to path: [{}]", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("Empty Bearer token in Authorization header for path: [{}]", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Empty Bearer token");
        }

        KeycloakTokenClaims claims;
        try {
            // Verify Keycloak signature, expiration, and parse token claims
            claims = jwtUtils.parseAndValidateToken(token);
        } catch (Exception e) {
            log.warn("Failed to validate Keycloak JWT token for path [{}]: {}", path, e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token: " + e.getMessage());
        }

        // 3. Extract unique Keycloak Token ID (jti claim)
        String tokenId = jwtUtils.extractTokenIdentifier(token, claims);
        String redisKey = jwtProperties.getBlacklistPrefix() + tokenId;

        // 4. Reactive Non-blocking check in Redis Blacklist
        return reactiveRedisTemplate.hasKey(redisKey)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        String subject = claims.getSubject();
                        String username = claims.getUsername();
                        String clientIp = request.getRemoteAddress() != null
                                ? request.getRemoteAddress().getAddress().getHostAddress()
                                : "UNKNOWN";

                        log.warn("WARNING: Revoked/Blacklisted Keycloak JWT detected! JTI: [{}], Subject: [{}], Username: [{}], Path: [{}], IP: [{}]",
                                tokenId, subject, username, path, clientIp);

                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                                "Token has been revoked/blacklisted. Please authenticate via Keycloak again.");
                    }

                    log.debug("Keycloak Token [{}] is active and valid. Forwarding request...", tokenId);

                    // 5. Enrich downstream request headers with normalized user identity & Keycloak realm roles
                    String rolesHeader = claims.getRealmRoles() != null ? String.join(",", claims.getRealmRoles()) : "";
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                            .header("X-Auth-User-Id", claims.getSubject() != null ? claims.getSubject() : "")
                            .header("X-Auth-Username", claims.getUsername() != null ? claims.getUsername() : "")
                            .header("X-Auth-Token-Id", tokenId)
                            .header("X-Auth-Roles", rolesHeader);

                    if (StringUtils.hasText(claims.getEmail())) {
                        requestBuilder.header("X-Auth-Email", claims.getEmail());
                    }

                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .onErrorResume(ex -> {
                    log.error("Error verifying Redis Blacklist for Keycloak token [{}]: {}", tokenId, ex.getMessage(), ex);
                    return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authentication verification failed: " + ex.getMessage());
                });
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            String fallbackJson = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
            bytes = fallbackJson.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isExcludedPath(String requestPath) {
        if (jwtProperties.getExcludedPaths() == null || jwtProperties.getExcludedPaths().isEmpty()) {
            return false;
        }
        for (String pattern : jwtProperties.getExcludedPaths()) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
