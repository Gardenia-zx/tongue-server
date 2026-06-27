package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentChatV2Request {

    @NotBlank
    @JsonProperty("request_id")
    private String requestId;

    @NotBlank
    @JsonProperty("client_message_id")
    private String clientMessageId;

    @NotBlank
    @JsonProperty("thread_id")
    private String threadId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @Valid
    @NotNull
    private Message message;

    @Valid
    @JsonProperty("context_binding")
    private ContextBinding contextBinding = new ContextBinding();

    @JsonProperty("client_context")
    private Map<String, Object> clientContext = new LinkedHashMap<String, Object>();

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public ContextBinding getContextBinding() { return contextBinding; }
    public void setContextBinding(ContextBinding contextBinding) { this.contextBinding = contextBinding; }
    public Map<String, Object> getClientContext() { return clientContext; }
    public void setClientContext(Map<String, Object> clientContext) { this.clientContext = clientContext; }

    public static class Message {
        private String role = "user";

        @JsonProperty("content_type")
        private String contentType = "text";

        @NotBlank
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ContextBinding {
        private String mode = "NONE";

        @JsonProperty("report_id")
        private Long reportId;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Long getReportId() { return reportId; }
        public void setReportId(Long reportId) { this.reportId = reportId; }
    }
}
