package com.example.gateway.security.revocation;

import com.example.gateway.config.GatewaySecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * High-performance Reactive Lettuce Redis Implementation of TokenRevocationService.
 * Executes non-blocking O(1) key lookups with timeout circuit guards.
 */
@Service
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationService.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySecurityProperties properties;

    public RedisTokenRevocationService(ReactiveStringRedisTemplate redisTemplate,
                                       GatewaySecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Mono<Boolean> isRevoked(String tokenId) {
        if (!properties.getBlacklist().isEnabled() || !StringUtils.hasText(tokenId)) {
            return Mono.just(Boolean.FALSE);
        }

        String redisKey = properties.getBlacklist().getPrefix() + tokenId.trim();
        Duration timeout = Duration.ofMillis(properties.getBlacklist().getTimeoutMs());

        return redisTemplate.hasKey(redisKey)
                .timeout(timeout)
                .onErrorResume(e -> {
                    log.error("Redis error or timeout checking revocation for token [{}]: {}", tokenId, e.getMessage());
                    // Fail-safe strategy: log error and allow request or fail-closed based on enterprise policy
                    return Mono.just(Boolean.FALSE);
                });
    }

    @Override
    public Mono<Boolean> revokeToken(String tokenId, Duration ttl) {
        if (!StringUtils.hasText(tokenId)) {
            return Mono.just(Boolean.FALSE);
        }

        String redisKey = properties.getBlacklist().getPrefix() + tokenId.trim();
        return redisTemplate.opsForValue()
                .set(redisKey, "REVOKED", ttl)
                .doOnSuccess(success -> log.info("Successfully revoked token [{}] with TTL {} seconds", tokenId, ttl.getSeconds()));
    }
}
