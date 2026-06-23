package com.tongue.server.admin.controller;

import com.tongue.server.admin.entity.AdminAuditLogEntity;
import com.tongue.server.admin.entity.SystemConfigEntity;
import com.tongue.server.admin.repository.AdminAuditLogRepository;
import com.tongue.server.admin.repository.SystemConfigRepository;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.auth.dto.DoctorProfileResponse;
import com.tongue.server.auth.entity.AppUserEntity;
import com.tongue.server.auth.entity.DoctorProfileEntity;
import com.tongue.server.auth.repository.AppUserRepository;
import com.tongue.server.auth.repository.DoctorProfileRepository;
import com.tongue.server.auth.service.DoctorService;
import com.tongue.server.common.ApiResponse;
import com.tongue.server.review.entity.DoctorReviewOrderEntity;
import com.tongue.server.review.repository.DoctorReviewOrderRepository;
import com.tongue.server.tongue.entity.TongueAnalysisTaskEntity;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.repository.TongueAnalysisTaskRepository;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppUserRepository appUserRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorService doctorService;
    private final TongueReportRepository reportRepository;
    private final TongueAnalysisTaskRepository taskRepository;
    private final DoctorReviewOrderRepository reviewOrderRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminController(
            AppUserRepository appUserRepository,
            DoctorProfileRepository doctorProfileRepository,
            DoctorService doctorService,
            TongueReportRepository reportRepository,
            TongueAnalysisTaskRepository taskRepository,
            DoctorReviewOrderRepository reviewOrderRepository,
            SystemConfigRepository systemConfigRepository,
            AdminAuditLogRepository auditLogRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.doctorService = doctorService;
        this.reportRepository = reportRepository;
        this.taskRepository = taskRepository;
        this.reviewOrderRepository = reviewOrderRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/users")
    public ApiResponse<List<AppUserEntity>> users() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(appUserRepository.findAll());
    }

    @GetMapping("/doctors")
    public ApiResponse<List<DoctorProfileEntity>> doctors() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(doctorProfileRepository.findAll());
    }

    @PostMapping("/doctors/{doctorId}/approve")
    public ApiResponse<DoctorProfileResponse> approveDoctor(@PathVariable Long doctorId) {
        AuthContext.requireRole("ADMIN");
        DoctorProfileResponse response = doctorService.approve(doctorId, true);
        audit("APPROVE_DOCTOR", "doctor_profile", doctorId, "{}");
        return ApiResponse.success(response);
    }

    @PostMapping("/doctors/{doctorId}/reject")
    public ApiResponse<DoctorProfileResponse> rejectDoctor(@PathVariable Long doctorId) {
        AuthContext.requireRole("ADMIN");
        DoctorProfileResponse response = doctorService.approve(doctorId, false);
        audit("REJECT_DOCTOR", "doctor_profile", doctorId, "{}");
        return ApiResponse.success(response);
    }

    @GetMapping("/reports")
    public ApiResponse<List<TongueReportEntity>> reports() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(reportRepository.findAll());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TongueAnalysisTaskEntity>> tasks() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(taskRepository.findAll());
    }

    @GetMapping("/reviews")
    public ApiResponse<List<DoctorReviewOrderEntity>> reviews() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(reviewOrderRepository.findAll());
    }

    @GetMapping("/system/config")
    public ApiResponse<List<SystemConfigEntity>> configs() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(systemConfigRepository.findAll());
    }

    @PutMapping("/system/config")
    public ApiResponse<SystemConfigEntity> upsertConfig(@RequestBody Map<String, String> body) {
        AuthContext.requireRole("ADMIN");
        String key = body.get("key");
        SystemConfigEntity config = systemConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            config = new SystemConfigEntity();
            config.configKey = key;
        }
        config.configValue = body.get("value");
        config.configGroup = body.get("group");
        systemConfigRepository.save(config);
        audit("UPSERT_CONFIG", "system_config", config.id, "{\"key\":\"" + key + "\"}");
        return ApiResponse.success(config);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogEntity>> auditLogs() {
        AuthContext.requireRole("ADMIN");
        return ApiResponse.success(auditLogRepository.findAll());
    }

    @GetMapping("/metrics/tasks")
    public ApiResponse<Map<String, Object>> taskMetrics() {
        AuthContext.requireRole("ADMIN");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("task_count", taskRepository.count());
        data.put("report_count", reportRepository.count());
        data.put("review_count", reviewOrderRepository.count());
        return ApiResponse.success(data);
    }

    @GetMapping("/metrics/errors")
    public ApiResponse<Map<String, Object>> errorMetrics() {
        AuthContext.requireRole("ADMIN");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        long failed = 0L;
        for (TongueAnalysisTaskEntity task : taskRepository.findAll()) {
            if ("FAILED".equals(task.status)) {
                failed++;
            }
        }
        data.put("failed_task_count", failed);
        return ApiResponse.success(data);
    }

    private void audit(String action, String targetType, Long targetId, String detailJson) {
        AdminAuditLogEntity log = new AdminAuditLogEntity();
        log.adminUserId = AuthContext.requireUserId();
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.detailJson = detailJson;
        auditLogRepository.save(log);
    }
}
