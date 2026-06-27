package com.tongue.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class AgentRunResponse {

    @JsonProperty("schema_version")
    private String schemaVersion;
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("trace_id")
    private String traceId;
    @JsonProperty("tenant_id")
    private String tenantId;
    @JsonProperty("turn_id")
    private String turnId;
    @JsonProperty("response_hash")
    private String responseHash;
    @JsonProperty("response_ref")
    private Map<String, Object> responseRef;
    @JsonProperty("thread_id")
    private String threadId;
    @JsonProperty("thread_epoch")
    private Integer threadEpoch;
    @JsonProperty("conversation_id")
    private String conversationId;
    @JsonProperty("report_id")
    private Long reportId;
    @JsonProperty("task_id")
    private Long taskId;
    private String status;
    @JsonProperty("intent_result")
    private Map<String, Object> intentResult;
    private Map<String, Object> message;
    @JsonProperty("next_action")
    private Map<String, Object> nextAction;
    @JsonProperty("state_snapshot")
    private Map<String, Object> stateSnapshot;

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

    public String getTurnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public void setResponseHash(String responseHash) {
        this.responseHash = responseHash;
    }

    public Map<String, Object> getResponseRef() {
        return responseRef;
    }

    public void setResponseRef(Map<String, Object> responseRef) {
        this.responseRef = responseRef;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getIntentResult() {
        return intentResult;
    }

    public void setIntentResult(Map<String, Object> intentResult) {
        this.intentResult = intentResult;
    }

    public Map<String, Object> getMessage() {
        return message;
    }

    public void setMessage(Map<String, Object> message) {
        this.message = message;
    }

    public Map<String, Object> getNextAction() {
        return nextAction;
    }

    public void setNextAction(Map<String, Object> nextAction) {
        this.nextAction = nextAction;
    }

    public Map<String, Object> getStateSnapshot() {
        return stateSnapshot;
    }

    public void setStateSnapshot(Map<String, Object> stateSnapshot) {
        this.stateSnapshot = stateSnapshot;
    }
}
