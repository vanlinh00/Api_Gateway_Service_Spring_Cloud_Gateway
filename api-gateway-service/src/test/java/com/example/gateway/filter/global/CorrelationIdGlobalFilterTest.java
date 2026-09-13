package com.example.gateway.filter.global;

import com.example.gateway.core.constant.GatewayConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdGlobalFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    @DisplayName("Generates new Correlation ID when missing")
    void shouldGenerateCorrelationIdWhenMissing() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        String correlationId = captured.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID);
        assertNotNull(correlationId);
        assertEquals(correlationId, captured.getResponse().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID));
    }

    @Test
    @DisplayName("Preserves existing Correlation ID from client")
    void shouldPreserveExistingCorrelationId() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        String existingId = "client-trace-777";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(GatewayConstants.HEADER_CORRELATION_ID, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        assertEquals(existingId, captured.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID));
        assertEquals(existingId, captured.getResponse().getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID));
    }
}
