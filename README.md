# Microservice Authorization & Token Revocation Architecture

A centralized authentication and fine-grained authorization (FGA) architecture for distributed microservices.

---

## 1. Service-to-Service API Authorization (FGA)

Downstream microservices (e.g., `invoice-service`, `order-service`) evaluate granular action permissions via:

- **`common-auth-library` Client**: Services import the shared library to enforce declarative security (e.g., `@RequirePermission("invoice:read")`) on incoming requests.
- **High-Performance Redis Evaluation (`O(1)` SUNION)**:
  - User roles, tenant scopes, and resource permissions are cached in Redis sets.
  - Permission resolution executes via Redis `SUNION` in `O(1)` time, aggregating effective permissions across inherited roles with sub-millisecond latency.
- **Resilient Database Fallback**: If a cache miss occurs or Redis is degraded, the client falls back to `user-auth-service` APIs to load authoritative database permissions and asynchronously refresh the cache.

---

## 2. Token Revocation & Logout Verification

Stateless Keycloak JWT tokens are invalidated in real time across all services:

- **Revocation Registration**: Upon user logout, session termination, or admin ban, the unique JWT identifier (`jti`) is written to Redis under the `jwt:blacklist:<jti>` prefix with a TTL matching the remaining token lifespan.
- **Perimeter & Filter Verification**: The API Gateway and microservice filters inspect the `jti` against Redis before dispatching requests.
- **Instant Rejection**: If the key exists in Redis, the request is terminated immediately with `HTTP 401 Unauthorized` and an audit log warning, completely preventing replay attacks.
