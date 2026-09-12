package com.example.gateway.util;

import com.nimbusds.jwt.JWTClaimsSet;

import java.util.*;

/**
 * Normalized token claims representation supporting Keycloak OIDC specifications.
 * Exclusively parsed from Nimbus JOSE / JWT structures.
 */
public class KeycloakTokenClaims {

    private String jti;
    private String subject;
    private String username;
    private String email;
    private List<String> realmRoles = new ArrayList<>();
    private Date expiration;
    private Date issuedAt;
    private String issuer;

    public static KeycloakTokenClaims fromNimbus(JWTClaimsSet claimsSet, String rawToken) {
        KeycloakTokenClaims claims = new KeycloakTokenClaims();
        claims.setJti(claimsSet.getJWTID());
        claims.setSubject(claimsSet.getSubject());
        claims.setIssuer(claimsSet.getIssuer());
        claims.setExpiration(claimsSet.getExpirationTime());
        claims.setIssuedAt(claimsSet.getIssueTime());

        // Keycloak preferred_username and email
        try {
            String preferredUsername = claimsSet.getStringClaim("preferred_username");
            claims.setUsername(preferredUsername != null ? preferredUsername : claimsSet.getSubject());
            claims.setEmail(claimsSet.getStringClaim("email"));
        } catch (Exception ignored) {
            claims.setUsername(claimsSet.getSubject());
        }

        // Keycloak realm_access.roles
        try {
            Object realmAccessObj = claimsSet.getClaim("realm_access");
            if (realmAccessObj instanceof Map<?, ?> realmAccess) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> rolesList) {
                    for (Object r : rolesList) {
                        if (r != null) {
                            claims.getRealmRoles().add(r.toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return claims;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(List<String> realmRoles) {
        this.realmRoles = realmRoles;
    }

    public Date getExpiration() {
        return expiration;
    }

    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
