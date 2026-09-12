package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Keycloak JWKS URI để tải Public Keys kiểm tra chữ ký số RSA/EC của Keycloak
     * Ví dụ: http://localhost:8080/realms/myrealm/protocol/openid-connect/certs
     */
    private String jwkSetUri = "";

    /**
     * Keycloak Issuer URI mong đợi (claim 'iss')
     * Ví dụ: http://localhost:8080/realms/myrealm
     */
    private String issuerUri = "";

    /**
     * Khóa bí mật dùng cho HS256 (fallback nếu cấu hình HMAC secret thay vì JWKS)
     */
    private String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /**
     * Tiền tố key lưu trữ trong Redis (VD: "jwt:blacklist:")
     */
    private String blacklistPrefix = "jwt:blacklist:";

    /**
     * Danh sách đường dẫn public không cần xác thực token
     */
    private List<String> excludedPaths = new ArrayList<>();

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

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
}

