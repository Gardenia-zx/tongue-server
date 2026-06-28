package com.tongue.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongue.server.agent.context.entity.AgentChatMessageEntity;
import com.tongue.server.config.AgentProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AgentGatewayClientV2 {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AgentGatewayClientV2(AgentProperties properties, ObjectMapper objectMapper, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .build();
    }

    public JsonNode run(Invocation invocation) {
        ObjectNode payload = buildPayload(invocation);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    properties.getBaseUrl() + properties.getRunPath(),
                    new HttpEntity<JsonNode>(payload, headers),
                    JsonNode.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AgentGatewayException("AGENT_EMPTY_RESPONSE", "Agent returned an empty or non-2xx response");
            }
            return response.getBody();
        } catch (RestClientException ex) {
            throw new AgentGatewayException("AGENT_CALL_FAILED", ex.getMessage(), ex);
        }
    }

    ObjectNode buildPayload(Invocation invocation) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schema_version", "1.0");
        payload.put("request_id", invocation.getRequestId());
        payload.put("trace_id", invocation.getTraceId());
        payload.put("tenant_id", String.valueOf(invocation.getUserId()));
        payload.put("user_id", invocation.getUserId());
        payload.put("thread_id", invocation.getThreadId());
        payload.put("thread_epoch", invocation.getThreadEpoch());
        payload.put("turn_id", invocation.getTurnId());
        payload.put("user_message_id", invocation.getUserMessageId());
        payload.put("assistant_message_id", invocation.getAssistantMessageId());
        payload.put("conversation_id", invocation.getConversationId());
        if (invocation.getReportId() != null) payload.put("report_id", invocation.getReportId());

        ObjectNode message = payload.putObject("message");
        message.put("message_id", invocation.getUserMessageId());
        message.put("role", "user");
        message.put("content_type", "text");
        message.put("content", invocation.getContent());
        message.putArray("attachments");

        ObjectNode clientContext = payload.putObject("client_context");
        clientContext.put("page", "analysis");
        clientContext.put("locale", "zh-CN");
        clientContext.put("report_context_mode", invocation.getReportContextMode());
        if (invocation.getReportId() != null) clientContext.put("active_report_id", invocation.getReportId());
        clientContext.set("extra", objectMapper.valueToTree(invocation.getClientContext()));

        String contextMode = invocation.getContextMode();
        boolean mysqlRecovery = "mysql_recovery".equals(contextMode);
        if (mysqlRecovery || invocation.getActiveReportRef() != null || invocation.getLoadedReportSections() != null) {
            ObjectNode contextBundle = payload.putObject("context_bundle");
            if (mysqlRecovery) {
                contextBundle.put("mode", "mysql_recovery");
                contextBundle.put("conversation_id", invocation.getConversationId());
                contextBundle.set("recent_messages", toRecentMessages(invocation.getRecentMessages()));
            }
            if (invocation.getActiveReportRef() != null) {
                contextBundle.set("active_report_ref", invocation.getActiveReportRef());
            }
            if (invocation.getLoadedReportSections() != null) {
                contextBundle.set("loaded_report_sections", invocation.getLoadedReportSections());
            }
        }

        ObjectNode options = payload.putObject("options");
        options.putObject("context").put("mode", contextMode);
        ObjectNode memoryOptions = options.putObject("memory");
        memoryOptions.put("can_read", invocation.isMemoryCanRead());
        memoryOptions.put("can_write", invocation.isMemoryCanWrite());
        return payload;
    }

    private ArrayNode toRecentMessages(List<AgentChatMessageEntity> messages) {
        ArrayNode result = objectMapper.createArrayNode();
        if (messages == null) return result;
        for (AgentChatMessageEntity item : messages) {
            ObjectNode message = result.addObject();
            message.put("message_id", item.getMessageId());
            message.put("role", item.getRole().toLowerCase());
            message.put("content_type", item.getContentType());
            message.put("content", item.getContent());
            if (item.getReportId() != null) message.put("report_id", item.getReportId());
        }
        return result;
    }

    public static class Invocation {
        private long userId;
        private String requestId;
        private String traceId;
        private String turnId;
        private String threadId;
        private int threadEpoch = 1;
        private String conversationId;
        private String userMessageId;
        private String assistantMessageId;
        private String content;
        private String contextBinding;
        private String reportContextMode = "AUTO";
        private Long reportId;
        private JsonNode activeReportRef;
        private JsonNode loadedReportSections;
        private List<AgentChatMessageEntity> recentMessages;
        private Map<String, Object> clientContext;
        private String contextMode = "stateful";
        private boolean memoryCanRead = true;
        private boolean memoryCanWrite = true;

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getTurnId() { return turnId; }
        public void setTurnId(String turnId) { this.turnId = turnId; }
        public String getThreadId() { return threadId; }
        public void setThreadId(String threadId) { this.threadId = threadId; }
        public int getThreadEpoch() { return threadEpoch; }
        public void setThreadEpoch(int threadEpoch) { this.threadEpoch = threadEpoch; }
        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
        public String getUserMessageId() { return userMessageId; }
        public void setUserMessageId(String userMessageId) { this.userMessageId = userMessageId; }
        public String getAssistantMessageId() { return assistantMessageId; }
        public void setAssistantMessageId(String assistantMessageId) { this.assistantMessageId = assistantMessageId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getContextBinding() { return contextBinding; }
        public void setContextBinding(String contextBinding) { this.contextBinding = contextBinding; }
        public String getReportContextMode() {
            return reportContextMode == null || reportContextMode.trim().isEmpty() ? "AUTO" : reportContextMode.trim().toUpperCase();
        }
        public void setReportContextMode(String reportContextMode) { this.reportContextMode = reportContextMode; }
        public Long getReportId() { return reportId; }
        public void setReportId(Long reportId) { this.reportId = reportId; }
        public JsonNode getActiveReportRef() { return activeReportRef; }
        public void setActiveReportRef(JsonNode activeReportRef) { this.activeReportRef = activeReportRef; }
        public JsonNode getLoadedReportSections() { return loadedReportSections; }
        public void setLoadedReportSections(JsonNode loadedReportSections) { this.loadedReportSections = loadedReportSections; }
        public List<AgentChatMessageEntity> getRecentMessages() { return recentMessages; }
        public void setRecentMessages(List<AgentChatMessageEntity> recentMessages) { this.recentMessages = recentMessages; }
        public Map<String, Object> getClientContext() { return clientContext; }
        public void setClientContext(Map<String, Object> clientContext) { this.clientContext = clientContext; }
        public String getContextMode() {
            return contextMode == null || contextMode.trim().isEmpty() ? "stateful" : contextMode.trim().toLowerCase();
        }
        public void setContextMode(String contextMode) { this.contextMode = contextMode; }
        public boolean isMemoryCanRead() { return memoryCanRead; }
        public void setMemoryCanRead(boolean memoryCanRead) { this.memoryCanRead = memoryCanRead; }
        public boolean isMemoryCanWrite() { return memoryCanWrite; }
        public void setMemoryCanWrite(boolean memoryCanWrite) { this.memoryCanWrite = memoryCanWrite; }
    }

    public static class AgentGatewayException extends RuntimeException {
        private final String code;
        public AgentGatewayException(String code, String message) { super(message); this.code = code; }
        public AgentGatewayException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String getCode() { return code; }
    }
}
