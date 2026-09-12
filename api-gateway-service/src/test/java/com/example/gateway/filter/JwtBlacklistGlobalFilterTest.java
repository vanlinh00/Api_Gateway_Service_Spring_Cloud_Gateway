package com.example.gateway.filter;

import com.example.gateway.config.JwtProperties;
import com.example.gateway.util.JwtUtils;
import com.example.gateway.util.KeycloakTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistGlobalFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Mock
    private GatewayFilterChain chain;

    private JwtBlacklistGlobalFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getBlacklistPrefix()).thenReturn("jwt:blacklist:");
        lenient().when(jwtProperties.getExcludedPaths()).thenReturn(Collections.singletonList("/api/v1/auth/login"));

        filter = new JwtBlacklistGlobalFilter(jwtUtils, jwtProperties, reactiveRedisTemplate);
    }

    @Test
    @DisplayName("Exception: Request without Authorization Header returns 401 Unauthorized")
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Exception: Revoked Keycloak token in Redis Blacklist returns 401 Unauthorized")
    void shouldReturn401WhenTokenIsBlacklistedInRedis() throws Exception {
        String token = "valid.keycloak.token";
        String tokenId = "c9c22881-8b2b-4d40-9da2-88749a5ad30a";
        String redisKey = "jwt:blacklist:c9c22881-8b2b-4d40-9da2-88749a5ad30a";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeycloakTokenClaims claims = new KeycloakTokenClaims();
        claims.setJti(tokenId);
        claims.setSubject("user-sub-123");
        claims.setUsername("john.doe");
        claims.setRealmRoles(Arrays.asList("user", "admin"));

        when(jwtUtils.parseAndValidateToken(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Success: Active Keycloak token not in Redis Blacklist forwards request with user headers")
    void shouldPassFilterWhenTokenIsNotBlacklisted() throws Exception {
        String token = "valid.keycloak.token";
        String tokenId = "f8a11324-1111-2222-3333-444455556666";
        String redisKey = "jwt:blacklist:f8a11324-1111-2222-3333-444455556666";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeycloakTokenClaims claims = new KeycloakTokenClaims();
        claims.setJti(tokenId);
        claims.setSubject("user-sub-456");
        claims.setUsername("alice.smith");
        claims.setRealmRoles(Collections.singletonList("developer"));

        when(jwtUtils.parseAndValidateToken(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }
}
