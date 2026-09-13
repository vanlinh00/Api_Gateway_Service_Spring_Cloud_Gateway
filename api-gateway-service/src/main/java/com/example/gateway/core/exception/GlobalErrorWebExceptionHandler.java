package com.example.gateway.core.exception;

import com.example.gateway.core.constant.GatewayConstants;
import com.example.gateway.core.model.GatewayErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Centralized Enterprise Reactive Error Web Exception Handler.
 * Intercepts all gateway errors (401, 403, 404, 502, 503) and serializes
 * a standardized RFC 7807 GatewayErrorResponse JSON with correlation context.
 */
@Component
@Order(-2) // Runs with high precedence before default Spring WebFlux error handler
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveHttpStatus(ex);
        String correlationId = exchange.getAttributeOrDefault(
                GatewayConstants.ATTR_CORRELATION_ID,
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID)
        );
        if (correlationId == null) {
            correlationId = "N/A";
        }

        String path = exchange.getRequest().getURI().getPath();
        String message = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();

        if (status.is5xxServerError()) {
            log.error("[CorrelationId: {}] Internal Gateway Error on path [{}]: {}", correlationId, path, message, ex);
        } else {
            log.warn("[CorrelationId: {}] Gateway Handled Exception on path [{}]: Status {} - {}", correlationId, path, status.value(), message);
        }

        GatewayErrorResponse errorResponse = GatewayErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                correlationId
        );

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException jsonEx) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveHttpStatus(Throwable ex) {
        if (ex instanceof GatewayException gatewayException) {
            return gatewayException.getStatus();
        }
        if (ex instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.resolve(responseStatusException.getStatusCode().value()) != null
                    ? HttpStatus.resolve(responseStatusException.getStatusCode().value())
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
