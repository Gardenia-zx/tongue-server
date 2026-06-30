package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HealthPlanExecutionSummaryResponse {
    @JsonProperty("plan_id")
    public Long planId;
    @JsonProperty("source_report_id")
    public Long sourceReportId;
    @JsonProperty("retake_report_id")
    public Long retakeReportId;
    public String status;
    @JsonProperty("start_date")
    public LocalDate startDate;
    @JsonProperty("end_date")
    public LocalDate endDate;
    @JsonProperty("elapsed_days")
    public int elapsedDays;
    @JsonProperty("total_days")
    public int totalDays;
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
    @JsonProperty("missed_dates")
    public List<LocalDate> missedDates = new ArrayList<LocalDate>();
    public String recommendation;
}
