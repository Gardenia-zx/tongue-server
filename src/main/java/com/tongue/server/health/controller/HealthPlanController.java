package com.tongue.server.health.controller;

import com.tongue.server.common.ApiResponse;
import com.tongue.server.health.dto.CheckinSummaryResponse;
import com.tongue.server.health.dto.DailyCheckinRequest;
import com.tongue.server.health.dto.DailyCheckinResponse;
import com.tongue.server.health.dto.HealthPlanDraftUpdateRequest;
import com.tongue.server.health.dto.HealthPlanExecutionSummaryResponse;
import com.tongue.server.health.dto.HealthPlanResponse;
import com.tongue.server.health.dto.HealthPlanReviewResponse;
import com.tongue.server.health.service.HealthPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HealthPlanController {

    private final HealthPlanService healthPlanService;

    public HealthPlanController(HealthPlanService healthPlanService) {
        this.healthPlanService = healthPlanService;
    }

    @GetMapping("/api/health-plans/current")
    public ApiResponse<HealthPlanResponse> current() {
        return ApiResponse.success(healthPlanService.current());
    }

    @GetMapping("/api/health-plans/{planId}")
    public ApiResponse<HealthPlanResponse> detail(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.detail(planId));
    }

    @GetMapping("/api/health-plans/{planId}/execution-summary")
    public ApiResponse<HealthPlanExecutionSummaryResponse> executionSummary(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.executionSummary(planId));
    }

    @PostMapping("/api/health-plans/from-report/{reportId}")
    public ApiResponse<HealthPlanResponse> fromReport(@PathVariable Long reportId) {
        return ApiResponse.success(healthPlanService.createDraftFromReport(reportId));
    }

    @PostMapping("/api/health-plans/from-report/{reportId}/draft")
    public ApiResponse<HealthPlanResponse> draftFromReport(@PathVariable Long reportId) {
        return ApiResponse.success(healthPlanService.createDraftFromReport(reportId));
    }

    @PutMapping("/api/health-plans/{planId}/draft")
    public ApiResponse<HealthPlanResponse> updateDraft(
            @PathVariable Long planId,
            @RequestBody HealthPlanDraftUpdateRequest request
    ) {
        return ApiResponse.success(healthPlanService.updateDraft(planId, request));
    }

    @PostMapping("/api/health-plans/{planId}/review")
    public ApiResponse<HealthPlanReviewResponse> review(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.reviewDraft(planId));
    }

    @PostMapping("/api/health-plans/{planId}/generate-detailed")
    public ApiResponse<HealthPlanResponse> generateDetailed(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.generateDetailed(planId));
    }

    @PostMapping("/api/health-plans/{planId}/activate")
    public ApiResponse<HealthPlanResponse> activate(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.activate(planId));
    }

    @PostMapping("/api/health-plans/{planId}/close")
    public ApiResponse<HealthPlanResponse> close(@PathVariable Long planId) {
        return ApiResponse.success(healthPlanService.close(planId));
    }

    @GetMapping("/api/checkins")
    public ApiResponse<List<DailyCheckinResponse>> checkins(
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        return ApiResponse.success(healthPlanService.checkins(days));
    }

    @PostMapping("/api/checkins/today")
    public ApiResponse<DailyCheckinResponse> checkinToday(@RequestBody DailyCheckinRequest request) {
        return ApiResponse.success(healthPlanService.checkinToday(request));
    }

    @GetMapping("/api/checkins/summary")
    public ApiResponse<CheckinSummaryResponse> summary(
            @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        return ApiResponse.success(healthPlanService.summary(days));
    }
}
