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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
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
    private final ProfileAvatarStorageService avatarStorageService;

    public AuthService(
            AuthProperties authProperties,
            SmsCodeStore smsCodeStore,
            JwtTokenService jwtTokenService,
            AppUserRepository appUserRepository,
            UserProfileRepository userProfileRepository,
            SmsCodeRepository smsCodeRepository,
            UserTokenRepository userTokenRepository,
            ProfileAvatarStorageService avatarStorageService
    ) {
        this.authProperties = authProperties;
        this.smsCodeStore = smsCodeStore;
        this.jwtTokenService = jwtTokenService;
        this.appUserRepository = appUserRepository;
        this.userProfileRepository = userProfileRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.userTokenRepository = userTokenRepository;
        this.avatarStorageService = avatarStorageService;
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
        AppUserEntity user = requireUser(userId);
        return toUserMe(user);
    }

    @Transactional
    public UserMeResponse updateProfile(UserProfileUpdateRequest request) {
        Long userId = AuthContext.requireUserId();
        UserProfileEntity profile = profileForUpdate(userId);

        if (request.nickname != null) profile.nickname = limitedText(request.nickname, 64, "昵称");
        if (request.gender != null) profile.gender = normalizeGender(request.gender);
        if (request.birthDate != null) {
            validateBirthDate(request.birthDate);
            profile.birthDate = request.birthDate;
            profile.age = Period.between(request.birthDate, LocalDate.now()).getYears();
        } else if (request.age != null) {
            validateRange(request.age.doubleValue(), 0, 120, "年龄");
            profile.age = request.age;
        }
        if (request.email != null) profile.email = normalizeEmail(request.email);
        if (request.avatarFileId != null) profile.avatarFileId = request.avatarFileId;
        if (request.healthFocus != null) profile.healthFocus = limitedText(request.healthFocus, 500, "健康关注方向");
        if (request.profileNote != null) profile.profileNote = limitedText(request.profileNote, 500, "补充说明");

        if (request.heightCm != null) {
            validateRange(request.heightCm, 50, 250, "身高");
            profile.heightCm = request.heightCm;
        }
        if (request.weightKg != null) {
            validateRange(request.weightKg, 10, 350, "体重");
            profile.weightKg = request.weightKg;
        }
        if (request.sleepHours != null) {
            validateRange(request.sleepHours, 0, 24, "睡眠时长");
            profile.sleepHours = request.sleepHours;
        }
        if (request.exerciseFrequency != null) {
            profile.exerciseFrequency = limitedText(request.exerciseFrequency, 32, "运动频率");
        }
        if (request.dietaryPreference != null) {
            profile.dietaryPreference = limitedText(request.dietaryPreference, 500, "饮食偏好");
        }

        if (request.answerDetailLevel != null) {
            profile.answerDetailLevel = normalizeAnswerDetail(request.answerDetailLevel);
        }
        if (request.useHealthProfile != null) profile.useHealthProfile = request.useHealthProfile;
        if (request.useHistoryReports != null) profile.useHistoryReports = request.useHistoryReports;
        if (request.useLongTermMemory != null) profile.useLongTermMemory = request.useLongTermMemory;

        if (request.tongueReminderEnabled != null) profile.tongueReminderEnabled = request.tongueReminderEnabled;
        if (request.tongueReminderTime != null) profile.tongueReminderTime = normalizeTime(request.tongueReminderTime, "舌象复拍提醒");
        if (request.sleepReminderEnabled != null) profile.sleepReminderEnabled = request.sleepReminderEnabled;
        if (request.sleepReminderTime != null) profile.sleepReminderTime = normalizeTime(request.sleepReminderTime, "睡眠记录提醒");

        userProfileRepository.save(profile);
        return toUserMe(requireUser(userId));
    }

    @Transactional
    public UserMeResponse uploadAvatar(MultipartFile avatar) {
        Long userId = AuthContext.requireUserId();
        UserProfileEntity profile = profileForUpdate(userId);
        profile.avatarFileName = avatarStorageService.save(userId, avatar, profile.avatarFileName);
        profile.avatarFileId = null;
        userProfileRepository.save(profile);
        return toUserMe(requireUser(userId));
    }

    @Transactional
    public UserMeResponse removeAvatar() {
        Long userId = AuthContext.requireUserId();
        UserProfileEntity profile = profileForUpdate(userId);
        String previous = profile.avatarFileName;
        profile.avatarFileName = null;
        profile.avatarFileId = null;
        userProfileRepository.save(profile);
        avatarStorageService.deleteQuietly(previous);
        return toUserMe(requireUser(userId));
    }

    private UserProfileEntity profileForUpdate(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfileEntity created = new UserProfileEntity();
                    created.userId = userId;
                    applyDefaultPreferences(created);
                    return created;
                });
    }

    private AppUserEntity requireUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null));
    }

    private void ensureProfile(AppUserEntity user) {
        if (!userProfileRepository.findByUserId(user.id).isPresent()) {
            UserProfileEntity profile = new UserProfileEntity();
            profile.userId = user.id;
            profile.nickname = "用户" + user.id;
            applyDefaultPreferences(profile);
            userProfileRepository.save(profile);
        }
    }

    private void applyDefaultPreferences(UserProfileEntity profile) {
        if (!StringUtils.hasText(profile.answerDetailLevel)) profile.answerDetailLevel = "STANDARD";
        if (profile.useHealthProfile == null) profile.useHealthProfile = Boolean.TRUE;
        if (profile.useHistoryReports == null) profile.useHistoryReports = Boolean.TRUE;
        if (profile.useLongTermMemory == null) profile.useLongTermMemory = Boolean.FALSE;
        if (profile.tongueReminderEnabled == null) profile.tongueReminderEnabled = Boolean.FALSE;
        if (profile.sleepReminderEnabled == null) profile.sleepReminderEnabled = Boolean.FALSE;
        if (!StringUtils.hasText(profile.tongueReminderTime)) profile.tongueReminderTime = "09:00";
        if (!StringUtils.hasText(profile.sleepReminderTime)) profile.sleepReminderTime = "22:00";
    }

    private UserMeResponse toUserMe(AppUserEntity user) {
        UserProfileEntity profile = userProfileRepository.findByUserId(user.id).orElse(null);
        UserMeResponse response = new UserMeResponse();
        response.userId = user.id;
        response.phone = user.phone;
        response.role = user.role;
        if (profile != null) {
            applyDefaultPreferences(profile);
            response.nickname = profile.nickname;
            response.gender = profile.gender;
            response.age = profile.age;
            response.birthDate = profile.birthDate;
            response.email = profile.email;
            response.avatarFileId = profile.avatarFileId;
            response.avatarUrl = avatarStorageService.publicUrl(profile.avatarFileName);
            response.healthFocus = profile.healthFocus;
            response.profileNote = profile.profileNote;
            response.heightCm = profile.heightCm;
            response.weightKg = profile.weightKg;
            response.sleepHours = profile.sleepHours;
            response.exerciseFrequency = profile.exerciseFrequency;
            response.dietaryPreference = profile.dietaryPreference;
            response.answerDetailLevel = profile.answerDetailLevel;
            response.useHealthProfile = profile.useHealthProfile;
            response.useHistoryReports = profile.useHistoryReports;
            response.useLongTermMemory = profile.useLongTermMemory;
            response.tongueReminderEnabled = profile.tongueReminderEnabled;
            response.tongueReminderTime = profile.tongueReminderTime;
            response.sleepReminderEnabled = profile.sleepReminderEnabled;
            response.sleepReminderTime = profile.sleepReminderTime;
        }
        return response;
    }

    private String normalizeGender(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (!"male".equals(normalized) && !"female".equals(normalized)
                && !"other".equals(normalized) && !"unknown".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "性别选项无效", null);
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 128 || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确", null);
        }
        return normalized;
    }

    private String normalizeAnswerDetail(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"CONCISE".equals(normalized) && !"STANDARD".equals(normalized) && !"DETAILED".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回答详细程度选项无效", null);
        }
        return normalized;
    }

    private String normalizeTime(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return null;
        if (!normalized.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "时间格式不正确", null);
        }
        return normalized;
    }

    private String limitedText(String value, int maxLength, String label) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + maxLength + " 个字符", null);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now();
        if (birthDate.isAfter(today) || birthDate.isBefore(today.minusYears(120))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出生日期不正确", null);
        }
    }

    private void validateRange(double value, double min, double max, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "超出有效范围", null);
        }
    }

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.trim().length() < 6) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID, "手机号格式不正确", null);
        }
    }
}
