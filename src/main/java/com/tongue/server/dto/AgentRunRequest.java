package com.tongue.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class AgentRunRequest {

    @JsonProperty("schema_version")
    private String schemaVersion = "1.0";
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("trace_id")
    private String traceId;
    @JsonProperty("tenant_id")
    private String tenantId;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("thread_id")
    private String threadId;
    @JsonProperty("thread_epoch")
    private Integer threadEpoch = 1;
    @JsonProperty("turn_id")
    private String turnId;
    @JsonProperty("user_message_id")
    private String userMessageId;
    @JsonProperty("assistant_message_id")
    private String assistantMessageId;
    @JsonProperty("request_hash")
    private String requestHash;
    @JsonProperty("reset_reason")
    private String resetReason;
    @JsonProperty("conversation_id")
    private String conversationId;
    @JsonProperty("report_id")
    private Long reportId;
    @JsonProperty("task_id")
    private Long taskId;
    @JsonProperty("task_version")
    private Integer taskVersion;
    private AgentMessage message;
    @JsonProperty("client_context")
    private AgentClientContext clientContext;
    @JsonProperty("context_bundle")
    private Map<String, Object> contextBundle;
    private Map<String, Object> options;

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public Integer getThreadEpoch() {
        return threadEpoch;
    }

    public void setThreadEpoch(Integer threadEpoch) {
        this.threadEpoch = threadEpoch;
    }

    public String getTurnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId;
    }

    public String getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(String userMessageId) {
        this.userMessageId = userMessageId;
    }

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResetReason() {
        return resetReason;
    }

    public void setResetReason(String resetReason) {
        this.resetReason = resetReason;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(Integer taskVersion) {
        this.taskVersion = taskVersion;
    }

    public AgentMessage getMessage() {
        return message;
    }

    public void setMessage(AgentMessage message) {
        this.message = message;
    }

    public AgentClientContext getClientContext() {
        return clientContext;
    }

    public void setClientContext(AgentClientContext clientContext) {
        this.clientContext = clientContext;
    }

    public Map<String, Object> getContextBundle() {
        return contextBundle;
    }

    public void setContextBundle(Map<String, Object> contextBundle) {
        this.contextBundle = contextBundle;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public static class AgentMessage {
        @JsonProperty("message_id")
        private String messageId;
        private String role;
        @JsonProperty("content_type")
        private String contentType;
        private String content;
        private List<AgentAttachment> attachments;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<AgentAttachment> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<AgentAttachment> attachments) {
            this.attachments = attachments;
        }
    }

    public static class AgentAttachment {
        @JsonProperty("file_id")
        private Long fileId;
        @JsonProperty("file_type")
        private String fileType;
        private String purpose;

        public Long getFileId() {
            return fileId;
        }

        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }
    }

    public static class AgentClientContext {
        private String page;
        @JsonProperty("active_report_id")
        private Long activeReportId;
        @JsonProperty("device_type")
        private String deviceType;
        private String locale;
        private Map<String, Object> extra;

        public String getPage() {
            return page;
        }

        public void setPage(String page) {
            this.page = page;
        }

        public Long getActiveReportId() {
            return activeReportId;
        }

        public void setActiveReportId(Long activeReportId) {
            this.activeReportId = activeReportId;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra;
        }
    }
}
