package com.tongue.server.service;

import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.TongueAnalyzeResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TongueAnalyzeService {

    private final LocalFileStorageService fileStorageService;
    private final LocalReportStorageService reportStorageService;
    private final TongueAgentClient tongueAgentClient;
    private final AtomicLong reportIdGenerator = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong taskIdGenerator = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<String, ReentrantLock> threadLocks =
            new ConcurrentHashMap<String, ReentrantLock>();

    public TongueAnalyzeService(
            LocalFileStorageService fileStorageService,
            LocalReportStorageService reportStorageService,
            TongueAgentClient tongueAgentClient
    ) {
        this.fileStorageService = fileStorageService;
        this.reportStorageService = reportStorageService;
        this.tongueAgentClient = tongueAgentClient;
    }

    public TongueAnalyzeResponse analyze(TongueAnalyzeCommand command) {
        Long reportId = reportIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        String traceId = resolveTraceId(command.getClientTraceId());
        String threadId = resolveThreadId(command.getThreadId(), command.getUserId(), reportId);

        ReentrantLock lock = threadLocks.computeIfAbsent(
                threadId,
                key -> new ReentrantLock()
        );
        if (!lock.tryLock()) {
            throw new BusinessException(
                    ErrorCode.AGENT_THREAD_BUSY,
                    "同一会话正在处理中，请稍后再试",
                    traceId
            );
        }

        try {
            StoredImageFile storedImage = fileStorageService.storeTongueImage(
                    command.getUserId(),
                    reportId,
                    command.getImage(),
                    traceId
            );
            AgentRunRequest agentRequest = buildAgentRequest(
                    command,
                    storedImage,
                    reportId,
                    taskId,
                    traceId,
                    threadId
            );
            AgentRunResponse agentResponse = tongueAgentClient.runAgent(agentRequest);
            ensureAgentCompleted(agentResponse, traceId);

            TongueAnalyzeResponse response = buildAnalyzeResponse(
                    agentResponse,
                    reportId,
                    taskId,
                    threadId
            );
            reportStorageService.saveAgentResult(
                    command.getUserId(),
                    reportId,
                    response,
                    agentResponse,
                    traceId
            );
            return response;
        } finally {
            lock.unlock();
        }
    }

    private AgentRunRequest buildAgentRequest(
            TongueAnalyzeCommand command,
            StoredImageFile storedImage,
            Long reportId,
            Long taskId,
            String traceId,
            String threadId
    ) {
        AgentRunRequest.AgentAttachment attachment = new AgentRunRequest.AgentAttachment();
        attachment.setFileId(storedImage.getFileId());
        attachment.setFileType("image");
        attachment.setPurpose("tongue_image");

        AgentRunRequest.AgentMessage message = new AgentRunRequest.AgentMessage();
        String userMessageId = "legacy_analysis_user_" + taskId;
        String assistantMessageId = "legacy_analysis_assistant_" + taskId;
        String turnId = (emptyToNull(command.getConversationId()) != null
                ? emptyToNull(command.getConversationId())
                : threadId) + ":" + userMessageId + ":" + assistantMessageId;
        message.setMessageId(userMessageId);
        message.setRole("user");
        message.setContentType("mixed");
        message.setContent("我想做一次舌象分析");
        message.setAttachments(Collections.singletonList(attachment));

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("image_path", storedImage.getStoragePath());
        extra.put("tongue_image_path", storedImage.getStoragePath());
        extra.put("file_id", storedImage.getFileId());
        extra.put("original_filename", storedImage.getOriginalFilename());
        extra.put("content_type", storedImage.getContentType());
        extra.put("source", "tongue-server-upload");

        AgentRunRequest.AgentClientContext clientContext =
                new AgentRunRequest.AgentClientContext();
        clientContext.setPage("tongue_analyze");
        clientContext.setActiveReportId(reportId);
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
        request.setTraceId(traceId);
        request.setTenantId(String.valueOf(command.getUserId()));
        request.setUserId(command.getUserId());
        request.setThreadId(threadId);
        request.setThreadEpoch(1);
        request.setTurnId(turnId);
        request.setUserMessageId(userMessageId);
        request.setAssistantMessageId(assistantMessageId);
        request.setConversationId(emptyToNull(command.getConversationId()));
        request.setReportId(reportId);
        request.setTaskId(taskId);
        request.setTaskVersion(1);
        request.setMessage(message);
        request.setClientContext(clientContext);
        request.setOptions(options);
        return request;
    }

    private TongueAnalyzeResponse buildAnalyzeResponse(
            AgentRunResponse agentResponse,
            Long reportId,
            Long taskId,
            String threadId
    ) {
        Map<String, Object> message = safeMap(agentResponse.getMessage());
        Map<String, Object> nextAction = safeMap(agentResponse.getNextAction());
        Map<String, Object> payload = safeMap(nextAction.get("payload"));
        Map<String, Object> stateSnapshot = safeMap(agentResponse.getStateSnapshot());
        Map<String, Object> tongueAnalysis = safeMap(stateSnapshot.get("tongue_analysis"));

        TongueAnalyzeResponse response = new TongueAnalyzeResponse();
        response.setReportId(agentResponse.getReportId() != null
                ? agentResponse.getReportId()
                : reportId);
        response.setTaskId(agentResponse.getTaskId() != null
                ? agentResponse.getTaskId()
                : taskId);
        response.setThreadId(agentResponse.getThreadId() != null
                ? agentResponse.getThreadId()
                : threadId);
        response.setStatus(agentResponse.getStatus());
        response.setSummary(toStringValue(message.get("content")));
        response.setDetectedFeatureCodes(toStringList(
                firstNonNull(
                        payload.get("detected_feature_codes"),
                        tongueAnalysis.get("detected_feature_codes")
                )
        ));
        response.setRagQuery(toStringValue(
                firstNonNull(payload.get("rag_query"), tongueAnalysis.get("rag_query"))
        ));
        response.setDraftReport(payload.get("draft_report"));
        return response;
    }

    private void ensureAgentCompleted(AgentRunResponse agentResponse, String traceId) {
        if ("COMPLETED".equals(agentResponse.getStatus())) {
            return;
        }

        Map<String, Object> nextAction = safeMap(agentResponse.getNextAction());
        String actionType = toStringValue(nextAction.get("type"));
        int code = "TONGUE_MODEL_FAILED".equals(actionType)
                ? ErrorCode.MODEL_SERVICE_FAILED
                : ErrorCode.AGENT_CALL_FAILED;
        throw new BusinessException(
                code,
                "舌象分析流程未完成，Agent 状态：" + agentResponse.getStatus(),
                traceId
        );
    }

    private String resolveTraceId(String clientTraceId) {
        if (StringUtils.hasText(clientTraceId)) {
            return clientTraceId.trim();
        }
        return "trace_" + UUID.randomUUID().toString();
    }

    private String resolveThreadId(String requestThreadId, Long userId, Long reportId) {
        if (StringUtils.hasText(requestThreadId)) {
            return requestThreadId.trim();
        }
        return "tongue_analysis_" + userId + "_" + reportId;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<String>();
        }

        List<?> rawList = (List<?>) value;
        List<String> result = new ArrayList<String>();
        for (Object item : rawList) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
