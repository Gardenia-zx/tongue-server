package com.tongue.server.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.tongue.entity.TongueReportEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthPlanServiceTest {

    private final HealthPlanService service = new HealthPlanService(
            null,
            null,
            null,
            null,
            new ObjectMapper(),
            null
    );

    @Test
    void extractsSchema2Plans() {
        TongueReportEntity report = new TongueReportEntity();
        report.draftReportJson = "{"
                + "\"structured_report\":{"
                + "\"schema_version\":\"2.0\","
                + "\"diet_plan\":{\"goal\":\"清淡饮食\",\"actions\":[\"少生冷\"]},"
                + "\"sleep_plan\":{\"goal\":\"规律作息\",\"actions\":[\"23点前睡\"]},"
                + "\"exercise_plan\":{\"goal\":\"温和运动\",\"actions\":[\"快走20分钟\"]},"
                + "\"three_day_observation\":[\"腹胀\",\"睡眠\"]"
                + "}"
                + "}";

        HealthPlanService.PlanPayload payload = service.extractPlan(report);

        assertTrue(payload.isComplete());
        assertEquals("清淡饮食", payload.dietGoal.get("goal"));
        assertEquals("规律作息", payload.sleepGoal.get("goal"));
        assertEquals("温和运动", payload.exerciseGoal.get("goal"));
        assertEquals(2, payload.observationItems.size());
    }

    @Test
    void oldReportWithoutPlansIsNotComplete() {
        TongueReportEntity report = new TongueReportEntity();
        report.draftReportJson = "{\"summary\":\"旧报告摘要\"}";

        HealthPlanService.PlanPayload payload = service.extractPlan(report);

        assertFalse(payload.isComplete());
    }
}
