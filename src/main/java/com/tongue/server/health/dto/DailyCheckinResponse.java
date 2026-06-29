package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyCheckinResponse {
    @JsonProperty("checkin_id")
    public Long checkinId;
    @JsonProperty("plan_id")
    public Long planId;
    @JsonProperty("checkin_date")
    public LocalDate checkinDate;
    @JsonProperty("diet_done")
    public Boolean dietDone;
    @JsonProperty("sleep_done")
    public Boolean sleepDone;
    @JsonProperty("exercise_done")
    public Boolean exerciseDone;
    public Object observation;
    public String note;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
