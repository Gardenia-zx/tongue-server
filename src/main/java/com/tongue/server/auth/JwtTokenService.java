package com.tongue.server.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AuthProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public JwtTokenService(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    public String createToken(Long userId, String phone, String role) {
        try {
            Map<String, Object> header = new LinkedHashMap<String, Object>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            long expiresAt = Instant.now().getEpochSecond() + authProperties.getTokenTtlSeconds();
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("sub", String.valueOf(userId));
            payload.put("phone", phone);
            payload.put("role", role);
            payload.put("exp", expiresAt);

            String unsigned = base64Url(objectMapper.writeValueAsBytes(header))
                    + "."
                    + base64Url(objectMapper.writeValueAsBytes(payload));
            return unsigned + "." + sign(unsigned);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 生成失败", null, ex);
        }
    }

    public JwtClaims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid jwt format");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new IllegalArgumentException("invalid jwt signature");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            long expiresAt = Long.parseLong(String.valueOf(payload.get("exp")));
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("jwt expired");
            }

            JwtClaims claims = new JwtClaims();
            claims.userId = Long.parseLong(String.valueOf(payload.get("sub")));
            claims.phone = payload.get("phone") == null ? null : String.valueOf(payload.get("phone"));
            claims.role = payload.get("role") == null ? "USER" : String.valueOf(payload.get("role"));
            claims.expiresAt = expiresAt;
            return claims;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录状态无效或已过期", null, ex);
        }
    }

    public String tokenHash(String token) {
        return sign(token);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            ));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
