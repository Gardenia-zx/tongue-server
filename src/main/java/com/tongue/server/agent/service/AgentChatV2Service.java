package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import com.tongue.server.agent.context.entity.AgentChatTurnEntity;
import com.tongue.server.agent.dto.AgentChatV2Request;
import com.tongue.server.agent.dto.AgentChatV2Response;
import com.tongue.server.agent.service.AgentChatTurnStore.AgentChatConflictException;
import com.tongue.server.agent.service.AgentChatTurnStore.BeginTurnResult;
import com.tongue.server.agent.service.AgentGatewayClientV2.AgentGatewayException;
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
        Long requestedReportId = "ACTIVE_REPORT".equals(bindingMode)
                ? request.getContextBinding().getReportId()
                : null;

        String turnId = "turn_" + UUID.randomUUID();
        String assistantMessageId = "msg_assistant_" + UUID.randomUUID();
        String traceId = "trace_" + UUID.randomUUID();
        String requestHash = requestHash(userId, request, conversationId, bindingMode, requestedReportId);

        BeginTurnResult begin = turnStore.begin(
                userId,
                request,
                conversationId,
                turnId,
                assistantMessageId,
                traceId,
                requestHash,
                bindingMode,
                requestedReportId
        );
        if (!begin.isNewTurn()) {
            return begin.getReplayResponse();
        }

        AgentChatTurnEntity turn = begin.getTurn();
        Long boundReportId = turn.getBoundReportId();
        try {
            JsonNode activeReportRef = boundReportId == null
                    ? null
                    : turnStore.loadTrustedReportRef(userId, boundReportId);
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
                    ? Collections.<String, Object>emptyMap()
                    : request.getClientContext());

            JsonNode agentResponse = gatewayClient.run(invocation);
            validateAgentOwnership(agentResponse, request.getRequestId(), turnId);
            AgentChatV2Response response = mapResponse(
                    request,
                    conversationId,
                    assistantMessageId,
                    traceId,
                    agentResponse
            );

            Long responseReportId = response.getAssistantMessage().getReportRef() == null
                    ? null
                    : response.getAssistantMessage().getReportRef().getReportId();
            if (responseReportId != null) {
                turnStore.loadTrustedReportRef(userId, responseReportId);
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
                ? "AUTO"
                : binding.getMode().trim().toUpperCase(Locale.ROOT);
        if (!"AUTO".equals(mode)
                && !"NONE".equals(mode)
                && !"ACTIVE_REPORT".equals(mode)
                && !"LAST_ANSWER".equals(mode)) {
            throw new AgentChatConflictException("INVALID_CONTEXT_BINDING", "不支持的 context_binding.mode");
        }
        if ("ACTIVE_REPORT".equals(mode) && (binding == null || binding.getReportId() == null)) {
            throw new AgentChatConflictException("REPORT_ID_REQUIRED", "ACTIVE_REPORT 模式必须提供 report_id");
        }
        return mode;
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

        JsonNode reportContext = source.path("state_snapshot").path("report_context");
        boolean loaded = "LOADED".equalsIgnoreCase(reportContext.path("status").asText(""));
        if (loaded && source.hasNonNull("report_id")) {
            message.setReportRef(new AgentChatV2Response.ReportRef(
                    source.get("report_id").asLong(),
                    "REFERENCED"
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
