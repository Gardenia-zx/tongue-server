package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ReportListItemResponse {
    @JsonProperty("report_id")
    public Long reportId;
    public String status;
    @JsonProperty("feature_summary")
    public String featureSummary;
    @JsonProperty("analysis_quality_score")
    public Double analysisQualityScore;
    @JsonProperty("analysis_quality_level")
    public String analysisQualityLevel;
    @JsonProperty("quality_version")
    public String qualityVersion;
    @JsonProperty("analysis_quality_version")
    public String analysisQualityVersion;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
