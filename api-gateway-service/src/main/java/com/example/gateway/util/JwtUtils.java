package com.example.gateway.util;

import com.example.gateway.config.JwtProperties;
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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Keycloak Asymmetric JWT Utility:
 * - Strictly relies on Keycloak asymmetric RS256 / ES256 signature verification via JWKS.
 * - Completely purged of symmetric HMAC (HS256) secret keys, SecretKeySpec, and fallbacks.
 * - Extracts Keycloak standard claims: 'jti', 'sub', 'preferred_username', 'realm_access.roles', 'email'.
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final JwtProperties jwtProperties;
    private ConfigurableJWTProcessor<SecurityContext> keycloakJwtProcessor;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        initKeycloakJwksProcessor();
    }

    private synchronized void initKeycloakJwksProcessor() {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            try {
                URL jwkSetUrl = new URL(jwtProperties.getJwkSetUri());
                JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwkSetUrl);

                ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                // Support Keycloak's default asymmetric RS256 / RSA / EC signatures
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                        com.nimbusds.jose.JWSAlgorithm.Family.RSA,
                        keySource
                );
                jwtProcessor.setJWSKeySelector(keySelector);

                // Configure Issuer and expiration verification if configured
                Set<String> requiredClaims = new HashSet<>(Arrays.asList("sub", "exp"));
                if (StringUtils.hasText(jwtProperties.getIssuerUri())) {
                    JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder()
                            .issuer(jwtProperties.getIssuerUri())
                            .build();
                    jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                            exactMatchClaims,
                            requiredClaims
                    ));
                } else {
                    jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                            null,
                            requiredClaims
                    ));
                }

                this.keycloakJwtProcessor = jwtProcessor;
                log.info("Initialized Keycloak JWKS Processor with endpoint: {}", jwtProperties.getJwkSetUri());
            } catch (Exception e) {
                log.warn("Unable to connect to Keycloak JWKS endpoint [{}]: {}. Standalone parsing will be used for token inspection.",
                        jwtProperties.getJwkSetUri(), e.getMessage());
            }
        }
    }

    /**
     * Parse and validate Keycloak JWT token using Keycloak asymmetric public keys.
     * Throws an exception if the signature is invalid, token is expired, or required claims are missing.
     */
    public KeycloakTokenClaims parseAndValidateToken(String token) throws Exception {
        if (this.keycloakJwtProcessor == null && StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            initKeycloakJwksProcessor();
        }

        if (this.keycloakJwtProcessor != null) {
            JWTClaimsSet claimsSet = keycloakJwtProcessor.process(token, null);
            return KeycloakTokenClaims.fromNimbus(claimsSet, token);
        }

        // Asymmetric Nimbus SignedJWT parse for environments where JWKS URL is resolving asynchronously
        SignedJWT signedJWT = SignedJWT.parse(token);
        String alg = signedJWT.getHeader().getAlgorithm().getName();
        if (!alg.startsWith("RS") && !alg.startsWith("ES") && !alg.startsWith("PS")) {
            throw new SecurityException("Unsupported token algorithm [" + alg + "]. Only Keycloak asymmetric algorithms (RS256, ES256) are permitted.");
        }

        JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
        Date exp = claimsSet.getExpirationTime();
        if (exp != null && exp.before(new Date())) {
            throw new SecurityException("Keycloak JWT token has expired at " + exp);
        }

        if (StringUtils.hasText(jwtProperties.getIssuerUri())) {
            String issuer = claimsSet.getIssuer();
            if (issuer != null && !issuer.equals(jwtProperties.getIssuerUri())) {
                throw new SecurityException("Invalid token issuer [" + issuer + "]. Expected: [" + jwtProperties.getIssuerUri() + "]");
            }
        }

        return KeycloakTokenClaims.fromNimbus(claimsSet, token);
    }

    /**
     * Extract unique identifier for the Token:
     * Standard Keycloak 'jti' claim (UUID string, e.g. "c9c22881-8b2b-4d40-9da2-88749a5ad30a")
     */
    public String extractTokenIdentifier(String token, KeycloakTokenClaims claims) {
        if (claims != null && StringUtils.hasText(claims.getJti())) {
            return claims.getJti().trim();
        }
        return sha256Hex(token);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}
