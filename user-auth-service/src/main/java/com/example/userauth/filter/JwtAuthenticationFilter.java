package com.example.userauth.filter;

import com.example.userauth.config.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Security Filter validating Keycloak OIDC JWT tokens and checking token revocation in Redis.
 * - Skips public paths defined in jwt.excluded-paths.
 * - Validates signature via Keycloak JWKS endpoint.
 * - Validates revocation status using jwt.blacklist-prefix + token ID (jti).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Lazy or injected JwtDecoder
    private JwtDecoder jwtDecoder;

    public JwtAuthenticationFilter(JwtProperties jwtProperties, StringRedisTemplate redisTemplate) {
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    public void setJwtDecoder(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Skip verification for excluded public endpoints
        if (isExcludedPath(path)) {
            log.debug("Path [{}] is whitelisted. Skipping JWT Blacklist check.", path);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract Authorization Header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header", path);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Empty Bearer token", path);
            return;
        }

        // 3. Decode & Verify Keycloak JWT using Asymmetric JWKS Decoder
        Jwt jwt;
        try {
            if (this.jwtDecoder == null) {
                this.jwtDecoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
                        .withJwkSetUri(jwtProperties.getJwkSetUri()).build();
            }
            jwt = this.jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Failed to decode/verify Keycloak JWT token: {}", e.getMessage());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT: " + e.getMessage(), path);
            return;
        }

        // 4. Extract Keycloak Token Identifier (jti claim)
        String tokenId = jwt.getId();
        if (!StringUtils.hasText(tokenId)) {
            tokenId = jwt.getSubject();
        }

        // 5. Query Redis Blacklist using configured prefix
        String redisKey = jwtProperties.getBlacklistPrefix() + tokenId;
        Boolean isBlacklisted = Boolean.FALSE;
        try {
            isBlacklisted = redisTemplate.hasKey(redisKey);
        } catch (Exception e) {
            log.error("Redis error while checking token revocation for [{}]: {}", tokenId, e.getMessage());
        }

        if (Boolean.TRUE.equals(isBlacklisted)) {
            String subject = jwt.getSubject();
            String clientIp = request.getRemoteAddr();

            log.warn("WARNING: Revoked/Blacklisted Keycloak JWT detected! JTI: [{}], Subject: [{}], Path: [{}], IP: [{}]",
                    tokenId, subject, path, clientIp);

            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Token has been revoked/blacklisted. Please log in again.", path);
            return;
        }

        // 6. Populate Spring Security Context with Keycloak Realm Roles
        List<SimpleGrantedAuthority> authorities = extractKeycloakAuthorities(jwt);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwt.getSubject(),
                jwt,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
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

    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractKeycloakAuthorities(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            for (Object r : roles) {
                if (r != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase()));
                }
            }
        }
        return authorities;
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", status);
        errorBody.put("error", HttpStatus.valueOf(status).getReasonPhrase());
        errorBody.put("message", message);
        errorBody.put("path", path);

        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }
}
