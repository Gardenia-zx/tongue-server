package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckinSummaryResponse {
    public int days;
    @JsonProperty("checkin_count")
    public int checkinCount;
    @JsonProperty("checkin_rate")
    public double checkinRate;
    @JsonProperty("diet_rate")
    public double dietRate;
    @JsonProperty("sleep_rate")
    public double sleepRate;
    @JsonProperty("exercise_rate")
    public double exerciseRate;
    @JsonProperty("retake_completed")
    public boolean retakeCompleted;
}
