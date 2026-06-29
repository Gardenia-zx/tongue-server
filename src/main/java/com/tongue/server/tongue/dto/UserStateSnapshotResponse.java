package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class UserStateSnapshotResponse {
    @JsonProperty("snapshot_id")
    public Long snapshotId;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("report_id")
    public Long reportId;
    @JsonProperty("task_id")
    public Long taskId;
    @JsonProperty("observation_window")
    public String observationWindow;
    @JsonProperty("sleep_status")
    public String sleepStatus;
    @JsonProperty("digestion_status")
    public String digestionStatus;
    @JsonProperty("bowel_status")
    public String bowelStatus;
    @JsonProperty("current_states")
    public List<String> currentStates;
    @JsonProperty("health_goals")
    public List<String> healthGoals;
    @JsonProperty("free_description")
    public String freeDescription;
    public Boolean skipped;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
