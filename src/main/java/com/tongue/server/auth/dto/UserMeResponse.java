package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class UserMeResponse {
    @JsonProperty("user_id")
    public Long userId;
    public String phone;
    public String role;
    public String nickname;
    public String gender;
    public Integer age;

    @JsonProperty("birth_date")
    public LocalDate birthDate;

    public String email;

    @JsonProperty("avatar_file_id")
    public Long avatarFileId;

    @JsonProperty("avatar_url")
    public String avatarUrl;

    @JsonProperty("health_focus")
    public String healthFocus;

    @JsonProperty("profile_note")
    public String profileNote;

    @JsonProperty("height_cm")
    public Double heightCm;

    @JsonProperty("weight_kg")
    public Double weightKg;

    @JsonProperty("sleep_hours")
    public Double sleepHours;

    @JsonProperty("exercise_frequency")
    public String exerciseFrequency;

    @JsonProperty("dietary_preference")
    public String dietaryPreference;

    @JsonProperty("answer_detail_level")
    public String answerDetailLevel;

    @JsonProperty("use_health_profile")
    public Boolean useHealthProfile;

    @JsonProperty("use_history_reports")
    public Boolean useHistoryReports;

    @JsonProperty("use_long_term_memory")
    public Boolean useLongTermMemory;

    @JsonProperty("tongue_reminder_enabled")
    public Boolean tongueReminderEnabled;

    @JsonProperty("tongue_reminder_time")
    public String tongueReminderTime;

    @JsonProperty("sleep_reminder_enabled")
    public Boolean sleepReminderEnabled;

    @JsonProperty("sleep_reminder_time")
    public String sleepReminderTime;
}
