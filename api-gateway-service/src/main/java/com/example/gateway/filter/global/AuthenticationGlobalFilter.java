package com.example.gateway.filter.global;

import com.example.gateway.core.constant.GatewayConstants;
import com.example.gateway.core.exception.AuthenticationException;
import com.example.gateway.core.exception.TokenRevokedException;
import com.example.gateway.core.model.AuthenticatedUser;
import com.example.gateway.security.jwt.JwtVerifier;
import com.example.gateway.security.matcher.PublicPathMatcher;
import com.example.gateway.security.revocation.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise Ingress Authentication Global Filter.
 * 
 * Orchestrates:
 * 1. Public route whitelisting.
 * 2. Keycloak OIDC JWT asymmetric signature validation via JWKS.
 * 3. Reactive Redis Token Blacklist lookup.
 * 4. Downstream identity header enrichment (X-Auth-User-Id, X-Auth-Roles, etc.).
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGlobalFilter.class);

    private final PublicPathMatcher pathMatcher;
    private final JwtVerifier jwtVerifier;
    private final TokenRevocationService revocationService;

    public AuthenticationGlobalFilter(PublicPathMatcher pathMatcher,
                                      JwtVerifier jwtVerifier,
                                      TokenRevocationService revocationService) {
        this.pathMatcher = pathMatcher;
        this.jwtVerifier = jwtVerifier;
        this.revocationService = revocationService;
    }

    @Override
    public int getOrder() {
        return 0; // Executes after tracing and timing filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String correlationId = exchange.getAttributeOrDefault(GatewayConstants.ATTR_CORRELATION_ID, "N/A");

        // 1. Skip authentication for whitelisted public routes
        if (pathMatcher.isPublic(path)) {
            log.debug("[CorrelationId: {}] Public path [{}] bypassed authentication", correlationId, path);
            return chain.filter(exchange);
        }

        // 2. Extract Authorization Bearer Token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(GatewayConstants.BEARER_PREFIX)) {
            log.warn("[CorrelationId: {}] Missing or invalid Authorization header on path [{}]", correlationId, path);
            return Mono.error(new AuthenticationException("Missing or invalid Authorization header. Expected Bearer token."));
        }

        String token = authHeader.substring(GatewayConstants.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("[CorrelationId: {}] Empty Bearer token on path [{}]", correlationId, path);
            return Mono.error(new AuthenticationException("Bearer token must not be empty."));
        }

        // 3. Asymmetric Keycloak Verification & Context Extraction
        return jwtVerifier.verifyAndExtract(token)
                .flatMap(user -> {
                    // 4. Redis Reactive Blacklist / Revocation Verification
                    return revocationService.isRevoked(user.tokenId())
                            .flatMap(revoked -> {
                                if (Boolean.TRUE.equals(revoked)) {
                                    log.warn("[CorrelationId: {}] REVOKED KEYCLOAK TOKEN REJECTED: JTI: [{}], Subject: [{}], User: [{}], Path: [{}]",
                                            correlationId, user.tokenId(), user.userId(), user.username(), path);
                                    return Mono.error(new TokenRevokedException(user.tokenId()));
                                }

                                // 5. Enrich downstream request headers with validated identity
                                ServerHttpRequest.Builder mutatedRequestBuilder = exchange.getRequest().mutate()
                                        .header(GatewayConstants.HEADER_AUTH_USER_ID, user.userId() != null ? user.userId() : "")
                                        .header(GatewayConstants.HEADER_AUTH_USERNAME, user.username() != null ? user.username() : "")
                                        .header(GatewayConstants.HEADER_AUTH_ROLES, user.getRolesAsCsv())
                                        .header(GatewayConstants.HEADER_AUTH_TOKEN_ID, user.tokenId());

                                if (StringUtils.hasText(user.email())) {
                                    mutatedRequestBuilder.header(GatewayConstants.HEADER_AUTH_EMAIL, user.email());
                                }

                                exchange.getAttributes().put(GatewayConstants.ATTR_USER_CONTEXT, user);

                                log.debug("[CorrelationId: {}] Authenticated [{}] with roles [{}]. Forwarding downstream.",
                                        correlationId, user.username(), user.getRolesAsCsv());

                                return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());
                            });
                });
    }
}
