package com.example.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Enterprise Spring Cloud LoadBalancer Configuration for API Gateway.
 * 
 * Enables:
 * - Reactive non-blocking client-side load balancing for microservices (lb://user-service, lb://order-service, lb://resource-service).
 * - Caffeine-backed service instance caching with automatic TTL expiration.
 * - LoadBalanced WebClient.Builder for reactive internal gateway communications.
 */
@Configuration
@LoadBalancerClients
public class LoadBalancerConfig {

    /**
     * Provides a non-blocking WebClient.Builder pre-configured with Spring Cloud LoadBalancer filter.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
