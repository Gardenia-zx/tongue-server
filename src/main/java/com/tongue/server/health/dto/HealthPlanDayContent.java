package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HealthPlanDayContent {

    @JsonProperty("day_index")
    public Integer dayIndex;

    public LocalDate date;

    public DietContent diet = new DietContent();

    public ExerciseContent exercise = new ExerciseContent();

    public SleepContent sleep = new SleepContent();

    public List<String> observations = new ArrayList<String>();

    public static class DietContent {
        public List<String> breakfast = new ArrayList<String>();
        public List<String> lunch = new ArrayList<String>();
        public List<String> dinner = new ArrayList<String>();
        public List<String> avoid = new ArrayList<String>();
    }

    public static class ExerciseContent {
        public String activity;

        @JsonProperty("duration_minutes")
        public Integer durationMinutes;

        public String intensity;

        public List<String> warmup = new ArrayList<String>();
        public List<String> cooldown = new ArrayList<String>();
    }

    public static class SleepContent {
        @JsonProperty("target_bedtime")
        public String targetBedtime;

        @JsonProperty("target_wake_time")
        public String targetWakeTime;

        public List<String> actions = new ArrayList<String>();
    }
}
