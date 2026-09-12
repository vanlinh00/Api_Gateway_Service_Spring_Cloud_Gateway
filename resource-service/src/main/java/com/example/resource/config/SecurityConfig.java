package com.example.resource.config;

import com.example.resource.security.GatewayHeaderFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SecurityConfig for Resource Service:
 * - NO Keycloak JWKS endpoint configured.
 * - NO JWT decoding or cryptographic CPU cycles.
 * - NO Redis connection for token revocation checks.
 * - Relies entirely on GatewayHeaderFilter and enforces fine-grained Authorization (@PreAuthorize).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayHeaderFilter gatewayHeaderFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(GatewayHeaderFilter gatewayHeaderFilter) {
        this.gatewayHeaderFilter = gatewayHeaderFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/api/v1/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // 401 when request arrives without Gateway pre-authentication headers
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            Map<String, Object> error = new LinkedHashMap<>();
                            error.put("timestamp", Instant.now().toString());
                            error.put("status", HttpStatus.UNAUTHORIZED.value());
                            error.put("error", "Unauthorized");
                            error.put("message", "Access denied: Missing trusted Gateway identity headers (X-Auth-User-Id)");
                            error.put("path", request.getRequestURI());
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                        // 403 when user is authenticated by Gateway but lacks required permission/role (Author failure)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            Map<String, Object> error = new LinkedHashMap<>();
                            error.put("timestamp", Instant.now().toString());
                            error.put("status", HttpStatus.FORBIDDEN.value());
                            error.put("error", "Forbidden");
                            error.put("message", "Authorization failed: User does not possess the required role/permission for this resource");
                            error.put("path", request.getRequestURI());
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                )
                .addFilterBefore(gatewayHeaderFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
