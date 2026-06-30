package com.tongue.server.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.health.dto.CheckinSummaryResponse;
import com.tongue.server.health.dto.DailyCheckinRequest;
import com.tongue.server.health.dto.DailyCheckinResponse;
import com.tongue.server.health.dto.HealthPlanDayContent;
import com.tongue.server.health.dto.HealthPlanDraftUpdateRequest;
import com.tongue.server.health.dto.HealthPlanResponse;
import com.tongue.server.health.dto.HealthPlanReviewResponse;
import com.tongue.server.health.entity.UserDailyCheckinEntity;
import com.tongue.server.health.entity.UserHealthPlanEntity;
import com.tongue.server.health.repository.UserDailyCheckinRepository;
import com.tongue.server.health.repository.UserHealthPlanRepository;
import com.tongue.server.notification.service.NotificationService;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.UserStateSnapshotEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import com.tongue.server.tongue.repository.UserStateSnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HealthPlanService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String CLOSED = "CLOSED";
    private static final String RETAKE_NOTIFICATION_TYPE = "HEALTH_PLAN_RETAKE";
    private static final int PLAN_DAYS = 7;

    private final TongueReportRepository reportRepository;
    private final UserStateSnapshotRepository stateSnapshotRepository;
    private final UserHealthPlanRepository planRepository;
    private final UserDailyCheckinRepository checkinRepository;
    private final NotificationService notificationService;
    private final TongueAgentClient tongueAgentClient;
    private final ObjectMapper objectMapper;

    public HealthPlanService(
            TongueReportRepository reportRepository,
            UserStateSnapshotRepository stateSnapshotRepository,
            UserHealthPlanRepository planRepository,
            UserDailyCheckinRepository checkinRepository,
            NotificationService notificationService,
            TongueAgentClient tongueAgentClient,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.stateSnapshotRepository = stateSnapshotRepository;
        this.planRepository = planRepository;
        this.checkinRepository = checkinRepository;
        this.notificationService = notificationService;
        this.tongueAgentClient = tongueAgentClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HealthPlanResponse current() {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = planRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE).orElse(null);
        return plan == null ? null : toPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public HealthPlanResponse detail(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        return toPlanResponse(plan);
    }

    @Transactional
    public HealthPlanResponse createDraftFromReport(Long reportId) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在或无权访问", null));

        UserHealthPlanEntity existingDraft = planRepository
                .findFirstByUserIdAndSourceReportIdAndStatusOrderByCreatedAtDesc(userId, reportId, DRAFT)
                .orElse(null);
        if (existingDraft != null) {
            return toPlanResponse(existingDraft);
        }

        PlanPayload payload = extractPlan(report);
        if (!payload.isComplete()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这份报告缺少饮食、睡眠或运动建议，无法生成计划草稿", null);
        }

        closePlansByStatus(userId, DRAFT);

        LocalDate today = LocalDate.now();
        UserHealthPlanEntity plan = new UserHealthPlanEntity();
        plan.userId = userId;
        plan.sourceReportId = report.id;
        plan.status = DRAFT;
        plan.startDate = today;
        plan.endDate = today.plusDays(PLAN_DAYS - 1L);
        plan.dietGoalJson = writeJson(payload.dietGoal);
        plan.sleepGoalJson = writeJson(payload.sleepGoal);
        plan.exerciseGoalJson = writeJson(payload.exerciseGoal);
        plan.observationItemsJson = writeJson(payload.observationItems);
        plan.schemaVersion = "2.0";
        plan.generationMode = "RECOMMENDED_DRAFT";
        plan.planContentJson = writeJson(buildDraftDays(payload, today));
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional
    public HealthPlanResponse updateDraft(Long planId, HealthPlanDraftUpdateRequest request) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        requireDraft(plan);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划内容不能为空", null);
        }

        List<HealthPlanDayContent> days = sanitizeDays(request.days, plan.startDate);
        plan.planContentJson = writeJson(days);
        plan.schemaVersion = StringUtils.hasText(request.schemaVersion) ? request.schemaVersion.trim() : "2.0";
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public HealthPlanReviewResponse reviewDraft(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        requireDraft(plan);

        Map<String, Object> response = tongueAgentClient.processHealthPlan(
                buildAgentPlanRequest(plan, "review")
        );
        return toReviewResponse(response);
    }

    @Transactional
    public HealthPlanResponse generateDetailedDraft(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        requireDraft(plan);

        Map<String, Object> response = tongueAgentClient.processHealthPlan(
                buildAgentPlanRequest(plan, "generate_detailed")
        );
        String agentStatus = text(response.get("status"));
        if (!"COMPLETED".equals(agentStatus)) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    textOrDefault(response.get("summary"), "AI 未能生成有效的 7 天计划，原草稿未修改"),
                    null
            );
        }

        List<HealthPlanDayContent> generatedDays;
        try {
            generatedDays = objectMapper.convertValue(
                    response.get("days"),
                    new TypeReference<List<HealthPlanDayContent>>() {}
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "AI 返回的健康计划结构无法解析，原草稿未修改",
                    null,
                    ex
            );
        }

        List<HealthPlanDayContent> sanitized = sanitizeDays(generatedDays, plan.startDate);
        validateDetailedGeneration(sanitized);

        plan.planContentJson = writeJson(sanitized);
        plan.generationMode = "AI_DETAILED";
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional
    public HealthPlanResponse activate(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        requireDraft(plan);

        LocalDate today = LocalDate.now();
        List<HealthPlanDayContent> days = sanitizeDays(readPlanDays(plan), today);
        validateForActivation(days);

        closePlansByStatus(userId, ACTIVE);
        notificationService.markUnreadTypeRead(userId, RETAKE_NOTIFICATION_TYPE);

        plan.status = ACTIVE;
        plan.startDate = today;
        plan.endDate = today.plusDays(PLAN_DAYS - 1L);
        plan.planContentJson = writeJson(days);
        plan.activatedAt = LocalDateTime.now();
        plan.closedAt = null;
        plan = planRepository.save(plan);

        createRetakeNotification(plan, 3);
        createRetakeNotification(plan, 7);
        return toPlanResponse(plan);
    }

    @Transactional
    public HealthPlanResponse close(Long planId) {
        Long userId = AuthContext.requireUserId();
        UserHealthPlanEntity plan = requireOwnedPlan(planId, userId);
        boolean wasActive = ACTIVE.equals(plan.status);
        plan.status = CLOSED;
        plan.closedAt = LocalDateTime.now();
        if (wasActive) {
            notificationService.markUnreadTypeRead(userId, RETAKE_NOTIFICATION_TYPE);
        }
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

        if (checkinRepository.findByUserIdAndCheckinDate(userId, today).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "今天已经记录了，请明天再来打卡", null);
        }

        UserDailyCheckinEntity entity = new UserDailyCheckinEntity();
        entity.userId = userId;
        entity.planId = plan.id;
        entity.checkinDate = today;
        entity.dietDone = Boolean.TRUE.equals(request.dietDone);
        entity.sleepDone = Boolean.TRUE.equals(request.sleepDone);
        entity.exerciseDone = Boolean.TRUE.equals(request.exerciseDone);
        entity.observationJson = writeJson(request.observation);
        entity.note = trimToNull(request.note);

        try {
            return toCheckinResponse(checkinRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "今天已经记录了，请明天再来打卡", null, ex);
        }
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

    private Map<String, Object> buildAgentPlanRequest(UserHealthPlanEntity plan, String mode) {
        TongueReportEntity report = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(plan.sourceReportId, plan.userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "健康计划来源报告不存在", null));
        UserStateSnapshotEntity snapshot = stateSnapshotRepository
                .findByReportIdAndUserId(plan.sourceReportId, plan.userId)
                .orElse(null);

        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("mode", mode);
        request.put("plan_id", plan.id);
        request.put("source_report_id", plan.sourceReportId);
        request.put("plan_days", readPlanDays(plan));
        request.put("draft_report", readObjectMap(report.draftReportJson));
        request.put("state_snapshot", snapshot == null ? null : snapshotMap(snapshot));
        request.put("personalization_signals", personalizationSignals(plan.sourceReportId));
        return request;
    }

    private Map<String, Object> snapshotMap(UserStateSnapshotEntity snapshot) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("observation_window", snapshot.observationWindow);
        result.put("sleep_status", snapshot.sleepStatus);
        result.put("digestion_status", snapshot.digestionStatus);
        result.put("bowel_status", snapshot.bowelStatus);
        result.put("current_states", readStringList(snapshot.currentStatesJson));
        result.put("health_goals", readStringList(snapshot.healthGoalsJson));
        result.put("free_description", snapshot.freeDescription);
        result.put("skipped", Boolean.TRUE.equals(snapshot.skipped));
        return result;
    }

    private HealthPlanReviewResponse toReviewResponse(Map<String, Object> response) {
        HealthPlanReviewResponse result = new HealthPlanReviewResponse();
        result.status = textOrDefault(response.get("status"), "FAILED");
        result.summary = textOrDefault(response.get("summary"), "AI 未返回有效的评估结论");
        result.issues = stringList(response.get("issues"));
        result.suggestions = stringList(response.get("suggestions"));
        result.recommendedAction = text(response.get("recommended_action"));
        if (!("REASONABLE".equals(result.status)
                || "NEEDS_IMPROVEMENT".equals(result.status)
                || "FAILED".equals(result.status))) {
            result.status = "FAILED";
            result.summary = "AI 返回了无法识别的评估状态";
            result.recommendedAction = null;
        }
        return result;
    }

    private List<HealthPlanDayContent> buildDraftDays(PlanPayload payload, LocalDate startDate) {
        List<HealthPlanDayContent> days = new ArrayList<HealthPlanDayContent>();
        List<List<String>> breakfasts = Arrays.asList(
                Arrays.asList("小米粥一碗", "水煮鸡蛋一个", "焯青菜一份"),
                Arrays.asList("燕麦粥一碗", "鸡蛋一个", "黄瓜或番茄一份"),
                Arrays.asList("全麦面包两片", "无糖豆浆一杯", "熟蔬菜一份"),
                Arrays.asList("南瓜粥一碗", "蒸蛋一份", "清炒青菜一份"),
                Arrays.asList("馒头一个", "鸡蛋一个", "无糖豆浆一杯"),
                Arrays.asList("杂粮粥一碗", "豆腐或鸡蛋一份", "青菜一份"),
                Arrays.asList("燕麦或小米粥一碗", "鸡蛋一个", "水果一小份")
        );
        List<List<String>> lunches = Arrays.asList(
                Arrays.asList("米饭一拳大小", "清炒小白菜一份", "鸡肉或豆腐一份"),
                Arrays.asList("米饭一拳大小", "西兰花或菜花一份", "鱼肉或豆腐一份"),
                Arrays.asList("杂粮饭一拳大小", "芹菜胡萝卜一份", "瘦肉或鸡蛋一份"),
                Arrays.asList("米饭一拳大小", "菠菜或油麦菜一份", "鸡胸肉或豆制品一份"),
                Arrays.asList("面条一碗", "青菜一份", "鸡蛋或瘦肉一份"),
                Arrays.asList("米饭一拳大小", "冬瓜或南瓜一份", "鱼肉或豆腐一份"),
                Arrays.asList("杂粮饭一拳大小", "两种熟蔬菜", "鸡肉、鱼肉或豆腐一份")
        );
        List<List<String>> dinners = Arrays.asList(
                Arrays.asList("杂粮粥一小碗", "番茄炒蛋一份", "菠菜一份"),
                Arrays.asList("南瓜粥一小碗", "蒸蛋一份", "清炒西兰花一份"),
                Arrays.asList("面条一小碗", "青菜一份", "豆腐一份"),
                Arrays.asList("小米粥一小碗", "清蒸鱼或豆腐一份", "熟蔬菜一份"),
                Arrays.asList("米饭半拳到一拳", "番茄或冬瓜一份", "鸡蛋一份"),
                Arrays.asList("山药粥一小碗", "豆腐一份", "清炒青菜一份"),
                Arrays.asList("杂粮粥一小碗", "蒸蛋或豆腐一份", "熟蔬菜一份")
        );
        String[] activities = {"晚饭后快走", "八段锦加轻松散步", "快走与慢走交替", "舒缓拉伸加散步", "室内步行或骑行", "低强度全身活动", "轻松快走"};
        int[] durations = {20, 25, 24, 20, 25, 20, 30};

        for (int index = 0; index < PLAN_DAYS; index++) {
            HealthPlanDayContent day = new HealthPlanDayContent();
            day.dayIndex = index + 1;
            day.date = startDate.plusDays(index);
            day.diet.breakfast = new ArrayList<String>(breakfasts.get(index));
            day.diet.lunch = new ArrayList<String>(lunches.get(index));
            day.diet.dinner = new ArrayList<String>(dinners.get(index));
            day.diet.avoid = new ArrayList<String>(Arrays.asList("夜宵", "油炸食品", "大量冰饮"));
            day.exercise.activity = activities[index];
            day.exercise.durationMinutes = durations[index];
            day.exercise.intensity = index == 6 ? "中等强度，以可以正常说话为准" : "低到中等强度，不追求大汗";
            day.exercise.warmup = new ArrayList<String>(Arrays.asList("原地慢走或踏步 2 分钟", "活动肩颈、髋部和踝关节"));
            day.exercise.cooldown = new ArrayList<String>(Arrays.asList("慢走 2 分钟", "小腿和大腿后侧各拉伸 30 秒"));
            day.sleep.targetBedtime = "23:30";
            day.sleep.targetWakeTime = "07:30";
            day.sleep.actions = mergePlanActions(
                    stringListFromMap(payload.sleepGoal, "actions"),
                    Arrays.asList("睡前 30 分钟减少手机使用", "晚餐后避免大量咖啡因和酒精"),
                    3
            );
            day.observations = payload.observationItems.isEmpty()
                    ? new ArrayList<String>(Arrays.asList("饭后是否腹胀", "运动后疲劳程度", "入睡所需时间"))
                    : new ArrayList<String>(payload.observationItems);
            days.add(day);
        }
        return days;
    }

    private List<String> mergePlanActions(List<String> primary, List<String> fallback, int limit) {
        List<String> result = new ArrayList<String>();
        for (String item : primary) addUnique(result, item, limit);
        for (String item : fallback) addUnique(result, item, limit);
        return result;
    }

    private void addUnique(List<String> target, String value, int limit) {
        String cleaned = cleanText(value, 120);
        if (!StringUtils.hasText(cleaned) || target.contains(cleaned) || target.size() >= limit) return;
        target.add(cleaned);
    }

    private List<HealthPlanDayContent> sanitizeDays(List<HealthPlanDayContent> input, LocalDate startDate) {
        if (input == null || input.size() != PLAN_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "健康计划必须包含完整的 7 天安排", null);
        }

        List<HealthPlanDayContent> days = new ArrayList<HealthPlanDayContent>();
        Set<Integer> indexes = new HashSet<Integer>();
        for (HealthPlanDayContent raw : input) {
            if (raw == null || raw.dayIndex == null || raw.dayIndex < 1 || raw.dayIndex > PLAN_DAYS || !indexes.add(raw.dayIndex)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "计划日期序号不完整或重复", null);
            }
            HealthPlanDayContent day = new HealthPlanDayContent();
            day.dayIndex = raw.dayIndex;
            day.date = startDate.plusDays(raw.dayIndex - 1L);
            HealthPlanDayContent.DietContent diet = raw.diet == null ? new HealthPlanDayContent.DietContent() : raw.diet;
            day.diet.breakfast = cleanList(diet.breakfast, 12, 80);
            day.diet.lunch = cleanList(diet.lunch, 12, 80);
            day.diet.dinner = cleanList(diet.dinner, 12, 80);
            day.diet.avoid = cleanList(diet.avoid, 12, 80);

            HealthPlanDayContent.ExerciseContent exercise = raw.exercise == null ? new HealthPlanDayContent.ExerciseContent() : raw.exercise;
            day.exercise.activity = cleanText(exercise.activity, 80);
            day.exercise.durationMinutes = exercise.durationMinutes;
            day.exercise.intensity = cleanText(exercise.intensity, 120);
            day.exercise.warmup = cleanList(exercise.warmup, 8, 100);
            day.exercise.cooldown = cleanList(exercise.cooldown, 8, 100);

            HealthPlanDayContent.SleepContent sleep = raw.sleep == null ? new HealthPlanDayContent.SleepContent() : raw.sleep;
            day.sleep.targetBedtime = cleanText(sleep.targetBedtime, 16);
            day.sleep.targetWakeTime = cleanText(sleep.targetWakeTime, 16);
            day.sleep.actions = cleanList(sleep.actions, 10, 120);
            day.observations = cleanList(raw.observations, 12, 120);
            days.add(day);
        }
        days.sort(Comparator.comparingInt(item -> item.dayIndex));
        return days;
    }

    private void validateForActivation(List<HealthPlanDayContent> days) {
        for (HealthPlanDayContent day : days) {
            int index = day.dayIndex == null ? 0 : day.dayIndex;
            if (day.diet.breakfast.isEmpty() || day.diet.lunch.isEmpty() || day.diet.dinner.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第 " + index + " 天的早餐、午餐和晚餐都需要至少保留一项", null);
            }
            if (!StringUtils.hasText(day.exercise.activity)
                    || day.exercise.durationMinutes == null
                    || day.exercise.durationMinutes < 5
                    || day.exercise.durationMinutes > 180) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第 " + index + " 天需要填写有效的运动项目和 5 到 180 分钟的时长", null);
            }
            if (!StringUtils.hasText(day.exercise.intensity)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第 " + index + " 天需要填写运动强度", null);
            }
            if (!StringUtils.hasText(day.sleep.targetBedtime)
                    || !StringUtils.hasText(day.sleep.targetWakeTime)
                    || day.sleep.actions.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第 " + index + " 天需要填写睡眠时间和至少一项睡前行动", null);
            }
        }
    }

    private void validateDetailedGeneration(List<HealthPlanDayContent> days) {
        try {
            validateForActivation(days);
            for (HealthPlanDayContent day : days) {
                int index = day.dayIndex == null ? 0 : day.dayIndex;
                if (day.diet.avoid.isEmpty()
                        || day.exercise.warmup.isEmpty()
                        || day.exercise.cooldown.isEmpty()
                        || day.observations.isEmpty()) {
                    throw new BusinessException(
                            ErrorCode.AGENT_CALL_FAILED,
                            "AI 生成的第 " + index + " 天计划缺少避免项、热身、放松或观察项，原草稿未修改",
                            null
                    );
                }
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.AGENT_CALL_FAILED) throw ex;
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "AI 生成的 7 天计划不完整，原草稿未修改：" + ex.getMessage(),
                    null,
                    ex
            );
        }
    }

    private UserHealthPlanEntity requireOwnedPlan(Long planId, Long userId) {
        return planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "健康计划不存在", null));
    }

    private void requireDraft(UserHealthPlanEntity plan) {
        if (!DRAFT.equals(plan.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有计划草稿可以编辑、评估或启用", null);
        }
    }

    private void closePlansByStatus(Long userId, String status) {
        for (UserHealthPlanEntity plan : planRepository.findByUserIdAndStatus(userId, status)) {
            plan.status = CLOSED;
            plan.closedAt = LocalDateTime.now();
            planRepository.save(plan);
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
        response.days = readPlanDays(plan);
        response.schemaVersion = StringUtils.hasText(plan.schemaVersion) ? plan.schemaVersion : "1.0";
        response.generationMode = StringUtils.hasText(plan.generationMode) ? plan.generationMode : "LEGACY";
        response.activatedAt = plan.activatedAt;

        HealthPlanDayContent currentDay = ACTIVE.equals(plan.status) ? currentPlanDay(plan, response.days) : null;
        if (currentDay != null) {
            response.dietGoal = currentDietGoal(currentDay);
            response.sleepGoal = currentSleepGoal(currentDay);
            response.exerciseGoal = currentExerciseGoal(currentDay);
            response.observationItems = currentDay.observations;
        } else {
            response.dietGoal = readMap(plan.dietGoalJson);
            response.sleepGoal = readMap(plan.sleepGoalJson);
            response.exerciseGoal = readMap(plan.exerciseGoalJson);
            response.observationItems = readStringList(plan.observationItemsJson);
        }

        response.todayCheckin = ACTIVE.equals(plan.status)
                ? checkinRepository.findByUserIdAndCheckinDate(plan.userId, LocalDate.now()).map(this::toCheckinResponse).orElse(null)
                : null;
        response.nextRetakeDate = ACTIVE.equals(plan.status) ? nextRetakeDate(plan) : null;
        response.personalizationSignals = personalizationSignals(plan.sourceReportId);
        response.createdAt = plan.createdAt;
        response.updatedAt = plan.updatedAt;
        return response;
    }

    private HealthPlanDayContent currentPlanDay(UserHealthPlanEntity plan, List<HealthPlanDayContent> days) {
        if (days == null || days.isEmpty()) return null;
        LocalDate today = LocalDate.now();
        for (HealthPlanDayContent day : days) {
            if (today.equals(day.date)) return day;
        }
        long offset = ChronoUnit.DAYS.between(plan.startDate, today);
        int dayIndex = (int) Math.max(0, Math.min(PLAN_DAYS - 1L, offset));
        return days.get(dayIndex);
    }

    private Map<String, Object> currentDietGoal(HealthPlanDayContent day) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("goal", "第 " + day.dayIndex + " 天饮食安排");
        List<String> actions = new ArrayList<String>();
        actions.add("早餐：" + String.join("、", day.diet.breakfast));
        actions.add("午餐：" + String.join("、", day.diet.lunch));
        actions.add("晚餐：" + String.join("、", day.diet.dinner));
        if (!day.diet.avoid.isEmpty()) actions.add("今天尽量避免：" + String.join("、", day.diet.avoid));
        result.put("actions", actions);
        result.put("frequency", "今天");
        result.put("duration", "第 " + day.dayIndex + " 天");
        return result;
    }

    private Map<String, Object> currentExerciseGoal(HealthPlanDayContent day) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("goal", "第 " + day.dayIndex + " 天运动安排");
        List<String> actions = new ArrayList<String>();
        actions.add(day.exercise.activity + " " + day.exercise.durationMinutes + " 分钟");
        actions.add("强度：" + day.exercise.intensity);
        if (!day.exercise.warmup.isEmpty()) actions.add("热身：" + String.join("、", day.exercise.warmup));
        if (!day.exercise.cooldown.isEmpty()) actions.add("放松：" + String.join("、", day.exercise.cooldown));
        result.put("actions", actions);
        result.put("frequency", "今天");
        result.put("duration", day.exercise.durationMinutes + " 分钟");
        return result;
    }

    private Map<String, Object> currentSleepGoal(HealthPlanDayContent day) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("goal", "建议 " + day.sleep.targetBedtime + " 入睡，" + day.sleep.targetWakeTime + " 起床");
        result.put("actions", day.sleep.actions);
        result.put("frequency", "今晚");
        result.put("duration", "第 " + day.dayIndex + " 天");
        return result;
    }

    private List<HealthPlanDayContent> readPlanDays(UserHealthPlanEntity plan) {
        if (!StringUtils.hasText(plan.planContentJson)) return new ArrayList<HealthPlanDayContent>();
        try {
            return objectMapper.readValue(plan.planContentJson, new TypeReference<List<HealthPlanDayContent>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<HealthPlanDayContent>();
        }
    }

    private List<String> personalizationSignals(Long reportId) {
        TongueReportEntity report = reportRepository.findById(reportId).orElse(null);
        if (report == null) return new ArrayList<String>();
        JsonNode root = readTree(report.draftReportJson);
        return toStringList(root.path("metadata").path("personalization_signals"));
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

    private String cleanText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String cleaned = value.trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private List<String> cleanList(List<String> values, int maxItems, int maxLength) {
        List<String> result = new ArrayList<String>();
        if (values == null) return result;
        for (String value : values) {
            String cleaned = cleanText(value, maxLength);
            if (!StringUtils.hasText(cleaned) || result.contains(cleaned)) continue;
            result.add(cleaned);
            if (result.size() >= maxItems) break;
        }
        return result;
    }

    private List<String> stringListFromMap(Map<String, Object> map, String key) {
        return map == null ? new ArrayList<String>() : stringList(map.get(key));
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<String>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value) {
            String cleaned = cleanText(item == null ? null : String.valueOf(item), 500);
            if (StringUtils.hasText(cleaned)) result.add(cleaned);
        }
        return result;
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
            if (item.isTextual() && StringUtils.hasText(item.asText())) result.add(item.asText());
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

    private Map<String, Object> readObjectMap(String json) {
        return readMap(json);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String result = text(value);
        return StringUtils.hasText(result) ? result : fallback;
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
