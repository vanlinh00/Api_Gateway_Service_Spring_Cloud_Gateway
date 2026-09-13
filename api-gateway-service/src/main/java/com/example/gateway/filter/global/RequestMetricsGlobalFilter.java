package com.example.gateway.filter.global;

import com.example.gateway.core.constant.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise Observability Global Filter:
 * Measures request latency, appends 'X-Response-Time-Ms', and emits structured audit logs.
 */
@Component
public class RequestMetricsGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsGlobalFilter.class);

    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(GatewayConstants.ATTR_START_TIME, startTime);

        return chain.filter(exchange).doFinally(signalType -> {
            long duration = System.currentTimeMillis() - startTime;
            String correlationId = exchange.getAttributeOrDefault(GatewayConstants.ATTR_CORRELATION_ID, "N/A");
            String method = exchange.getRequest().getMethod().name();
            String path = exchange.getRequest().getURI().getPath();
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            int statusCode = status != null ? status.value() : 0;

            exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_RESPONSE_TIME_MS, String.valueOf(duration));

            log.info("[CorrelationId: {}] {} {} -> Status: {} ({}ms)",
                    correlationId, method, path, statusCode, duration);
        });
    }
}
