package com.example.gateway.controller;

import com.example.gateway.core.constant.GatewayConstants;
import com.example.gateway.core.model.GatewayErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Fallback Controller for Resilience4j Circuit Breakers.
 * Handles timeouts or outages of downstream microservices gracefully.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/user-service")
    public Mono<ResponseEntity<GatewayErrorResponse>> userServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "User Service");
    }

    @GetMapping("/order-service")
    public Mono<ResponseEntity<GatewayErrorResponse>> orderServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Order Service");
    }

    @GetMapping("/resource-service")
    public Mono<ResponseEntity<GatewayErrorResponse>> resourceServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Resource Service");
    }

    private Mono<ResponseEntity<GatewayErrorResponse>> buildFallbackResponse(ServerWebExchange exchange, String serviceName) {
        String correlationId = exchange.getAttributeOrDefault(
                GatewayConstants.ATTR_CORRELATION_ID,
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID)
        );
        String path = exchange.getRequest().getURI().getPath();

        log.warn("[CorrelationId: {}] Circuit breaker triggered fallback for {} on path [{}]",
                correlationId, serviceName, path);

        GatewayErrorResponse error = GatewayErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                serviceName + " is currently experiencing issues or timing out. Please retry shortly.",
                path,
                correlationId
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }
}
