package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ReportListItemResponse {
    @JsonProperty("report_id")
    public Long reportId;
    public String status;
    @JsonProperty("feature_summary")
    public String featureSummary;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
