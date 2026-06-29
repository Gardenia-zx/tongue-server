package com.tongue.server.auth.controller;

import com.tongue.server.auth.dto.AuthLoginResponse;
import com.tongue.server.auth.dto.SmsLoginRequest;
import com.tongue.server.auth.dto.SmsSendRequest;
import com.tongue.server.auth.dto.SmsSendResponse;
import com.tongue.server.auth.dto.UserMeResponse;
import com.tongue.server.auth.dto.UserProfileUpdateRequest;
import com.tongue.server.auth.service.AuthService;
import com.tongue.server.auth.service.ProfileAvatarStorageService;
import com.tongue.server.common.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final ProfileAvatarStorageService avatarStorageService;

    public AuthController(AuthService authService, ProfileAvatarStorageService avatarStorageService) {
        this.authService = authService;
        this.avatarStorageService = avatarStorageService;
    }

    @PostMapping("/auth/sms/send")
    public ApiResponse<SmsSendResponse> sendSms(@Valid @RequestBody SmsSendRequest request) {
        return ApiResponse.success(authService.sendSms(request.phone));
    }

    @PostMapping("/auth/sms/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.success(authService.login(request.phone, request.code));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.logout(authorization);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserMeResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @PutMapping("/users/me/profile")
    public ApiResponse<UserMeResponse> updateProfile(
            @RequestBody UserProfileUpdateRequest request
    ) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PostMapping(value = "/users/me/avatar", consumes = "multipart/form-data")
    public ApiResponse<UserMeResponse> uploadAvatar(@RequestPart("avatar") MultipartFile avatar) {
        return ApiResponse.success(authService.uploadAvatar(avatar));
    }

    @DeleteMapping("/users/me/avatar")
    public ApiResponse<UserMeResponse> removeAvatar() {
        return ApiResponse.success(authService.removeAvatar());
    }

    @GetMapping("/public/profile-avatars/{fileName:.+}")
    public ResponseEntity<Resource> profileAvatar(@PathVariable String fileName) throws Exception {
        ProfileAvatarStorageService.AvatarResource avatar = avatarStorageService.load(fileName);
        return ResponseEntity.ok()
                .contentType(avatar.getMediaType())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(avatar.getResource());
    }
}
