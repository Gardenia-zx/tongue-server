package com.tongue.server.trend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.ApiResponse;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.TongueReportFeatureEntity;
import com.tongue.server.tongue.repository.TongueReportFeatureRepository;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tongue/trends")
public class TrendController {

    private final TongueReportRepository reportRepository;
    private final TongueReportFeatureRepository featureRepository;

    public TrendController(
            TongueReportRepository reportRepository,
            TongueReportFeatureRepository featureRepository
    ) {
        this.reportRepository = reportRepository;
        this.featureRepository = featureRepository;
    }

    @GetMapping("/overview")
    public ApiResponse<TrendOverviewResponse> overview(
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        Long userId = AuthContext.requireUserId();
        List<TongueReportEntity> reports = reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        List<TongueReportFeatureEntity> features = featureRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId,
                        LocalDateTime.now().minusDays(days)
                );
        TrendOverviewResponse response = new TrendOverviewResponse();
        response.reportCount = reports.size();
        response.days = days;
        response.featureCount = features.size();
        response.latestReportId = reports.isEmpty() ? null : reports.get(0).id;
        return ApiResponse.success(response);
    }

    @GetMapping("/features")
    public ApiResponse<List<FeatureTrendResponse>> features(
            @RequestParam(value = "days", defaultValue = "90") int days
    ) {
        Long userId = AuthContext.requireUserId();
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (TongueReportFeatureEntity feature : featureRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId,
                        LocalDateTime.now().minusDays(days)
                )) {
            Integer count = counts.get(feature.featureCode);
            counts.put(feature.featureCode, count == null ? 1 : count + 1);
        }

        List<FeatureTrendResponse> result = new ArrayList<FeatureTrendResponse>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            FeatureTrendResponse item = new FeatureTrendResponse();
            item.featureCode = entry.getKey();
            item.count = entry.getValue();
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/timeline")
    public ApiResponse<List<TimelineItemResponse>> timeline() {
        Long userId = AuthContext.requireUserId();
        List<TimelineItemResponse> result = new ArrayList<TimelineItemResponse>();
        for (TongueReportEntity report : reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            TimelineItemResponse item = new TimelineItemResponse();
            item.reportId = report.id;
            item.featureSummary = report.featureSummary;
            item.createdAt = report.createdAt;
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    public static class TrendOverviewResponse {
        @JsonProperty("report_count")
        public int reportCount;
        @JsonProperty("feature_count")
        public int featureCount;
        public int days;
        @JsonProperty("latest_report_id")
        public Long latestReportId;
    }

    public static class FeatureTrendResponse {
        @JsonProperty("feature_code")
        public String featureCode;
        public int count;
    }

    public static class TimelineItemResponse {
        @JsonProperty("report_id")
        public Long reportId;
        @JsonProperty("feature_summary")
        public String featureSummary;
        @JsonProperty("created_at")
        public LocalDateTime createdAt;
    }
}
