package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ReportVersionResponse {
    @JsonProperty("version_id")
    public Long versionId;
    @JsonProperty("version_no")
    public Integer versionNo;
    @JsonProperty("source_type")
    public String sourceType;
    public String summary;
    @JsonProperty("report_json")
    public Object reportJson;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
}
