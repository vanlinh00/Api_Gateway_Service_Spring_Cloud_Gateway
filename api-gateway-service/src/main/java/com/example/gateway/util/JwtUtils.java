package com.example.gateway.util;

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
            // Fallback nếu chuỗi secret là raw string chưa mã hoá Base64
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
        // Fallback: SHA-256 Hash của token để tránh lưu chuỗi JWT quá dài trong Redis
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
            return input; // Fallback
        }
    }
}
