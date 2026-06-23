package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskStatusResponse {
    @JsonProperty("task_id")
    public Long taskId;
    @JsonProperty("report_id")
    public Long reportId;
    public String status;
    public Double progress;
    @JsonProperty("current_stage")
    public String currentStage;
    @JsonProperty("error_code")
    public String errorCode;
    @JsonProperty("error_message")
    public String errorMessage;
}
