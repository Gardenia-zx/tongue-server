package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentChatV2Response {

    private String status;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("turn_id")
    private String turnId;

    @JsonProperty("thread_id")
    private String threadId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("assistant_message")
    private AssistantMessage assistantMessage;

    private Execution execution;

    @JsonProperty("trace_id")
    private String traceId;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTurnId() { return turnId; }
    public void setTurnId(String turnId) { this.turnId = turnId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public AssistantMessage getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(AssistantMessage assistantMessage) { this.assistantMessage = assistantMessage; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AssistantMessage {
        @JsonProperty("message_id")
        private String messageId;
        private String role = "assistant";
        @JsonProperty("content_type")
        private String contentType = "text";
        private String content;
        @JsonProperty("structured_content")
        private JsonNode structuredContent;
        @JsonProperty("report_ref")
        private ReportRef reportRef;
        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public JsonNode getStructuredContent() { return structuredContent; }
        public void setStructuredContent(JsonNode structuredContent) { this.structuredContent = structuredContent; }
        public ReportRef getReportRef() { return reportRef; }
        public void setReportRef(ReportRef reportRef) { this.reportRef = reportRef; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class ReportRef {
        @JsonProperty("report_id")
        private Long reportId;
        private String relation;

        public ReportRef() {}
        public ReportRef(Long reportId, String relation) {
            this.reportId = reportId;
            this.relation = relation;
        }
        public Long getReportId() { return reportId; }
        public void setReportId(Long reportId) { this.reportId = reportId; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
    }

    public static class Execution {
        private String status;
        @JsonProperty("finish_reason")
        private String finishReason;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    }
}
