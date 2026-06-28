package com.tongue.server.trend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tongue/trends")
public class TrendController {

    private final TongueReportRepository reportRepository;
    private final TongueReportFeatureRepository featureRepository;
    private final ObjectMapper objectMapper;

    public TrendController(
            TongueReportRepository reportRepository,
            TongueReportFeatureRepository featureRepository,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.featureRepository = featureRepository;
        this.objectMapper = objectMapper;
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

    @GetMapping("/series")
    public ApiResponse<List<TrendSeriesPointResponse>> series(
            @RequestParam(value = "days", defaultValue = "90") int days
    ) {
        Long userId = AuthContext.requireUserId();
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<TongueReportEntity> reports = new ArrayList<TongueReportEntity>();
        for (TongueReportEntity report : reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            if (report.createdAt != null && !report.createdAt.isBefore(start)) {
                reports.add(report);
            }
        }
        Collections.reverse(reports);

        List<TrendSeriesPointResponse> result = new ArrayList<TrendSeriesPointResponse>();
        FeatureSnapshot previous = null;
        for (TongueReportEntity report : reports) {
            FeatureSnapshot current = snapshot(report);
            Set<String> timelineCodes = new LinkedHashSet<String>();
            timelineCodes.addAll(current.supportedCodes);
            timelineCodes.addAll(current.detectedCodes);
            if (previous != null) {
                timelineCodes.addAll(previous.detectedCodes);
            }

            for (String code : timelineCodes) {
                if (current.unsupportedCodes.contains(code)) {
                    result.add(seriesPoint(report, code, "UNSUPPORTED", null, "UNSUPPORTED", false));
                    continue;
                }
                boolean supported = current.supportedCodes.contains(code);
                boolean detected = current.detectedCodes.contains(code);
                boolean wasDetected = previous != null && previous.detectedCodes.contains(code);
                if (detected) {
                    String changeType = wasDetected ? "PERSISTENT" : "NEW";
                    if (wasDetected) {
                        Double oldConfidence = previous.confidenceByCode.get(code);
                        Double newConfidence = current.confidenceByCode.get(code);
                        if (oldConfidence != null && newConfidence != null && Math.abs(newConfidence - oldConfidence) >= 0.1) {
                            changeType = newConfidence > oldConfidence ? "CONFIDENCE_UP" : "CONFIDENCE_DOWN";
                        }
                    }
                    result.add(seriesPoint(report, code, "DETECTED", current.confidenceByCode.get(code), changeType, supported));
                } else if (wasDetected && supported) {
                    result.add(seriesPoint(report, code, "NOT_DETECTED", null, "DISAPPEARED", true));
                }
            }
            previous = current;
        }
        return ApiResponse.success(result);
    }

    private TrendSeriesPointResponse seriesPoint(
            TongueReportEntity report,
            String code,
            String status,
            Double confidence,
            String changeType,
            boolean supported
    ) {
        TrendSeriesPointResponse item = new TrendSeriesPointResponse();
        item.reportId = report.id;
        item.createdAt = report.createdAt;
        item.featureCode = code;
        item.status = status;
        item.confidence = confidence;
        item.changeType = changeType;
        item.supported = supported;
        return item;
    }

    private FeatureSnapshot snapshot(TongueReportEntity report) {
        FeatureSnapshot snapshot = new FeatureSnapshot();
        Map<String, Object> standard = parseJsonObject(report.standardFeaturesJson);
        addStrings(snapshot.detectedCodes, parseJson(report.detectedFeatureCodes));
        addStrings(snapshot.detectedCodes, standard.get("detected_feature_codes"));
        addStrings(snapshot.supportedCodes, standard.get("supported_feature_codes"));
        addStrings(snapshot.unsupportedCodes, standard.get("unsupported_feature_codes"));
        collectConfidence(standard, snapshot.confidenceByCode);
        for (TongueReportFeatureEntity feature : featureRepository.findByReportId(report.id)) {
            snapshot.detectedCodes.add(feature.featureCode);
            if (feature.confidence != null) {
                snapshot.confidenceByCode.put(feature.featureCode, feature.confidence);
            }
        }
        if (snapshot.supportedCodes.isEmpty()) {
            snapshot.supportedCodes.addAll(snapshot.detectedCodes);
        }
        return snapshot;
    }

    private Object parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        Object value = parseJson(json);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private void addStrings(Set<String> target, Object value) {
        if (!(value instanceof List)) {
            return;
        }
        for (Object item : (List<?>) value) {
            if (item != null && !String.valueOf(item).trim().isEmpty()) {
                target.add(String.valueOf(item));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectConfidence(Object value, Map<String, Double> result) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            String code = stringValue(map.get("code"));
            Double confidence = doubleValue(map.get("confidence"));
            if (code != null && confidence != null) {
                result.put(code, Math.max(0.0, Math.min(1.0, confidence)));
            }
            for (Object child : map.values()) {
                collectConfidence(child, result);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collectConfidence(child, result);
            }
        }
    }

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
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

    public static class TrendSeriesPointResponse {
        @JsonProperty("report_id")
        public Long reportId;
        @JsonProperty("created_at")
        public LocalDateTime createdAt;
        @JsonProperty("feature_code")
        public String featureCode;
        public String status;
        public Double confidence;
        @JsonProperty("change_type")
        public String changeType;
        public boolean supported;
    }

    private static class FeatureSnapshot {
        public Set<String> detectedCodes = new LinkedHashSet<String>();
        public Set<String> supportedCodes = new LinkedHashSet<String>();
        public Set<String> unsupportedCodes = new LinkedHashSet<String>();
        public Map<String, Double> confidenceByCode = new HashMap<String, Double>();
    }
}
