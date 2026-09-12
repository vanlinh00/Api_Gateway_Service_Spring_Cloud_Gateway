package com.example.resource.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GatewayHeaderFilter:
 * Offloads Authentication (Authen) entirely to API Gateway.
 * 
 * Extracts pre-authenticated user context forwarded by api-gateway-service:
 * - X-Auth-User-Id
 * - X-Auth-Username
 * - X-Auth-Roles
 * 
 * Populates Spring SecurityContextHolder so downstream controllers and services
 * can execute fine-grained Authorization (Author / RBAC) without re-validating JWTs or querying Redis.
 */
@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayHeaderFilter.class);

    public static final String HEADER_USER_ID = "X-Auth-User-Id";
    public static final String HEADER_USERNAME = "X-Auth-Username";
    public static final String HEADER_ROLES = "X-Auth-Roles";
    public static final String HEADER_EMAIL = "X-Auth-Email";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        String username = request.getHeader(HEADER_USERNAME);
        String rolesHeader = request.getHeader(HEADER_ROLES);

        // If gateway headers are present, user is already authenticated by the Gateway!
        if (StringUtils.hasText(userId)) {
            String principal = StringUtils.hasText(username) ? username : userId;
            List<SimpleGrantedAuthority> authorities = parseRoles(rolesHeader);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null, // No credentials needed in internal microservice hops
                    authorities
            );

            // Store detailed user context
            UserContext userContext = new UserContext(
                    userId,
                    username,
                    request.getHeader(HEADER_EMAIL),
                    authorities.stream().map(SimpleGrantedAuthority::getAuthority).toList()
            );
            authentication.setDetails(userContext);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated internal request from Gateway: user=[{}], roles={}", principal, authorities);
        } else {
            // For endpoints requiring authentication, lack of X-Auth-User-Id means request bypassed Gateway
            log.debug("No Gateway authentication headers found on path: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> parseRoles(String rolesHeader) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (!StringUtils.hasText(rolesHeader)) {
            return authorities;
        }

        String[] roles = rolesHeader.split(",");
        for (String role : roles) {
            String trimmed = role.trim();
            if (!trimmed.isEmpty()) {
                // Ensure standard Spring Security 'ROLE_' prefix
                String roleName = trimmed.toUpperCase();
                if (!roleName.startsWith("ROLE_")) {
                    roleName = "ROLE_" + roleName;
                }
                authorities.add(new SimpleGrantedAuthority(roleName));
            }
        }
        return authorities;
    }
}
