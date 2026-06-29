package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DailyCheckinRequest {
    @JsonProperty("diet_done")
    public Boolean dietDone;
    @JsonProperty("sleep_done")
    public Boolean sleepDone;
    @JsonProperty("exercise_done")
    public Boolean exerciseDone;
    public Object observation;
    public String note;
}
