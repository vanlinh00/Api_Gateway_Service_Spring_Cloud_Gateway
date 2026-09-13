package com.example.gateway.security.revocation;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Contract for querying and managing token revocation status (Token Blacklist).
 */
public interface TokenRevocationService {

    /**
     * Checks whether the given token identifier is blacklisted.
     *
     * @param tokenId The Keycloak 'jti' claim (UUID string)
     * @return Mono emitting true if revoked, false otherwise
     */
    Mono<Boolean> isRevoked(String tokenId);

    /**
     * Registers a token as revoked in the revocation store with an expiration TTL.
     *
     * @param tokenId The Keycloak 'jti' claim
     * @param ttl Time-to-live until the token would naturally expire
     * @return Mono emitting true on successful revocation
     */
    Mono<Boolean> revokeToken(String tokenId, Duration ttl);
}
