package com.example.userauth.config;

import com.example.userauth.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

/**
 * Spring Security configuration for user-auth-service.
 * Strictly asymmetric Keycloak validation using NimbusJwtDecoder with JWKS / Issuer URI.
 * Zero references to SecretKeySpec or HMAC symmetric keys.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtProperties jwtProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtProperties jwtProperties, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtProperties = jwtProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Permit excluded public paths
                    if (jwtProperties.getExcludedPaths() != null && !jwtProperties.getExcludedPaths().isEmpty()) {
                        auth.requestMatchers(jwtProperties.getExcludedPaths().toArray(new String[0])).permitAll();
                    }
                    auth.requestMatchers("/actuator/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Instantiates the JwtDecoder bean strictly using Keycloak JWKS endpoint or Issuer location.
     * Purged of any SecretKeySpec or HMAC symmetric key decryption.
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
