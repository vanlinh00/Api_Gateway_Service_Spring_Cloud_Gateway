# Kiến Trúc Xác Thực (Authen) & Phân Quyền (Author) Trong Microservices

Kiến trúc phân tách trách nhiệm giữa **API Gateway** và **Resource Service (Downstream Microservice)**.

---

## 1. Phân Tách Trách Nhiệm: Authen vs Author

| Hạng mục | API Gateway (`api-gateway-service`) | Resource Service (`resource-service`) |
|---|---|---|
| **Nhiệm vụ chính** | **Authentication (Xác thực danh tính)** | **Authorization (Kiểm tra quyền hạn)** |
| **Kiểm tra JWT** | Giải mã & xác thực chữ ký Keycloak RS256 qua JWKS | **KHÔNG** cần giải mã hay xác thực lại chữ ký |
| **Kiểm tra Logout/Revocation** | Truy vấn Redis Blacklist (`jwt:blacklist:<jti>`) | **KHÔNG** cần kết nối hay truy vấn Redis |
| **Dữ liệu tiếp nhận** | Bearer Token từ Client ngoài internet | Trusted Internal Headers từ Gateway (`X-Auth-*`) |
| **Mã lỗi phản hồi** | `401 Unauthorized` (nếu sai token / đã logout) | `403 Forbidden` (nếu user không đủ quyền) |

---

## 2. Vì Sao Resource Service KHÔNG CẦN Check Authen Nữa?

1. **Bảo vệ tập trung tại cửa ngõ (Perimeter Defense):**
   - API Gateway đóng vai trò là "người gác cổng" duy nhất tiếp xúc với internet.
   - Gateway đã xác minh chữ ký số của Keycloak, hạn sử dụng token, và kiểm tra danh sách đen (Blacklist) trong Redis. Request chỉ đến được mạng nội bộ khi đã vượt qua bước Authen thành công.

2. **Tối ưu hiệu năng & giảm độ trễ (Latency):**
   - Nếu mỗi microservice (Order, Invoice, Inventory, User...) đều lặp lại việc tải JWKS, tính toán mật mã RSA, và gọi Redis Blacklist thì CPU và network latency sẽ bị nhân lên gấp nhiều lần.
   - Resource Service chỉ cần đọc context đã xác thực sẵn qua headers (`X-Auth-User-Id`, `X-Auth-Roles`).

3. **Độc lập và đơn giản hóa mã nguồn:**
   - Resource Service không cần thư viện OAuth2 Resource Server, không cần cấu hình Keycloak URL, không cần kết nối Redis Blacklist.
   - Code chỉ tập trung 100% vào nghiệp vụ phân quyền: `@PreAuthorize("hasRole('ADMIN')")` hoặc kiểm tra quyền sở hữu tài nguyên (User A chỉ sửa đơn hàng của User A).

---

## 3. Luồng Hoạt Động Cụ Thể

```
Client ──[ Bearer JWT ]──► API Gateway (Port 8080)
                              │
                              ├─ 1. Check Authen: Keycloak JWKS (RS256)
                              ├─ 2. Check Blacklist: Redis 'jwt:blacklist:<jti>'
                              │     └── Thất bại: Trả về HTTP 401 Unauthorized
                              │
                              ▼ Thành công: Bổ sung Header
                        ┌──────────────────────────────┐
                        │ X-Auth-User-Id: usr-12345    │
                        │ X-Auth-Roles: ROLE_USER      │
                        └──────────────┬───────────────┘
                                       │
                                       ▼ (Mạng nội bộ)
                          Resource Service (Port 8082)
                                       │
                                       ├─ KHÔNG check Authen
                                       └─ CHỈ check Author (@PreAuthorize):
                                             ├─ /user-data: Cho phép (ROLE_USER) ──► 200 OK
                                             └─ /admin-audit: Từ chối            ──► 403 Forbidden
```
