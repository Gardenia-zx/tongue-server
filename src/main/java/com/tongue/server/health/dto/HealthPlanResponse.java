package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class HealthPlanResponse {
    @JsonProperty("plan_id")
    public Long planId;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("source_report_id")
    public Long sourceReportId;
    public String status;
    @JsonProperty("start_date")
    public LocalDate startDate;
    @JsonProperty("end_date")
    public LocalDate endDate;
    @JsonProperty("diet_goal")
    public Map<String, Object> dietGoal;
    @JsonProperty("sleep_goal")
    public Map<String, Object> sleepGoal;
    @JsonProperty("exercise_goal")
    public Map<String, Object> exerciseGoal;
    @JsonProperty("observation_items")
    public List<String> observationItems;
    public List<HealthPlanDayContent> days;
    @JsonProperty("schema_version")
    public String schemaVersion;
    @JsonProperty("generation_mode")
    public String generationMode;
    @JsonProperty("activated_at")
    public LocalDateTime activatedAt;
    @JsonProperty("today_checkin")
    public DailyCheckinResponse todayCheckin;
    @JsonProperty("next_retake_date")
    public LocalDate nextRetakeDate;
    @JsonProperty("personalization_signals")
    public List<String> personalizationSignals;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
