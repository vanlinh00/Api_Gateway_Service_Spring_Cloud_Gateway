package com.example.gateway.security.jwt;

import com.example.gateway.core.model.AuthenticatedUser;
import reactor.core.publisher.Mono;

/**
 * Contract for verifying Keycloak OIDC JWT tokens and extracting user context.
 */
public interface JwtVerifier {

    /**
     * Validates cryptographic signature via JWKS, verifies expiration and issuer,
     * and constructs an AuthenticatedUser context.
     *
     * @param token Raw Bearer JWT token string
     * @return Mono emitting the AuthenticatedUser or erroring with AuthenticationException
     */
    Mono<AuthenticatedUser> verifyAndExtract(String token);
}
