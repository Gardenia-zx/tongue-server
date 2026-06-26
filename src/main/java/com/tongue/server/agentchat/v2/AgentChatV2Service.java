package com.tongue.server.agentchat.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.tongue.server.agentchat.v2.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agentchat.v2.AgentChatTurnStore.BeginTurnResult;
import com.tongue.server.agentchat.v2.AgentGatewayClientV2.AgentGatewayException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AgentChatV2Service {

    private final AgentChatTurnStore turnStore;
    private final AgentGatewayClientV2 gatewayClient;

    public AgentChatV2Service(AgentChatTurnStore turnStore, AgentGatewayClientV2 gatewayClient) {
        this.turnStore = turnStore;
        this.gatewayClient = gatewayClient;
    }

    public AgentChatV2Response chat(long userId, AgentChatV2Request request) {
        validateRequest(request);

        String conversationId = normalizeConversationId(request);
        String bindingMode = normalizeBindingMode(request.getContextBinding());
        Long boundReportId = "ACTIVE_REPORT".equals(bindingMode)
                ? request.getContextBinding().getReportId()
                : null;
        JsonNode activeReport = boundReportId == null
                ? null
                : turnStore.loadOwnedReport(userId, boundReportId);

        String turnId = "turn_" + UUID.randomUUID().toString();
        String assistantMessageId = "msg_assistant_" + UUID.randomUUID().toString();
        String traceId = "trace_" + UUID.randomUUID().toString();
        String requestHash = requestHash(userId, request, conversationId, bindingMode, boundReportId);

        BeginTurnResult begin = turnStore.begin(
                userId,
                request,
                conversationId,
                turnId,
                assistantMessageId,
                traceId,
                requestHash,
                bindingMode,
                boundReportId
        );
        if (!begin.isNewTurn()) {
            return begin.getReplayResponse();
        }

        AgentTurnEntity turn = begin.getTurn();
        try {
            List<AgentMessageEntity> recentMessages;
            if (shouldLoadHistory(bindingMode)) {
                recentMessages = turnStore.recentMessages(userId, conversationId);
                recentMessages.removeIf(item -> turnId.equals(item.getTurnId()));
            } else {
                recentMessages = Collections.emptyList();
            }

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
            invocation.setReportId(boundReportId);
            invocation.setActiveReport(activeReport);
            invocation.setRecentMessages(recentMessages);
            invocation.setClientContext(request.getClientContext() == null
                    ? Collections.<String, Object>emptyMap()
                    : request.getClientContext());

            JsonNode agentResponse = gatewayClient.run(invocation);
            validateAgentOwnership(agentResponse, request.getRequestId(), turnId);
            AgentChatV2Response response = mapResponse(
                    request,
                    conversationId,
                    assistantMessageId,
                    traceId,
                    bindingMode,
                    boundReportId,
                    agentResponse
            );

            Long responseReportId = response.getAssistantMessage().getReportRef() == null
                    ? null
                    : response.getAssistantMessage().getReportRef().getReportId();
            if (responseReportId != null) {
                turnStore.loadOwnedReport(userId, responseReportId);
            }
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

    private void validateRequest(AgentChatV2Request request) {
        if (request.getMessage() == null || request.getMessage().getContent() == null
                || request.getMessage().getContent().trim().isEmpty()) {
            throw new AgentChatConflictException("INVALID_MESSAGE", "消息内容不能为空");
        }
        if (request.getMessage().getRole() != null
                && !"user".equalsIgnoreCase(request.getMessage().getRole())) {
            throw new AgentChatConflictException("INVALID_MESSAGE_ROLE", "客户端只能提交 user 消息");
        }
    }

    private String normalizeConversationId(AgentChatV2Request request) {
        String value = request.getConversationId();
        if (value == null || value.trim().isEmpty()) {
            return "conversation_" + request.getThreadId();
        }
        return value.trim();
    }

    private String normalizeBindingMode(AgentChatV2Request.ContextBinding binding) {
        String mode = binding == null || binding.getMode() == null
                ? "NONE"
                : binding.getMode().trim().toUpperCase(Locale.ROOT);
        if (!"NONE".equals(mode) && !"ACTIVE_REPORT".equals(mode) && !"LAST_ANSWER".equals(mode)) {
            throw new AgentChatConflictException("INVALID_CONTEXT_BINDING", "不支持的 context_binding.mode");
        }
        if ("ACTIVE_REPORT".equals(mode) && (binding == null || binding.getReportId() == null)) {
            throw new AgentChatConflictException("REPORT_ID_REQUIRED", "ACTIVE_REPORT 模式必须提供 report_id");
        }
        return mode;
    }

    private boolean shouldLoadHistory(String bindingMode) {
        return "LAST_ANSWER".equals(bindingMode) || "ACTIVE_REPORT".equals(bindingMode);
    }

    private void validateAgentOwnership(JsonNode response, String requestId, String turnId) {
        String actualRequestId = response.path("request_id").asText("");
        String actualTurnId = response.path("turn_id").asText("");
        if (!requestId.equals(actualRequestId) || !turnId.equals(actualTurnId)) {
            throw new AgentChatConflictException(
                    "AGENT_TURN_MISMATCH",
                    "Agent 响应的 request_id 或 turn_id 与当前请求不一致"
            );
        }
    }

    private AgentChatV2Response mapResponse(
            AgentChatV2Request request,
            String conversationId,
            String assistantMessageId,
            String traceId,
            String bindingMode,
            Long boundReportId,
            JsonNode source
    ) {
        JsonNode sourceMessage = source.path("message");
        String content = sourceMessage.path("content").asText("").trim();
        if (content.isEmpty()) {
            throw new AgentChatConflictException("INVALID_AGENT_RESPONSE", "Agent 返回了空消息");
        }

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
        Long responseReportId = source.hasNonNull("report_id")
                ? source.get("report_id").asLong()
                : boundReportId;
        if (responseReportId != null && ("ACTIVE_REPORT".equals(bindingMode) || source.hasNonNull("report_id"))) {
            message.setReportRef(new AgentChatV2Response.ReportRef(
                    responseReportId,
                    source.hasNonNull("report_id") && boundReportId == null ? "GENERATED" : "REFERENCED"
            ));
        }
        message.setCreatedAt(LocalDateTime.now());
        response.setAssistantMessage(message);

        AgentChatV2Response.Execution execution = new AgentChatV2Response.Execution();
        JsonNode agentLoop = source.path("state_snapshot").path("agent_loop");
        execution.setStatus(agentLoop.path("status").asText(response.getStatus()));
        execution.setFinishReason(agentLoop.path("finish_reason").asText("final_answer"));
        response.setExecution(execution);
        return response;
    }

    private String requestHash(
            long userId,
            AgentChatV2Request request,
            String conversationId,
            String bindingMode,
            Long reportId
    ) {
        String canonical = userId + "|" + request.getThreadId() + "|" + conversationId + "|"
                + request.getClientMessageId() + "|" + request.getMessage().getContent().trim() + "|"
                + bindingMode + "|" + String.valueOf(reportId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
