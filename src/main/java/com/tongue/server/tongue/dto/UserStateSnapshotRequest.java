package com.tongue.server.tongue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UserStateSnapshotRequest {
    @JsonProperty("observation_window")
    public String observationWindow = "LAST_3_DAYS";
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
}
