package com.tongue.server.auth.controller;

import com.tongue.server.auth.dto.AuthLoginResponse;
import com.tongue.server.auth.dto.SmsLoginRequest;
import com.tongue.server.auth.dto.SmsSendRequest;
import com.tongue.server.auth.dto.SmsSendResponse;
import com.tongue.server.auth.dto.UserMeResponse;
import com.tongue.server.auth.dto.UserProfileUpdateRequest;
import com.tongue.server.auth.service.AuthService;
import com.tongue.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
