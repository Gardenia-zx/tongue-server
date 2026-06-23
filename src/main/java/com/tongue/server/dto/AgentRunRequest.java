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
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("thread_id")
    private String threadId;
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

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public static class AgentMessage {
        private String role;
        @JsonProperty("content_type")
        private String contentType;
        private String content;
        private List<AgentAttachment> attachments;

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
