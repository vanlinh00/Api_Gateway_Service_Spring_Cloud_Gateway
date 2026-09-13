# API Gateway Service - Production-Grade Architecture

Enterprise Ingress Gateway built on **Spring Cloud Gateway (WebFlux)**, **Keycloak OIDC**, and **Reactive Redis**.

---

## 1. Modular Package Architecture

```
com.example.gateway
├── ApiGatewayApplication.java
│
├── core/
│   ├── constant/
│   │   └── GatewayConstants.java           # Tracing, Auth, and Context constants
│   ├── exception/
│   │   ├── GatewayException.java           # Base abstract domain exception
│   │   ├── AuthenticationException.java    # 401 Unauthorized exceptions
│   │   ├── TokenRevokedException.java      # 401 Token Blacklisted exception
│   │   └── GlobalErrorWebExceptionHandler.java # Centralized reactive error format (RFC 7807)
│   └── model/
│       ├── AuthenticatedUser.java          # Immutable user domain context
│       └── GatewayErrorResponse.java       # Unified error JSON envelope
│
├── config/
│   ├── GatewaySecurityProperties.java      # Type-safe @ConfigurationProperties
│   ├── RedisConfig.java                    # Reactive Lettuce connection pool & templates
│   ├── SecurityConfig.java                 # WebFlux security & Keycloak JWT decoders
│   └── CircuitBreakerConfig.java           # Reactive Resilience4j circuit breakers
│
├── security/
│   ├── jwt/
│   │   ├── JwtVerifier.java                # Interface contract
│   │   └── NimbusKeycloakJwtVerifier.java  # JWKS asymmetric RS256/ES256 verification
│   ├── revocation/
│   │   ├── TokenRevocationService.java     # Revocation store interface contract
│   │   └── RedisTokenRevocationService.java# Reactive Redis Lettuce O(1) blacklist lookups
│   └── matcher/
│       └── PublicPathMatcher.java          # AntPathMatcher for public route whitelisting
│
├── filter/global/
│   ├── CorrelationIdGlobalFilter.java      # Order(-100): Distributed tracing (X-Correlation-Id)
│   ├── RequestMetricsGlobalFilter.java     # Order(-50):  Latency metrics (X-Response-Time-Ms) & audit logging
│   └── AuthenticationGlobalFilter.java     # Order(0):    Keycloak JWKS + Redis Blacklist verification
│
└── controller/
    └── FallbackController.java             # Resilience4j circuit breaker fallback endpoints
```

---

## 2. Senior / Enterprise Design Highlights

1. **Separation of Concerns (SoC) & Dependency Inversion (DIP)**:
   - Filters do not touch Nimbus internals or Redis commands directly.
   - Decoupled contracts: `JwtVerifier` and `TokenRevocationService` can be independently tested, mocked, or swapped (e.g. Memory Cache, Sentinel, Redis Cluster).

2. **Distributed Tracing (`X-Correlation-Id`)**:
   - Every request is tagged with a trace identifier at Order `-100`.
   - Propagated to downstream services and returned in the HTTP response headers for debugging.

3. **Observability & Latency Monitoring (`X-Response-Time-Ms`)**:
   - `RequestMetricsGlobalFilter` calculates processing duration in milliseconds and outputs structured access logs tagged with Correlation ID.

4. **Fault Tolerance & Resilience (Resilience4j Circuit Breaker)**:
   - Downstream microservice timeouts or crashes trigger circuit breakers and route cleanly to `/fallback/...`, returning unified 503 JSON without leaking internal network traces.

5. **RFC 7807 Standardized Unified Error Handling**:
   - `GlobalErrorWebExceptionHandler` intercepts all reactive exceptions (missing header, expired token, revoked token, bad gateway) and produces uniform, consistent JSON envelopes.
