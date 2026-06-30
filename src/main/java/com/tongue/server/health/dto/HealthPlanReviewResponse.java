package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class HealthPlanReviewResponse {
    public String status;
    public String summary;
    public List<String> issues = new ArrayList<String>();
    public List<String> suggestions = new ArrayList<String>();
    @JsonProperty("recommended_action")
    public String recommendedAction;
}
