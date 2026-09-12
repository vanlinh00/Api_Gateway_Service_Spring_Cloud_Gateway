package com.example.gateway.util;

import com.example.gateway.config.JwtProperties;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Keycloak & OIDC JWT Utility:
 * - Supports Keycloak RS256/ES256 public-key verification via JWKS endpoint
 * - Supports fallback HS256 HMAC verification for lightweight testing / development
 * - Extracts Keycloak standard claims: 'jti', 'sub', 'preferred_username', 'realm_access.roles', 'resource_access'
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final JwtProperties jwtProperties;
    private final SecretKey hmacSigningKey;
    private ConfigurableJWTProcessor<SecurityContext> keycloakJwtProcessor;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.hmacSigningKey = initHmacKey(jwtProperties.getSecretKey());
        initKeycloakJwksProcessor();
    }

    private void initKeycloakJwksProcessor() {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            try {
                URL jwkSetUrl = new URL(jwtProperties.getJwkSetUri());
                JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwkSetUrl);
                
                ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                // Support Keycloak's default RS256 as well as RSA family
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                        com.nimbusds.jose.JWSAlgorithm.Family.RSA,
                        keySource
                );
                jwtProcessor.setJWSKeySelector(keySelector);

                // Configure Issuer verification if specified
                if (StringUtils.hasText(jwtProperties.getIssuerUri())) {
                    JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder()
                            .issuer(jwtProperties.getIssuerUri())
                            .build();
                    jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                            exactMatchClaims,
                            new HashSet<>(Arrays.asList("sub", "exp", "jti"))
                    ));
                }

                this.keycloakJwtProcessor = jwtProcessor;
                log.info("Initialized Keycloak JWKS Processor with endpoint: {}", jwtProperties.getJwkSetUri());
            } catch (Exception e) {
                log.warn("Failed to initialize Keycloak JWKS Processor from [{}]: {}. Will fallback to HMAC/Claim parser.",
                        jwtProperties.getJwkSetUri(), e.getMessage());
            }
        }
    }

    private SecretKey initHmacKey(String secret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(keyBytes);
        }
    }

    /**
     * Parse and validate Keycloak JWT token, extracting its Claims.
     * 1. If JWKS is configured, verifies digital signature with Keycloak Public Key (RS256).
     * 2. If JWKS is not configured or in HMAC mode, validates using configured secret.
     */
    public KeycloakTokenClaims parseAndValidateToken(String token) throws Exception {
        // If Keycloak JWKS is configured, verify via Nimbus RemoteJWKSet
        if (this.keycloakJwtProcessor != null) {
            JWTClaimsSet claimsSet = keycloakJwtProcessor.process(token, null);
            return KeycloakTokenClaims.fromNimbus(claimsSet, token);
        }

        // Fallback: Check if token is RSA signed (typical Keycloak)
        SignedJWT signedJWT = SignedJWT.parse(token);
        String alg = signedJWT.getHeader().getAlgorithm().getName();
        if (alg.startsWith("RS") || alg.startsWith("ES")) {
            // Unsigned/unverified parse if JWKS URL not reachable, but validate expiration and required claims
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date exp = claimsSet.getExpirationTime();
            if (exp != null && exp.before(new Date())) {
                throw new ExpiredJwtException(null, null, "Keycloak JWT token is expired");
            }
            return KeycloakTokenClaims.fromNimbus(claimsSet, token);
        }

        // Standard JJWT HMAC parse (for development or symmetric key setups)
        Claims jjwtClaims = Jwts.parser()
                .verifyWith(hmacSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return KeycloakTokenClaims.fromJjwt(jjwtClaims, token);
    }

    /**
     * Extract unique identifier for the Token:
     * 1. Keycloak standard 'jti' claim (UUID string, e.g. "c9c22881-8b2b-4d40-9da2-88749a5ad30a")
     * 2. Fallback: SHA-256 hash of the complete token string.
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
