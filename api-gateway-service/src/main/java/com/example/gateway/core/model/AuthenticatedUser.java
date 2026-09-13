package com.example.gateway.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable Domain Record representing an authenticated Keycloak User Context.
 */
public record AuthenticatedUser(
        String userId,
        String username,
        String email,
        String tokenId,
        List<String> realmRoles,
        Instant issuedAt,
        Instant expiresAt
) {
    public AuthenticatedUser {
        realmRoles = realmRoles != null ? List.copyOf(realmRoles) : Collections.emptyList();
    }

    public String getRolesAsCsv() {
        return String.join(",", realmRoles);
    }
}
