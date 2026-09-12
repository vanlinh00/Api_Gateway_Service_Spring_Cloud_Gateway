package com.example.gateway.filter;

import com.example.gateway.config.JwtProperties;
import com.example.gateway.dto.ErrorResponse;
import com.example.gateway.util.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * GlobalFilter kiểm tra tính hợp lệ của JWT và tra cứu danh sách thu hồi (Blacklist) trong Redis.
 * Sử dụng Non-blocking Reactive I/O với ReactiveStringRedisTemplate của Spring Data Redis.
 */
@Component
public class JwtBlacklistGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtBlacklistGlobalFilter(JwtUtils jwtUtils,
                                    JwtProperties jwtProperties,
                                    ReactiveStringRedisTemplate reactiveRedisTemplate) {
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Kiểm tra xem đường dẫn có nằm trong danh sách White-list (Public endpoints) hay không
        if (isExcludedPath(path)) {
            log.debug("Path [{}] is public/excluded. Skipping JWT Blacklist check.", path);
            return chain.filter(exchange);
        }

        // 2. Trích xuất header "Authorization"
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 2.1. Nếu không có token hoặc token không đúng định dạng "Bearer <token>"
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for request to path: [{}]", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        // 2.2. Lấy chuỗi token thực tế
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("Empty Bearer token in Authorization header for path: [{}]", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Empty Bearer token");
        }

        Claims claims;
        try {
            // Giải mã JWT và trích xuất claims
            claims = jwtUtils.extractAllClaims(token);
        } catch (Exception e) {
            log.warn("Failed to parse/validate JWT token for path [{}]: {}", path, e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token: " + e.getMessage());
        }

        // 3. Lấy định danh duy nhất của Token (Ưu tiên JTI - JWT ID, hoặc mã băm của Token)
        String tokenId = jwtUtils.extractTokenIdentifier(token, claims);
        String redisKey = jwtProperties.getBlacklistPrefix() + tokenId;

        // 4. Kiểm tra sự tồn tại trong Redis Blacklist bằng Reactive API (Non-blocking)
        return reactiveRedisTemplate.hasKey(redisKey)
                .flatMap(isBlacklisted -> {
                    // 4.1. Nếu Token ĐÃ CÓ trong Redis Blacklist -> Ghi log Warning và Chặn request (HTTP 401)
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        String subject = claims.getSubject();
                        String clientIp = request.getRemoteAddress() != null 
                                ? request.getRemoteAddress().getAddress().getHostAddress() 
                                : "UNKNOWN";

                        log.warn("WARNING: Revoked/Blacklisted JWT detected! Token ID: [{}], Subject: [{}], Path: [{}], Client IP: [{}]",
                                tokenId, subject, path, clientIp);

                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                                "Token has been revoked/blacklisted. Please log in again.");
                    }

                    // 4.2. Nếu Token KHÔNG CÓ trong Redis Blacklist -> Cho phép request đi tiếp qua chuỗi lọc
                    log.debug("Token [{}] is valid and not blacklisted. Forwarding request to downstream service...", tokenId);

                    // Tùy chọn: Gắn thông tin User đã xác thực vào downstream headers để các backend service sử dụng
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-Auth-User-Id", claims.getSubject() != null ? claims.getSubject() : "")
                            .header("X-Auth-Token-Id", tokenId)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(ex -> {
                    // Xử lý khi có sự cố kết nối Redis hoặc ngoại lệ ngoài ý muốn
                    log.error("Error occurred while checking Redis Blacklist for token [{}]: {}", tokenId, ex.getMessage(), ex);
                    return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authentication verification failed: " + ex.getMessage());
                });
    }

    /**
     * Ghi response lỗi chuẩn JSON và mã HTTP 401 Unauthorized về phía client ngay lập tức
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            String fallbackJson = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
            bytes = fallbackJson.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Kiểm tra đường dẫn có khớp với danh sách excluded paths không
     */
    private boolean isExcludedPath(String requestPath) {
        if (jwtProperties.getExcludedPaths() == null || jwtProperties.getExcludedPaths().isEmpty()) {
            return false;
        }
        for (String pattern : jwtProperties.getExcludedPaths()) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Thứ tự ưu tiên của Filter.
     * Giá trị âm (-1) giúp filter chạy sớm trước khi request được chuyển đến Netty RoutingFilter
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
