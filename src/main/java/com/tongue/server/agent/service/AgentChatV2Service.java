package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import com.tongue.server.agent.context.entity.AgentChatTurnEntity;
import com.tongue.server.agent.dto.AgentChatV2Request;
import com.tongue.server.agent.dto.AgentChatV2Response;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agent.service.AgentChatTurnStore.BeginTurnResult;
import com.tongue.server.agent.service.AgentGatewayClientV2.AgentGatewayException;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.TongueReportVersionEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import com.tongue.server.tongue.repository.TongueReportVersionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentChatV2Service {

    private static final Set<String> ALLOWED_REPORT_SECTIONS = new HashSet<String>(Arrays.asList(
            "feature_summary", "interpretation", "dietary_advice", "exercise_advice",
            "lifestyle_advice", "risk_disclaimer", "rag_evidence_summary", "full_report"
    ));

    private final AgentChatTurnStore turnStore;
    private final AgentGatewayClientV2 gatewayClient;
    private final TongueReportRepository reportRepository;
    private final TongueReportVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public AgentChatV2Service(
            AgentChatTurnStore turnStore,
            AgentGatewayClientV2 gatewayClient,
            TongueReportRepository reportRepository,
            TongueReportVersionRepository versionRepository,
            ObjectMapper objectMapper
    ) {
        this.turnStore = turnStore;
        this.gatewayClient = gatewayClient;
        this.reportRepository = reportRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public AgentChatV2Response chat(long userId, AgentChatV2Request request) {
        validateRequest(request);
        String conversationId = normalizeConversationId(request);
        String bindingMode = normalizeBindingMode(request.getContextBinding());
        Long requestedReportId = "ACTIVE_REPORT".equals(bindingMode)
                ? request.getContextBinding().getReportId() : null;

        String turnId = "turn_" + UUID.randomUUID();
        String assistantMessageId = "msg_assistant_" + UUID.randomUUID();
        String traceId = "trace_" + UUID.randomUUID();
        String requestHash = requestHash(userId, request, conversationId, bindingMode, requestedReportId);

        BeginTurnResult begin = turnStore.begin(
                userId, request, conversationId, turnId, assistantMessageId,
                traceId, requestHash, bindingMode, requestedReportId
        );
        if (!begin.isNewTurn()) return begin.getReplayResponse();

        AgentChatTurnEntity turn = begin.getTurn();
        Long boundReportId = turn.getBoundReportId();
        try {
            JsonNode activeReportRef = boundReportId == null
                    ? null : turnStore.loadTrustedReportRef(userId, boundReportId);
            List<AgentChatMessageEntity> recentMessages = Collections.emptyList();

            AgentGatewayClientV2.Invocation invocation = new AgentGatewayClientV2.Invocation();
            invocation.setUserId(userId);
            invocation.setRequestId(request.getRequestId());
            invocation.setTraceId(traceId);
            invocation.setTurnId(turnId);
            invocation.setThreadId(request.getThreadId());
            invocation.setThreadEpoch(1);
            invocation.setConversationId(conversationId);
            invocation.setUserMessageId(request.getClientMessageId());
            invocation.setAssistantMessageId(assistantMessageId);
            invocation.setContent(request.getMessage().getContent());
            invocation.setContextBinding(bindingMode);
            invocation.setReportContextMode(bindingMode);
            invocation.setReportId(boundReportId);
            invocation.setActiveReportRef(activeReportRef);
            invocation.setRecentMessages(recentMessages);
            invocation.setContextMode("stateful");
            invocation.setMemoryCanRead(true);
            invocation.setMemoryCanWrite(true);
            invocation.setClientContext(request.getClientContext() == null
                    ? Collections.<String, Object>emptyMap() : request.getClientContext());

            JsonNode agentResponse = gatewayClient.run(invocation);
            validateAgentOwnership(agentResponse, request.getRequestId(), turnId);
            AgentChatV2Response response = mapResponse(
                    request, conversationId, assistantMessageId, traceId, agentResponse
            );

            Long responseReportId = response.getAssistantMessage().getReportRef() == null
                    ? null : response.getAssistantMessage().getReportRef().getReportId();
            if (responseReportId != null) turnStore.loadTrustedReportRef(userId, responseReportId);
            turnStore.complete(turn, assistantMessageId, response, responseReportId);
            return response;
        } catch (AgentChatConflictException ex) {
            turnStore.fail(turn, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (AgentGatewayException ex) {
            turnStore.fail(turn, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            turnStore.fail(turn, "AGENT_CHAT_FAILED", ex.getMessage());
            throw ex;
        }
    }

    public Map<String, Object> loadReportSections(Long reportId, Map<String, Object> request) {
        Long requestReportId = asLong(request.get("report_id"));
        Long userId = asLong(request.get("user_id"));
        if (reportId == null || requestReportId == null || !reportId.equals(requestReportId) || userId == null) {
            return statusBody("REPORT_ID_MISMATCH", reportId);
        }

        TongueReportEntity report = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId).orElse(null);
        if (report == null) return statusBody("NOT_FOUND", reportId);

        List<TongueReportVersionEntity> versions = versionRepository.findByReportIdOrderByVersionNoDesc(reportId);
        int currentVersion = versions.isEmpty() ? 1 : versions.get(0).versionNo;
        Integer requestedVersion = asInteger(request.get("report_version"));
        if (requestedVersion != null && requestedVersion.intValue() != currentVersion) {
            Map<String, Object> result = statusBody("VERSION_MISMATCH", reportId);
            result.put("report_version", currentVersion);
            return result;
        }

        List<String> requestedSections = asStringList(request.get("sections"));
        if (requestedSections.isEmpty()) return statusBody("SECTIONS_REQUIRED", reportId);
        for (String section : requestedSections) {
            if (!ALLOWED_REPORT_SECTIONS.contains(section)) return statusBody("INVALID_SECTION", reportId);
        }

        JsonNode draft = parseJson(report.draftReportJson);
        JsonNode evidence = parseJson(report.ragEvidenceJson);
        Map<String, Object> sections = new LinkedHashMap<String, Object>();
        for (String section : requestedSections) {
            Object value = sectionValue(section, report, draft, evidence);
            if (value != null) sections.put(section, value);
        }

        if (sections.isEmpty()) {
            Map<String, Object> result = statusBody("NO_REPORT_CONTENT", reportId);
            result.put("report_version", currentVersion);
            return result;
        }

        Map<String, Object> result = statusBody("OK", reportId);
        result.put("report_version", currentVersion);
        result.put("sections", sections);
        return result;
    }

    private Object sectionValue(String section, TongueReportEntity report, JsonNode draft, JsonNode evidence) {
        if ("feature_summary".equals(section)) return report.featureSummary;
        if ("interpretation".equals(section)) {
            return nodeOrFallback(firstNode(draft, "interpretation", "general_interpretation", "summary"), report.summary);
        }
        if ("dietary_advice".equals(section)) {
            return jsonValue(firstNode(draft, "dietary_advice", "dietaryAdvice", "diet_advice"));
        }
        if ("exercise_advice".equals(section)) {
            return jsonValue(firstNode(draft, "exercise_advice", "exerciseAdvice"));
        }
        if ("lifestyle_advice".equals(section)) {
            return jsonValue(firstNode(draft, "lifestyle_advice", "health_notes", "health_suggestions"));
        }
        if ("risk_disclaimer".equals(section)) return report.riskDisclaimer;
        if ("rag_evidence_summary".equals(section)) return jsonValue(evidence);
        if ("full_report".equals(section)) return buildDisplayReport(report, draft, evidence);
        return null;
    }

    private Map<String, Object> buildDisplayReport(
            TongueReportEntity report,
            JsonNode draft,
            JsonNode evidence
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        putIfPresent(result, "summary", report.summary);
        putIfPresent(result, "feature_summary", report.featureSummary);
        putIfPresent(result, "interpretation",
                nodeOrFallback(firstNode(draft, "interpretation", "general_interpretation", "summary"), report.summary));
        putIfPresent(result, "dietary_advice",
                jsonValue(firstNode(draft, "dietary_advice", "dietaryAdvice", "diet_advice")));
        putIfPresent(result, "exercise_advice",
                jsonValue(firstNode(draft, "exercise_advice", "exerciseAdvice")));
        putIfPresent(result, "lifestyle_advice",
                jsonValue(firstNode(draft, "lifestyle_advice", "health_notes", "health_suggestions")));
        putIfPresent(result, "risk_disclaimer", report.riskDisclaimer);
        putIfPresent(result, "rag_evidence_summary", jsonValue(evidence));
        if (draft != null && !draft.isNull()) {
            putIfPresent(result, "structured_report", jsonValue(draft));
        }
        return result;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) return;
        if (value instanceof String && ((String) value).trim().isEmpty()) return;
        target.put(key, value);
    }

    private JsonNode firstNode(JsonNode root, String... keys) {
        if (root == null || root.isNull()) return null;
        for (String key : keys) {
            JsonNode direct = root.get(key);
            if (direct != null && !direct.isNull()) return direct;
            JsonNode metadata = root.path("metadata").get(key);
            if (metadata != null && !metadata.isNull()) return metadata;
        }
        return null;
    }

    private Object nodeOrFallback(JsonNode node, Object fallback) {
        Object value = jsonValue(node);
        return value == null ? fallback : value;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return objectMapper.convertValue(node, Object.class);
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return objectMapper.readTree(raw); } catch (Exception ignored) { return null; }
    }

    private Map<String, Object> statusBody(String status, Long reportId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("status", status);
        result.put("report_id", reportId);
        return result;
    }

    private Long asLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (Object item : (List<?>) value) {
            if (item != null && !String.valueOf(item).trim().isEmpty()) result.add(String.valueOf(item).trim());
        }
        return result;
    }

    private void validateRequest(AgentChatV2Request request) {
        if (request.getMessage() == null || request.getMessage().getContent() == null
                || request.getMessage().getContent().trim().isEmpty()) {
            throw new AgentChatConflictException("INVALID_MESSAGE", "消息内容不能为空");
        }
        if (request.getMessage().getRole() != null && !"user".equalsIgnoreCase(request.getMessage().getRole())) {
            throw new AgentChatConflictException("INVALID_MESSAGE_ROLE", "客户端只能提交 user 消息");
        }
    }

    private String normalizeConversationId(AgentChatV2Request request) {
        String value = request.getConversationId();
        return value == null || value.trim().isEmpty() ? "conversation_" + request.getThreadId() : value.trim();
    }

    private String normalizeBindingMode(AgentChatV2Request.ContextBinding binding) {
        String mode = binding == null || binding.getMode() == null ? "AUTO" : binding.getMode().trim().toUpperCase(Locale.ROOT);
        if (!"AUTO".equals(mode) && !"NONE".equals(mode)
                && !"ACTIVE_REPORT".equals(mode) && !"LAST_ANSWER".equals(mode)) {
            throw new AgentChatConflictException("INVALID_CONTEXT_BINDING", "不支持的 context_binding.mode");
        }
        if ("ACTIVE_REPORT".equals(mode) && (binding == null || binding.getReportId() == null)) {
            throw new AgentChatConflictException("REPORT_ID_REQUIRED", "ACTIVE_REPORT 模式必须提供 report_id");
        }
        return mode;
    }

    private void validateAgentOwnership(JsonNode response, String requestId, String turnId) {
        if (!requestId.equals(response.path("request_id").asText(""))
                || !turnId.equals(response.path("turn_id").asText(""))) {
            throw new AgentChatConflictException("AGENT_TURN_MISMATCH", "Agent 响应不属于当前请求");
        }
    }

    private AgentChatV2Response mapResponse(AgentChatV2Request request, String conversationId, String assistantMessageId, String traceId, JsonNode source) {
        JsonNode sourceMessage = source.path("message");
        String content = sourceMessage.path("content").asText("").trim();
        if (content.isEmpty()) throw new AgentChatConflictException("INVALID_AGENT_RESPONSE", "Agent 返回了空消息");

        AgentChatV2Response response = new AgentChatV2Response();
        response.setStatus(source.path("status").asText("COMPLETED"));
        response.setRequestId(request.getRequestId());
        response.setTurnId(source.path("turn_id").asText());
        response.setThreadId(source.path("thread_id").asText(request.getThreadId()));
        response.setConversationId(conversationId);
        response.setTraceId(source.path("trace_id").asText(traceId));

        AgentChatV2Response.AssistantMessage message = new AgentChatV2Response.AssistantMessage();
        message.setMessageId(assistantMessageId);
        message.setRole("assistant");
        message.setContentType(sourceMessage.path("content_type").asText("text"));
        message.setContent(content);
        if (sourceMessage.has("structured_content") && !sourceMessage.get("structured_content").isNull()) {
            message.setStructuredContent(sourceMessage.get("structured_content"));
        }
        JsonNode snapshot = source.path("state_snapshot");
        JsonNode reportContext = snapshot.path("report_context");
        if (reportContext.isMissingNode() || reportContext.isEmpty()) {
            reportContext = snapshot.path("intent_result").path("report_context");
        }
        if ("LOADED".equalsIgnoreCase(reportContext.path("status").asText("")) && source.hasNonNull("report_id")) {
            message.setReportRef(new AgentChatV2Response.ReportRef(source.get("report_id").asLong(), "REFERENCED"));
        }
        message.setCreatedAt(LocalDateTime.now());
        response.setAssistantMessage(message);

        AgentChatV2Response.Execution execution = new AgentChatV2Response.Execution();
        JsonNode agentLoop = snapshot.path("agent_loop");
        execution.setStatus(agentLoop.path("status").asText(response.getStatus()));
        execution.setFinishReason(agentLoop.path("finish_reason").asText("final_answer"));
        response.setExecution(execution);
        return response;
    }

    private String requestHash(long userId, AgentChatV2Request request, String conversationId, String bindingMode, Long reportId) {
        String canonical = userId + "|" + request.getThreadId() + "|" + conversationId + "|"
                + request.getClientMessageId() + "|" + request.getMessage().getContent().trim() + "|"
                + bindingMode + "|" + String.valueOf(reportId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
