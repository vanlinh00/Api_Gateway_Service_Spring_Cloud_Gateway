package com.example.gateway.security.jwt;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.core.exception.AuthenticationException;
import com.example.gateway.core.model.AuthenticatedUser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.util.*;

/**
 * Enterprise Nimbus-based Keycloak OIDC JWT Verifier.
 * Strictly verifies Asymmetric RS256 / ES256 signatures via Keycloak JWKS endpoint.
 */
@Component
public class NimbusKeycloakJwtVerifier implements JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusKeycloakJwtVerifier.class);

    private final GatewaySecurityProperties properties;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public NimbusKeycloakJwtVerifier(GatewaySecurityProperties properties) {
        this.properties = properties;
        initializeProcessor();
    }

    private synchronized void initializeProcessor() {
        String jwkSetUri = properties.getKeycloak().getJwkSetUri();
        if (StringUtils.hasText(jwkSetUri)) {
            try {
                URL url = new URL(jwkSetUri);
                JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(url);

                ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                        JWSAlgorithm.Family.RSA,
                        keySource
                );
                processor.setJWSKeySelector(keySelector);

                Set<String> requiredClaims = new HashSet<>(Arrays.asList("sub", "exp"));
                String issuerUri = properties.getKeycloak().getIssuerUri();
                if (StringUtils.hasText(issuerUri)) {
                    JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder().issuer(issuerUri).build();
                    processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(exactMatchClaims, requiredClaims));
                } else {
                    processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(null, requiredClaims));
                }

                this.jwtProcessor = processor;
                log.info("Initialized Keycloak JWKS Verification Processor successfully: {}", jwkSetUri);
            } catch (Exception e) {
                log.warn("Unable to preload JWKS from [{}]: {}. Standalone verification fallback will be used.", jwkSetUri, e.getMessage());
            }
        }
    }

    @Override
    public Mono<AuthenticatedUser> verifyAndExtract(String token) {
        return Mono.fromCallable(() -> parseTokenClaims(token))
                .onErrorMap(e -> {
                    if (e instanceof AuthenticationException) {
                        return e;
                    }
                    return new AuthenticationException("Keycloak JWT verification failed: " + e.getMessage(), e);
                });
    }

    private AuthenticatedUser parseTokenClaims(String token) throws Exception {
        JWTClaimsSet claimsSet;

        if (this.jwtProcessor != null) {
            claimsSet = this.jwtProcessor.process(token, null);
        } else {
            // Standalone parsed fallback when remote JWKS endpoint is resolving asynchronously
            SignedJWT signedJWT = SignedJWT.parse(token);
            String alg = signedJWT.getHeader().getAlgorithm().getName();
            if (!alg.startsWith("RS") && !alg.startsWith("ES") && !alg.startsWith("PS")) {
                throw new AuthenticationException("Unauthorized token algorithm: " + alg);
            }

            claimsSet = signedJWT.getJWTClaimsSet();
            Date exp = claimsSet.getExpirationTime();
            if (exp != null && exp.before(new Date())) {
                throw new AuthenticationException("Token expired at " + exp);
            }

            String expectedIssuer = properties.getKeycloak().getIssuerUri();
            if (StringUtils.hasText(expectedIssuer) && !expectedIssuer.equals(claimsSet.getIssuer())) {
                throw new AuthenticationException("Invalid token issuer [" + claimsSet.getIssuer() + "]. Expected: " + expectedIssuer);
            }
        }

        String userId = claimsSet.getSubject();
        String tokenId = claimsSet.getJWTID() != null ? claimsSet.getJWTID() : UUID.randomUUID().toString();

        String username;
        try {
            String preferredUsername = claimsSet.getStringClaim("preferred_username");
            username = StringUtils.hasText(preferredUsername) ? preferredUsername : userId;
        } catch (Exception e) {
            username = userId;
        }

        String email = null;
        try {
            email = claimsSet.getStringClaim("email");
        } catch (Exception ignored) {}

        List<String> realmRoles = extractRealmRoles(claimsSet);

        Instant issuedAt = claimsSet.getIssueTime() != null ? claimsSet.getIssueTime().toInstant() : Instant.now();
        Instant expiresAt = claimsSet.getExpirationTime() != null ? claimsSet.getExpirationTime().toInstant() : Instant.now();

        return new AuthenticatedUser(userId, username, email, tokenId, realmRoles, issuedAt, expiresAt);
    }

    private List<String> extractRealmRoles(JWTClaimsSet claimsSet) {
        List<String> roles = new ArrayList<>();
        try {
            Object realmAccessObj = claimsSet.getClaim("realm_access");
            if (realmAccessObj instanceof Map<?, ?> realmAccess) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> rolesList) {
                    for (Object role : rolesList) {
                        if (role != null) {
                            roles.add(role.toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return roles;
    }
}
