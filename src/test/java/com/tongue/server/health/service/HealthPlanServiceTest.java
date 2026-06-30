package com.tongue.server.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.health.dto.DailyCheckinRequest;
import com.tongue.server.health.dto.HealthPlanExecutionSummaryResponse;
import com.tongue.server.health.entity.UserDailyCheckinEntity;
import com.tongue.server.health.entity.UserHealthPlanEntity;
import com.tongue.server.tongue.entity.TongueReportEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

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

    @Test
    void executionSummaryUsesElapsedDaysForActivePlan() {
        UserHealthPlanEntity plan = plan(LocalDate.of(2026, 1, 1), "ACTIVE");

        HealthPlanExecutionSummaryResponse summary = service.buildExecutionSummary(
                plan,
                Arrays.asList(
                        checkin(LocalDate.of(2026, 1, 1), true, true, false),
                        checkin(LocalDate.of(2026, 1, 3), true, false, true)
                ),
                null,
                LocalDate.of(2026, 1, 3)
        );

        assertEquals(3, summary.elapsedDays);
        assertEquals(2, summary.checkinCount);
        assertEquals(0.667, summary.checkinRate);
        assertEquals(1, summary.missedDates.size());
        assertEquals(LocalDate.of(2026, 1, 2), summary.missedDates.get(0));
    }

    @Test
    void executionSummaryIncludesRetakeReport() {
        UserHealthPlanEntity plan = plan(LocalDate.of(2026, 1, 1), "CLOSED");
        TongueReportEntity retake = new TongueReportEntity();
        retake.id = 22L;
        retake.createdAt = LocalDateTime.of(2026, 1, 7, 9, 0);

        HealthPlanExecutionSummaryResponse summary = service.buildExecutionSummary(
                plan,
                Collections.<UserDailyCheckinEntity>emptyList(),
                retake,
                LocalDate.of(2026, 1, 8)
        );

        assertTrue(summary.retakeCompleted);
        assertEquals(22L, summary.retakeReportId);
        assertEquals(7, summary.elapsedDays);
    }

    @Test
    void dailyCheckinRequestAcceptsCamelCaseFields() throws Exception {
        DailyCheckinRequest request = new ObjectMapper().readValue(
                "{\"dietDone\":true,\"sleepDone\":true,\"exerciseDone\":true}",
                DailyCheckinRequest.class
        );

        assertTrue(request.dietDone);
        assertTrue(request.sleepDone);
        assertTrue(request.exerciseDone);
    }

    @Test
    void applyCheckinRequestUpdatesExistingRow() {
        UserDailyCheckinEntity row = checkin(LocalDate.of(2026, 1, 1), false, false, false);
        DailyCheckinRequest request = new DailyCheckinRequest();
        request.dietDone = true;
        request.sleepDone = true;
        request.exerciseDone = true;

        service.applyCheckinRequest(row, request);

        assertTrue(row.dietDone);
        assertTrue(row.sleepDone);
        assertTrue(row.exerciseDone);
    }

    private UserHealthPlanEntity plan(LocalDate startDate, String status) {
        UserHealthPlanEntity plan = new UserHealthPlanEntity();
        plan.id = 9L;
        plan.userId = 3L;
        plan.sourceReportId = 11L;
        plan.status = status;
        plan.startDate = startDate;
        plan.endDate = startDate.plusDays(6);
        return plan;
    }

    private UserDailyCheckinEntity checkin(LocalDate date, boolean diet, boolean sleep, boolean exercise) {
        UserDailyCheckinEntity row = new UserDailyCheckinEntity();
        row.userId = 3L;
        row.planId = 9L;
        row.checkinDate = date;
        row.dietDone = diet;
        row.sleepDone = sleep;
        row.exerciseDone = exercise;
        return row;
    }
}
