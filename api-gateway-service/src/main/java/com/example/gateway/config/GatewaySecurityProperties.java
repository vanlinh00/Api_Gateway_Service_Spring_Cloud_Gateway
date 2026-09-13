package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-Safe Enterprise Security Properties for API Gateway.
 */
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private KeycloakProperties keycloak = new KeycloakProperties();
    private BlacklistProperties blacklist = new BlacklistProperties();
    private List<String> excludedPaths = new ArrayList<>();

    public KeycloakProperties getKeycloak() {
        return keycloak;
    }

    public void setKeycloak(KeycloakProperties keycloak) {
        this.keycloak = keycloak;
    }

    public BlacklistProperties getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(BlacklistProperties blacklist) {
        this.blacklist = blacklist;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public static class KeycloakProperties {
        private String jwkSetUri = "http://localhost:8180/realms/master/protocol/openid-connect/certs";
        private String issuerUri = "http://localhost:8180/realms/master";

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
    }

    public static class BlacklistProperties {
        private String prefix = "jwt:blacklist:";
        private boolean enabled = true;
        private long timeoutMs = 800; // Fail-safe Redis query timeout

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
