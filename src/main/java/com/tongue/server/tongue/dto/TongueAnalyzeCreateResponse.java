package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TongueAnalyzeCreateResponse {
    @JsonProperty("report_id")
    public Long reportId;
    @JsonProperty("task_id")
    public Long taskId;
    public String status;
}
