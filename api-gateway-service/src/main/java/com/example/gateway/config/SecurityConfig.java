package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive WebFlux Security Configuration.
 * 
 * Configures the foundational reactive security filter chain for Spring Cloud Gateway.
 * Ingress authentication, Keycloak JWKS asymmetric token verification, and Redis token blacklist
 * checks are orchestrated non-blockingly by {@link com.example.gateway.filter.global.AuthenticationGlobalFilter}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Authentication and authorization are enforced by Gateway Global Filters
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
