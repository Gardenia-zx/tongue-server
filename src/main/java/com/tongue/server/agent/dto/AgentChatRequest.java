package com.tongue.server.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;

public class AgentChatRequest {

    @NotBlank
    public String message;

    @JsonProperty("thread_id")
    public String threadId;

    @JsonProperty("conversation_id")
    public String conversationId;
}
