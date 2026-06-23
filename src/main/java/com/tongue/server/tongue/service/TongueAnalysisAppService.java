package com.tongue.server.tongue.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.storage.StorageResult;
import com.tongue.server.storage.entity.FileObjectEntity;
import com.tongue.server.storage.repository.FileObjectRepository;
import com.tongue.server.storage.service.StorageService;
import com.tongue.server.tongue.dto.EvidenceResponse;
import com.tongue.server.tongue.dto.FeatureResponse;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class TongueAnalysisAppService {

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
            NotificationService notificationService
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

        TongueReportEntity report = new TongueReportEntity();
        report.userId = userId;
        report.reportStatus = "DRAFT";
        report.sourceType = "AI";
        report.threadId = StringUtils.hasText(requestThreadId)
                ? requestThreadId.trim()
                : "tongue_analysis_" + userId + "_" + UUID.randomUUID();
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

        submitTaskAfterCommit(task.id, conversationId, normalizedUserDescription);

        TongueAnalyzeCreateResponse response = new TongueAnalyzeCreateResponse();
        response.reportId = report.id;
        response.taskId = task.id;
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
        submitTaskAfterCommit(task.id, null, null);
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
            ReportListItemResponse item = new ReportListItemResponse();
            item.reportId = report.id;
            item.status = report.reportStatus;
            item.featureSummary = report.featureSummary;
            item.createdAt = report.createdAt;
            item.updatedAt = report.updatedAt;
            result.add(item);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse reportDetail(Long reportId) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = loadReportForUser(reportId, userId);
        return toReportDetail(report);
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

    private AgentRunRequest buildAgentRequest(
            TongueAnalysisTaskEntity task,
            TongueReportEntity report,
            FileObjectEntity imageFile,
            String conversationId,
            String userDescription
    ) {
        String normalizedUserDescription = normalizeUserDescription(userDescription);

        AgentRunRequest.AgentAttachment attachment = new AgentRunRequest.AgentAttachment();
        attachment.setFileId(imageFile.id);
        attachment.setFileType("image");
        attachment.setPurpose("tongue_image");

        AgentRunRequest.AgentMessage message = new AgentRunRequest.AgentMessage();
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
        memory.put("can_read", true);
        memory.put("can_write", false);
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("memory", memory);

        AgentRunRequest request = new AgentRunRequest();
        request.setSchemaVersion("1.0");
        request.setRequestId(UUID.randomUUID().toString());
        request.setTraceId(task.traceId);
        request.setUserId(task.userId);
        request.setThreadId(report.threadId);
        request.setConversationId(conversationId);
        request.setReportId(report.id);
        request.setTaskId(task.id);
        request.setTaskVersion(1);
        request.setMessage(message);
        request.setClientContext(clientContext);
        request.setOptions(options);
        return request;
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

        report.summary = toStringValue(message.get("content"));
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
            feature.confidence = null;
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
