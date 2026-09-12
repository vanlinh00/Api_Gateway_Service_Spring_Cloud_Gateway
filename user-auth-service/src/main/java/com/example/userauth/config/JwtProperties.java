package com.example.userauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keycloak OIDC & JWT Blacklist configuration properties.
 * Strictly relies on Keycloak asymmetric signing (RS256/JWKS).
 * HMAC secret-key fallbacks have been completely purged.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Keycloak JWKS URI used to download Public Keys for RS256/ES256 signature validation.
     * Example: http://localhost:8180/realms/master/protocol/openid-connect/certs
     */
    private String jwkSetUri = "http://localhost:8180/realms/master/protocol/openid-connect/certs";

    /**
     * Expected Keycloak Realm Issuer URI (claim 'iss').
     * Example: http://localhost:8180/realms/master
     */
    private String issuerUri = "http://localhost:8180/realms/master";

    /**
     * Redis key prefix used for storing blacklisted token IDs.
     * Example: "jwt:blacklist:"
     */
    private String blacklistPrefix = "jwt:blacklist:";

    /**
     * Public endpoints that skip JWT validation and revocation checks.
     */
    private List<String> excludedPaths = new ArrayList<>();

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getBlacklistPrefix() {
        return blacklistPrefix;
    }

    public void setBlacklistPrefix(String blacklistPrefix) {
        this.blacklistPrefix = blacklistPrefix;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
