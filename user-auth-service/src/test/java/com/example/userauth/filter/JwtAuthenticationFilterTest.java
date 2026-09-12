package com.example.userauth.filter;

import com.example.userauth.config.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private FilterChain filterChain;

    private JwtProperties jwtProperties;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setBlacklistPrefix("jwt:blacklist:");
        jwtProperties.setExcludedPaths(Collections.singletonList("/api/v1/auth/login"));

        filter = new JwtAuthenticationFilter(jwtProperties, redisTemplate);
        filter.setJwtDecoder(jwtDecoder);
    }

    @Test
    @DisplayName("Should skip validation when path is in excluded-paths")
    void shouldSkipValidationForExcludedPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should return 401 when token is revoked in Redis blacklist")
    void shouldReturn401WhenTokenIsBlacklisted() throws Exception {
        String token = "sample.keycloak.token";
        String jti = "revoked-uuid-1234";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-001");
        claims.put("jti", jti);

        Jwt jwt = new Jwt(
                token,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Collections.singletonMap("alg", "RS256"),
                claims
        );

        when(jwtDecoder.decode(eq(token))).thenReturn(jwt);
        when(redisTemplate.hasKey(eq("jwt:blacklist:" + jti))).thenReturn(Boolean.TRUE);

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should authenticate and forward when token is not blacklisted")
    void shouldAuthenticateWhenTokenIsNotBlacklisted() throws Exception {
        String token = "sample.keycloak.token";
        String jti = "active-uuid-5678";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-002");
        claims.put("jti", jti);

        Jwt jwt = new Jwt(
                token,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Collections.singletonMap("alg", "RS256"),
                claims
        );

        when(jwtDecoder.decode(eq(token))).thenReturn(jwt);
        when(redisTemplate.hasKey(eq("jwt:blacklist:" + jti))).thenReturn(Boolean.FALSE);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
