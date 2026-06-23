package com.tongue.server.auth.service;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.auth.CurrentUser;
import com.tongue.server.auth.JwtClaims;
import com.tongue.server.auth.JwtTokenService;
import com.tongue.server.auth.dto.AuthLoginResponse;
import com.tongue.server.auth.dto.SmsSendResponse;
import com.tongue.server.auth.dto.UserMeResponse;
import com.tongue.server.auth.dto.UserProfileUpdateRequest;
import com.tongue.server.auth.entity.AppUserEntity;
import com.tongue.server.auth.entity.SmsCodeEntity;
import com.tongue.server.auth.entity.UserProfileEntity;
import com.tongue.server.auth.entity.UserTokenEntity;
import com.tongue.server.auth.repository.AppUserRepository;
import com.tongue.server.auth.repository.SmsCodeRepository;
import com.tongue.server.auth.repository.UserProfileRepository;
import com.tongue.server.auth.repository.UserTokenRepository;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class AuthService {

    private static final String LOGIN_SCENE = "LOGIN";
    private final AuthProperties authProperties;
    private final SmsCodeStore smsCodeStore;
    private final JwtTokenService jwtTokenService;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final SmsCodeRepository smsCodeRepository;
    private final UserTokenRepository userTokenRepository;

    public AuthService(
            AuthProperties authProperties,
            SmsCodeStore smsCodeStore,
            JwtTokenService jwtTokenService,
            AppUserRepository appUserRepository,
            UserProfileRepository userProfileRepository,
            SmsCodeRepository smsCodeRepository,
            UserTokenRepository userTokenRepository
    ) {
        this.authProperties = authProperties;
        this.smsCodeStore = smsCodeStore;
        this.jwtTokenService = jwtTokenService;
        this.appUserRepository = appUserRepository;
        this.userProfileRepository = userProfileRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.userTokenRepository = userTokenRepository;
    }

    @Transactional
    public SmsSendResponse sendSms(String phone) {
        validatePhone(phone);
        String code = authProperties.getDevSmsCode();
        smsCodeStore.put(phone, code, authProperties.getSmsTtlSeconds());

        SmsCodeEntity entity = new SmsCodeEntity();
        entity.phone = phone;
        entity.code = code;
        entity.scene = LOGIN_SCENE;
        entity.expiresAt = LocalDateTime.now().plusSeconds(authProperties.getSmsTtlSeconds());
        smsCodeRepository.save(entity);

        SmsSendResponse response = new SmsSendResponse();
        response.ttlSeconds = authProperties.getSmsTtlSeconds();
        response.devCode = authProperties.isExposeDevSmsCode() ? code : null;
        return response;
    }

    @Transactional
    public AuthLoginResponse login(String phone, String code) {
        validatePhone(phone);
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "验证码不能为空", null);
        }

        String cachedCode = smsCodeStore.get(phone);
        boolean matched = code.equals(cachedCode);
        Optional<SmsCodeEntity> latest = smsCodeRepository.findFirstByPhoneAndSceneOrderByCreatedAtDesc(
                phone,
                LOGIN_SCENE
        );
        if (!matched && latest.isPresent()) {
            SmsCodeEntity sms = latest.get();
            matched = code.equals(sms.code)
                    && sms.usedAt == null
                    && sms.expiresAt.isAfter(LocalDateTime.now());
        }
        if (!matched) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "验证码错误或已过期", null);
        }

        AppUserEntity user = appUserRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            user = new AppUserEntity();
            user.phone = phone;
            user.role = "USER";
            user.status = "ACTIVE";
            appUserRepository.save(user);
        }
        ensureProfile(user);
        if (latest.isPresent()) {
            latest.get().usedAt = LocalDateTime.now();
            smsCodeRepository.save(latest.get());
        }
        smsCodeStore.remove(phone);

        String token = jwtTokenService.createToken(user.id, user.phone, user.role);
        JwtClaims claims = jwtTokenService.parse(token);
        UserTokenEntity userToken = new UserTokenEntity();
        userToken.userId = user.id;
        userToken.tokenHash = jwtTokenService.tokenHash(token);
        userToken.expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(claims.expiresAt),
                ZoneId.systemDefault()
        );
        userTokenRepository.save(userToken);

        AuthLoginResponse response = new AuthLoginResponse();
        response.accessToken = token;
        response.expiresAt = claims.expiresAt;
        response.user = toUserMe(user);
        return response;
    }

    public CurrentUser authenticateToken(String token) {
        JwtClaims claims = jwtTokenService.parse(token);
        String tokenHash = jwtTokenService.tokenHash(token);
        UserTokenEntity tokenEntity = userTokenRepository
                .findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TOKEN_INVALID,
                        "登录状态无效或已过期",
                        null
                ));
        if (tokenEntity.expiresAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录状态无效或已过期", null);
        }
        return new CurrentUser(claims.userId, claims.phone, claims.role);
    }

    @Transactional
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String normalized = token.replace("Bearer ", "").trim();
        userTokenRepository.findByTokenHashAndRevokedAtIsNull(jwtTokenService.tokenHash(normalized))
                .ifPresent(entity -> {
                    entity.revokedAt = LocalDateTime.now();
                    userTokenRepository.save(entity);
                });
    }

    @Transactional(readOnly = true)
    public UserMeResponse currentUser() {
        Long userId = AuthContext.requireUserId();
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null));
        return toUserMe(user);
    }

    @Transactional
    public UserMeResponse updateProfile(UserProfileUpdateRequest request) {
        Long userId = AuthContext.requireUserId();
        UserProfileEntity profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfileEntity created = new UserProfileEntity();
                    created.userId = userId;
                    return created;
                });
        profile.nickname = request.nickname;
        profile.gender = request.gender;
        profile.age = request.age;
        profile.avatarFileId = request.avatarFileId;
        profile.healthFocus = request.healthFocus;
        userProfileRepository.save(profile);
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null));
        return toUserMe(user);
    }

    private void ensureProfile(AppUserEntity user) {
        if (!userProfileRepository.findByUserId(user.id).isPresent()) {
            UserProfileEntity profile = new UserProfileEntity();
            profile.userId = user.id;
            profile.nickname = "用户" + user.id;
            userProfileRepository.save(profile);
        }
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

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.trim().length() < 6) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "手机号格式不正确", null);
        }
    }
}
