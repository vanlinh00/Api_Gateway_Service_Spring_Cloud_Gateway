package com.example.resource.controller;

import com.example.resource.config.SecurityConfig;
import com.example.resource.security.GatewayHeaderFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResourceController.class)
@Import({SecurityConfig.class, GatewayHeaderFilter.class})
class ResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("401 Unauthorized: Bypassing Gateway (missing X-Auth-User-Id header)")
    void shouldReturn401WhenGatewayHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/resources/user-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Access denied: Missing trusted Gateway identity headers (X-Auth-User-Id)"));
    }

    @Test
    @DisplayName("200 OK: Normal user with ROLE_USER can access user data")
    void shouldAllowUserRoleToAccessUserData() throws Exception {
        mockMvc.perform(get("/api/v1/resources/user-data")
                        .header("X-Auth-User-Id", "usr-12345")
                        .header("X-Auth-Username", "john.doe")
                        .header("X-Auth-Roles", "USER")
                        .header("X-Auth-Email", "john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.userId").value("usr-12345"));
    }

    @Test
    @DisplayName("403 Forbidden: Normal user with ROLE_USER is denied access to admin endpoint (Author check)")
    void shouldDenyUserRoleFromAccessingAdminAudit() throws Exception {
        mockMvc.perform(get("/api/v1/resources/admin-audit")
                        .header("X-Auth-User-Id", "usr-12345")
                        .header("X-Auth-Username", "john.doe")
                        .header("X-Auth-Roles", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Authorization failed: User does not possess the required role/permission for this resource"));
    }

    @Test
    @DisplayName("200 OK: Admin user with ROLE_ADMIN is authorized for admin endpoint")
    void shouldAllowAdminRoleToAccessAdminAudit() throws Exception {
        mockMvc.perform(get("/api/v1/resources/admin-audit")
                        .header("X-Auth-User-Id", "adm-99999")
                        .header("X-Auth-Username", "admin.alice")
                        .header("X-Auth-Roles", "ADMIN,USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.adminUser").value("admin.alice"));
    }
}
