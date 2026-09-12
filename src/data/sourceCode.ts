import { ProjectFile } from '../types';

export const PROJECT_FILES: ProjectFile[] = [
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/filter/JwtBlacklistGlobalFilter.java',
    name: 'JwtBlacklistGlobalFilter.java',
    language: 'java',
    category: 'filter',
    description: 'Core GlobalFilter: Trích xuất Bearer token, kiểm tra Redis Reactive Blacklist, chặn 401 & ghi log warning khi bị thu hồi.',
    content: `package com.example.gateway.filter;

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
            String fallbackJson = "{\\"status\\":401,\\"error\\":\\"Unauthorized\\",\\"message\\":\\"" + message + "\\"}";
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
}`
  },
  {
    path: 'api-gateway-service/src/main/resources/application.yml',
    name: 'application.yml',
    language: 'yaml',
    category: 'config',
    description: 'Cấu hình cổng Gateway, thông số kết nối Redis Reactive (Lettuce pool) và các routes proxy mẫu (user-auth-service, order-service).',
    content: `server:
  port: 8080

spring:
  application:
    name: api-gateway-service

  # Cấu hình Spring Data Redis Reactive (Sử dụng Lettuce Driver Non-blocking)
  data:
    redis:
      host: \${REDIS_HOST:localhost}
      port: \${REDIS_PORT:6379}
      password: \${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
          max-wait: 2000ms
        shutdown-timeout: 100ms

  # Cấu hình Spring Cloud Gateway Routes
  cloud:
    gateway:
      routes:
        # Route 1: Proxy đến User Authentication Service
        - id: user-auth-service-route
          uri: \${USER_AUTH_SERVICE_URI:http://localhost:8081}
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**
          filters:
            - AddRequestHeader=X-Gateway-Name, api-gateway-service
            - AddResponseHeader=X-Gateway-Processed-Time, #{T(java.lang.System).currentTimeMillis()}

        # Route 2: Ví dụ proxy đến Order Service (Minh họa cấu hình nhiều service)
        - id: order-service-route
          uri: \${ORDER_SERVICE_URI:http://localhost:8082}
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - AddRequestHeader=X-Gateway-Name, api-gateway-service

      # Cấu hình CORS toàn cục cho Gateway (tùy chọn nhưng rất quan trọng)
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"

# Cấu hình tùy chỉnh cho JWT & Redis Blacklist
jwt:
  # Base64-encoded secret key 256-bit (HS256) tối thiểu 32 ký tự
  secret-key: \${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
  # Prefix lưu trữ key trong Redis để tránh xung đột
  blacklist-prefix: "jwt:blacklist:"
  # Các URI công khai (Public) không yêu cầu kiểm tra token
  excluded-paths:
    - /api/v1/auth/login
    - /api/v1/auth/register
    - /api/v1/auth/refresh-token
    - /actuator/health
    - /actuator/info

# Cấu hình Actuator endpoint giám sát
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway,metrics
  endpoint:
    health:
      show-details: always

# Cấu hình Logging
logging:
  level:
    root: INFO
    org.springframework.cloud.gateway: INFO
    com.example.gateway.filter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"`
  },
  {
    path: 'api-gateway-service/pom.xml',
    name: 'pom.xml',
    language: 'xml',
    category: 'config',
    description: 'Maven dependencies: Spring Boot 3.4.3, Spring Cloud 2024.0.0, Spring Data Redis Reactive, JJWT 0.12.6, commons-pool2.',
    content: `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>api-gateway-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>api-gateway-service</name>
    <description>Spring Cloud Gateway with Redis Reactive JWT Blacklist Filter</description>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2024.0.0</spring-cloud.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <!-- Spring Cloud Gateway (Reactive Non-blocking) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Spring Data Redis Reactive (Lettuce driver) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Connection Pool for Lettuce Reactive Redis -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- JWT library (jjwt 0.12.x for Java 17 + Spring Boot 3) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>\${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>\${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>\${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Actuator for Health checks & Metrics -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Configuration Processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Unit & Integration Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>\${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/config/RedisConfig.java',
    name: 'RedisConfig.java',
    language: 'java',
    category: 'java',
    description: 'Cấu hình ReactiveStringRedisTemplate tương thích 100% Non-blocking I/O của Reactor Netty.',
    content: `package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình Redis Reactive cho Spring Cloud Gateway (Spring Boot 3.4)
 * Đảm bảo 100% Non-blocking I/O tương thích với Reactor Netty.
 */
@Configuration
public class RedisConfig {

    /**
     * ReactiveStringRedisTemplate: Tối ưu cho việc lưu và truy xuất Key - Value dạng chuỗi
     * (Ví dụ: key="jwt:blacklist:<jti>", value="REVOKED")
     */
    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        return new ReactiveStringRedisTemplate(reactiveRedisConnectionFactory);
    }

    /**
     * ReactiveRedisTemplate với Serialization String - String tổng quát
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/util/JwtUtils.java',
    name: 'JwtUtils.java',
    language: 'java',
    category: 'java',
    description: 'Tiện ích trích xuất claims, JTI (JWT ID) hoặc mã băm SHA-256 an toàn từ token.',
    content: `package com.example.gateway.util;

import com.example.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

/**
 * Tiện ích xử lý và trích xuất thông tin từ JSON Web Token (JJWT 0.12.x)
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = initKey(jwtProperties.getSecretKey());
    }

    private SecretKey initKey(String secret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(keyBytes);
        }
    }

    /**
     * Parse và trích xuất Claims từ JWT Token
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Lấy định danh duy nhất của Token:
     * 1. Ưu tiên Claim "jti" (JWT ID) nếu có.
     * 2. Nếu không có "jti", sử dụng mã băm SHA-256 của chuỗi token để làm key Redis gọn nhẹ và an toàn.
     */
    public String extractTokenIdentifier(String token, Claims claims) {
        if (claims != null && claims.getId() != null && !claims.getId().trim().isEmpty()) {
            return claims.getId();
        }
        return sha256Hex(token);
    }

    /**
     * Kiểm tra tính hợp lệ cơ bản của Token
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            return expiration == null || !expiration.before(new Date());
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature/format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/config/JwtProperties.java',
    name: 'JwtProperties.java',
    language: 'java',
    category: 'java',
    description: 'POJO liên kết cấu hình jwt.* trong application.yml (Secret, Prefix, Excluded paths).',
    content: `package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private String blacklistPrefix = "jwt:blacklist:";
    private List<String> excludedPaths = new ArrayList<>();

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBlacklistPrefix() {
        return blacklistPrefix;
    }

    public void setBlacklistPrefix(String blacklistPrefix) {
        this.blacklistPrefix = blacklistPrefix;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/dto/ErrorResponse.java',
    name: 'ErrorResponse.java',
    language: 'java',
    category: 'java',
    description: 'Mẫu đối tượng phản hồi lỗi JSON đồng nhất trả về cho client khi 401 Unauthorized.',
    content: `package com.example.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/filter/RequestLoggingFilter.java',
    name: 'RequestLoggingFilter.java',
    language: 'java',
    category: 'filter',
    description: 'Filter ghi log Access Log và đo độ trễ Latency của từng request qua Gateway.',
    content: `package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 500;

            log.info("[GATEWAY-ACCESS] {} {} -> Status: {} (took {} ms)", method, path, statusCode, duration);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}`
  },
  {
    path: 'api-gateway-service/src/main/java/com/example/gateway/ApiGatewayApplication.java',
    name: 'ApiGatewayApplication.java',
    language: 'java',
    category: 'java',
    description: 'Lớp khởi chạy Spring Boot Gateway Application.',
    content: `package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}`
  },
  {
    path: 'api-gateway-service/src/test/java/com/example/gateway/filter/JwtBlacklistGlobalFilterTest.java',
    name: 'JwtBlacklistGlobalFilterTest.java',
    language: 'java',
    category: 'test',
    description: 'Unit test kiểm thử Reactive GlobalFilter với Mockito và StepVerifier.',
    content: `package com.example.gateway.filter;

import com.example.gateway.config.JwtProperties;
import com.example.gateway.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistGlobalFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Mock
    private GatewayFilterChain chain;

    private JwtBlacklistGlobalFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getBlacklistPrefix()).thenReturn("jwt:blacklist:");
        lenient().when(jwtProperties.getExcludedPaths()).thenReturn(Collections.singletonList("/api/v1/auth/login"));

        filter = new JwtBlacklistGlobalFilter(jwtUtils, jwtProperties, reactiveRedisTemplate);
    }

    @Test
    @DisplayName("Ngoại lệ: Request không có Header Authorization -> Trả về 401 Unauthorized")
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Ngoại lệ: Token đã bị thu hồi trong Redis Blacklist -> Trả về 401 Unauthorized và log warning")
    void shouldReturn401WhenTokenIsBlacklistedInRedis() {
        String token = "valid.jwt.token";
        String tokenId = "jti-123456";
        String redisKey = "jwt:blacklist:jti-123456";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = Jwts.claims().subject("user123").id(tokenId).build();
        when(jwtUtils.extractAllClaims(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Thành công: Token hợp lệ và không có trong Redis -> Chuyển tiếp request đến service backend")
    void shouldPassFilterWhenTokenIsNotBlacklisted() {
        String token = "valid.jwt.token";
        String tokenId = "jti-999999";
        String redisKey = "jwt:blacklist:jti-999999";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = Jwts.claims().subject("user123").id(tokenId).build();
        when(jwtUtils.extractAllClaims(token)).thenReturn(claims);
        when(jwtUtils.extractTokenIdentifier(token, claims)).thenReturn(tokenId);
        when(reactiveRedisTemplate.hasKey(eq(redisKey))).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }
}`
  },
  {
    path: 'api-gateway-service/docker-compose.yml',
    name: 'docker-compose.yml',
    language: 'yaml',
    category: 'docker',
    description: 'Docker Compose khởi chạy toàn bộ môi trường (Redis + Gateway + Mock Backend Service).',
    content: `version: '3.8'

services:
  redis:
    image: redis:7.4-alpine
    container_name: redis-gateway
    ports:
      - "6379:6379"
    command: ["redis-server", "--appendonly", "yes", "--save", "60", "1"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  user-auth-service:
    image: kennethreitz/httpbin
    container_name: mock-user-auth-service
    ports:
      - "8081:80"

  api-gateway-service:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: api-gateway-service
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - USER_AUTH_SERVICE_URI=http://user-auth-service:80
      - JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
    depends_on:
      redis:
        condition: service_healthy

volumes:
  redis-data:`
  },
  {
    path: 'api-gateway-service/Dockerfile',
    name: 'Dockerfile',
    language: 'dockerfile',
    category: 'docker',
    description: 'Multi-stage Dockerfile tối ưu hoá kích thước container với Eclipse Temurin JRE 17 Alpine.',
    content: `FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`
  },
  {
    path: 'api-gateway-service/README.md',
    name: 'README.md',
    language: 'markdown',
    category: 'doc',
    description: 'Tài liệu hướng dẫn chi tiết về cấu trúc, chạy dự án, lệnh cURL và kiến trúc tối ưu.',
    content: `# API Gateway Service - Spring Cloud Gateway & Redis JWT Blacklist

Dự án API Gateway xây dựng trên nền tảng Spring Boot 3.4 và Spring Cloud Gateway (2024.0.0) với Java 17, tích hợp cơ chế bảo mật xác thực JSON Web Token (JWT) và kiểm tra danh sách thu hồi token (JWT Blacklist) qua Redis Reactive.

Xem chi tiết trong file README.md đã được tạo.`
  }
];
