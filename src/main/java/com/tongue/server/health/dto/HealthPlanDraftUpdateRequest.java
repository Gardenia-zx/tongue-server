package com.tongue.server.health.dto;

import java.util.ArrayList;
import java.util.List;

public class HealthPlanDraftUpdateRequest {

    public String schemaVersion = "2.0";

    public List<HealthPlanDayContent> days = new ArrayList<HealthPlanDayContent>();
}
