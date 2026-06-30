package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DailyCheckinRequest {
    @JsonProperty("diet_done")
    @JsonAlias("dietDone")
    public Boolean dietDone;
    @JsonProperty("sleep_done")
    @JsonAlias("sleepDone")
    public Boolean sleepDone;
    @JsonProperty("exercise_done")
    @JsonAlias("exerciseDone")
    public Boolean exerciseDone;
    public Object observation;
    public String note;
}
