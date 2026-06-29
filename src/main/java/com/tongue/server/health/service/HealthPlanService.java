package com.tongue.server.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.health.dto.CheckinSummaryResponse;
import com.tongue.server.health.dto.DailyCheckinRequest;
import com.tongue.server.health.dto.DailyCheckinResponse;
import com.tongue.server.health.dto.HealthPlanResponse;
import com.tongue.server.health.entity.UserDailyCheckinEntity;
import com.tongue.server.health.entity.UserHealthPlanEntity;
import com.tongue.server.health.repository.UserDailyCheckinRepository;
import com.tongue.server.health.repository.UserHealthPlanRepository;
import com.tongue.server.notification.service.NotificationService;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthPlanService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CLOSED = "CLOSED";
    private static final String RETAKE_NOTIFICATION_TYPE = "HEALTH_PLAN_RETAKE";

    private final TongueReportRepository reportRepository;
    private final UserHealthPlanRepository planRepository;
    private final UserDailyCheckinRepository checkinRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public HealthPlanService(
            TongueReportRepository reportRepository,
            UserHealthPlanRepository planRepository,
            UserDailyCheckinRepository checkinRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.planRepository = planRepository;
        this.checkinRepository = checkinRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HealthPlanResponse current() {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = planRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE).orElse(null);
        return plan == null ? null : toPlanResponse(plan);
    }

    @Transactional
    public HealthPlanResponse createFromReport(Long reportId) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在或无权访问", null));
        PlanPayload payload = extractPlan(report);
        if (!payload.isComplete()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这份报告缺少可执行的饮食、睡眠或运动计划，无法生成健康计划", null);
        }

        closeActivePlans(userId);
        notificationService.markUnreadTypeRead(userId, RETAKE_NOTIFICATION_TYPE);

        LocalDate today = LocalDate.now();
        UserHealthPlanEntity plan = new UserHealthPlanEntity();
        plan.userId = userId;
        plan.sourceReportId = report.id;
        plan.status = ACTIVE;
        plan.startDate = today;
        plan.endDate = today.plusDays(6);
        plan.dietGoalJson = writeJson(payload.dietGoal);
        plan.sleepGoalJson = writeJson(payload.sleepGoal);
        plan.exerciseGoalJson = writeJson(payload.exerciseGoal);
        plan.observationItemsJson = writeJson(payload.observationItems);
        plan = planRepository.save(plan);

        createRetakeNotification(plan, 3);
        createRetakeNotification(plan, 7);
        return toPlanResponse(plan);
    }

    @Transactional
    public HealthPlanResponse close(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "健康计划不存在", null));
        plan.status = CLOSED;
        plan.closedAt = LocalDateTime.now();
        notificationService.markUnreadTypeRead(userId, RETAKE_NOTIFICATION_TYPE);
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public List<DailyCheckinResponse> checkins(int days) {
        Long userId = AuthContext.requireUserId();
        int safeDays = clampDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);
        List<DailyCheckinResponse> responses = new ArrayList<DailyCheckinResponse>();
        for (UserDailyCheckinEntity entity : checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(userId, start, end)) {
            responses.add(toCheckinResponse(entity));
        }
        return responses;
    }

    @Transactional
    public DailyCheckinResponse checkinToday(DailyCheckinRequest request) {
        Long userId = AuthContext.requireUserId();
        if (request == null) request = new DailyCheckinRequest();
        UserHealthPlanEntity plan = planRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "当前没有进行中的健康计划", null));
        LocalDate today = LocalDate.now();
        UserDailyCheckinEntity entity = checkinRepository.findByUserIdAndCheckinDate(userId, today)
                .orElseGet(UserDailyCheckinEntity::new);
        entity.userId = userId;
        entity.planId = plan.id;
        entity.checkinDate = today;
        entity.dietDone = Boolean.TRUE.equals(request.dietDone);
        entity.sleepDone = Boolean.TRUE.equals(request.sleepDone);
        entity.exerciseDone = Boolean.TRUE.equals(request.exerciseDone);
        entity.observationJson = writeJson(request.observation);
        entity.note = trimToNull(request.note);
        return toCheckinResponse(checkinRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CheckinSummaryResponse summary(int days) {
        Long userId = AuthContext.requireUserId();
        int safeDays = clampDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);
        List<UserDailyCheckinEntity> rows = checkinRepository.findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(userId, start, end);
        int diet = 0;
        int sleep = 0;
        int exercise = 0;
        for (UserDailyCheckinEntity row : rows) {
            if (Boolean.TRUE.equals(row.dietDone)) diet += 1;
            if (Boolean.TRUE.equals(row.sleepDone)) sleep += 1;
            if (Boolean.TRUE.equals(row.exerciseDone)) exercise += 1;
        }

        CheckinSummaryResponse response = new CheckinSummaryResponse();
        response.days = safeDays;
        response.checkinCount = rows.size();
        response.checkinRate = rate(rows.size(), safeDays);
        response.dietRate = rate(diet, safeDays);
        response.sleepRate = rate(sleep, safeDays);
        response.exerciseRate = rate(exercise, safeDays);
        response.retakeCompleted = retakeCompleted(userId);
        return response;
    }

    PlanPayload extractPlan(TongueReportEntity report) {
        JsonNode root = readTree(report.draftReportJson);
        JsonNode structured = firstObject(
                root.path("structured_report"),
                root.path("metadata").path("structured_answer"),
                root
        );

        PlanPayload payload = new PlanPayload();
        payload.dietGoal = toMap(firstObject(structured.path("diet_plan"), root.path("diet_plan")));
        payload.sleepGoal = toMap(firstObject(structured.path("sleep_plan"), root.path("sleep_plan")));
        payload.exerciseGoal = toMap(firstObject(structured.path("exercise_plan"), root.path("exercise_plan")));
        payload.observationItems = toStringList(firstExisting(structured.path("three_day_observation"), root.path("three_day_observation")));
        return payload;
    }

    private void closeActivePlans(Long userId) {
        for (UserHealthPlanEntity active : planRepository.findByUserIdAndStatus(userId, ACTIVE)) {
            active.status = CLOSED;
            active.closedAt = LocalDateTime.now();
            planRepository.save(active);
        }
    }

    private void createRetakeNotification(UserHealthPlanEntity plan, int day) {
        LocalDate date = plan.startDate.plusDays(day - 1L);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("plan_id", plan.id);
        payload.put("source_report_id", plan.sourceReportId);
        payload.put("retake_date", date.toString());
        notificationService.create(
                plan.userId,
                RETAKE_NOTIFICATION_TYPE,
                "复拍舌象提醒",
                "今天是健康计划第 " + day + " 天，建议在相似光线下复拍一次舌象。",
                writeJson(payload),
                date.atTime(9, 0)
        );
    }

    private HealthPlanResponse toPlanResponse(UserHealthPlanEntity plan) {
        HealthPlanResponse response = new HealthPlanResponse();
        response.planId = plan.id;
        response.userId = plan.userId;
        response.sourceReportId = plan.sourceReportId;
        response.status = plan.status;
        response.startDate = plan.startDate;
        response.endDate = plan.endDate;
        response.dietGoal = readMap(plan.dietGoalJson);
        response.sleepGoal = readMap(plan.sleepGoalJson);
        response.exerciseGoal = readMap(plan.exerciseGoalJson);
        response.observationItems = readStringList(plan.observationItemsJson);
        response.todayCheckin = checkinRepository.findByUserIdAndCheckinDate(plan.userId, LocalDate.now())
                .map(this::toCheckinResponse)
                .orElse(null);
        response.nextRetakeDate = nextRetakeDate(plan);
        response.createdAt = plan.createdAt;
        response.updatedAt = plan.updatedAt;
        return response;
    }

    private DailyCheckinResponse toCheckinResponse(UserDailyCheckinEntity entity) {
        DailyCheckinResponse response = new DailyCheckinResponse();
        response.checkinId = entity.id;
        response.planId = entity.planId;
        response.checkinDate = entity.checkinDate;
        response.dietDone = entity.dietDone;
        response.sleepDone = entity.sleepDone;
        response.exerciseDone = entity.exerciseDone;
        response.observation = readObject(entity.observationJson);
        response.note = entity.note;
        response.createdAt = entity.createdAt;
        response.updatedAt = entity.updatedAt;
        return response;
    }

    private LocalDate nextRetakeDate(UserHealthPlanEntity plan) {
        LocalDate today = LocalDate.now();
        LocalDate day3 = plan.startDate.plusDays(2);
        if (!day3.isBefore(today)) return day3;
        LocalDate day7 = plan.startDate.plusDays(6);
        return day7.isBefore(today) ? null : day7;
    }

    private boolean retakeCompleted(Long userId) {
        UserHealthPlanEntity plan = planRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE).orElse(null);
        if (plan == null) return false;
        TongueReportEntity latest = reportRepository.findFirstByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).orElse(null);
        return latest != null
                && !latest.id.equals(plan.sourceReportId)
                && latest.createdAt != null
                && !latest.createdAt.toLocalDate().isBefore(plan.startDate);
    }

    private int clampDays(int days) {
        if (days < 1) return 1;
        return Math.min(days, 180);
    }

    private double rate(int count, int days) {
        if (days <= 0) return 0.0;
        return Math.round((count * 1000.0) / days) / 1000.0;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode firstObject(JsonNode first, JsonNode second) {
        return firstObject(first, second, objectMapper.createObjectNode());
    }

    private JsonNode firstObject(JsonNode first, JsonNode second, JsonNode fallback) {
        if (first != null && first.isObject() && first.size() > 0) return first;
        if (second != null && second.isObject() && second.size() > 0) return second;
        return fallback != null && fallback.isObject() ? fallback : objectMapper.createObjectNode();
    }

    private JsonNode firstExisting(JsonNode first, JsonNode second) {
        if (first != null && !first.isMissingNode() && !first.isNull()) return first;
        return second;
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject() || node.size() == 0) return new LinkedHashMap<String, Object>();
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<String>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<String, Object>() : value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.REPORT_SAVE_FAILED, "保存健康计划失败", null, e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<String, Object>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<String>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<String>();
        }
    }

    private Object readObject(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    static class PlanPayload {
        Map<String, Object> dietGoal = new LinkedHashMap<String, Object>();
        Map<String, Object> sleepGoal = new LinkedHashMap<String, Object>();
        Map<String, Object> exerciseGoal = new LinkedHashMap<String, Object>();
        List<String> observationItems = new ArrayList<String>();

        boolean isComplete() {
            return !dietGoal.isEmpty() && !sleepGoal.isEmpty() && !exerciseGoal.isEmpty();
        }
    }
}
