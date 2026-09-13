package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Reactive Redis Configuration for Spring Cloud Gateway.
 * Provides high-throughput, non-blocking string operations for token blacklist lookups.
 */
@Configuration
public class RedisConfig {

    /**
     * Primary non-blocking String Redis Template for token revocation checks (O(1) lookups).
     */
    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        return new ReactiveStringRedisTemplate(reactiveRedisConnectionFactory);
    }
}
