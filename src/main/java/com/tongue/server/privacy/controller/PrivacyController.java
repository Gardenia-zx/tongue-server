package com.tongue.server.privacy.controller;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.auth.entity.AppUserEntity;
import com.tongue.server.auth.repository.AppUserRepository;
import com.tongue.server.common.ApiResponse;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.storage.entity.FileObjectEntity;
import com.tongue.server.storage.repository.FileObjectRepository;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private final AppUserRepository appUserRepository;
    private final TongueReportRepository reportRepository;
    private final FileObjectRepository fileObjectRepository;

    public PrivacyController(
            AppUserRepository appUserRepository,
            TongueReportRepository reportRepository,
            FileObjectRepository fileObjectRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.reportRepository = reportRepository;
        this.fileObjectRepository = fileObjectRepository;
    }

    @PostMapping("/delete-reports")
    public ApiResponse<Object> deleteReports() {
        Long userId = AuthContext.requireUserId();
        for (TongueReportEntity report : reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            report.deletedAt = LocalDateTime.now();
            reportRepository.save(report);
        }
        for (FileObjectEntity file : fileObjectRepository.findByOwnerUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE")) {
            file.status = "DELETED";
            file.deletedAt = LocalDateTime.now();
            fileObjectRepository.save(file);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/delete-account")
    public ApiResponse<Object> deleteAccount() {
        Long userId = AuthContext.requireUserId();
        deleteReports();
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null));
        user.status = "DELETED";
        appUserRepository.save(user);
        return ApiResponse.success(null);
    }
}
