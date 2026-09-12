# User Auth Service (user-auth-service)

A Spring Boot 3.4 microservice utilizing **Keycloak Asymmetric (RS256 / JWKS)** JWT validation and **Redis Token Blacklist** revocation.

---

## Keycloak Asymmetric Architecture
All symmetric HMAC (HS256) secret keys, `SecretKeySpec`, and fallback code paths have been completely removed:
- **Asymmetric Signature Verification**: Evaluates tokens using public certificates exposed by Keycloak's JWKS endpoint (`jwt.jwk-set-uri`).
- **Decoder Instantiation**: Uses `NimbusJwtDecoder.withJwkSetUri(...)` or `NimbusJwtDecoder.withIssuerLocation(...)`.
- **Token Revocation Check**: Intercepts requests in `JwtAuthenticationFilter`, extracts the Keycloak UUID identifier (`jti`), queries Redis with `jwt.blacklist-prefix`, and skips whitelisted endpoints in `jwt.excluded-paths`.

---

## Configuration (`application.yml`)
```yaml
jwt:
  jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8180/realms/master/protocol/openid-connect/certs}
  issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/master}
  blacklist-prefix: "jwt:blacklist:"
  excluded-paths:
    - /api/v1/auth/login
    - /api/v1/auth/register
    - /actuator/health
    - /actuator/info
```
