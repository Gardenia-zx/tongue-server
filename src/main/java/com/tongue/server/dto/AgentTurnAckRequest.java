package com.tongue.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AgentTurnAckRequest {

    @JsonProperty("tenant_id")
    private String tenantId;
    @JsonProperty("turn_id")
    private String turnId;
    @JsonProperty("assistant_message_id")
    private String assistantMessageId;
    @JsonProperty("response_hash")
    private String responseHash;

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

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public void setResponseHash(String responseHash) {
        this.responseHash = responseHash;
    }
}
