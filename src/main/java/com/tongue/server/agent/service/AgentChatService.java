package com.tongue.server.agent.service;

import com.tongue.server.agent.dto.AgentChatRequest;
import com.tongue.server.agent.dto.AgentChatResponse;
import com.tongue.server.agent.context.entity.AgentConversationEntity;
import com.tongue.server.agent.context.entity.AgentMessageEntity;
import com.tongue.server.agent.context.service.ConversationContextService;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.client.TongueAgentClient;
import com.tongue.server.dto.AgentRunRequest;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.AgentTurnAckRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentChatService {

    private final TongueAgentClient tongueAgentClient;
    private final ConversationContextService conversationContextService;

    public AgentChatService(
            TongueAgentClient tongueAgentClient,
            ConversationContextService conversationContextService
    ) {
        this.tongueAgentClient = tongueAgentClient;
        this.conversationContextService = conversationContextService;
    }

    public AgentChatResponse chat(AgentChatRequest chatRequest) {
        Long userId = AuthContext.requireUserId();
        String messageText = chatRequest.message == null ? "" : chatRequest.message.trim();
        AgentConversationEntity conversation = conversationContextService.ensureConversation(
                userId,
                chatRequest.conversationId,
                chatRequest.threadId
        );
        String clientRequestId = StringUtils.hasText(chatRequest.clientRequestId)
                ? chatRequest.clientRequestId.trim()
                : UUID.randomUUID().toString();
        String userMessageId = "user_" + clientRequestId;
        String assistantMessageId = "assistant_" + clientRequestId;
        String turnId = String.valueOf(conversation.id) + ":" + userMessageId + ":" + assistantMessageId;
        Map<String, Object> contextBundle =
                conversationContextService.buildContextBundleForChat(conversation, messageText);

        AgentRunRequest.AgentMessage message = new AgentRunRequest.AgentMessage();
        message.setMessageId(userMessageId);
        message.setRole("user");
        message.setContentType("text");
        message.setContent(messageText.trim());
        message.setAttachments(new ArrayList<AgentRunRequest.AgentAttachment>());

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("source", "tongue-server-chat");
        extra.put("context_bundle", contextBundle);

        AgentRunRequest.AgentClientContext clientContext = new AgentRunRequest.AgentClientContext();
        clientContext.setPage("ai_chat");
        clientContext.setActiveReportId(conversation.activeReportId);
        clientContext.setDeviceType("web");
        clientContext.setLocale("zh-CN");
        clientContext.setExtra(extra);

        Map<String, Object> memory = new LinkedHashMap<String, Object>();
        memory.put("can_read", true);
        memory.put("can_write", false);
        Map<String, Object> contextOptions = new LinkedHashMap<String, Object>();
        contextOptions.put("mode", "stateless");
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("memory", memory);
        options.put("context", contextOptions);

        AgentRunRequest request = new AgentRunRequest();
        request.setSchemaVersion("1.0");
        request.setRequestId(clientRequestId);
        request.setTraceId("trace_chat_" + UUID.randomUUID());
        request.setTenantId(String.valueOf(userId));
        request.setUserId(userId);
        request.setThreadId(conversation.threadId);
        request.setThreadEpoch(conversation.threadEpoch == null ? 1 : conversation.threadEpoch);
        request.setTurnId(turnId);
        request.setUserMessageId(userMessageId);
        request.setAssistantMessageId(assistantMessageId);
        request.setConversationId(String.valueOf(conversation.id));
        request.setReportId(conversation.activeReportId);
        request.setMessage(message);
        request.setClientContext(clientContext);
        request.setContextBundle(contextBundle);
        request.setOptions(options);

        Map<String, Object> userMetadata = new LinkedHashMap<String, Object>();
        userMetadata.put("source", "web_chat");
        userMetadata.put("turn_id", turnId);
        userMetadata.put("assistant_message_id", assistantMessageId);
        conversationContextService.appendUserMessage(
                conversation,
                messageText,
                "text",
                null,
                conversation.activeReportId,
                userMetadata,
                userMessageId
        );

        AgentRunResponse agentResponse = tongueAgentClient.runAgent(request);
        String content = extractContent(agentResponse);
        Map<String, Object> structuredContent = extractStructuredContent(agentResponse);
        if (!StringUtils.hasText(content)) {
            AgentMessageEntity existingAssistant = resolveAssistantFromResponseRef(
                    conversation,
                    agentResponse
            );
            if (existingAssistant != null) {
                content = existingAssistant.content;
            }
        }
        Map<String, Object> nextAction = safeMap(agentResponse.getNextAction());
        Map<String, Object> payload = safeMap(nextAction.get("payload"));
        Map<String, Object> stateSnapshot = safeMap(agentResponse.getStateSnapshot());
        Map<String, Object> assistantMetadata = new LinkedHashMap<String, Object>();
        assistantMetadata.put("source", "python_agent");
        assistantMetadata.put("status", agentResponse.getStatus());
        assistantMetadata.put("node_name", stateSnapshot.get("current_node"));
        assistantMetadata.put("route_target", payload.get("route_target"));
        assistantMetadata.put("answer_type", payload.get("answer_type"));
        assistantMetadata.put("rag_query", payload.get("rag_query"));
        assistantMetadata.put("report_id", conversation.activeReportId);
        assistantMetadata.put("turn_id", turnId);
        assistantMetadata.put("response_hash", agentResponse.getResponseHash());
        assistantMetadata.put("user_message_id", userMessageId);
        assistantMetadata.put("assistant_message_id", assistantMessageId);
        if (structuredContent != null && !structuredContent.isEmpty()) {
            assistantMetadata.put("structured_content", structuredContent);
        }
        if (StringUtils.hasText(content) && !isResponseRefReplay(agentResponse)) {
            conversationContextService.appendAssistantMessageAndMemoryOutbox(
                    conversation,
                    content,
                    conversation.activeReportId,
                    assistantMetadata,
                    assistantMessageId,
                    String.valueOf(userId),
                    turnId,
                    userMessageId,
                    conversation.threadEpoch == null ? 1 : conversation.threadEpoch
            );
            ackAgentTurn(agentResponse, assistantMessageId);
        }

        AgentChatResponse response = new AgentChatResponse();
        response.status = agentResponse.getStatus();
        response.threadId = conversation.threadId;
        response.conversationId = String.valueOf(conversation.id);
        response.intentResult = agentResponse.getIntentResult();
        response.nextAction = agentResponse.getNextAction();
        response.content = content;
        response.structuredContent = structuredContent;
        return response;
    }

    private boolean isResponseRefReplay(AgentRunResponse agentResponse) {
        return agentResponse.getMessage() == null
                && agentResponse.getResponseRef() != null
                && !agentResponse.getResponseRef().isEmpty();
    }

    private AgentMessageEntity resolveAssistantFromResponseRef(
            AgentConversationEntity conversation,
            AgentRunResponse agentResponse
    ) {
        Map<String, Object> responseRef = safeMap(agentResponse.getResponseRef());
        Object value = responseRef.get("assistant_message_id");
        if (value == null) {
            return null;
        }
        return conversationContextService
                .findMessageByExternalId(conversation, String.valueOf(value))
                .orElse(null);
    }

    private void ackAgentTurn(
            AgentRunResponse agentResponse,
            String assistantMessageId
    ) {
        if (!StringUtils.hasText(agentResponse.getTenantId())
                || !StringUtils.hasText(agentResponse.getTurnId())
                || !StringUtils.hasText(agentResponse.getResponseHash())) {
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
            // Python keeps the encrypted response snapshot until ACK succeeds or reconciliation handles it.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }

    private String extractContent(AgentRunResponse agentResponse) {
        Map<String, Object> message = agentResponse.getMessage();
        if (message == null) {
            return "";
        }
        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStructuredContent(AgentRunResponse agentResponse) {
        Map<String, Object> message = agentResponse.getMessage();
        if (message == null) {
            return null;
        }
        Object structuredContent = message.get("structured_content");
        if (structuredContent instanceof Map) {
            return (Map<String, Object>) structuredContent;
        }
        return null;
    }
}
