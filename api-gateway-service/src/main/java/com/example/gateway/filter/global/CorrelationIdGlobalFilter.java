package com.example.gateway.filter.global;

import com.example.gateway.core.constant.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Filter that establishes Distributed Tracing context.
 * Guarantees every request has an 'X-Correlation-Id' across all downstream hops.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public int getOrder() {
        // Runs earliest before all other filters
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String existingCorrelationId = request.getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID);

        String correlationId = StringUtils.hasText(existingCorrelationId)
                ? existingCorrelationId
                : UUID.randomUUID().toString();

        exchange.getAttributes().put(GatewayConstants.ATTR_CORRELATION_ID, correlationId);

        // Mutate request with correlation id for downstream services
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(GatewayConstants.HEADER_CORRELATION_ID, correlationId)
                .header(GatewayConstants.HEADER_GATEWAY_NAME, GatewayConstants.GATEWAY_NAME_VALUE)
                .build();

        // Mutate response with correlation id for client debugging
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_CORRELATION_ID, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
