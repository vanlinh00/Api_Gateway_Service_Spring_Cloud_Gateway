package com.example.gateway.filter;

import com.example.gateway.config.JwtProperties;
import com.example.gateway.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
    @DisplayName("Ngoại lệ: Request không có Header Authorization -> Trả về 401 Unauthorized")
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Ngoại lệ: Token đã bị thu hồi trong Redis Blacklist -> Trả về 401 Unauthorized và log warning")
    void shouldReturn401WhenTokenIsBlacklistedInRedis() {
        String token = "valid.jwt.token";
        String tokenId = "jti-123456";
        String redisKey = "jwt:blacklist:jti-123456";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = Jwts.claims().subject("user123").id(tokenId).build();
        when(jwtUtils.extractAllClaims(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Thành công: Token hợp lệ và không có trong Redis -> Chuyển tiếp request đến service backend")
    void shouldPassFilterWhenTokenIsNotBlacklisted() {
        String token = "valid.jwt.token";
        String tokenId = "jti-999999";
        String redisKey = "jwt:blacklist:jti-999999";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = Jwts.claims().subject("user123").id(tokenId).build();
        when(jwtUtils.extractAllClaims(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }
}
