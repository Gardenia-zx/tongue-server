package com.tongue.server.tongue.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.context.entity.AgentConversationEntity;
import com.tongue.server.agent.context.service.ConversationContextService;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.auth.service.AuthService;
import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.AgentTurnAckRequest;
import com.tongue.server.storage.StorageResult;
import com.tongue.server.storage.entity.FileObjectEntity;
import com.tongue.server.storage.repository.FileObjectRepository;
import com.tongue.server.storage.service.StorageService;
import com.tongue.server.tongue.dto.EvidenceResponse;
import com.tongue.server.tongue.dto.FeatureResponse;
import com.tongue.server.tongue.dto.DashboardResponse;
import com.tongue.server.tongue.dto.ReportCompareRequest;
import com.tongue.server.tongue.dto.ReportCompareResponse;
import com.tongue.server.tongue.dto.ReportDetailResponse;
import com.tongue.server.tongue.dto.ReportListItemResponse;
import com.tongue.server.tongue.dto.ReportVersionResponse;
import com.tongue.server.tongue.dto.TaskStatusResponse;
import com.tongue.server.tongue.dto.TongueAnalyzeCreateResponse;
import com.tongue.server.tongue.entity.TongueAnalysisTaskEntity;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.TongueReportEvidenceEntity;
import com.tongue.server.tongue.entity.TongueReportFeatureEntity;
import com.tongue.server.tongue.entity.TongueReportVersionEntity;
import com.tongue.server.tongue.repository.TongueAnalysisTaskRepository;
import com.tongue.server.tongue.repository.TongueReportEvidenceRepository;
import com.tongue.server.tongue.repository.TongueReportFeatureRepository;
import com.tongue.server.tongue.repository.TongueReportRepository;
import com.tongue.server.tongue.repository.TongueReportVersionRepository;
import com.tongue.server.notification.service.NotificationService;
import com.tongue.server.notification.repository.UserNotificationRepository;
import com.tongue.server.review.entity.DoctorReviewOrderEntity;
import com.tongue.server.review.repository.DoctorReviewOrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class TongueAnalysisAppService {

    private static final String QUALITY_VERSION = "quality_v1";
    private static final double CONFIDENCE_CHANGE_THRESHOLD = 0.1;

    private final StorageService storageService;
    private final TongueAgentClient tongueAgentClient;
    private final ObjectMapper objectMapper;
    private final Executor analysisTaskExecutor;
    private final FileObjectRepository fileObjectRepository;
    private final TongueAnalysisTaskRepository taskRepository;
    private final TongueReportRepository reportRepository;
    private final TongueReportVersionRepository versionRepository;
    private final TongueReportFeatureRepository featureRepository;
    private final TongueReportEvidenceRepository evidenceRepository;
    private final NotificationService notificationService;
    private final ConversationContextService conversationContextService;
    private final AuthService authService;
    private final UserNotificationRepository notificationRepository;
    private final DoctorReviewOrderRepository reviewOrderRepository;

    public TongueAnalysisAppService(
            StorageService storageService,
            TongueAgentClient tongueAgentClient,
            ObjectMapper objectMapper,
            @Qualifier("analysisTaskExecutor") Executor analysisTaskExecutor,
            FileObjectRepository fileObjectRepository,
            TongueAnalysisTaskRepository taskRepository,
            TongueReportRepository reportRepository,
            TongueReportVersionRepository versionRepository,
            TongueReportFeatureRepository featureRepository,
            TongueReportEvidenceRepository evidenceRepository,
            NotificationService notificationService,
            ConversationContextService conversationContextService,
            AuthService authService,
            UserNotificationRepository notificationRepository,
            DoctorReviewOrderRepository reviewOrderRepository
    ) {
        this.storageService = storageService;
        this.tongueAgentClient = tongueAgentClient;
        this.objectMapper = objectMapper;
        this.analysisTaskExecutor = analysisTaskExecutor;
        this.fileObjectRepository = fileObjectRepository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.versionRepository = versionRepository;
        this.featureRepository = featureRepository;
        this.evidenceRepository = evidenceRepository;
        this.notificationService = notificationService;
        this.conversationContextService = conversationContextService;
        this.authService = authService;
        this.notificationRepository = notificationRepository;
        this.reviewOrderRepository = reviewOrderRepository;
    }

    @Transactional
    public TongueAnalyzeCreateResponse createAnalysis(
            MultipartFile image,
            String conversationId,
            String requestThreadId,
            String traceId,
            String userDescription
    ) {
        Long userId = AuthContext.requireUserId();
        String normalizedUserDescription = normalizeUserDescription(userDescription);
        AgentConversationEntity conversation = conversationContextService.ensureConversation(
                userId,
                conversationId,
                requestThreadId
        );

        TongueReportEntity report = new TongueReportEntity();
        report.userId = userId;
        report.reportStatus = "DRAFT";
        report.sourceType = "AI";
        report.threadId = conversation.threadId;
        reportRepository.save(report);

        StorageResult imageResult = storageService.storeTongueImage(userId, report.id, image, traceId);
        report.imageFileId = imageResult.fileObjectId;
        reportRepository.save(report);

        TongueAnalysisTaskEntity task = new TongueAnalysisTaskEntity();
        task.userId = userId;
        task.reportId = report.id;
        task.status = "PENDING";
        task.currentStage = "PENDING";
        task.progress = 0.0;
        task.traceId = traceId;
        taskRepository.save(task);

        report.taskId = task.id;
        reportRepository.save(report);

        Map<String, Object> userMetadata = new LinkedHashMap<String, Object>();
        userMetadata.put("source", "tongue_analyze");
        userMetadata.put("task_id", task.id);
        userMetadata.put("trace_id", traceId);
        String userMessageId = "analysis_user_" + task.id;
        conversationContextService.appendUserMessage(
                conversation,
                buildAnalyzeUserMessage(normalizedUserDescription),
                "mixed",
                report.imageFileId,
                report.id,
                userMetadata,
                userMessageId
        );
        conversationContextService.markImageSubmitted(conversation, report.imageFileId);

        submitTaskAfterCommit(task.id, String.valueOf(conversation.id), normalizedUserDescription);

        TongueAnalyzeCreateResponse response = new TongueAnalyzeCreateResponse();
        response.reportId = report.id;
        response.taskId = task.id;
        response.conversationId = String.valueOf(conversation.id);
        response.threadId = conversation.threadId;
        response.status = task.status;
        return response;
    }

    public void submitTask(
            final Long taskId,
            final String conversationId,
            final String userDescription
    ) {
        analysisTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                processTask(taskId, conversationId, userDescription);
            }
        });
    }

    public void processTask(Long taskId, String conversationId, String userDescription) {
        TongueAnalysisTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            updateTask(task, "RUNNING", "MODEL_ANALYZING", 0.15, null, null);
            TongueReportEntity report = reportRepository.findById(task.reportId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "报告不存在",
                            task.traceId
                    ));
            FileObjectEntity imageFile = fileObjectRepository.findById(report.imageFileId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "舌象图片不存在",
                            task.traceId
                    ));

            AgentRunRequest agentRequest = buildAgentRequest(
                    task,
                    report,
                    imageFile,
                    conversationId,
                    userDescription
            );
            task.requestId = agentRequest.getRequestId();
            taskRepository.save(task);
            AgentRunResponse agentResponse = tongueAgentClient.runAgent(agentRequest);

            updateTask(task, "RUNNING", "REPORT_GENERATING", 0.85, null, null);
            saveAgentReport(task, report, agentResponse);
            conversationContextService.markReportReady(
                    resolveConversationId(conversationId, report),
                    task.userId,
                    report,
                    report.summary,
                    agentRequest.getAssistantMessageId(),
                    String.valueOf(task.userId),
                    agentRequest.getTurnId(),
                    agentRequest.getUserMessageId(),
                    agentRequest.getThreadEpoch()
            );
            ackAgentTurn(agentResponse, agentRequest.getAssistantMessageId());
            updateTask(task, "COMPLETED", "REPORT_READY", 1.0, null, null);
            notificationService.create(
                    task.userId,
                    "ANALYSIS_COMPLETED",
                    "舌象分析已完成",
                    "你的舌象分析报告已生成。",
                    "{\"report_id\":" + report.id + ",\"task_id\":" + task.id + "}"
            );
        } catch (Exception ex) {
            updateTask(
                    task,
                    "FAILED",
                    "FAILED",
                    1.0,
                    ex instanceof BusinessException
                            ? String.valueOf(((BusinessException) ex).getCode())
                            : "50000",
                    ex.getMessage()
            );
            notificationService.create(
                    task.userId,
                    "ANALYSIS_FAILED",
                    "舌象分析失败",
                    ex.getMessage(),
                    "{\"task_id\":" + task.id + "}"
            );
        }
    }

    @Transactional(readOnly = true)
    public TaskStatusResponse taskStatus(Long taskId) {
        Long userId = AuthContext.requireUserId();
        TongueAnalysisTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在", null));
        if (!userId.equals(task.userId) && !"ADMIN".equals(AuthContext.get().role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有任务访问权限", null);
        }
        return toTaskStatus(task);
    }

    @Transactional
    public TaskStatusResponse retry(Long taskId) {
        Long userId = AuthContext.requireUserId();
        TongueAnalysisTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在", null));
        if (!userId.equals(task.userId) && !"ADMIN".equals(AuthContext.get().role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有任务访问权限", null);
        }
        if (!"FAILED".equals(task.status)) {
            throw new BusinessException(ErrorCode.TASK_NOT_RETRYABLE, "当前任务状态不可重试", task.traceId);
        }
        task.status = "PENDING";
        task.currentStage = "PENDING";
        task.progress = 0.0;
        task.errorCode = null;
        task.errorMessage = null;
        task.startedAt = null;
        task.finishedAt = null;
        taskRepository.save(task);
        submitTaskAfterCommit(
                task.id,
                conversationContextService.resolveConversationIdForReport(userId, task.reportId),
                null
        );
        return toTaskStatus(task);
    }

    private void submitTaskAfterCommit(
            final Long taskId,
            final String conversationId,
            final String userDescription
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submitTask(taskId, conversationId, userDescription);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitTask(taskId, conversationId, userDescription);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ReportListItemResponse> reports() {
        Long userId = AuthContext.requireUserId();
        List<ReportListItemResponse> result = new ArrayList<ReportListItemResponse>();
        for (TongueReportEntity report : reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            result.add(toReportListItem(report));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long userId = AuthContext.requireUserId();
        List<TongueReportEntity> reports = reportRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        DashboardResponse response = new DashboardResponse();
        response.user = authService.currentUser();
        response.reportCount = reports.size();
        response.latestReport = reports.isEmpty() ? null : toReportListItem(reports.get(0));
        response.trendStatus = buildTrendStatus(reports.size());
        LocalDateTime now = LocalDateTime.now();
        response.unreadNotificationCount = notificationRepository.countVisibleUnreadByUserId(userId, now);
        response.recentNotifications = notificationRepository.findTopVisibleByUserIdOrderByCreatedAtDesc(userId, now, PageRequest.of(0, 3));
        response.todos = buildDashboardTodos(userId, reports.size(), response.unreadNotificationCount);
        return response;
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse reportDetail(Long reportId) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = loadReportForUser(reportId, userId);
        return toReportDetail(report);
    }

    @Transactional(readOnly = true)
    public ReportCompareResponse compareReports(ReportCompareRequest request) {
        if (request == null || request.baseReportId == null || request.targetReportId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "璇烽€夋嫨闇€瑕佸姣旂殑涓や唤鎶ュ憡", null);
        }
        Long userId = AuthContext.requireUserId();
        TongueReportEntity baseReport = loadReportForUser(request.baseReportId, userId);
        TongueReportEntity targetReport = loadReportForUser(request.targetReportId, userId);
        FeatureSnapshot base = buildFeatureSnapshot(baseReport);
        FeatureSnapshot target = buildFeatureSnapshot(targetReport);

        ReportCompareResponse response = new ReportCompareResponse();
        response.baseReportId = baseReport.id;
        response.targetReportId = targetReport.id;

        for (String code : target.detectedCodes) {
            if (!base.detectedCodes.contains(code)) {
                response.added.add(diffItem(code, null, target.confidenceByCode.get(code), "ADDED"));
            }
        }
        for (String code : base.detectedCodes) {
            if (!target.detectedCodes.contains(code) && target.supportedCodes.contains(code)) {
                response.removed.add(diffItem(code, base.confidenceByCode.get(code), null, "REMOVED"));
            }
        }
        for (String code : target.detectedCodes) {
            if (!base.detectedCodes.contains(code)) {
                continue;
            }
            Double baseConfidence = base.confidenceByCode.get(code);
            Double targetConfidence = target.confidenceByCode.get(code);
            response.persistent.add(diffItem(code, baseConfidence, targetConfidence, "PERSISTENT"));
            if (baseConfidence != null && targetConfidence != null
                    && Math.abs(targetConfidence - baseConfidence) >= CONFIDENCE_CHANGE_THRESHOLD) {
                response.changed.add(diffItem(
                        code,
                        baseConfidence,
                        targetConfidence,
                        targetConfidence > baseConfidence ? "CONFIDENCE_UP" : "CONFIDENCE_DOWN"
                ));
            }
        }
        Set<String> unsupported = new LinkedHashSet<String>();
        unsupported.addAll(base.unsupportedCodes);
        unsupported.addAll(target.unsupportedCodes);
        for (String code : unsupported) {
            response.unsupported.add(diffItem(code, base.confidenceByCode.get(code), target.confidenceByCode.get(code), "UNSUPPORTED"));
        }

        applyCompareExplanation(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReportVersionResponse> versions(Long reportId) {
        Long userId = AuthContext.requireUserId();
        loadReportForUser(reportId, userId);
        List<ReportVersionResponse> result = new ArrayList<ReportVersionResponse>();
        for (TongueReportVersionEntity version : versionRepository.findByReportIdOrderByVersionNoDesc(reportId)) {
            ReportVersionResponse item = new ReportVersionResponse();
            item.versionId = version.id;
            item.versionNo = version.versionNo;
            item.sourceType = version.sourceType;
            item.summary = version.summary;
            item.reportJson = parseJson(version.reportJson);
            item.createdAt = version.createdAt;
            result.add(item);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<FeatureResponse> features(Long reportId) {
        Long userId = AuthContext.requireUserId();
        loadReportForUser(reportId, userId);
        List<FeatureResponse> result = new ArrayList<FeatureResponse>();
        for (TongueReportFeatureEntity feature : featureRepository.findByReportId(reportId)) {
            FeatureResponse item = new FeatureResponse();
            item.featureId = feature.id;
            item.featureCode = feature.featureCode;
            item.featureGroup = feature.featureGroup;
            item.confidence = feature.confidence;
            result.add(item);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> evidence(Long reportId) {
        Long userId = AuthContext.requireUserId();
        loadReportForUser(reportId, userId);
        List<EvidenceResponse> result = new ArrayList<EvidenceResponse>();
        for (TongueReportEvidenceEntity evidence : evidenceRepository.findByReportId(reportId)) {
            EvidenceResponse item = new EvidenceResponse();
            item.evidenceId = evidence.id;
            item.chunkId = evidence.chunkId;
            item.docId = evidence.docId;
            item.title = evidence.title;
            item.content = evidence.content;
            item.sourceUri = evidence.sourceUri;
            item.finalScore = evidence.finalScore;
            result.add(item);
        }
        return result;
    }

    @Transactional
    public void deleteReport(Long reportId) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = loadReportForUser(reportId, userId);
        report.deletedAt = LocalDateTime.now();
        reportRepository.save(report);
        conversationContextService.clearActiveReport(userId, reportId);
    }

    private String normalizeUserDescription(String userDescription) {
        if (!StringUtils.hasText(userDescription)) {
            return null;
        }
        String normalized = userDescription.trim();
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500);
        }
        return normalized;
    }

    private String buildAnalyzeUserMessage(String normalizedUserDescription) {
        if (StringUtils.hasText(normalizedUserDescription)) {
            return "我想做一次舌象分析。\n用户补充描述：" + normalizedUserDescription;
        }
        return "我想做一次舌象分析";
    }

    private String resolveConversationId(String conversationId, TongueReportEntity report) {
        if (StringUtils.hasText(conversationId)) {
            return conversationId;
        }
        String resolved = conversationContextService.resolveConversationIdForReport(report.userId, report.id);
        return StringUtils.hasText(resolved) ? resolved : null;
    }

    private AgentRunRequest buildAgentRequest(
            TongueAnalysisTaskEntity task,
            TongueReportEntity report,
            FileObjectEntity imageFile,
            String conversationId,
            String userDescription
    ) {
        String normalizedUserDescription = normalizeUserDescription(userDescription);
        String resolvedConversationId = resolveConversationId(conversationId, report);
        AgentConversationEntity conversation = conversationContextService.ensureConversation(
                task.userId,
                resolvedConversationId,
                report.threadId
        );
        Map<String, Object> contextBundle = conversationContextService.buildContextBundleForAnalysis(
                conversation,
                report.id,
                normalizedUserDescription
        );

        AgentRunRequest.AgentAttachment attachment = new AgentRunRequest.AgentAttachment();
        attachment.setFileId(imageFile.id);
        attachment.setFileType("image");
        attachment.setPurpose("tongue_image");

        AgentRunRequest.AgentMessage message = new AgentRunRequest.AgentMessage();
        String userMessageId = "analysis_user_" + task.id;
        String assistantMessageId = "analysis_assistant_" + task.id;
        String turnId = String.valueOf(conversation.id) + ":" + userMessageId + ":" + assistantMessageId;
        message.setMessageId(userMessageId);
        message.setRole("user");
        message.setContentType("mixed");
        if (StringUtils.hasText(normalizedUserDescription)) {
            message.setContent("我想做一次舌象分析。\n用户补充描述：" + normalizedUserDescription);
        } else {
            message.setContent("我想做一次舌象分析");
        }
        message.setAttachments(Collections.singletonList(attachment));

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        if (StringUtils.hasText(imageFile.publicUrl)) {
            extra.put("image_url", imageFile.publicUrl);
            extra.put("tongue_image_url", imageFile.publicUrl);
        }
        extra.put("image_path", imageFile.storagePath);
        extra.put("tongue_image_path", imageFile.storagePath);
        extra.put("file_id", imageFile.id);
        extra.put("source", "tongue-server-storage");
        extra.put("context_bundle", contextBundle);
        if (StringUtils.hasText(normalizedUserDescription)) {
            extra.put("user_description", normalizedUserDescription);
            extra.put("symptom_description", normalizedUserDescription);
        }

        AgentRunRequest.AgentClientContext clientContext = new AgentRunRequest.AgentClientContext();
        clientContext.setPage("tongue_analyze");
        clientContext.setActiveReportId(report.id);
        clientContext.setDeviceType("web");
        clientContext.setLocale("zh-CN");
        clientContext.setExtra(extra);

        Map<String, Object> memory = new LinkedHashMap<String, Object>();
        memory.put("can_read", false);
        memory.put("can_write", false);
        Map<String, Object> contextOptions = new LinkedHashMap<String, Object>();
        contextOptions.put("mode", "stateless");
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("memory", memory);
        options.put("context", contextOptions);

        AgentRunRequest request = new AgentRunRequest();
        request.setSchemaVersion("1.0");
        request.setRequestId(UUID.randomUUID().toString());
        request.setTraceId(task.traceId);
        request.setTenantId(String.valueOf(task.userId));
        request.setUserId(task.userId);
        request.setThreadId(conversation.threadId);
        request.setThreadEpoch(conversation.threadEpoch == null ? 1 : conversation.threadEpoch);
        request.setTurnId(turnId);
        request.setUserMessageId(userMessageId);
        request.setAssistantMessageId(assistantMessageId);
        request.setConversationId(String.valueOf(conversation.id));
        request.setReportId(report.id);
        request.setTaskId(task.id);
        request.setTaskVersion(1);
        request.setMessage(message);
        request.setClientContext(clientContext);
        request.setContextBundle(contextBundle);
        request.setOptions(options);
        return request;
    }

    private void ackAgentTurn(
            AgentRunResponse agentResponse,
            String assistantMessageId
    ) {
        if (!StringUtils.hasText(agentResponse.getTenantId())
                || !StringUtils.hasText(agentResponse.getTurnId())
                || !StringUtils.hasText(agentResponse.getResponseHash())
                || !StringUtils.hasText(assistantMessageId)) {
            return;
        }
        AgentTurnAckRequest ackRequest = new AgentTurnAckRequest();
        ackRequest.setTenantId(agentResponse.getTenantId());
        ackRequest.setTurnId(agentResponse.getTurnId());
        ackRequest.setAssistantMessageId(assistantMessageId);
        ackRequest.setResponseHash(agentResponse.getResponseHash());
        try {
            tongueAgentClient.ackTurn(ackRequest);
        } catch (RuntimeException ignored) {
            // Python retains the encrypted snapshot until ACK/reconciliation succeeds.
        }
    }

    private void saveAgentReport(
            TongueAnalysisTaskEntity task,
            TongueReportEntity report,
            AgentRunResponse agentResponse
    ) throws Exception {
        if (!"COMPLETED".equals(agentResponse.getStatus())) {
            throw new BusinessException(
                    ErrorCode.AGENT_CALL_FAILED,
                    "Agent 状态不是 COMPLETED：" + agentResponse.getStatus(),
                    task.traceId
            );
        }
        Map<String, Object> message = safeMap(agentResponse.getMessage());
        Map<String, Object> nextAction = safeMap(agentResponse.getNextAction());
        Map<String, Object> payload = safeMap(nextAction.get("payload"));
        Map<String, Object> stateSnapshot = safeMap(agentResponse.getStateSnapshot());
        Map<String, Object> tongueAnalysis = safeMap(stateSnapshot.get("tongue_analysis"));
        Map<String, Object> draftReport = safeMap(payload.get("draft_report"));

        Object detectedCodes = firstNonNull(
                payload.get("detected_feature_codes"),
                tongueAnalysis.get("detected_feature_codes")
        );
        Object standardFeatures = draftReport.get("standard_features");
        Object ragEvidence = draftReport.get("rag_evidence");

        String draftSummary = toStringValue(draftReport.get("summary"));
        report.summary = StringUtils.hasText(draftSummary)
                ? draftSummary
                : toStringValue(message.get("content"));
        report.featureSummary = toStringValue(draftReport.get("feature_summary"));
        report.detectedFeatureCodes = writeJson(detectedCodes);
        report.standardFeaturesJson = writeJson(standardFeatures);
        report.ragQuery = toStringValue(firstNonNull(payload.get("rag_query"), draftReport.get("rag_query")));
        report.ragGrounded = Boolean.valueOf(String.valueOf(draftReport.get("rag_grounded")));
        report.ragEvidenceJson = writeJson(ragEvidence);
        report.draftReportJson = writeJson(draftReport);
        report.riskDisclaimer = toStringValue(draftReport.get("risk_disclaimer"));
        report.reportStatus = toStringValue(draftReport.get("report_status"));
        if (!StringUtils.hasText(report.reportStatus) || "DRAFT".equals(report.reportStatus)) {
            report.reportStatus = "COMPLETED";
        }
        reportRepository.save(report);

        saveReportVersion(report, draftReport);
        saveFeatures(report, detectedCodes, standardFeatures);
        saveEvidence(report, ragEvidence);
    }

    private void saveReportVersion(TongueReportEntity report, Object draftReport) throws Exception {
        TongueReportVersionEntity version = new TongueReportVersionEntity();
        version.reportId = report.id;
        version.versionNo = Long.valueOf(versionRepository.countByReportId(report.id) + 1L).intValue();
        version.sourceType = "AI";
        version.summary = report.summary;
        version.reportJson = writeJson(draftReport);
        versionRepository.save(version);
    }

    private void saveFeatures(
            TongueReportEntity report,
            Object detectedCodes,
            Object standardFeatures
    ) {
        if (!(detectedCodes instanceof List)) {
            return;
        }
        Map<String, Double> confidenceByCode = featureConfidenceByCode(standardFeatures);
        for (Object codeValue : (List<?>) detectedCodes) {
            if (codeValue == null) {
                continue;
            }
            String code = String.valueOf(codeValue);
            TongueReportFeatureEntity feature = new TongueReportFeatureEntity();
            feature.reportId = report.id;
            feature.userId = report.userId;
            feature.featureCode = code;
            feature.featureGroup = code.contains(".")
                    ? code.substring(0, code.indexOf('.'))
                    : "unknown";
            feature.confidence = confidenceByCode.get(code);
            featureRepository.save(feature);
        }
    }

    private void saveEvidence(TongueReportEntity report, Object ragEvidence) {
        if (!(ragEvidence instanceof List)) {
            return;
        }
        for (Object item : (List<?>) ragEvidence) {
            Map<String, Object> evidenceMap = safeMap(item);
            if (evidenceMap.isEmpty()) {
                continue;
            }
            TongueReportEvidenceEntity evidence = new TongueReportEvidenceEntity();
            evidence.reportId = report.id;
            evidence.chunkId = toStringValue(evidenceMap.get("chunk_id"));
            evidence.docId = toStringValue(evidenceMap.get("doc_id"));
            evidence.title = toStringValue(evidenceMap.get("title"));
            evidence.content = toStringValue(evidenceMap.get("content"));
            evidence.sourceUri = toStringValue(evidenceMap.get("source_uri"));
            Object score = evidenceMap.get("final_score");
            evidence.finalScore = score == null ? null : Double.valueOf(String.valueOf(score));
            evidenceRepository.save(evidence);
        }
    }

    private ReportListItemResponse toReportListItem(TongueReportEntity report) {
        ReportListItemResponse item = new ReportListItemResponse();
        item.reportId = report.id;
        item.status = report.reportStatus;
        item.featureSummary = report.featureSummary;
        QualityResult quality = calculateQuality(report, toStructuredReport(report));
        item.analysisQualityScore = quality.score;
        item.analysisQualityLevel = quality.level;
        item.qualityVersion = QUALITY_VERSION;
        item.analysisQualityVersion = QUALITY_VERSION;
        item.createdAt = report.createdAt;
        item.updatedAt = report.updatedAt;
        return item;
    }

    private Map<String, Object> buildTrendStatus(int reportCount) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("report_count", reportCount);
        if (reportCount <= 0) {
            status.put("status", "EMPTY");
            status.put("message", "Complete the first report to start trend tracking.");
        } else if (reportCount == 1) {
            status.put("status", "COLLECTING");
            status.put("message", "At least two reports are needed for comparison.");
        } else {
            status.put("status", "READY");
            status.put("message", "Trend comparison is ready.");
        }
        return status;
    }
    private List<DashboardResponse.DashboardTodoResponse> buildDashboardTodos(
            Long userId,
            int reportCount,
            long unreadNotificationCount
    ) {
        List<DashboardResponse.DashboardTodoResponse> todos = new ArrayList<DashboardResponse.DashboardTodoResponse>();
        for (TongueAnalysisTaskEntity task : taskRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if ("FAILED".equals(task.status)) {
                todos.add(todo("RETRY_ANALYSIS", "分析失败", "可以重试失败的分析任务。", "retry", task.reportId, task.id, null));
                break;
            }
        }
        for (DoctorReviewOrderEntity order : reviewOrderRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if ("SUBMITTED".equals(order.status) || "IN_REVIEW".equals(order.status)) {
                todos.add(todo("REVIEW_PENDING", "医生复核进行中", "查看报告复核进度。", "open_reviews", order.reportId, null, order.id));
                break;
            }
        }
        if (reportCount < 2) {
            todos.add(todo("BUILD_TREND", "继续积累趋势", "再完成一次分析后可对比变化。", "start_analysis", null, null, null));
        }
        if (unreadNotificationCount > 0) {
            todos.add(todo("READ_NOTIFICATIONS", "未读通知", "查看新的分析或复核提醒。", "open_notifications", null, null, null));
        }
        return todos;
    }
    private DashboardResponse.DashboardTodoResponse todo(
            String type,
            String title,
            String content,
            String action,
            Long reportId,
            Long taskId,
            Long reviewId
    ) {
        DashboardResponse.DashboardTodoResponse todo = new DashboardResponse.DashboardTodoResponse();
        todo.type = type;
        todo.title = title;
        todo.content = content;
        todo.action = action;
        todo.reportId = reportId;
        todo.taskId = taskId;
        todo.reviewId = reviewId;
        return todo;
    }

    private void applyCompareExplanation(ReportCompareResponse response) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("base_report_id", response.baseReportId);
        payload.put("target_report_id", response.targetReportId);
        payload.put("added", diffPayload(response.added));
        payload.put("removed", diffPayload(response.removed));
        payload.put("persistent", diffPayload(response.persistent));
        payload.put("changed", diffPayload(response.changed));
        payload.put("unsupported", diffPayload(response.unsupported));
        try {
            Map<String, Object> result = tongueAgentClient.explainReportCompare(payload);
            response.agentStatus = StringUtils.hasText(toStringValue(result.get("status")))
                    ? toStringValue(result.get("status"))
                    : "COMPLETED";
            response.explanation = firstText(result.get("explanation"));
            response.observationSuggestions = stringList(result.get("observation_suggestions"));
        } catch (RuntimeException ex) {
            response.agentStatus = "FAILED";
        }
        if (!StringUtils.hasText(response.explanation)) {
            response.explanation = fallbackCompareExplanation(response);
        }
        if (response.observationSuggestions.isEmpty()) {
            response.observationSuggestions.add("后续复拍尽量保持相似的光线、角度和时间。");
            response.observationSuggestions.add("如果变化持续并伴随明显不适，请咨询专业医生。");
        }
    }
    private List<Map<String, Object>> diffPayload(List<ReportCompareResponse.FeatureDiffItem> items) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ReportCompareResponse.FeatureDiffItem item : items) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("feature_code", item.featureCode);
            map.put("base_confidence", item.baseConfidence);
            map.put("target_confidence", item.targetConfidence);
            map.put("change_type", item.changeType);
            result.add(map);
        }
        return result;
    }

    private String fallbackCompareExplanation(ReportCompareResponse response) {
        return "本次对比发现新增 " + response.added.size()
                + " 项、消失 " + response.removed.size()
                + " 项、持续 " + response.persistent.size()
                + " 项、置信度变化 " + response.changed.size()
                + " 项。结果用于日常健康观察，不作为诊断结论。";
    }
    private ReportCompareResponse.FeatureDiffItem diffItem(
            String code,
            Double baseConfidence,
            Double targetConfidence,
            String changeType
    ) {
        ReportCompareResponse.FeatureDiffItem item = new ReportCompareResponse.FeatureDiffItem();
        item.featureCode = code;
        item.baseConfidence = baseConfidence;
        item.targetConfidence = targetConfidence;
        item.changeType = changeType;
        return item;
    }

    private FeatureSnapshot buildFeatureSnapshot(TongueReportEntity report) {
        FeatureSnapshot snapshot = new FeatureSnapshot();
        Object standardFeatures = parseJson(report.standardFeaturesJson);
        Map<String, Object> standardMap = safeMap(standardFeatures);
        addStringValues(snapshot.detectedCodes, firstNonNull(
                parseJson(report.detectedFeatureCodes),
                standardMap.get("detected_feature_codes")
        ));
        addStringValues(snapshot.supportedCodes, standardMap.get("supported_feature_codes"));
        addStringValues(snapshot.unsupportedCodes, standardMap.get("unsupported_feature_codes"));
        snapshot.confidenceByCode.putAll(featureConfidenceByCode(standardFeatures));
        for (TongueReportFeatureEntity feature : featureRepository.findByReportId(report.id)) {
            snapshot.detectedCodes.add(feature.featureCode);
            if (feature.confidence != null) {
                snapshot.confidenceByCode.put(feature.featureCode, feature.confidence);
            }
        }
        if (snapshot.supportedCodes.isEmpty()) {
            snapshot.supportedCodes.addAll(snapshot.detectedCodes);
        }
        return snapshot;
    }

    private Map<String, Object> toStructuredReport(TongueReportEntity report) {
        Map<String, Object> draft = safeMap(parseJson(report.draftReportJson));
        Map<String, Object> structured = safeMap(draft.get("structured_report"));
        Map<String, Object> metadata = safeMap(draft.get("metadata"));
        Map<String, Object> answer = safeMap(metadata.get("structured_answer"));
        Map<String, Object> sections = safeMap(metadata.get("structured_sections"));

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schema_version", firstText(
                structured.get("schema_version"),
                answer.get("schema_version"),
                draft.get("schema_version"),
                "1.0"
        ));
        result.put("comprehensive_summary", firstText(
                structured.get("comprehensive_summary"),
                answer.get("comprehensive_summary"),
                answer.get("summary"),
                draft.get("comprehensive_summary"),
                draft.get("result_summary"),
                draft.get("summary"),
                report.summary,
                report.featureSummary
        ));
        result.put("tongue_features", firstListObject(
                structured.get("tongue_features"),
                answer.get("tongue_features"),
                draft.get("tongue_features"),
                standardFeatureItems(report)
        ));
        result.put("health_interpretation", firstText(
                structured.get("health_interpretation"),
                answer.get("health_interpretation"),
                structured.get("tongue_feature_explanation"),
                answer.get("tongue_feature_explanation"),
                draft.get("health_interpretation"),
                draft.get("interpretation"),
                sections.get("general_interpretation"),
                report.summary
        ));
        result.put("tongue_feature_explanation", firstText(
                structured.get("tongue_feature_explanation"),
                answer.get("tongue_feature_explanation"),
                draft.get("tongue_feature_explanation"),
                result.get("health_interpretation")
        ));
        result.put("recognition_evidence", firstListObject(
                structured.get("recognition_evidence"),
                answer.get("recognition_evidence"),
                draft.get("recognition_evidence")
        ));
        result.put("recognition_limits", firstListObject(
                structured.get("recognition_limits"),
                answer.get("recognition_limits"),
                draft.get("recognition_limits")
        ));
        result.put("dimension_values", firstListObject(
                structured.get("dimension_values"),
                answer.get("dimension_values"),
                draft.get("dimension_values")
        ));
        result.put("conditional_analysis", firstListObject(
                structured.get("conditional_analysis"),
                answer.get("conditional_analysis"),
                draft.get("conditional_analysis")
        ));
        result.put("diet_plan", safeMap(firstNonNull(
                structured.get("diet_plan"),
                firstNonNull(answer.get("diet_plan"), draft.get("diet_plan"))
        )));
        result.put("sleep_plan", safeMap(firstNonNull(
                structured.get("sleep_plan"),
                firstNonNull(answer.get("sleep_plan"), draft.get("sleep_plan"))
        )));
        result.put("exercise_plan", safeMap(firstNonNull(
                structured.get("exercise_plan"),
                firstNonNull(answer.get("exercise_plan"), draft.get("exercise_plan"))
        )));
        result.put("three_day_observation", firstStringList(
                structured.get("three_day_observation"),
                answer.get("three_day_observation"),
                draft.get("three_day_observation"),
                answer.get("observation_points")
        ));
        result.put("followup_questions", firstStringList(
                structured.get("followup_questions"),
                answer.get("followup_questions"),
                draft.get("followup_questions")
        ));
        result.put("dietary_advice", firstStringList(
                structured.get("dietary_advice"),
                answer.get("dietary_advice"),
                answer.get("dietaryAdvice")
        ));
        result.put("exercise_advice", firstStringList(
                structured.get("exercise_advice"),
                answer.get("exercise_advice"),
                answer.get("exerciseAdvice")
        ));
        result.put("lifestyle_advice", firstStringList(
                structured.get("lifestyle_advice"),
                answer.get("lifestyle_advice"),
                answer.get("lifestyleAdvice")
        ));
        result.put("risk_tips", firstStringList(
                structured.get("risk_tips"),
                answer.get("risk_tips"),
                draft.get("risk_tips"),
                draft.get("risk_reminder"),
                report.riskDisclaimer
        ));
        result.put("evidence_refs", evidenceRefs(report, firstNonNull(
                structured.get("evidence_refs"),
                firstNonNull(answer.get("evidence_refs"), firstNonNull(draft.get("evidence_refs"), draft.get("rag_evidence")))
        )));
        return result;
    }

    private List<Object> standardFeatureItems(TongueReportEntity report) {
        List<Object> result = new ArrayList<Object>();
        collectFeatureItems(parseJson(report.standardFeaturesJson), result, new HashSet<String>());
        if (!result.isEmpty()) {
            return result;
        }
        for (TongueReportFeatureEntity feature : featureRepository.findByReportId(report.id)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("code", feature.featureCode);
            item.put("status", "DETECTED");
            item.put("confidence", feature.confidence);
            result.add(item);
        }
        return result;
    }

    private void collectFeatureItems(Object value, List<Object> result, Set<String> seen) {
        if (value instanceof Map) {
            Map<String, Object> map = safeMap(value);
            String code = firstText(map.get("code"));
            if (StringUtils.hasText(code) && !seen.contains(code)) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("code", code);
                item.put("name", firstText(map.get("name")));
                item.put("status", firstText(map.get("status"), "DETECTED"));
                item.put("confidence", asDouble(map.get("confidence")));
                result.add(item);
                seen.add(code);
            }
            for (Object child : map.values()) {
                collectFeatureItems(child, result, seen);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collectFeatureItems(child, result, seen);
            }
        }
    }

    private List<Object> evidenceRefs(TongueReportEntity report, Object rawRefs) {
        List<TongueReportEvidenceEntity> rows = evidenceRepository.findByReportId(report.id);
        Map<String, Long> idByKey = new LinkedHashMap<String, Long>();
        for (TongueReportEvidenceEntity row : rows) {
            idByKey.put(evidenceKey(row.docId, row.chunkId), row.id);
        }

        List<Object> refs = firstListObject(rawRefs);
        if (refs.isEmpty()) {
            for (TongueReportEvidenceEntity row : rows) {
                Map<String, Object> ref = new LinkedHashMap<String, Object>();
                ref.put("doc_id", row.docId);
                ref.put("chunk_id", row.chunkId);
                ref.put("title", row.title);
                ref.put("final_score", row.finalScore);
                ref.put("evidence_id", row.id);
                refs.add(ref);
            }
            return refs;
        }

        List<Object> mapped = new ArrayList<Object>();
        for (Object raw : refs) {
            Map<String, Object> ref = new LinkedHashMap<String, Object>(safeMap(raw));
            String docId = firstText(ref.get("doc_id"), ref.get("docId"));
            String chunkId = firstText(ref.get("chunk_id"), ref.get("chunkId"));
            Long evidenceId = idByKey.get(evidenceKey(docId, chunkId));
            if (evidenceId != null) {
                ref.put("evidence_id", evidenceId);
            }
            mapped.add(ref);
        }
        return mapped;
    }

    private String evidenceKey(String docId, String chunkId) {
        return String.valueOf(docId) + "::" + String.valueOf(chunkId);
    }

    private QualityResult calculateQuality(TongueReportEntity report, Object structuredReport) {
        QualityResult result = new QualityResult();
        addMetric(result, "image", "图片/文件有效性", 0.20, report.imageFileId == null ? null : 1.0);
        addMetric(result, "model_confidence", "模型置信度", 0.35, averageConfidence(report));
        addMetric(result, "report_completeness", "报告完整度", 0.30, structuredCompleteness(safeMap(structuredReport)));
        addMetric(result, "evidence_coverage", "证据覆盖率", 0.15, evidenceCoverage(report));
        double score = 0.0;
        double weight = 0.0;
        for (Object value : result.metrics.values()) {
            Map<String, Object> metric = safeMap(value);
            if (Boolean.TRUE.equals(metric.get("included"))) {
                Double metricScore = asDouble(metric.get("score"));
                Double metricWeight = asDouble(metric.get("weight"));
                if (metricScore != null && metricWeight != null) {
                    score += metricScore * metricWeight;
                    weight += metricWeight;
                }
            }
        }
        if (weight <= 0.0) {
            result.level = "UNKNOWN";
            return result;
        }
        result.score = Math.round((score / weight) * 1000.0) / 10.0;
        if (result.score >= 85.0) {
            result.level = "HIGH";
        } else if (result.score >= 70.0) {
            result.level = "MEDIUM";
        } else {
            result.level = "LOW";
        }
        return result;
    }

    private void addMetric(QualityResult result, String key, String label, double weight, Double score) {
        Map<String, Object> metric = new LinkedHashMap<String, Object>();
        metric.put("label", label);
        metric.put("weight", weight);
        metric.put("score", score == null ? null : clamp01(score));
        metric.put("included", score != null);
        result.metrics.put(key, metric);
    }

    private Double averageConfidence(TongueReportEntity report) {
        List<Double> values = new ArrayList<Double>();
        for (TongueReportFeatureEntity feature : featureRepository.findByReportId(report.id)) {
            if (feature.confidence != null) {
                values.add(clamp01(feature.confidence));
            }
        }
        if (values.isEmpty()) {
            values.addAll(featureConfidenceByCode(parseJson(report.standardFeaturesJson)).values());
        }
        if (values.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (Double value : values) {
            total += clamp01(value);
        }
        return total / values.size();
    }

    private Double structuredCompleteness(Map<String, Object> structuredReport) {
        int total = 5;
        int present = 0;
        if (StringUtils.hasText(firstText(structuredReport.get("comprehensive_summary")))) {
            present++;
        }
        if (!firstListObject(structuredReport.get("tongue_features")).isEmpty()) {
            present++;
        }
        if (StringUtils.hasText(firstText(structuredReport.get("health_interpretation")))) {
            present++;
        }
        if (!firstStringList(structuredReport.get("dietary_advice"), structuredReport.get("exercise_advice"), structuredReport.get("lifestyle_advice")).isEmpty()) {
            present++;
        }
        if (!firstStringList(structuredReport.get("risk_tips")).isEmpty()) {
            present++;
        }
        return present == 0 ? null : Double.valueOf(present) / total;
    }

    private Double evidenceCoverage(TongueReportEntity report) {
        int count = evidenceRepository.findByReportId(report.id).size();
        boolean hasEvidenceSignal = count > 0 || StringUtils.hasText(report.ragEvidenceJson);
        if (!hasEvidenceSignal) {
            return null;
        }
        if (count == 0) {
            List<Object> parsed = firstListObject(parseJson(report.ragEvidenceJson));
            count = parsed.size();
        }
        return Math.min(1.0, count / 3.0);
    }

    private Map<String, Double> featureConfidenceByCode(Object standardFeatures) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        collectFeatureConfidence(standardFeatures, result);
        return result;
    }

    private void collectFeatureConfidence(Object value, Map<String, Double> result) {
        if (value instanceof Map) {
            Map<String, Object> map = safeMap(value);
            String code = firstText(map.get("code"));
            Double confidence = asDouble(map.get("confidence"));
            if (StringUtils.hasText(code) && confidence != null) {
                result.put(code, clamp01(confidence));
            }
            for (Object child : map.values()) {
                collectFeatureConfidence(child, result);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collectFeatureConfidence(child, result);
            }
        }
    }

    private void addStringValues(Set<String> target, Object value) {
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    target.add(String.valueOf(item));
                }
            }
        }
    }

    private List<String> firstStringList(Object... values) {
        for (Object value : values) {
            List<String> result = stringList(value);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return new ArrayList<String>();
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = firstText(item);
                if (StringUtils.hasText(text)) {
                    result.add(text);
                }
            }
        } else {
            String text = firstText(value);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private List<Object> firstListObject(Object... values) {
        for (Object value : values) {
            if (value instanceof List && !((List<?>) value).isEmpty()) {
                return new ArrayList<Object>((List<?>) value);
            }
        }
        return new ArrayList<Object>();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String && StringUtils.hasText((String) value)) {
                return ((String) value).trim();
            }
            if (value instanceof Map) {
                Map<String, Object> map = safeMap(value);
                for (String key : new String[]{"content", "text", "summary", "title"}) {
                    Object nested = map.get(key);
                    if (nested instanceof String && StringUtils.hasText((String) nested)) {
                        return ((String) nested).trim();
                    }
                }
            }
        }
        return "";
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double clamp01(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static class QualityResult {
        public Double score;
        public String level = "UNKNOWN";
        public Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    }

    private static class FeatureSnapshot {
        public Set<String> detectedCodes = new LinkedHashSet<String>();
        public Set<String> supportedCodes = new LinkedHashSet<String>();
        public Set<String> unsupportedCodes = new LinkedHashSet<String>();
        public Map<String, Double> confidenceByCode = new LinkedHashMap<String, Double>();
    }

    private TongueReportEntity loadReportForUser(Long reportId, Long userId) {
        if ("ADMIN".equals(AuthContext.get().role)) {
            return reportRepository.findById(reportId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "报告不存在",
                            null
                    ));
        }
        return reportRepository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "报告不存在",
                        null
                ));
    }

    private ReportDetailResponse toReportDetail(TongueReportEntity report) {
        ReportDetailResponse response = new ReportDetailResponse();
        response.reportId = report.id;
        response.taskId = report.taskId;
        response.imageFileId = report.imageFileId;
        response.threadId = report.threadId;
        response.reportStatus = report.reportStatus;
        response.summary = report.summary;
        response.featureSummary = report.featureSummary;
        response.detectedFeatureCodes = parseJson(report.detectedFeatureCodes);
        response.standardFeatures = parseJson(report.standardFeaturesJson);
        response.ragQuery = report.ragQuery;
        response.ragGrounded = report.ragGrounded;
        response.ragEvidence = parseJson(report.ragEvidenceJson);
        response.draftReport = parseJson(report.draftReportJson);
        response.riskDisclaimer = report.riskDisclaimer;
        response.structuredReport = toStructuredReport(report);
        QualityResult quality = calculateQuality(report, response.structuredReport);
        response.analysisQualityScore = quality.score;
        response.analysisQualityLevel = quality.level;
        response.qualityVersion = QUALITY_VERSION;
        response.analysisQualityVersion = QUALITY_VERSION;
        response.qualityMetrics = quality.metrics;
        return response;
    }

    private TaskStatusResponse toTaskStatus(TongueAnalysisTaskEntity task) {
        TaskStatusResponse response = new TaskStatusResponse();
        response.taskId = task.id;
        response.reportId = task.reportId;
        response.status = task.status;
        response.progress = task.progress;
        response.currentStage = task.currentStage;
        response.errorCode = task.errorCode;
        response.errorMessage = task.errorMessage;
        return response;
    }

    private void updateTask(
            TongueAnalysisTaskEntity task,
            String status,
            String stage,
            Double progress,
            String errorCode,
            String errorMessage
    ) {
        task.status = status;
        task.currentStage = stage;
        task.progress = progress;
        task.errorCode = errorCode;
        task.errorMessage = errorMessage;
        if ("RUNNING".equals(status) && task.startedAt == null) {
            task.startedAt = LocalDateTime.now();
        }
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status)) {
            task.finishedAt = LocalDateTime.now();
        }
        taskRepository.save(task);
    }

    private String writeJson(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {
            });
        } catch (Exception ex) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}


