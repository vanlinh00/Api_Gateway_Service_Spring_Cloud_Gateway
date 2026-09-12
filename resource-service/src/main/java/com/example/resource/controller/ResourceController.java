package com.example.resource.controller;

import com.example.resource.security.UserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource Controller demonstrating that resource-service ONLY validates Authorization (Author / RBAC),
 * completely trusting the Authentication (Authen) performed upstream by api-gateway-service.
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    /**
     * Accessible by any authenticated user (e.g. ROLE_USER or ROLE_ADMIN)
     */
    @GetMapping("/user-data")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserData(Authentication authentication) {
        UserContext context = (UserContext) authentication.getDetails();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "User resource accessed successfully (Authorization passed)");
        response.put("userId", context.userId());
        response.put("username", context.username());
        response.put("email", context.email());
        response.put("roles", context.roles());
        response.put("data", List.of(
                Map.of("id", "ORD-101", "item", "Laptop Stand", "amount", 45.0),
                Map.of("id", "ORD-102", "item", "Mechanical Keyboard", "amount", 120.0)
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Accessible ONLY by users with ROLE_ADMIN.
     * Regular users (ROLE_USER) are blocked by Spring Security with HTTP 403 Forbidden!
     */
    @GetMapping("/admin-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminAudit(Authentication authentication) {
        UserContext context = (UserContext) authentication.getDetails();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Sensitive administrative resource accessed (ROLE_ADMIN verified)");
        response.put("adminUser", context.username());
        response.put("auditLogs", Arrays.asList(
                "Audit: Gateway forwarded request with validated Keycloak token",
                "Audit: Redis revocation check passed at ingress",
                "Audit: System operational in high-performance mode"
        ));

        return ResponseEntity.ok(response);
    }
}
