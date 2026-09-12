package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

/**
 * Security & Keycloak JWT Decoder Configuration.
 * Instantiates JWT Decoders strictly via asymmetric JWKS endpoint or Issuer location.
 * Completely purges HMAC SecretKeySpec and symmetric fallbacks.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtProperties jwtProperties;

    public SecurityConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    /**
     * Reactive JwtDecoder for Spring Cloud Gateway WebFlux pipeline.
     * Strictly asymmetric using Keycloak JWKS endpoint or Issuer URI.
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            return NimbusReactiveJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri()).build();
        }
        if (StringUtils.hasText(jwtProperties.getIssuerUri())) {
            return NimbusReactiveJwtDecoder.withIssuerLocation(jwtProperties.getIssuerUri()).build();
        }
        throw new IllegalStateException("Neither jwt.jwk-set-uri nor jwt.issuer-uri is configured for Keycloak verification");
    }

    /**
     * Standard NimbusJwtDecoder bean strictly configured with Keycloak JWKS or Issuer.
     * Zero references to SecretKeySpec, HMAC, or symmetric decryption.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri()).build();
        }
        if (StringUtils.hasText(jwtProperties.getIssuerUri())) {
            return NimbusJwtDecoder.withIssuerLocation(jwtProperties.getIssuerUri()).build();
        }
        throw new IllegalStateException("Neither jwt.jwk-set-uri nor jwt.issuer-uri is configured for Keycloak verification");
    }
}
