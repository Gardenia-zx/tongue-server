package com.tongue.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class UserProfileUpdateRequest {
    public String nickname;
    public String gender;
    public Integer age;

    @JsonAlias("birth_date")
    public LocalDate birthDate;

    public String email;

    @JsonProperty("avatar_file_id")
    @JsonAlias("avatarFileId")
    public Long avatarFileId;

    @JsonProperty("health_focus")
    @JsonAlias("healthFocus")
    public String healthFocus;

    @JsonAlias("profile_note")
    public String profileNote;

    @JsonAlias("height_cm")
    public Double heightCm;

    @JsonAlias("weight_kg")
    public Double weightKg;

    @JsonAlias("sleep_hours")
    public Double sleepHours;

    @JsonAlias("exercise_frequency")
    public String exerciseFrequency;

    @JsonAlias("dietary_preference")
    public String dietaryPreference;

    @JsonAlias("answer_detail_level")
    public String answerDetailLevel;

    @JsonAlias("use_health_profile")
    public Boolean useHealthProfile;

    @JsonAlias("use_history_reports")
    public Boolean useHistoryReports;

    @JsonAlias("use_long_term_memory")
    public Boolean useLongTermMemory;

    @JsonAlias("tongue_reminder_enabled")
    public Boolean tongueReminderEnabled;

    @JsonAlias("tongue_reminder_time")
    public String tongueReminderTime;

    @JsonAlias("sleep_reminder_enabled")
    public Boolean sleepReminderEnabled;

    @JsonAlias("sleep_reminder_time")
    public String sleepReminderTime;
}
