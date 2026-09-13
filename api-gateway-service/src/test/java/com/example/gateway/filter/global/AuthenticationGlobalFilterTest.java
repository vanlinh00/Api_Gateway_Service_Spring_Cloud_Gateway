package com.example.gateway.filter.global;

import com.example.gateway.core.constant.GatewayConstants;
import com.example.gateway.core.exception.AuthenticationException;
import com.example.gateway.core.exception.TokenRevokedException;
import com.example.gateway.core.model.AuthenticatedUser;
import com.example.gateway.security.jwt.JwtVerifier;
import com.example.gateway.security.matcher.PublicPathMatcher;
import com.example.gateway.security.revocation.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationGlobalFilterTest {

    @Mock
    private PublicPathMatcher pathMatcher;

    @Mock
    private JwtVerifier jwtVerifier;

    @Mock
    private TokenRevocationService revocationService;

    @Mock
    private GatewayFilterChain chain;

    private AuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationGlobalFilter(pathMatcher, jwtVerifier, revocationService);
    }

    @Test
    @DisplayName("Public path bypasses authentication filter")
    void shouldBypassAuthenticationForPublicRoutes() {
        when(pathMatcher.isPublic("/api/v1/auth/login")).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtVerifier, revocationService);
    }

    @Test
    @DisplayName("Missing Authorization header emits AuthenticationException")
    void shouldFailWhenAuthorizationHeaderIsMissing() {
        when(pathMatcher.isPublic("/api/v1/users/me")).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(AuthenticationException.class)
                .verify();

        verifyNoInteractions(chain, jwtVerifier, revocationService);
    }

    @Test
    @DisplayName("Blacklisted / Revoked token emits TokenRevokedException")
    void shouldFailWhenTokenIsBlacklistedInRedis() {
        when(pathMatcher.isPublic("/api/v1/orders")).thenReturn(false);

        String token = "valid.jwt.payload";
        AuthenticatedUser user = new AuthenticatedUser(
                "usr-1", "john", "john@example.com", "jti-12345",
                List.of("ROLE_USER"), Instant.now(), Instant.now().plusSeconds(300)
        );

        when(jwtVerifier.verifyAndExtract(token)).thenReturn(Mono.just(user));
        when(revocationService.isRevoked("jti-12345")).thenReturn(Mono.just(Boolean.TRUE));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(TokenRevokedException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Valid active token enriches downstream headers and proceeds")
    void shouldEnrichHeadersAndProceedForValidActiveToken() {
        when(pathMatcher.isPublic("/api/v1/orders")).thenReturn(false);

        String token = "valid.jwt.payload";
        AuthenticatedUser user = new AuthenticatedUser(
                "usr-42", "alice", "alice@example.com", "jti-999",
                List.of("ROLE_ADMIN", "ROLE_USER"), Instant.now(), Instant.now().plusSeconds(300)
        );

        when(jwtVerifier.verifyAndExtract(token)).thenReturn(Mono.just(user));
        when(revocationService.isRevoked("jti-999")).thenReturn(Mono.just(Boolean.FALSE));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange capturedExchange = captor.getValue();
        HttpHeaders forwardedHeaders = capturedExchange.getRequest().getHeaders();

        assertEquals("usr-42", forwardedHeaders.getFirst(GatewayConstants.HEADER_AUTH_USER_ID));
        assertEquals("alice", forwardedHeaders.getFirst(GatewayConstants.HEADER_AUTH_USERNAME));
        assertEquals("ROLE_ADMIN,ROLE_USER", forwardedHeaders.getFirst(GatewayConstants.HEADER_AUTH_ROLES));
        assertEquals("jti-999", forwardedHeaders.getFirst(GatewayConstants.HEADER_AUTH_TOKEN_ID));
        assertEquals("alice@example.com", forwardedHeaders.getFirst(GatewayConstants.HEADER_AUTH_EMAIL));
    }
}
