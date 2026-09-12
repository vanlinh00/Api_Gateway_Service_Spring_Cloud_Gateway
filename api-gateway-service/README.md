# API Gateway Service - Spring Cloud Gateway & Redis JWT Blacklist

Dự án API Gateway xây dựng trên nền tảng **Spring Boot 3.4** và **Spring Cloud Gateway (2024.0.0)** với **Java 17**, tích hợp cơ chế bảo mật xác thực JSON Web Token (JWT) và kiểm tra danh sách thu hồi token (**JWT Blacklist**) qua **Redis Reactive**.

---

## 🌟 Tính Năng Nổi Bật

1. **Non-blocking Reactive I/O 100%**: Sử dụng `ReactiveStringRedisTemplate` và `Spring Data Redis Reactive` (Lettuce driver), không gây nghẽn Thread Event Loop của Netty.
2. **GlobalFilter tự động**:
   - Trích xuất `Authorization: Bearer <token>`.
   - Chặn ngay lập tức trả về `HTTP 401 Unauthorized` nếu không có token hoặc sai định dạng.
   - Giải mã và trích xuất định danh token (`jti` hoặc SHA-256 hash của token).
   - Tra cứu Redis Blacklist siêu tốc qua `hasKey()`.
   - Nếu token **ĐÃ CÓ TRONG BLACKLIST**: Ghi log `WARN` cảnh báo token đã bị thu hồi kèm chi tiết Token ID, User ID, Client IP, và lập tức ngắt kết nối với mã lỗi `401`.
   - Nếu token **HỢP LỆ**: Bổ sung header định danh (`X-Auth-User-Id`, `X-Auth-Token-Id`) và chuyển tiếp sang backend microservice (`user-auth-service`).
3. **Cấu hình định tuyến Route linh hoạt**: Khai báo routes qua `application.yml` dễ mở rộng nhiều microservices trong tương lai.
4. **Tự động hết hạn (TTL)**: Khi thu hồi token tại Auth Service, chỉ cần set TTL bằng đúng thời gian sống còn lại của JWT để giải phóng bộ nhớ Redis.

---

## 📁 Cấu Trúc Dự Án

```
api-gateway-service/
├── pom.xml                                    # Spring Boot 3.4.3 & Spring Cloud 2024.0.0
├── Dockerfile                                 # Multi-stage build với JRE 17 Alpine
├── docker-compose.yml                         # Khởi chạy Redis + Gateway + Mock Service
├── README.md                                  # Hướng dẫn chi tiết
└── src/
    ├── main/
    │   ├── java/com/example/gateway/
    │   │   ├── ApiGatewayApplication.java      # Main entry point
    │   │   ├── config/
    │   │   │   ├── RedisConfig.java           # ReactiveStringRedisTemplate Bean
    │   │   │   └── JwtProperties.java         # Cấu hình Secret, Prefix, Excluded paths
    │   │   ├── dto/
    │   │   │   └── ErrorResponse.java         # Chuẩn JSON phản hồi lỗi 401
    │   │   ├── filter/
    │   │   │   ├── JwtBlacklistGlobalFilter.java  # Core GlobalFilter kiểm tra Blacklist
    │   │   │   └── RequestLoggingFilter.java  # Ghi log thời gian phản hồi (latency)
    │   │   └── util/
    │   │       └── JwtUtils.java              # Xử lý giải mã và trích xuất JTI / Hash
    │   └── resources/
    │       └── application.yml                # Cấu hình kết nối Redis, Route proxy
    └── test/
        └── java/com/example/gateway/filter/
            └── JwtBlacklistGlobalFilterTest.java # Unit test với StepVerifier
```

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### Cách 1: Khởi chạy bằng Docker Compose (Khuyên dùng)
```bash
cd api-gateway-service
docker compose up -d
```
Hệ thống sẽ tự động khởi tạo:
- **Redis Server**: Cổng `6379`
- **Mock User Auth Service**: Cổng `8081`
- **API Gateway Service**: Cổng `8080`

### Cách 2: Khởi chạy cục bộ bằng Maven
1. Bật Redis cục bộ:
```bash
docker run -d -p 6379:6379 --name my-redis redis:7-alpine
```
2. Chạy Spring Boot Gateway:
```bash
mvn clean spring-boot:run
```

---

## 🧪 Kịch Bản Kiểm Thử (cURL)

### 1. Test không có Token (hoặc sai định dạng)
```bash
curl -i http://localhost:8080/api/v1/users/profile
```
**Kết quả mong đợi:** HTTP 401 Unauthorized
```json
{
  "timestamp": "2026-09-12T13:45:00.123",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header",
  "path": "/api/v1/users/profile"
}
```

---

### 2. Thu hồi Token (Đưa vào Redis Blacklist)
Giả sử người dùng logout hoặc đổi mật khẩu, Auth Service lưu `jti` vào Redis:
```bash
# Giả sử token có JTI là "jti-token-999" với thời gian sống còn lại 3600 giây (1 giờ):
redis-cli SET "jwt:blacklist:jti-token-999" "REVOKED_USER_LOGOUT" EX 3600
```

---

### 3. Test gửi Request với Token ĐÃ BỊ THU HỒI
```bash
curl -i http://localhost:8080/api/v1/users/profile \
  -H "Authorization: Bearer <TOKEN_CO_JTI_LA_jti-token-999>"
```
**Kết quả mong đợi:** 
- Gateway **chặn ngay lập tức** và trả về HTTP `401 Unauthorized`:
```json
{
  "timestamp": "2026-09-12T13:45:10.500",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token has been revoked/blacklisted. Please log in again.",
  "path": "/api/v1/users/profile"
}
```
- Console Gateway ghi dòng log cảnh báo `WARN`:
```log
2026-09-12 13:45:10.500 [reactor-http-epoll-2] WARN  c.e.g.f.JwtBlacklistGlobalFilter - WARNING: Revoked/Blacklisted JWT detected! Token ID: [jti-token-999], Subject: [user@example.com], Path: [/api/v1/users/profile], Client IP: [127.0.0.1]
```

---

### 4. Test gửi Request với Token HỢP LỆ (Không có trong Redis)
```bash
curl -i http://localhost:8080/api/v1/users/profile \
  -H "Authorization: Bearer <VALID_ACTIVE_JWT>"
```
**Kết quả mong đợi:** Gateway forward request thành công đến `user-auth-service` (HTTP 200 OK).

---

## ⚡ Thiết Kế Tối Ưu Cho Môi Trường Production

1. **JTI vs Full Token String**:
   - Khuyên dùng claim `jti` (UUID v4) trong payload JWT thay vì lưu toàn bộ chuỗi token dài 500+ bytes vào Redis. Tiết kiệm 80-90% RAM Redis khi có hàng triệu user.
2. **TTL Khớp Hạn Dùng JWT**:
   - `TTL = exp - currentTime`. Khi JWT tự hết hạn theo thời gian, Redis tự động giải phóng key, không cần chạy cron job xóa rác.
3. **Reactive Connection Pool**:
   - Sử dụng thư viện `commons-pool2` kết hợp `lettuce.pool` đảm bảo throughput cao mà không tạo quá nhiều socket connection tới Redis.
