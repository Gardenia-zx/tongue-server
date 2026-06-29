package com.tongue.server.health.controller;

import com.tongue.server.common.ApiResponse;
import com.tongue.server.health.dto.CheckinSummaryResponse;
import com.tongue.server.health.dto.DailyCheckinRequest;
import com.tongue.server.health.dto.DailyCheckinResponse;
import com.tongue.server.health.dto.HealthPlanResponse;
import com.tongue.server.health.service.HealthPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/api/health-plans/from-report/{reportId}")
    public ApiResponse<HealthPlanResponse> fromReport(@PathVariable Long reportId) {
        return ApiResponse.success(healthPlanService.createFromReport(reportId));
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
