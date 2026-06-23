package com.tongue.server.admin.service;

import com.tongue.server.admin.entity.AdminUserEntity;
import com.tongue.server.admin.repository.AdminUserRepository;
import com.tongue.server.auth.JwtClaims;
import com.tongue.server.auth.JwtTokenService;
import com.tongue.server.auth.dto.AuthLoginResponse;
import com.tongue.server.auth.dto.UserMeResponse;
import com.tongue.server.auth.entity.AppUserEntity;
import com.tongue.server.auth.entity.UserProfileEntity;
import com.tongue.server.auth.entity.UserTokenEntity;
import com.tongue.server.auth.repository.AppUserRepository;
import com.tongue.server.auth.repository.UserProfileRepository;
import com.tongue.server.auth.repository.UserTokenRepository;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTokenRepository userTokenRepository;
    private final JwtTokenService jwtTokenService;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            AppUserRepository appUserRepository,
            UserProfileRepository userProfileRepository,
            UserTokenRepository userTokenRepository,
            JwtTokenService jwtTokenService
    ) {
        this.adminUserRepository = adminUserRepository;
        this.appUserRepository = appUserRepository;
        this.userProfileRepository = userProfileRepository;
        this.userTokenRepository = userTokenRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthLoginResponse login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "用户名或密码错误", null);
        }

        AdminUserEntity admin = adminUserRepository.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "用户名或密码错误", null));
        if (!"ACTIVE".equals(admin.status) || !matchesPassword(password, admin.passwordHash)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "用户名或密码错误", null);
        }

        AppUserEntity identity = ensureAdminIdentity(admin);
        String token = jwtTokenService.createToken(identity.id, admin.username, "ADMIN");
        JwtClaims claims = jwtTokenService.parse(token);

        UserTokenEntity userToken = new UserTokenEntity();
        userToken.userId = identity.id;
        userToken.tokenHash = jwtTokenService.tokenHash(token);
        userToken.expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(claims.expiresAt),
                ZoneId.systemDefault()
        );
        userTokenRepository.save(userToken);

        AuthLoginResponse response = new AuthLoginResponse();
        response.accessToken = token;
        response.expiresAt = claims.expiresAt;
        response.user = toUserMe(identity);
        return response;
    }

    private AppUserEntity ensureAdminIdentity(AdminUserEntity admin) {
        String phone = "admin:" + admin.id;
        AppUserEntity identity = appUserRepository.findByPhone(phone).orElse(null);
        if (identity == null) {
            identity = new AppUserEntity();
            identity.phone = phone;
        }
        identity.role = "ADMIN";
        identity.status = "ACTIVE";
        appUserRepository.save(identity);

        UserProfileEntity profile = userProfileRepository.findByUserId(identity.id).orElse(null);
        if (profile == null) {
            profile = new UserProfileEntity();
            profile.userId = identity.id;
        }
        profile.nickname = admin.username;
        userProfileRepository.save(profile);

        return identity;
    }

    private UserMeResponse toUserMe(AppUserEntity user) {
        UserProfileEntity profile = userProfileRepository.findByUserId(user.id).orElse(null);
        UserMeResponse response = new UserMeResponse();
        response.userId = user.id;
        response.phone = user.phone;
        response.role = user.role;
        if (profile != null) {
            response.nickname = profile.nickname;
            response.gender = profile.gender;
            response.age = profile.age;
            response.avatarFileId = profile.avatarFileId;
            response.healthFocus = profile.healthFocus;
        }
        return response;
    }

    private boolean matchesPassword(String rawPassword, String storedHash) {
        if (!StringUtils.hasText(storedHash)) {
            return false;
        }
        String normalized = storedHash.trim();
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        } else if (normalized.startsWith("{SHA256}")) {
            normalized = normalized.substring("{SHA256}".length());
        } else if (normalized.startsWith("{noop}")) {
            return constantTimeEquals(rawPassword, normalized.substring("{noop}".length()));
        }
        return constantTimeEquals(sha256Hex(rawPassword), normalized.toLowerCase());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
