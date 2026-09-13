package com.example.gateway.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated token's JTI is actively listed in the Redis Revocation Blacklist.
 */
public class TokenRevokedException extends GatewayException {

    private final String tokenId;

    public TokenRevokedException(String tokenId) {
        super("Token [" + tokenId + "] has been revoked/blacklisted. Please log in again.", HttpStatus.UNAUTHORIZED);
        this.tokenId = tokenId;
    }

    public String getTokenId() {
        return tokenId;
    }
}
