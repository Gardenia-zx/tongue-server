package com.tongue.server.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class HealthPlanDraftUpdateRequest {

    @JsonProperty("schema_version")
    public String schemaVersion = "2.0";

    public List<HealthPlanDayContent> days = new ArrayList<HealthPlanDayContent>();
}
